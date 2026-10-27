package mg.galaxam.sirh.dashboard.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import mg.galaxam.sirh.securite.CustomUserDetails;

/**
 * NotificationController — Gestion des notifications RH persistées en base.
 *
 * Pattern professionnel : les notifications sont générées par le scheduler
 * nocturne (NotificationRhScheduler), stockées en base et consultées par
 * le frontend via polling léger à l'ouverture de l'application.
 *
 * GET  /notifications            → notifications non lues de l'utilisateur courant
 * PUT  /notifications/marquer-lues → marquer toutes les notifs comme lues
 * PUT  /notifications/{id}/lire  → marquer une notif spécifique comme lue
 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    @Autowired
    private DataSource dataSource;

    /**
     * Recupere les notifications non lues de l'utilisateur courant.
     * Filtre par id_utilisateur ou par cible_role correspondant au role de l'utilisateur.
     * Limite a 50 notifications (performance UI).
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getNotifications() {
        try (Connection c = dataSource.getConnection()) {
            Integer idUtilisateur = getCurrentUserId(c);
            String userRoleCode = getCurrentUserRoleCode(c, idUtilisateur);

            boolean isAdmin = "ROLE_ADMIN".equals(userRoleCode);

            String sql = "SELECT id_notification, type_alerte, priorite, titre, message, " +
                    "lien, id_employe_concerne, est_lue, date_creation, date_lecture, cible_role " +
                    "FROM notification_rh " +
                    "WHERE est_lue = FALSE " +
                    "AND " + visibilityCondition(isAdmin) + " " +
                    "ORDER BY date_creation DESC " +
                    "LIMIT 50";

            List<Map<String, Object>> notifications = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                bindVisibility(ps, 1, idUtilisateur, userRoleCode, isAdmin);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Map<String, Object> n = new LinkedHashMap<>();
                    n.put("idNotification", rs.getInt("id_notification"));
                    n.put("typeAlerte", rs.getString("type_alerte"));
                    n.put("priorite", rs.getString("priorite"));
                    n.put("titre", rs.getString("titre"));
                    n.put("message", rs.getString("message"));
                    n.put("lien", rs.getString("lien"));
                    n.put("idEmployeConcerne", rs.getObject("id_employe_concerne"));
                    n.put("cibleRole", rs.getString("cible_role"));
                    n.put("estLue", rs.getBoolean("est_lue"));
                    n.put("dateCreation", rs.getTimestamp("date_creation"));
                    notifications.add(n);
                }
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("notifications", notifications);
            response.put("totalNonLues", notifications.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    /**
     * Marquer TOUTES les notifications de l'utilisateur comme lues.
     */
    @PutMapping("/marquer-lues")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> marquerToutesLues() {
        try (Connection c = dataSource.getConnection()) {
            Integer idUtilisateur = getCurrentUserId(c);
            String userRoleCode = getCurrentUserRoleCode(c, idUtilisateur);
            boolean isAdmin = "ROLE_ADMIN".equals(userRoleCode);

            String sql = "UPDATE notification_rh SET est_lue = TRUE, date_lecture = CURRENT_TIMESTAMP " +
                    "WHERE est_lue = FALSE " +
                    "AND " + visibilityCondition(isAdmin);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                bindVisibility(ps, 1, idUtilisateur, userRoleCode, isAdmin);
                int updated = ps.executeUpdate();
                return ResponseEntity.ok(Map.of("marquees", updated));
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    /**
     * Marquer UNE notification specifique comme lue.
     */
    @PutMapping("/{id}/lire")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> marquerUne(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            Integer idUtilisateur = getCurrentUserId(c);
            String userRoleCode = getCurrentUserRoleCode(c, idUtilisateur);
            boolean isAdmin = "ROLE_ADMIN".equals(userRoleCode);
            String sql = "UPDATE notification_rh SET est_lue = TRUE, date_lecture = CURRENT_TIMESTAMP " +
                    "WHERE id_notification = ? AND " + visibilityCondition(isAdmin);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, id);
                bindVisibility(ps, 2, idUtilisateur, userRoleCode, isAdmin);
                if (ps.executeUpdate() == 0) {
                    return ResponseEntity.status(404)
                            .body(Map.of("message", "Notification introuvable ou non accessible."));
                }
            }
            return ResponseEntity.ok(Map.of("id", id, "lue", true));
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    /**
     * Supprimer toutes les notifications (effacer l'historique).
     */
    @DeleteMapping("/purger-toutes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> purgerToutes() {
        try (Connection c = dataSource.getConnection()) {
            Integer idUtilisateur = getCurrentUserId(c);
            String userRoleCode = getCurrentUserRoleCode(c, idUtilisateur);
            boolean isAdmin = "ROLE_ADMIN".equals(userRoleCode);

            String sql = "DELETE FROM notification_rh " +
                    "WHERE " + visibilityCondition(isAdmin);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                bindVisibility(ps, 1, idUtilisateur, userRoleCode, isAdmin);
                int deleted = ps.executeUpdate();
                return ResponseEntity.ok(Map.of("supprimees", deleted));
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    // =========================================================================
    // Utilitaire — Resolution de l'utilisateur courant et de son role
    // =========================================================================

    private static String visibilityCondition(boolean isAdmin) {
        return isAdmin
                ? "(id_utilisateur = ? OR id_utilisateur IS NULL)"
                : "(id_utilisateur = ? OR (id_utilisateur IS NULL AND (cible_role IS NULL OR cible_role = ?)))";
    }

    private static void bindVisibility(PreparedStatement ps, int firstParameter, Integer idUtilisateur,
                                       String userRoleCode, boolean isAdmin) throws Exception {
        ps.setObject(firstParameter, idUtilisateur, java.sql.Types.INTEGER);
        if (!isAdmin) ps.setString(firstParameter + 1, userRoleCode);
    }

    private Integer getCurrentUserId(Connection c) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;
        if (auth.getPrincipal() instanceof CustomUserDetails) {
            return ((CustomUserDetails) auth.getPrincipal()).getIdUtilisateur();
        }
        // Fallback : recherche par identifiant
        String identifiant = auth.getName();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id_utilisateur FROM utilisateur WHERE LOWER(email) = LOWER(?)")) {
            ps.setString(1, identifiant);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : null;
        }
    }

    private String getCurrentUserRoleCode(Connection c, Integer idUtilisateur) {
        if (idUtilisateur == null) return null;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT r.code_role FROM utilisateur u JOIN ref_role r ON u.id_role = r.id_role WHERE u.id_utilisateur = ?")) {
            ps.setInt(1, idUtilisateur);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("code_role");
            }
        } catch (Exception ignored) {}
        return null;
    }
}
