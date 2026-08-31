package mg.galaxam.sirh.model.contrat.engine;

import java.sql.Date;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ContratValidationEngineTest {

    private ContratValidationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ContratValidationEngine();
    }

    @Test
    @DisplayName("Validation des dates pour contrat CDD (dateFin obligatoire et >= dateDebut)")
    void testValidationDatesCdd() {
        Date debut = Date.valueOf(LocalDate.of(2026, 1, 1));
        Date finValide = Date.valueOf(LocalDate.of(2026, 12, 31));
        Date finInvalide = Date.valueOf(LocalDate.of(2025, 12, 31));

        // Cas 1 : CDD valide avec dateFin
        assertDoesNotThrow(() -> engine.validerDatesContrat("CDD", debut, finValide, 3));

        // Cas 2 : CDD sans dateFin -> exception
        Exception ex1 = assertThrows(IllegalArgumentException.class, () -> engine.validerDatesContrat("CDD", debut, null, 3));
        assertTrue(ex1.getMessage().contains("date de fin est obligatoire"));

        // Cas 3 : CDD avec dateFin antérieure à dateDebut -> exception
        Exception ex2 = assertThrows(IllegalArgumentException.class, () -> engine.validerDatesContrat("CDD", debut, finInvalide, 3));
        assertTrue(ex2.getMessage().contains("antérieure"));
    }

    @Test
    @DisplayName("Validation des dates pour contrat CDI (dateFin optionnelle)")
    void testValidationDatesCdi() {
        Date debut = Date.valueOf(LocalDate.of(2026, 1, 1));
        assertDoesNotThrow(() -> engine.validerDatesContrat("CDI", debut, null, 3));
    }

    @Test
    @DisplayName("Calcul de la fin de période d'essai (ex: 3 mois)")
    void testCalculFinPeriodeEssai() {
        LocalDate debut = LocalDate.of(2026, 1, 1);
        LocalDate fin3mois = engine.calculerFinPeriodeEssai(debut, 3);
        assertEquals(LocalDate.of(2026, 3, 31), fin3mois);

        LocalDate fin6mois = engine.calculerFinPeriodeEssai(debut, 6);
        assertEquals(LocalDate.of(2026, 6, 30), fin6mois);
    }

    @Test
    @DisplayName("Transitions de statuts autorisées / interdites")
    void testTransitionsStatuts() {
        // Autorisées
        assertTrue(engine.estTransitionStatutAutorisee("BROUILLON", "EN_ATTENTE"));
        assertTrue(engine.estTransitionStatutAutorisee("BROUILLON", "VALIDE"));
        assertTrue(engine.estTransitionStatutAutorisee("VALIDE", "ACTIF"));
        assertTrue(engine.estTransitionStatutAutorisee("ACTIF", "CLOTURE"));

        // Interdites (depuis état terminal)
        assertFalse(engine.estTransitionStatutAutorisee("CLOTURE", "ACTIF"));
        assertFalse(engine.estTransitionStatutAutorisee("ROMPU", "VALIDE"));
        assertFalse(engine.estTransitionStatutAutorisee("REFUSE", "ACTIF"));
    }
}
