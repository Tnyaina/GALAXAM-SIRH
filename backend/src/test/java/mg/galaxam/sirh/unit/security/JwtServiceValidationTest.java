package mg.galaxam.sirh.unit.security;

import mg.galaxam.sirh.securite.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires de la validation JWT au démarrage.
 * P1-04 : vérifier que les secrets faibles sont rejetés et non renforcés silencieusement.
 */
@DisplayName("P1-04 — JwtService — Validation du secret JWT au démarrage")
class JwtServiceValidationTest {

    private JwtService serviceWith(String secret) throws Exception {
        JwtService svc = new JwtService();
        var field = JwtService.class.getDeclaredField("secretKeyString");
        field.setAccessible(true);
        field.set(svc, secret);
        var expField = JwtService.class.getDeclaredField("accessTokenExpirationMs");
        expField.setAccessible(true);
        expField.set(svc, 900000L);
        var refField = JwtService.class.getDeclaredField("refreshTokenExpirationMs");
        refField.setAccessible(true);
        refField.set(svc, 86400000L);
        return svc;
    }

    @Test
    @DisplayName("Secret absent (null) → IllegalStateException au démarrage")
    void nullSecretFailsFast() throws Exception {
        JwtService svc = serviceWith(null);
        assertThatThrownBy(svc::validateJwtSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret");
    }

    @Test
    @DisplayName("Secret blank → IllegalStateException au démarrage")
    void blankSecretFailsFast() throws Exception {
        JwtService svc = serviceWith("   ");
        assertThatThrownBy(svc::validateJwtSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret");
    }

    @Test
    @DisplayName("Secret trop court (16 chars) → IllegalStateException — ne doit PAS être renforcé silencieusement")
    void shortSecretIsRejectedNotStrengthened() throws Exception {
        JwtService svc = serviceWith("trop-court-secret");
        assertThatThrownBy(svc::validateJwtSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("64 bytes");
    }

    @Test
    @DisplayName("Secret de 32 bytes (trop court pour HS512) → rejeté")
    void thirtyTwoBytesSecretIsRejected() throws Exception {
        String secret32 = "a".repeat(32);
        JwtService svc = serviceWith(secret32);
        assertThatThrownBy(svc::validateJwtSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("64 bytes");
    }

    @Test
    @DisplayName("Secret de 64 bytes exact → accepté")
    void exactlyMinimumLengthIsAccepted() throws Exception {
        // Un secret avec '@' ne peut pas être décodé en Base64 → raw UTF-8 utilisé
        // "@".repeat(64) = 64 bytes UTF-8 exactement
        String secret64 = "@".repeat(64);
        JwtService svc = serviceWith(secret64);
        assertThatNoException().isThrownBy(svc::validateJwtSecret);
    }

    @Test
    @DisplayName("Secret Base64 encodant 64 bytes → accepté")
    void validBase64SecretIsAccepted() throws Exception {
        byte[] bytes = new byte[64];
        java.util.Arrays.fill(bytes, (byte) 0x41);
        String b64Secret = Base64.getEncoder().encodeToString(bytes);
        JwtService svc = serviceWith(b64Secret);
        assertThatNoException().isThrownBy(svc::validateJwtSecret);
    }

    @Test
    @DisplayName("Secret de 128 bytes (plus que le minimum) → accepté")
    void largeSecretIsAccepted() throws Exception {
        // Un secret avec '@' ne peut pas être décodé en Base64 → raw UTF-8 utilisé
        String secret128 = "@".repeat(128);
        JwtService svc = serviceWith(secret128);
        assertThatNoException().isThrownBy(svc::validateJwtSecret);
    }
}
