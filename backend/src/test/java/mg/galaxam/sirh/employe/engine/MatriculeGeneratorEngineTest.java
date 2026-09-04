package mg.galaxam.sirh.employe.engine;

import mg.galaxam.sirh.model.employe.engine.MatriculeGeneratorEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MatriculeGeneratorEngine")
class MatriculeGeneratorEngineTest {

    private MatriculeGeneratorEngine engine;

    @BeforeEach
    void setUp() { engine = new MatriculeGeneratorEngine(); }

    @Nested @DisplayName("genererMatriculeParDefaut (sequentiel)")
    class GenererMatriculeParDefaut {

        @Test @DisplayName("Sequence 1 -> 0001")
        void sequence1() { assertEquals("0001", engine.genererMatriculeParDefaut(1)); }

        @Test @DisplayName("Sequence 42 -> 0042")
        void sequence42() { assertEquals("0042", engine.genererMatriculeParDefaut(42)); }

        @Test @DisplayName("Sequence 1000 -> 1000")
        void sequence1000() { assertEquals("1000", engine.genererMatriculeParDefaut(1000)); }

        @Test @DisplayName("Sequence 0 -> 0001 (minimum 1)")
        void sequenceZero() { assertEquals("0001", engine.genererMatriculeParDefaut(0)); }
    }

    @Nested @DisplayName("genererMatriculePrefixe")
    class GenererMatriculePrefixe {

        @Test @DisplayName("EMP-2024-0001")
        void prefixeNormal() {
            assertEquals("EMP-2024-0001", engine.genererMatriculePrefixe(2024, 1));
        }

        @Test @DisplayName("EMP-2025-0042")
        void prefixeAvecSeq() {
            assertEquals("EMP-2025-0042", engine.genererMatriculePrefixe(2025, 42));
        }
    }

    @Nested @DisplayName("extraireSequence")
    class ExtraireSequence {

        @Test @DisplayName("0042 -> 42")
        void sequentiel() { assertEquals(42L, engine.extraireSequence("0042")); }

        @Test @DisplayName("EMP-2024-0042 -> 42")
        void prefixe() { assertEquals(42L, engine.extraireSequence("EMP-2024-0042")); }

        @Test @DisplayName("null -> null")
        void nullVal() { assertNull(engine.extraireSequence(null)); }

        @Test @DisplayName("Vide -> null")
        void vide() { assertNull(engine.extraireSequence("")); }

        @Test @DisplayName("Texte sans chiffre -> null (ou derniers chiffres)")
        void texteSansSeq() { assertNull(engine.extraireSequence("ABCDE")); }
    }

    @Nested @DisplayName("estFormatValide")
    class EstFormatValide {

        @Test @DisplayName("0001 -> valide (sequentiel)")
        void sequentielValide() { assertTrue(engine.estFormatValide("0001")); }

        @Test @DisplayName("EMP-2024-0001 -> valide (prefixe)")
        void prefixeValide() { assertTrue(engine.estFormatValide("EMP-2024-0001")); }

        @Test @DisplayName("null -> invalide")
        void nullInvalide() { assertFalse(engine.estFormatValide(null)); }

        @Test @DisplayName("Vide -> invalide")
        void videInvalide() { assertFalse(engine.estFormatValide("")); }

        @Test @DisplayName("ABCDE -> invalide")
        void texteInvalide() { assertFalse(engine.estFormatValide("ABCDE")); }
    }
}
