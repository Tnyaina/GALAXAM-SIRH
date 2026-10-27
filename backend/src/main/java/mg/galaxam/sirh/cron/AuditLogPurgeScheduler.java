package mg.galaxam.sirh.cron;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.backup.BackupConsistencyGate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler pour la maintenance et la purge périodique des logs d'audit.
 * Purge les enregistrements d'audit conservés au-delà de 36 mois.
 */
@Component
public class AuditLogPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditLogPurgeScheduler.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private SchedulerExecutionRegistry executionRegistry;

    @Autowired
    private BackupConsistencyGate backupGate;

    /**
     * Exécuté le 1er de chaque mois à 04:00:00.
     * Supprime les entrées d'audit_log antérieures au seuil calendaire de 36 mois.
     */
    @Scheduled(cron = "0 0 4 1 * ?", zone = "Indian/Antananarivo")
    public void purgerAnciensLogsAudit() throws Exception {
        backupGate.withWriteAccess(() -> executionRegistry.runMonthly("PURGE_AUDIT", this::purgerAnciensLogsAuditInterne));
    }

    public boolean rattraperPurgeMensuelle() throws Exception {
        return backupGate.withWriteAccess(this::rattraperPurgeMensuelleInterne);
    }

    private boolean rattraperPurgeMensuelleInterne() throws Exception {
        if (LocalDate.now(java.time.ZoneId.of("Indian/Antananarivo")).getDayOfMonth() == 1
                && java.time.LocalTime.now(java.time.ZoneId.of("Indian/Antananarivo")).isBefore(java.time.LocalTime.of(4, 0))) {
            return true;
        }
        LocalDate moisCourant = LocalDate.now(java.time.ZoneId.of("Indian/Antananarivo")).withDayOfMonth(1);
        return executionRegistry.runIfNotCompleted("PURGE_AUDIT", moisCourant, this::purgerAnciensLogsAuditInterne);
    }

    private void purgerAnciensLogsAuditInterne() throws Exception {
        Timestamp seuil36Mois = Timestamp.valueOf(LocalDate.now(java.time.ZoneId.of("Indian/Antananarivo")).minusMonths(36).atStartOfDay());
        String sql = "DELETE FROM audit_log WHERE date_action < ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, seuil36Mois);
            int nbSupprimes = ps.executeUpdate();
            if (nbSupprimes > 0) {
                log.info("[AuditLogPurgeScheduler] Purge des logs d'audit effectuée : {} entrées antérieures à 36 mois supprimées.", nbSupprimes);
            }
        } catch (Exception e) {
            log.error("[AuditLogPurgeScheduler] Erreur lors de la purge des logs d'audit : {}", e.getMessage(), e);
            throw e;
        }
    }
}
