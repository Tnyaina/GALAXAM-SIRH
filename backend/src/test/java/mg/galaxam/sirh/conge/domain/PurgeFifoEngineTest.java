package mg.galaxam.sirh.conge.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PurgeFifoEngineTest {

    private final PurgeFifoEngine engine = new PurgeFifoEngine();

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
}
