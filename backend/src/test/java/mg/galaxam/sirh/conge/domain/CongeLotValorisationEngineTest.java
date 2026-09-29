package mg.galaxam.sirh.conge.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CongeLotValorisationEngineTest {
    private final CongeLotValorisationEngine engine = new CongeLotValorisationEngine();
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static CongeLotValorisationEngine.Mouvement m(int id, String type, String jours,
            String date, Integer demande, String commentaire, String valeur) {
        return new CongeLotValorisationEngine.Mouvement(id, type, n(jours), LocalDate.parse(date),
                demande, commentaire, valeur == null ? null : n(valeur));
    }

    @Test
    void sansStockInitialConsommeLaValeurHistoriqueDuPremierLot() {
        var result = engine.calculer(List.of(
                m(1, "ACQUISITION", "2.50", "2026-01-28", null, null, "25000.00"),
                m(2, "PRISE", "-1.00", "2026-02-01", 12, null, null)));
        assertEquals(0, result.soldeJours().compareTo(n("1.50")));
        assertEquals(0, result.soldeValorisation().compareTo(n("15000.00")));
        assertEquals(1, result.imputations().get(0).idSource());
        assertEquals(0, result.imputations().get(0).valeur().compareTo(n("10000.00")));
    }

    @Test
    void reprisePuisDeuxAcquisitionsTraversentTroisTauxSansRevalorisation() {
        var result = engine.calculer(List.of(
                m(1, "AJUSTEMENT", "1.00", "2026-01-01", null,
                        "Reprise cutover - Solde initial au 2026-01-01", "9000.00"),
                m(2, "ACQUISITION", "2.00", "2026-01-28", null, null, "20000.00"),
                m(3, "ACQUISITION", "2.00", "2026-02-28", null, null, "24000.00"),
                m(4, "PRISE", "-4.00", "2026-03-01", 15, null, null)));
        assertEquals(List.of(1, 2, 3), result.imputations().stream()
                .map(CongeLotValorisationEngine.Imputation::idSource).toList());
        assertEquals(List.of(n("9000.00"), n("20000.00"), n("12000.00")), result.imputations().stream()
                .map(CongeLotValorisationEngine.Imputation::valeur).toList());
        assertEquals(0, result.soldeValorisation().compareTo(n("12000.00")));
        assertEquals(0, result.soldeJours().compareTo(n("1.00")));
    }

    @Test
    void annulationRestaureChaqueLotEtSaValeur() {
        var result = engine.calculer(List.of(
                m(1, "ACQUISITION", "1.00", "2026-01-28", null, null, "10000.00"),
                m(2, "ACQUISITION", "2.00", "2026-02-28", null, null, "24000.00"),
                m(3, "PRISE", "-2.00", "2026-03-01", 20, null, null),
                m(4, "ANNULATION", "2.00", "2026-03-02", 20, null, null)));
        assertEquals(0, result.soldeJours().compareTo(n("3.00")));
        assertEquals(0, result.soldeValorisation().compareTo(n("34000.00")));
        assertEquals(4, result.imputations().size());
    }

    @Test
    void reductionDuneDemandeValideeRestitueLaValeurDesDerniersLots() {
        var result = engine.calculer(List.of(
                m(1, "ACQUISITION", "1.00", "2026-01-28", null, null, "10000.00"),
                m(2, "ACQUISITION", "2.00", "2026-02-28", null, null, "24000.00"),
                m(3, "PRISE", "-3.00", "2026-03-01", 20, null, null),
                m(4, "AJUSTEMENT", "1.00", "2026-03-02", 20,
                        "Ajustement de solde suite modification durée de la demande #20", null)));
        assertEquals(0, result.soldeJours().compareTo(n("1.00")));
        assertEquals(0, result.soldeValorisation().compareTo(n("12000.00")));
        assertEquals(0, result.imputations().get(2).valeur().compareTo(n("-12000.00")));
    }

    @Test
    void correctionCutoverNeRevalorisePasLaPrisePassee() {
        var result = engine.calculer(List.of(
                m(1, "AJUSTEMENT", "2.00", "2026-01-01", null,
                        "[SOLDE_INITIAL] Reprise cutover", "18000.00"),
                m(2, "PRISE", "-1.00", "2026-02-01", 40, null, null),
                m(3, "CORRECTION_CUTOVER", "0.00", "2026-03-01", 1, null, "10000.00"),
                m(4, "PRISE", "-1.00", "2026-04-01", 41, null, null)));
        assertEquals(n("9000.00"), result.imputations().get(0).valeur());
        assertEquals(n("10000.00"), result.imputations().get(1).valeur());
        assertEquals(0, result.soldeValorisation().compareTo(BigDecimal.ZERO));
    }

    @Test
    void anticipationNeToucheQueLesJoursSansLotEtGardeSonTaux() {
        var result = engine.calculer(List.of(
                m(1, "ACQUISITION", "1.00", "2026-01-28", null, null, "9000.00"),
                m(2, "PRISE", "-2.00", "2026-02-01", 50, null, "11000.00"),
                m(3, "ACQUISITION", "2.00", "2026-03-28", null, null, "24000.00")));
        assertEquals(n("9000.00"), result.imputations().get(0).valeur());
        assertEquals(n("11000.00"), result.imputations().get(1).valeur());
        assertEquals("ANTICIPATION", result.imputations().get(1).origine());
        assertEquals(0, result.joursSansLot().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.soldeJours().compareTo(n("1.00")));
        assertEquals(0, result.soldeValorisation().compareTo(n("12000.00")));
    }

    @Test
    void annulationDuneAnticipationRestaureLeDroitAcquisEnsuite() {
        var result = engine.calculer(List.of(
                m(1, "PRISE", "-1.00", "2026-01-01", 51, null, "9000.00"),
                m(2, "ACQUISITION", "2.00", "2026-02-28", null, null, "24000.00"),
                m(3, "ANNULATION", "1.00", "2026-03-01", 51, null, null)));
        assertEquals(0, result.soldeJours().compareTo(n("2.00")));
        assertEquals(0, result.soldeValorisation().compareTo(n("24000.00")));
        assertEquals(0, result.joursSansLot().compareTo(BigDecimal.ZERO));
    }

    @Test
    void anticipationSansStockGardeUneValeurNegativeAuTauxFige() {
        var result = engine.calculer(List.of(
                m(1, "PRISE", "-2.00", "2026-01-01", 52, null, "9500.00")));
        assertEquals(0, result.soldeJours().compareTo(n("-2.00")));
        assertEquals(0, result.soldeValorisation().compareTo(n("-19000.00")));
    }
}
