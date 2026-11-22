package mg.galaxam.sirh.contrat.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AncienneteEngineTest {
    private final AncienneteEngine engine = new AncienneteEngine();

    @Test
    void cumuleLesContratsLiesSansCompterLInterruptionNiUnContratNonLie() {
        var contrats = List.of(
                new AncienneteEngine.IntervalleContrat(1, LocalDate.parse("2025-01-01"),
                        LocalDate.parse("2025-06-30"), null),
                new AncienneteEngine.IntervalleContrat(2, LocalDate.parse("2025-08-01"),
                        null, 1),
                new AncienneteEngine.IntervalleContrat(3, LocalDate.parse("2024-01-01"),
                        LocalDate.parse("2024-12-31"), null));

        var bilan = engine.evaluerAnciennete(contrats, 2, LocalDate.parse("2026-01-31"));

        assertEquals(12, bilan.moisEffectifs());
        assertEquals(2, bilan.nombreContratsChaine());
        assertEquals(6, bilan.moisContratActuel());
    }

    @Test
    void additionneLesJoursResiduellementEtIgnoreLaPartieFutureDuContrat() {
        var chaine = List.of(
                new AncienneteEngine.IntervalleContrat(2, LocalDate.parse("2025-02-01"),
                        null, 1),
                new AncienneteEngine.IntervalleContrat(1, LocalDate.parse("2025-01-01"),
                        LocalDate.parse("2025-01-15"), null));

        assertEquals(1, engine.calculerMoisEffectifs(chaine, LocalDate.parse("2025-02-15")));
        assertEquals(0, engine.calculerMoisEffectifs(chaine, LocalDate.parse("2025-01-15")));
    }
}
