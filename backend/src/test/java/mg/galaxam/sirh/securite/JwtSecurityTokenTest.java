package mg.galaxam.sirh.securite;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.*;

public class JwtSecurityTokenTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        jwtService = new JwtService();

        // Injection des valeurs par réflexion pour test unitaire pur sans charger tout le contexte Spring
        Field secretField = JwtService.class.getDeclaredField("secretKeyString");
        secretField.setAccessible(true);
        secretField.set(jwtService, "dGhpc0lzQVN1cGVyU2VjdXJlSldUU2VjcmV0S2V5Rm9yR0FMQVhBTVNJUkgxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTA=");

        Field accessExpField = JwtService.class.getDeclaredField("accessTokenExpirationMs");
        accessExpField.setAccessible(true);
        accessExpField.set(jwtService, 900000L); // 15 min

        Field refreshExpField = JwtService.class.getDeclaredField("refreshTokenExpirationMs");
        refreshExpField.setAccessible(true);
        refreshExpField.set(jwtService, 604800000L); // 7 jours
    }

    @Test
    @DisplayName("Génération et extraction du sujet (identifiant) depuis le JWT")
    void testGenerationEtExtractionJwt() {
        UserDetails user = new User("admin.galaxam", "password", 
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("EMPLOYE_WRITE")));

        String token = jwtService.generateAccessToken(user);
        System.out.println("[TEST JWT] Token généré : " + token.substring(0, 30) + "...");

        assertNotNull(token);
        String username = jwtService.extractUsername(token);
        System.out.println("[TEST JWT] Username extrait : " + username);
        assertEquals("admin.galaxam", username);
        assertTrue(jwtService.isTokenValid(token, user));
    }

    @Test
    @DisplayName("Validation du Refresh Token")
    void testRefreshTokenValidation() {
        UserDetails user = new User("rh.agent", "password", 
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        String refreshToken = jwtService.generateRefreshToken(user);
        System.out.println("[TEST JWT] Refresh token : " + refreshToken.substring(0, 30) + "...");

        assertTrue(jwtService.isRefreshTokenValid(refreshToken));
        assertEquals("rh.agent", jwtService.extractUsername(refreshToken));
    }
}
