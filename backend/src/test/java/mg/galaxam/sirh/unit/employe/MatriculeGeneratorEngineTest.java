package mg.galaxam.sirh.unit.employe;

import static org.assertj.core.api.Assertions.assertThat;

import mg.galaxam.sirh.model.employe.engine.MatriculeGeneratorEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Tests - MatriculeGeneratorEngine")
class MatriculeGeneratorEngineTest {

    private MatriculeGeneratorEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MatriculeGeneratorEngine();
    }

    @Test
    @DisplayName("genererMatriculeSequentiel : Format 4 chiffres paddé de zéros")
    void testGenererMatriculeSequentiel() {
        assertThat(engine.genererMatriculeSequentiel(1)).isEqualTo("0001");
        assertThat(engine.genererMatriculeSequentiel(42)).isEqualTo("0042");
        assertThat(engine.genererMatriculeSequentiel(1234)).isEqualTo("1234");
    }

    @Test
    @DisplayName("genererMatriculePrefixe : Format normalisé EMP-YYYY-XXXX")
    void testGenererMatriculePrefixe() {
        assertThat(engine.genererMatriculePrefixe(2026, 5)).isEqualTo("EMP-2026-0005");
        assertThat(engine.genererMatriculePrefixe(2026, 128)).isEqualTo("EMP-2026-0128");
    }

    @Test
    @DisplayName("estFormatValide : Validation des formats séquentiels et préfixés")
    void testEstFormatValide() {
        assertThat(engine.estFormatValide("0042")).isTrue();
        assertThat(engine.estFormatValide("EMP-2026-0042")).isTrue();

        assertThat(engine.estFormatValide("INVALID")).isFalse();
        assertThat(engine.estFormatValide(null)).isFalse();
        assertThat(engine.estFormatValide("")).isFalse();
    }

    @Test
    @DisplayName("extraireSequence et extraireAnnee : Extraction précise des composants")
    void testExtraireComposants() {
        assertThat(engine.extraireSequence("EMP-2026-0042")).isEqualTo(42L);
        assertThat(engine.extraireAnnee("EMP-2026-0042")).isEqualTo(2026);

        assertThat(engine.extraireSequence("0085")).isEqualTo(85L);
        assertThat(engine.extraireAnnee("0085")).isNull();
    }
}
