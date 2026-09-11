package mg.galaxam.sirh.unit.pointage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Tests - CalendrierOuvrableEngine")
class CalendrierOuvrableEngineTest {

    private CalendrierOuvrableEngine engine;

    @BeforeEach
    void setUp() {
        engine = new CalendrierOuvrableEngine();
    }

    @Test
    @DisplayName("Détection des jours de week-end")
    void testWeekendDetection() {
        LocalDate vendredi = LocalDate.of(2026, 9, 4);
        LocalDate samedi = LocalDate.of(2026, 9, 5);
        LocalDate dimanche = LocalDate.of(2026, 9, 6);
        LocalDate lundi = LocalDate.of(2026, 9, 7);

        assertThat(CalendrierOuvrableEngine.isWeekend(vendredi)).isFalse();
        assertThat(CalendrierOuvrableEngine.isWeekend(samedi)).isTrue();
        assertThat(CalendrierOuvrableEngine.isWeekend(dimanche)).isTrue();
        assertThat(CalendrierOuvrableEngine.isWeekend(lundi)).isFalse();
    }

    @Test
    @DisplayName("Calculs de prorata journalier et demi-trajet")
    void testCalculsProrataEtTrajet() {
        // 53 200 Ar sur 22 jours ouvrables -> 2 418.18 Ar / jour
        assertThat(engine.calculerProrataJournalier(53200.0, 22)).isEqualTo(2418.18);

        // Montant par demi-trajet (aller ou retour) -> 1 209.09 Ar
        assertThat(engine.calculerMontantTrajet(2418.18)).isEqualTo(1209.09);
    }

    @Test
    @DisplayName("compterJoursCalendaires : Bornes inclusives et inversées")
    void testCompterJoursCalendaires() {
        LocalDate d1 = LocalDate.of(2026, 9, 1);
        LocalDate d2 = LocalDate.of(2026, 9, 10);

        assertThat(engine.compterJoursCalendaires(d1, d2)).isEqualTo(10);
        assertThat(engine.compterJoursCalendaires(d1, d1)).isEqualTo(1);
        assertThat(engine.compterJoursCalendaires(d2, d1)).isZero();
    }
}
