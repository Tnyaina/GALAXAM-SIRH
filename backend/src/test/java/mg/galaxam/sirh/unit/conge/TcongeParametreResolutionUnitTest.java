package mg.galaxam.sirh.unit.conge;

import java.util.HashMap;
import java.util.Map;
import mg.galaxam.sirh.model.paieconfig.ParametreGlobal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 2A-4C — Tests unitaires de la résolution stricte du paramètre DUREE_REFERENCE_TCONGE_MOIS.
 * Interdit tout fallback silencieux à 12 en cas d'absence ou d'invalidité.
 */
@DisplayName("Phase 2A-4C : Résolution stricte de DUREE_REFERENCE_TCONGE_MOIS")
public class TcongeParametreResolutionUnitTest {

    @Test
    @DisplayName("shouldResolveSuccessfullyWhenValid12IsPresent")
    void shouldResolveSuccessfullyWhenValid12IsPresent() {
        Map<String, String> params = new HashMap<>();
        params.put("DUREE_REFERENCE_TCONGE_MOIS", "12");

        int duree = ParametreGlobal.requirePositiveInt(params, "DUREE_REFERENCE_TCONGE_MOIS");
        assertThat(duree).isEqualTo(12);
    }

    @Test
    @DisplayName("shouldThrowExplicitErrorWhenParameterIsMissing")
    void shouldThrowExplicitErrorWhenParameterIsMissing() {
        Map<String, String> emptyParams = new HashMap<>();

        assertThatThrownBy(() -> ParametreGlobal.requirePositiveInt(emptyParams, "DUREE_REFERENCE_TCONGE_MOIS"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TCONGE_REFERENCE_DURATION_MISSING");
    }

    @Test
    @DisplayName("shouldThrowExplicitErrorWhenParameterIsNullMap")
    void shouldThrowExplicitErrorWhenParameterIsNullMap() {
        assertThatThrownBy(() -> ParametreGlobal.requirePositiveInt(null, "DUREE_REFERENCE_TCONGE_MOIS"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TCONGE_REFERENCE_DURATION_MISSING");
    }

    @Test
    @DisplayName("shouldThrowExplicitErrorWhenParameterIsZero")
    void shouldThrowExplicitErrorWhenParameterIsZero() {
        Map<String, String> params = new HashMap<>();
        params.put("DUREE_REFERENCE_TCONGE_MOIS", "0");

        assertThatThrownBy(() -> ParametreGlobal.requirePositiveInt(params, "DUREE_REFERENCE_TCONGE_MOIS"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TCONGE_REFERENCE_DURATION_INVALID");
    }

    @Test
    @DisplayName("shouldThrowExplicitErrorWhenParameterIsNegative")
    void shouldThrowExplicitErrorWhenParameterIsNegative() {
        Map<String, String> params = new HashMap<>();
        params.put("DUREE_REFERENCE_TCONGE_MOIS", "-1");

        assertThatThrownBy(() -> ParametreGlobal.requirePositiveInt(params, "DUREE_REFERENCE_TCONGE_MOIS"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TCONGE_REFERENCE_DURATION_INVALID");
    }

    @Test
    @DisplayName("shouldThrowExplicitErrorWhenParameterIsNotANumber")
    void shouldThrowExplicitErrorWhenParameterIsNotANumber() {
        Map<String, String> params = new HashMap<>();
        params.put("DUREE_REFERENCE_TCONGE_MOIS", "DOUZE_MOIS");

        assertThatThrownBy(() -> ParametreGlobal.requirePositiveInt(params, "DUREE_REFERENCE_TCONGE_MOIS"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TCONGE_REFERENCE_DURATION_INVALID");
    }
}
