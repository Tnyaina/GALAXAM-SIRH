package mg.galaxam.sirh.pointage.engine;

import mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires de CalendrierOuvrableEngine.
 * Les methodes necessitant une Connection sont testees avec null (comportement par defaut).
 * Les tests d'integration avec DB sont dans PointageSyntheseWorkflowIntegrationTest.
 */
@DisplayName("CalendrierOuvrableEngine")
class CalendrierOuvrableEngineTest {

    private CalendrierOuvrableEngine engine;

    @BeforeEach
    void setUp() { engine = new CalendrierOuvrableEngine(); }

    @Nested @DisplayName("compterJoursCalendaires")
    class CompterJoursCalendaires {

        @Test @DisplayName("Meme jour = 1")
        void memeJour() {
            LocalDate d = LocalDate.of(2024, 1, 15);
            assertEquals(1, engine.compterJoursCalendaires(d, d));
        }

        @Test @DisplayName("5 jours consecutifs = 5")
        void cinqJours() {
            assertEquals(5, engine.compterJoursCalendaires(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 5)));
        }

        @Test @DisplayName("Mois complet janvier = 31")
        void moisComplet() {
            assertEquals(31, engine.compterJoursCalendaires(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)));
        }

        @Test @DisplayName("null debut -> 0")
        void nullDebut() {
            assertEquals(0, engine.compterJoursCalendaires(null, LocalDate.now()));
        }

        @Test @DisplayName("null fin -> 0")
        void nullFin() {
            assertEquals(0, engine.compterJoursCalendaires(LocalDate.now(), null));
        }
    }

    @Nested @DisplayName("compterJoursOuvrables (sans DB)")
    class CompterJoursOuvrables {

        @Test @DisplayName("Semaine complete Lun-Ven (sans feries) = 5")
        void semaineComplete() {
            // 2024-01-08 = Lundi, 2024-01-12 = Vendredi
            assertEquals(5, engine.compterJoursOuvrables(
                LocalDate.of(2024, 1, 8), LocalDate.of(2024, 1, 12), null, null));
        }

        @Test @DisplayName("Semaine complete avec weekend Lun-Dim = 5 (Sam+Dim exclus)")
        void semaineAvecWeekend() {
            // 2024-01-08 (Lun) -> 2024-01-14 (Dim) = 5 jours ouvrables
            assertEquals(5, engine.compterJoursOuvrables(
                LocalDate.of(2024, 1, 8), LocalDate.of(2024, 1, 14), null, null));
        }

        @Test @DisplayName("Samedi seul = 0")
        void samediSeul() {
            // 2024-01-06 = Samedi
            assertEquals(0, engine.compterJoursOuvrables(
                LocalDate.of(2024, 1, 6), LocalDate.of(2024, 1, 6), null, null));
        }

        @Test @DisplayName("Dimanche seul = 0")
        void dimancheSeul() {
            // 2024-01-07 = Dimanche
            assertEquals(0, engine.compterJoursOuvrables(
                LocalDate.of(2024, 1, 7), LocalDate.of(2024, 1, 7), null, null));
        }

        @Test @DisplayName("null debut -> 0")
        void nullDebut() {
            assertEquals(0, engine.compterJoursOuvrables(null, LocalDate.now(), null, null));
        }

        @Test @DisplayName("null fin -> 0")
        void nullFin() {
            assertEquals(0, engine.compterJoursOuvrables(LocalDate.now(), null, null, null));
        }
    }

    @Nested @DisplayName("calculerProrataJournalier")
    class CalculerProrataJournalier {

        @Test @DisplayName("Montant 500000 sur 25j = 20000/j")
        void prorataStandard() {
            assertEquals(20000.0, engine.calculerProrataJournalier(500000.0, 25), 0.01);
        }

        @Test @DisplayName("0 jours ouvrables -> retourne montant reference (protection division par 0)")
        void zeroJours() {
            assertEquals(100000.0, engine.calculerProrataJournalier(100000.0, 0), 0.01);
        }
    }

    @Nested @DisplayName("calculerMontantTrajet")
    class CalculerMontantTrajet {

        @Test @DisplayName("Montant journalier 10000 -> trajet = 5000")
        void trajetDemi() {
            assertEquals(5000.0, engine.calculerMontantTrajet(10000.0), 0.01);
        }
    }
}
