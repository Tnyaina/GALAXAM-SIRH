package mg.galaxam.sirh.unit.security;

import mg.galaxam.sirh.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires de la validation CORS au démarrage.
 * P1-04 : vérifier que les wildcards sont rejetés en production.
 */
@DisplayName("P1-04 — SecurityConfig — Validation CORS au démarrage")
class SecurityConfigCorsValidationTest {

    private SecurityConfig configWith(String... origins) throws Exception {
        SecurityConfig config = new SecurityConfig();
        // Injecter la valeur via le champ réflexif (pour tests unitaires sans contexte Spring)
        var field = SecurityConfig.class.getDeclaredField("allowedOrigins");
        field.setAccessible(true);
        field.set(config, origins);
        return config;
    }

    @Test
    @DisplayName("Origine exacte valide → démarrage OK")
    void validExactOriginIsAccepted() throws Exception {
        SecurityConfig config = configWith("http://localhost:5173");
        assertThatNoException().isThrownBy(config::validateCorsConfiguration);
    }

    @Test
    @DisplayName("Plusieurs origines exactes valides → démarrage OK")
    void multipleValidOriginsAreAccepted() throws Exception {
        SecurityConfig config = configWith("https://app.galaxam.mg", "https://admin.galaxam.mg");
        assertThatNoException().isThrownBy(config::validateCorsConfiguration);
    }

    @Test
    @DisplayName("'*' exact → IllegalStateException")
    void wildcardStarIsRejected() throws Exception {
        SecurityConfig config = configWith("*");
        assertThatThrownBy(config::validateCorsConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("credentials");
    }

    @Test
    @DisplayName("Wildcard sous-domaine 'https://*.example.com' → IllegalStateException")
    void wildcardSubdomainPatternIsRejected() throws Exception {
        SecurityConfig config = configWith("https://*.example.com");
        assertThatThrownBy(config::validateCorsConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wildcard");
    }

    @Test
    @DisplayName("Pattern avec '?' → IllegalStateException")
    void questionMarkPatternIsRejected() throws Exception {
        SecurityConfig config = configWith("https://app?.galaxam.mg");
        assertThatThrownBy(config::validateCorsConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wildcard");
    }

    @Test
    @DisplayName("Liste vide → IllegalStateException")
    void emptyOriginsListIsRejected() throws Exception {
        SecurityConfig config = configWith();
        assertThatThrownBy(config::validateCorsConfiguration)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Origine null dans la liste → IllegalStateException")
    void nullOriginInListIsRejected() throws Exception {
        SecurityConfig config = configWith("https://app.galaxam.mg", null);
        assertThatThrownBy(config::validateCorsConfiguration)
                .isInstanceOf(IllegalStateException.class);
    }
}
