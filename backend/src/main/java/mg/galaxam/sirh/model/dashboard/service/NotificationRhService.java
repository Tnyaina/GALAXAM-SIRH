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
        "(id_utilisateur, type_alerte, priorite, titre, message, lien, id_employe_concerne) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?)";

    /**
     * Crée une notification RH sans utilisateur cible spécifique (notification globale / équipe RH).
     *
     * @param c                 Connexion JDBC active (dans la même transaction si applicable)
     * @param typeAlerte        Code métier (ex: EMPLOYE_CREE, MOUVEMENT_APPLIQUE, CONGE_EN_ATTENTE)
     * @param priorite          Niveau de priorité : HAUTE, NORMALE, INFO
     * @param titre             Titre court affiché dans le centre de notifications
     * @param message           Corps détaillé de la notification
     * @param lien              URL de navigation relative (ex: /employes/42?tab=documents)
     * @param idEmployeConcerne Identifiant de l'employé concerné (peut être null)
     */
    public static void creer(Connection c, String typeAlerte, String priorite,
                      String titre, String message, String lien,
                      Integer idEmployeConcerne) {
        creer(c, typeAlerte, priorite, titre, message, lien, idEmployeConcerne, null);
    }

    /**
     * Crée une notification RH avec utilisateur cible optionnel.
     *
     * @param idUtilisateur     Identifiant de l'utilisateur destinataire (null = tous les RH)
     */
    public static void creer(Connection c, String typeAlerte, String priorite,
                      String titre, String message, String lien,
                      Integer idEmployeConcerne, Integer idUtilisateur) {
        if (c == null) return;
        java.sql.Savepoint sp = null;
        try {
            // Savepoint pour que l'échec d'une notification n'annule pas la transaction principale
            if (!c.getAutoCommit()) {
                sp = c.setSavepoint("notif_rh_sp");
            }
            try (PreparedStatement ps = c.prepareStatement(SQL_INSERT)) {
                if (idUtilisateur != null) ps.setInt(1, idUtilisateur);
                else ps.setNull(1, Types.INTEGER);
                ps.setString(2, typeAlerte);
                ps.setString(3, priorite);
                ps.setString(4, titre);
                ps.setString(5, message);
                ps.setString(6, lien);
                if (idEmployeConcerne != null) ps.setInt(7, idEmployeConcerne);
                else ps.setNull(7, Types.INTEGER);
                ps.executeUpdate();
            }
            if (sp != null) c.releaseSavepoint(sp);
        } catch (Exception e) {
            if (sp != null) {
                try { c.rollback(sp); } catch (Exception ignored) {}
            }
            System.err.println("[NotificationRhService] Impossible de créer la notification RH (" +
                typeAlerte + ") : " + e.getMessage());
        }
    }
}
