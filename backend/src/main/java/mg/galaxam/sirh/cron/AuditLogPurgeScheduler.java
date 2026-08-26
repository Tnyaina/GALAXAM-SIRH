package mg.galaxam.sirh.cron;

import java.sql.Connection;
import java.sql.PreparedStatement;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler pour la maintenance et la purge périodique des logs d'audit.
 * Purge les enregistrements d'audit de plus d'un an pour préserver les performances BDD.
 */
@Component
public class AuditLogPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditLogPurgeScheduler.class);

    @Autowired
    private DataSource dataSource;

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
                log.info("[AuditLogPurgeScheduler] Purge des logs d'audit effectuée : {} entrées supprimées (> 1 an).", nbSupprimes);
            }
        } catch (Exception e) {
            log.error("[AuditLogPurgeScheduler] Erreur lors de la purge des logs d'audit : {}", e.getMessage(), e);
        }
    }
}
