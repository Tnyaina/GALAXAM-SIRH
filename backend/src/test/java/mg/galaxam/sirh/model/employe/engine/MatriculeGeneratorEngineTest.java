package mg.galaxam.sirh.model.employe.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MatriculeGeneratorEngineTest {

    private MatriculeGeneratorEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MatriculeGeneratorEngine();
    }

    @Test
    @DisplayName("Génération séquentielle par défaut (logique historique 4 chiffres)")
    void testGenerationMatriculeHistorique() {
        assertEquals("0001", engine.genererMatriculeParDefaut(1));
        assertEquals("0042", engine.genererMatriculeParDefaut(42));
        assertEquals("1234", engine.genererMatriculeParDefaut(1234));
    }

    @Test
    @DisplayName("Génération avec préfixe et année")
    void testGenerationMatriculePrefixe() {
        assertEquals("EMP-2026-0001", engine.genererMatriculePrefixe(2026, 1));
        assertEquals("EMP-2024-0042", engine.genererMatriculePrefixe(2024, 42));
    }

    @Test
    @DisplayName("Vérification du format de matricule (séquentiel et préfixé)")
    void testFormatMatricule() {
        assertTrue(engine.estFormatValide("0001"));
        assertTrue(engine.estFormatValide("0042"));
        assertTrue(engine.estFormatValide("1234"));
        assertTrue(engine.estFormatValide("EMP-2026-0001"));
        assertTrue(engine.estFormatValide("EMP-2024-0042"));

        assertFalse(engine.estFormatValide(""));
        assertFalse(engine.estFormatValide(null));
        assertFalse(engine.estFormatValide("ABC"));
    }

    @Test
    @DisplayName("Extraction du numéro de séquence")
    void testExtractionSequence() {
        assertEquals(1L, engine.extraireSequence("0001"));
        assertEquals(42L, engine.extraireSequence("0042"));
        assertEquals(42L, engine.extraireSequence("EMP-2026-0042"));
    }
}
