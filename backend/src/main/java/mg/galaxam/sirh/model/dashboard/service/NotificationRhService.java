package mg.galaxam.sirh.model.dashboard.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import org.springframework.stereotype.Service;

/**
 * Source unique de vérité pour la création de notifications RH.
 *
 * Toute notification du système doit passer par ce service.
 * Règle : une seule implémentation = une seule colonne SQL à modifier si le schéma évolue.
 *
 * Anciens patterns supprimés (remplacés par ce service) :
 *  - INSERT inline dans EmployeService.java
 *  - INSERT inline dans EmployeController.java
 *  - méthode statique privée creerNotificationRh() dans WorkflowRhService.java
 *  - INSERT inline dans PurgeCongeScheduler.java
 *  - méthode statique publique DemandeConge.creerNotificationRh()
 *  - INSERT inline dans NotificationRhScheduler.java
 */
@Service
public class NotificationRhService {

    private static final String SQL_INSERT =
        "INSERT INTO notification_rh " +
        "(id_utilisateur, cible_role, type_alerte, priorite, titre, message, lien, id_employe_concerne) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * Cree une notification RH sans utilisateur cible specifique (notification globale / equipe RH).
     */
    public static void creer(Connection c, String typeAlerte, String priorite,
                      String titre, String message, String lien,
                      Integer idEmployeConcerne) {
        creer(c, typeAlerte, priorite, titre, message, lien, idEmployeConcerne, null, null);
    }

    /**
     * Cree une notification RH avec utilisateur cible optionnel.
     */
    public static void creer(Connection c, String typeAlerte, String priorite,
                      String titre, String message, String lien,
                      Integer idEmployeConcerne, Integer idUtilisateur) {
        creer(c, typeAlerte, priorite, titre, message, lien, idEmployeConcerne, idUtilisateur, null);
    }

    /**
     * Cree une notification RH ciblee par role (ex: ROLE_ADMIN, ROLE_AGENT_POINTAGE).
     */
    public static void creerPourRole(Connection c, String cibleRole, String typeAlerte, String priorite,
                             String titre, String message, String lien,
                             Integer idEmployeConcerne) {
        creer(c, typeAlerte, priorite, titre, message, lien, idEmployeConcerne, null, cibleRole);
    }

    /**
     * Methode principale avec support de l'utilisateur cible et/ou du role cible.
     */
    public static void creer(Connection c, String typeAlerte, String priorite,
                      String titre, String message, String lien,
                      Integer idEmployeConcerne, Integer idUtilisateur, String cibleRole) {
        if (c == null) return;
        java.sql.Savepoint sp = null;
        try {
            // Savepoint pour que l'echec d'une notification n'annule pas la transaction principale
            if (!c.getAutoCommit()) {
                sp = c.setSavepoint("notif_rh_sp");
            }
            try (PreparedStatement ps = c.prepareStatement(SQL_INSERT)) {
                if (idUtilisateur != null) ps.setInt(1, idUtilisateur);
                else ps.setNull(1, Types.INTEGER);

                if (cibleRole != null && !cibleRole.trim().isEmpty()) ps.setString(2, cibleRole.trim());
                else ps.setNull(2, Types.VARCHAR);

                ps.setString(3, typeAlerte);
                ps.setString(4, priorite);
                ps.setString(5, titre);
                ps.setString(6, message);
                ps.setString(7, lien);
                if (idEmployeConcerne != null) ps.setInt(8, idEmployeConcerne);
                else ps.setNull(8, Types.INTEGER);
                ps.executeUpdate();
            }
            if (sp != null) c.releaseSavepoint(sp);
        } catch (Exception e) {
            if (sp != null) {
                try { c.rollback(sp); } catch (Exception ignored) {}
            }
            System.err.println("[NotificationRhService] Impossible de creer la notification RH (" +
                typeAlerte + ") : " + e.getMessage());
        }
    }
}
