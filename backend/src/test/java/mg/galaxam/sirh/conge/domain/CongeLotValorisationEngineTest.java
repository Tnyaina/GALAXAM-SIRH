package mg.galaxam.sirh.conge.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CongeLotValorisationEngineTest {
    private final CongeLotValorisationEngine engine = new CongeLotValorisationEngine();

    private static BigDecimal d(String value) { return new BigDecimal(value); }
    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, d(expected).compareTo(actual), "expected decimal " + expected + " but was " + actual);
    }

    private static CongeLotValorisationEngine.Mouvement movement(int id, String type, String days,
            String date, Integer request, String comment, String value) {
        return new CongeLotValorisationEngine.Mouvement(id, type, d(days), LocalDate.parse(date), request,
                comment, value == null ? null : d(value));
    }

    private static List<CongeLotValorisationEngine.Mouvement> twoHistoricalLots(String leaveDays) {
        return List.of(
                movement(1, "ACQUISITION", "5", "2024-12-31", null, "2024", "50000"),
                movement(2, "ACQUISITION", "10", "2025-12-31", null, "2025", "120000"),
                movement(3, "PRISE", "-" + leaveDays, "2026-02-01", 100, "Demande #100", "15000"));
    }

    @Test
    void tauxHistoriqueSixDecimalesConserveLaValorisationDuCas592() {
        var situation = engine.calculer(List.of(
                movement(1, "AJUSTEMENT", "3.00", "2026-09-25", null,
                        "[SOLDE_INITIAL] reprise", "95744.78"),
                movement(2, "PRISE", "-2.44", "2026-10-23", 592,
                        "Congé octobre", null)));
        assertDecimal("77872.42", situation.imputations().get(0).valeur());
    }

    @Test
    void repriseDetailleeConsommeLePlusAncienLotSelonSaDateOrigineEtSaValeur() {
        var situation = engine.calculer(List.of(
                new CongeLotValorisationEngine.Mouvement(1, "AJUSTEMENT", d("4"),
                        LocalDate.parse("2026-09-25"), null, "[SOLDE_INITIAL] lot récent", d("60000"),
                        LocalDate.parse("2025-12-31")),
                new CongeLotValorisationEngine.Mouvement(2, "AJUSTEMENT", d("3"),
                        LocalDate.parse("2026-09-25"), null, "[SOLDE_INITIAL] lot ancien", d("30000"),
                        LocalDate.parse("2024-12-31")),
                movement(3, "PRISE", "-5", "2026-10-01", 100, "Demande", "16000")));

        assertEquals(2, situation.imputations().get(0).idSource());
        assertDecimal("30000", situation.imputations().get(0).valeur());
        assertEquals(1, situation.imputations().get(1).idSource());
        assertDecimal("30000", situation.imputations().get(1).valeur());
        assertDecimal("30000", situation.soldeValorisation());
        assertEquals(LocalDate.parse("2025-12-31"), situation.lots().get(0).dateAcquisition());
    }

    @Test
    void consommationAncienLotSeulementRespecteValeurHistoriqueEtFifo() {
        var situation = engine.calculer(twoHistoricalLots("5"));

        assertDecimal("10", situation.soldeJours());
        assertDecimal("120000.00", situation.soldeValorisation());
        assertEquals(1, situation.imputations().size());
        var imputation = situation.imputations().get(0);
        assertEquals(1, imputation.idSource());
        assertDecimal("5", imputation.jours());
        assertDecimal("50000.00", imputation.valeur());
        assertEquals(1, situation.lots().size());
        assertDecimal("10", situation.lots().get(0).jours());
        assertDecimal("120000.00", situation.lots().get(0).valeur());
    }

    @Test
    void consommationTraverseDeuxLotsAvecLeurPropreValeurHistorique() {
        var situation = engine.calculer(twoHistoricalLots("8"));

        assertEquals(2, situation.imputations().size());
        assertEquals(1, situation.imputations().get(0).idSource());
        assertDecimal("5", situation.imputations().get(0).jours());
        assertDecimal("50000.00", situation.imputations().get(0).valeur());
        assertEquals(2, situation.imputations().get(1).idSource());
        assertDecimal("3", situation.imputations().get(1).jours());
        assertDecimal("36000.00", situation.imputations().get(1).valeur());
        assertDecimal("86000.00", situation.imputations().stream()
                .map(CongeLotValorisationEngine.Imputation::valeur).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertDecimal("7", situation.soldeJours());
        assertDecimal("84000.00", situation.soldeValorisation());
    }

    @Test
    void simulationLectureSeuleVentileLesLotsEtValoriseCommeUnePriseReelle() {
        var situation = engine.calculer(List.of(
                movement(1, "ACQUISITION", "5", "2024-12-31", null, "2024", "50000"),
                movement(2, "ACQUISITION", "10", "2025-12-31", null, "2025", "120000")));

        var simulation = engine.simulerPrise(situation, d("8"));

        assertDecimal("15", simulation.soldeAvant());
        assertDecimal("7", simulation.soldeApres());
        assertDecimal("86000.00", simulation.valeurEstimee());
        assertEquals(2, simulation.imputations().size());
        assertEquals(1, simulation.imputations().get(0).idSource());
        assertDecimal("5", simulation.imputations().get(0).jours());
        assertDecimal("50000.00", simulation.imputations().get(0).valeur());
        assertEquals(2, simulation.imputations().get(1).idSource());
        assertDecimal("3", simulation.imputations().get(1).jours());
        assertDecimal("36000.00", simulation.imputations().get(1).valeur());
        assertDecimal("15", situation.soldeJours());
        assertDecimal("170000.00", situation.soldeValorisation());
        var actual = engine.calculer(List.of(
                movement(1, "ACQUISITION", "5", "2024-12-31", null, "2024", "50000"),
                movement(2, "ACQUISITION", "10", "2025-12-31", null, "2025", "120000"),
                movement(3, "PRISE", "-8", "2026-02-01", 301, "committed take", null)));
        assertEquals(actual.imputations().stream().map(i -> i.idSource() + ":" + i.jours() + ":" + i.valeur()).toList(),
                simulation.imputations().stream().map(i -> i.idSource() + ":" + i.jours() + ":" + i.valeur()).toList());
        assertDecimal("86000.00", actual.imputations().stream().map(CongeLotValorisationEngine.Imputation::valeur)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void simulationConserveTauxHistoriqueEtReliquatArrondiDuLotPartiel() {
        var situation = engine.calculer(List.of(
                movement(1, "ACQUISITION", "3", "2024-01-01", null, "fractional historical value", "100.00"),
                movement(2, "PRISE", "-1", "2024-02-01", 201, "partial", null)));

        var simulation = engine.simulerPrise(situation, d("1"));

        assertDecimal("33.33", simulation.valeurEstimee());
        assertDecimal("2", situation.soldeJours());
        assertDecimal("66.67", situation.soldeValorisation());
    }

    @Test
    void simulationRespectePrioriteRepriseEtSignaleLeSoldeInsuffisantSansAnticipation() {
        var situation = engine.calculer(List.of(
                movement(1, "ACQUISITION", "4", "2024-01-01", null, "normal", "40000"),
                movement(2, "AJUSTEMENT", "2", "2024-06-01", null,
                        "[SOLDE_INITIAL] agrégé", "30000")));

        var simulation = engine.simulerPrise(situation, d("7"));

        assertDecimal("6", simulation.soldeAvant());
        assertDecimal("-1", simulation.soldeApres());
        assertDecimal("70000.00", simulation.valeurEstimee());
        assertEquals(false, simulation.soldeSuffisant());
        assertEquals(2, simulation.imputations().size());
        assertEquals(2, simulation.imputations().get(0).idSource());
        assertDecimal("2", simulation.imputations().get(0).jours());
        assertEquals(1, simulation.imputations().get(1).idSource());
        assertDecimal("4", simulation.imputations().get(1).jours());
    }

    @Test
    void epuisementExactConserveLaValeurRestanteApresPrisesPartielles() {
        var movements = List.of(
                movement(1, "ACQUISITION", "3", "2024-01-01", null, "fractional historical value", "100.00"),
                movement(2, "PRISE", "-1", "2024-02-01", 201, "partial", null),
                movement(3, "PRISE", "-2", "2024-03-01", 202, "exhaust lot", null));

        var situation = engine.calculer(movements);

        assertDecimal("0", situation.soldeJours());
        assertDecimal("0.00", situation.soldeValorisation());
        assertDecimal("33.33", situation.imputations().get(0).valeur());
        assertDecimal("66.67", situation.imputations().get(1).valeur());
        assertDecimal("100.00", situation.imputations().stream()
                .map(CongeLotValorisationEngine.Imputation::valeur).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void annulationRestitueLesMemesLotsEtValeursEtNeDoublePasLaConsommation() {
        var movements = List.of(
                movement(1, "ACQUISITION", "5", "2024-12-31", null, "2024", "50000"),
                movement(2, "ACQUISITION", "10", "2025-12-31", null, "2025", "120000"),
                movement(3, "PRISE", "-8", "2026-02-01", 100, "Demande #100", "15000"),
                movement(4, "ANNULATION", "8", "2026-02-02", 100, "annulation", null),
                movement(5, "PRISE", "-8", "2026-02-03", 101, "second demand", "15000"));

        var situation = engine.calculer(movements);

        assertDecimal("7", situation.soldeJours());
        assertDecimal("84000.00", situation.soldeValorisation());
        assertEquals(1, situation.lots().size());
        assertDecimal("7", situation.lots().get(0).jours());
        assertDecimal("84000.00", situation.lots().get(0).valeur());
        assertEquals(6, situation.imputations().size());
    }

    @Test
    void lotRepriseAgregeEstPrioritaireEtConserveValeurCertifieeDistincteDuTauxApplicable() {
        var movements = List.of(
                movement(2, "AJUSTEMENT", "5", "2024-06-30", null,
                        "[SOLDE_INITIAL] arrêté DAF", "70000"),
                movement(1, "ACQUISITION", "10", "2024-07-01", null, "post-cutover accrual", "100000"),
                movement(3, "PRISE", "-6", "2024-08-01", 301, "consume opening first", "12000"));

        var situation = engine.calculer(movements);

        assertEquals(2, situation.imputations().size());
        assertEquals(2, situation.imputations().get(0).idSource());
        assertDecimal("5", situation.imputations().get(0).jours());
        assertDecimal("70000.00", situation.imputations().get(0).valeur());
        assertDecimal("1", situation.imputations().get(1).jours());
        assertDecimal("10000.00", situation.imputations().get(1).valeur());
        assertDecimal("9", situation.soldeJours());
        assertDecimal("90000.00", situation.soldeValorisation());
        // The supplied 12,000 Ar TCONGE is only the fallback/reference rate on PRISE;
        // the opening lot keeps its certified implied rate of 14,000 Ar/day.
        assertDecimal("14000", situation.imputations().get(0).tauxHistorique());
    }

    @Test
    void correctionDeRepriseRemplaceLeReliquatEtSaValeurSansRecalculerLeTauxCertifie() {
        var movements = List.of(
                movement(1, "AJUSTEMENT", "5", "2024-01-01", null,
                        "[SOLDE_INITIAL] arrêté DAF", "70000"),
                movement(2, "PRISE", "-2", "2024-02-01", 501, "prise avant correction", "12000"),
                movement(3, "CORRECTION_CUTOVER", "1", "2024-03-01", 1,
                        "correction DAF", "60000"));

        var situation = engine.calculer(movements);

        assertDecimal("4", situation.soldeJours());
        assertDecimal("60000.00", situation.soldeValorisation());
        assertEquals(1, situation.lots().size());
        assertDecimal("4", situation.lots().get(0).jours());
        assertDecimal("60000.00", situation.lots().get(0).valeur());
        assertEquals(1, situation.imputations().size());
        assertDecimal("14000", situation.imputations().get(0).tauxHistorique());
    }

    @Test
    void priseAuDelaDuSoldeExigeUnTauxEtCreeUneAnticipationVisible() {
        var withoutRate = List.of(
                movement(1, "ACQUISITION", "2", "2025-01-01", null, "lot", "20000"),
                movement(2, "PRISE", "-3", "2025-02-01", 401, "excess", null));
        assertThrows(IllegalStateException.class, () -> engine.calculer(withoutRate));

        var withRate = List.of(
                movement(1, "ACQUISITION", "2", "2025-01-01", null, "lot", "20000"),
                movement(2, "PRISE", "-3", "2025-02-01", 401, "excess", "12000"));
        var situation = engine.calculer(withRate);
        assertDecimal("-1", situation.soldeJours());
        assertDecimal("-12000.00", situation.soldeValorisation());
        assertDecimal("1", situation.joursSansLot());
        assertEquals("ANTICIPATION", situation.imputations().get(1).origine());
        assertDecimal("12000.00", situation.imputations().get(1).valeur());
    }
}
