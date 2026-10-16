package mg.galaxam.sirh.securite;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.securite.api.AuthController;
import mg.galaxam.sirh.securite.api.UtilisateurController;
import mg.galaxam.sirh.securite.api.dto.LoginRequest;
import mg.galaxam.sirh.securite.api.dto.UtilisateurRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class UtilisateurSessionLifecycleIntegrationTest {

    @Autowired private DataSource dataSource;
    @Autowired private AuthController authController;
    @Autowired private UtilisateurController utilisateurController;

    @Test
    void desactivationReactivationEtLogoutRevoquentLesRefreshTokensEtConserventLesJournaux() throws Exception {
        int userId;
        String email = "session-lifecycle-" + Long.toUnsignedString(System.nanoTime()) + "@example.test";
        try (Connection c = dataSource.getConnection()) {
            int roleId;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id_role FROM ref_role WHERE est_actif = TRUE ORDER BY id_role LIMIT 1");
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Un rôle actif est requis pour ce test");
                roleId = rs.getInt(1);
            }
            Utilisateur user = new Utilisateur();
            user.setIdentifiant("Session lifecycle test");
            user.setEmail(email);
            user.setIdRole(roleId);
            user.setMotDePasseEnClair("TestPassword123");
            user.setEstActif(true);
            user.save(c);
            userId = user.getIdUtilisateur();
        }

        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "admin-test", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            String initialRefresh = login(email);
            UtilisateurRequest request = new UtilisateurRequest();
            request.setMotifDesactivation("Test sécurité");
            utilisateurController.deactivateUser(userId, request);
            assertNull(loadRefreshToken(userId));
            assertRefreshRejected(initialRefresh);

            utilisateurController.activateUser(userId);
            assertNull(loadRefreshToken(userId));
            assertRefreshRejected(initialRefresh);

            String reactivatedRefresh = login(email);
            assertNotNull(loadRefreshToken(userId));
            authController.logout(reactivatedRefresh, new MockHttpServletRequest());
            assertNull(loadRefreshToken(userId));
            assertRefreshRejected(reactivatedRefresh);

            try (Connection c = dataSource.getConnection()) {
                assertEquals(2, count(c, "journal_connexion", "id_utilisateur", userId));
                assertEquals(2, countAuditForUser(c, userId));
            }

            assertThrows(UnsupportedOperationException.class, () -> new Utilisateur().delete(null));
        } finally {
            SecurityContextHolder.clearContext();
            // This test uses only the dedicated test profile; remove its fixture and its test logs.
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM audit_log WHERE nom_table = 'utilisateur' AND id_enregistrement = ?")) {
                    ps.setString(1, String.valueOf(userId));
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM journal_connexion WHERE id_utilisateur = ?")) {
                    ps.setInt(1, userId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM utilisateur WHERE id_utilisateur = ?")) {
                    ps.setInt(1, userId);
                    ps.executeUpdate();
                }
                c.commit();
            }
        }
    }

    private String login(String email) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setMotDePasse("TestPassword123");
        ResponseEntity<?> response = authController.login(request, new MockHttpServletRequest());
        assertTrue(response.getStatusCode().is2xxSuccessful());
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        return setCookie.substring("refreshToken=".length(), setCookie.indexOf(';'));
    }

    private void assertRefreshRejected(String refreshToken) {
        assertThrows(RuntimeException.class,
                () -> authController.refresh(refreshToken, new MockHttpServletRequest()));
    }

    private String loadRefreshToken(int userId) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT refresh_token FROM utilisateur WHERE id_utilisateur = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    private int count(Connection c, String table, String column, int value) throws Exception {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private int countAuditForUser(Connection c, int userId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM audit_log WHERE nom_table = 'utilisateur' AND id_enregistrement = ?")) {
            ps.setString(1, String.valueOf(userId));
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }
}
