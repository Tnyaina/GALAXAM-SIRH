package mg.galaxam.sirh.api.controller.dashboard;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
     * Récupère les notifications non lues de l'utilisateur courant.
     * Inclut aussi les notifications broadcast (id_utilisateur IS NULL).
     * Limite à 50 notifications (performance UI).
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getNotifications() {
        try (Connection c = dataSource.getConnection()) {
            Integer idUtilisateur = getCurrentUserId(c);

            String sql = "SELECT id_notification, type_alerte, priorite, titre, message, " +
                    "lien, id_employe_concerne, est_lue, date_creation, date_lecture " +
                    "FROM notification_rh " +
                    "WHERE est_lue = FALSE " +
                    "AND (id_utilisateur IS NULL OR id_utilisateur = ?) " +
                    "ORDER BY date_creation DESC " +
                    "LIMIT 50";

            List<Map<String, Object>> notifications = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setObject(1, idUtilisateur);
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des notifications.");
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

            String sql = "UPDATE notification_rh SET est_lue = TRUE, date_lecture = CURRENT_TIMESTAMP " +
                    "WHERE est_lue = FALSE " +
                    "AND (id_utilisateur IS NULL OR id_utilisateur = ?)";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setObject(1, idUtilisateur);
                int updated = ps.executeUpdate();
                return ResponseEntity.ok(Map.of("marquees", updated));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors du marquage des notifications.");
        }
    }

    /**
     * Marquer UNE notification spécifique comme lue.
     */
    @PutMapping("/{id}/lire")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> marquerUne(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            String sql = "UPDATE notification_rh SET est_lue = TRUE, date_lecture = CURRENT_TIMESTAMP " +
                    "WHERE id_notification = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            return ResponseEntity.ok(Map.of("id", id, "lue", true));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors du marquage de la notification.");
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
            String sql = "DELETE FROM notification_rh WHERE (id_utilisateur IS NULL OR id_utilisateur = ?)";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setObject(1, idUtilisateur);
                int deleted = ps.executeUpdate();
                return ResponseEntity.ok(Map.of("supprimees", deleted));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la purge des notifications.");
        }
    }

    // =========================================================================
    // Utilitaire — Résolution de l'utilisateur courant
    // =========================================================================

    private Integer getCurrentUserId(Connection c) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;
        if (auth.getPrincipal() instanceof CustomUserDetails) {
            return ((CustomUserDetails) auth.getPrincipal()).getIdUtilisateur();
        }
        // Fallback : recherche par identifiant
        String identifiant = auth.getName();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id_utilisateur FROM utilisateur WHERE identifiant = ?")) {
            ps.setString(1, identifiant);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : null;
        }
    }
}
