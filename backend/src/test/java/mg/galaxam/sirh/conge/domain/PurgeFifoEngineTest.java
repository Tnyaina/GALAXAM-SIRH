package mg.galaxam.sirh.conge.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PurgeFifoEngineTest {

    private final PurgeFifoEngine engine = new PurgeFifoEngine();

    @Test
    void reconnaitLesDeuxFormatsDeRepriseCutover() {
        assertEquals(true, PurgeFifoEngine.estStockInitial("[SOLDE_INITIAL] Reprise cutover"));
        assertEquals(true, PurgeFifoEngine.estStockInitial("Reprise cutover - Solde initial au 2026-01-01"));
        assertEquals(false, PurgeFifoEngine.estStockInitial("Correction DAF"));
    }

    @Test
    void imputeLeStockHistoriqueAvantLesDroitsDates() {
        var droits = List.of(
                new PurgeFifoEngine.DroitDate(1, LocalDate.of(2022, 1, 1), 5),
                new PurgeFifoEngine.DroitDate(2, LocalDate.of(2023, 1, 1), 5));
        var result = engine.calculerImputation(4, droits, 7);

        assertEquals(4.0, result.getConsommationImputeeSurHistorique());
        assertEquals(3.0, result.getConsommationImputeeSurDroitsDates());
        assertEquals(2.0, result.getDetailsDroitsDates().get(0).getNbJoursRestants());
        assertEquals(5.0, result.getDetailsDroitsDates().get(1).getNbJoursRestants());
    }

    @Test
    void purgeUniquementLeReliquatPrescrit() {
        var result = engine.calculerImputation(0, List.of(
                new PurgeFifoEngine.DroitDate(1, LocalDate.of(2021, 1, 1), 5),
                new PurgeFifoEngine.DroitDate(2, LocalDate.of(2025, 1, 1), 4)), 2);

        assertEquals(2.0, engine.calculerJoursAExpierFifo(
                result, LocalDate.of(2024, 1, 1), 1));
    }

    @Test
    void nePurgeJamaisLeStockHistorique() {
        var result = engine.calculerImputation(10, List.of(), 0);
        assertEquals(0.0, engine.calculerJoursAExpierFifo(
                result, LocalDate.of(2024, 1, 1), 0));
    }

    @Test
    void soldeInitialNegatifEstCompenseParLesPremieresAcquisitions() {
        var result = engine.calculerImputation(-3, List.of(
                new PurgeFifoEngine.DroitDate(1, LocalDate.of(2022, 1, 1), 5)), 0);
        assertEquals(2.0, result.getDetailsDroitsDates().get(0).getNbJoursRestants());
        assertEquals(2.0, result.getSoldeTotalDisponible());
    }

    @Test
    void rejoueExpirationAvantLesPrisesPourConserverLeBonLotApresExpiration() {
        LocalDate reference = LocalDate.of(2026, 1, 1);
        List<PurgeFifoEngine.MouvementLedger> mouvements = List.of(
                new PurgeFifoEngine.MouvementLedger(1, "ACQUISITION", 5, LocalDate.of(2020, 1, 1), null, null),
                new PurgeFifoEngine.MouvementLedger(2, "ACQUISITION", 5, LocalDate.of(2021, 1, 1), null, null),
                new PurgeFifoEngine.MouvementLedger(3, "EXPIRATION", -5, LocalDate.of(2023, 2, 1), null,
                        "Expiration confirmée — droits acquis avant le 2020-02-01"),
                new PurgeFifoEngine.MouvementLedger(4, "PRISE", -3, LocalDate.of(2023, 3, 1), 42, null));

        List<PurgeFifoEngine.DroitDisponible> droits = engine.calculerDroitsDisponibles(mouvements, reference, 36);

        assertEquals(1, droits.size());
        assertEquals(2, droits.get(0).idMouvement());
        assertEquals(2.0, droits.get(0).joursRestants());
    }

    @Test
    void annulationRestaureLeLotConsommeParLaDemande() {
        LocalDate reference = LocalDate.of(2026, 1, 1);
        List<PurgeFifoEngine.MouvementLedger> mouvements = List.of(
                new PurgeFifoEngine.MouvementLedger(1, "ACQUISITION", 5, LocalDate.of(2022, 1, 1), null, null),
                new PurgeFifoEngine.MouvementLedger(2, "PRISE", -3, LocalDate.of(2022, 3, 1), 42, null),
                new PurgeFifoEngine.MouvementLedger(3, "ANNULATION", 3, LocalDate.of(2022, 3, 2), 42, null));

        List<PurgeFifoEngine.DroitDisponible> droits = engine.calculerDroitsDisponibles(mouvements, reference, 36);

        assertEquals(1, droits.size());
        assertEquals(1, droits.get(0).idMouvement());
        assertEquals(5.0, droits.get(0).joursRestants());
    }

    @Test
    void unAjustementNegatifAnterieurNexposePasUneAcquisitionFutureCommeDroitDisponible() {
        LocalDate reference = LocalDate.of(2026, 1, 1);
        List<PurgeFifoEngine.MouvementLedger> mouvements = List.of(
                new PurgeFifoEngine.MouvementLedger(1, "AJUSTEMENT", -5, LocalDate.of(2022, 1, 1), null, "Correction"),
                new PurgeFifoEngine.MouvementLedger(2, "ACQUISITION", 10, LocalDate.of(2022, 2, 1), null, null));

        List<PurgeFifoEngine.DroitDisponible> droits = engine.calculerDroitsDisponibles(mouvements, reference, 36);

        assertEquals(1, droits.size());
        assertEquals(5.0, droits.get(0).joursRestants());
    }
}
