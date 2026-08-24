package mg.galaxam.sirh.cron;

import java.sql.Connection;
import java.sql.PreparedStatement;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler pour la maintenance et la purge périodique des logs d'audit.
 * Purge les enregistrements d'audit de plus d'un an pour préserver les performances BDD.
 */
@Component
public class AuditLogPurgeScheduler {

    @Autowired
    private DataSource dataSource;

    /**
     * Rattrapage automatique lors du démarrage du serveur Spring Boot.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void auDemarrageApplication() {
        purgerAnciensLogsAudit();
    }

    /**
     * Exécuté le 1er de chaque mois à 04:00:00.
     * Supprime les entrées d'audit_log de plus de 365 jours.
     */
    @Scheduled(cron = "0 0 4 1 * ?")
    public void purgerAnciensLogsAudit() {
        String sql = "DELETE FROM audit_log WHERE date_action < CURRENT_TIMESTAMP - INTERVAL '365 days'";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int nbSupprimes = ps.executeUpdate();
            if (nbSupprimes > 0) {
                System.out.println("[AuditLogPurgeScheduler] Purge des logs d'audit effectuée : " + nbSupprimes + " entrées supprimées (> 1 an).");
            }
        } catch (Exception e) {
            System.err.println("[AuditLogPurgeScheduler] Erreur lors de la purge des logs d'audit : " + e.getMessage());
        }
    }
}
