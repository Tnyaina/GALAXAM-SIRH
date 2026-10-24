package mg.galaxam.sirh.cron;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.backup.BackupConsistencyGate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler pour la maintenance de la sécurité, purge des sessions expirées et des notifications lues.
 * S'exécute chaque nuit à 03:30:00.
 */
@Component
public class SessionSecurityScheduler {

    private static final Logger log = LoggerFactory.getLogger(SessionSecurityScheduler.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private SchedulerExecutionRegistry executionRegistry;

    @Autowired
    private BackupConsistencyGate backupGate;

    /**
     * Exécuté chaque nuit à 03:30:00.
     * Nettoie les anciennes notifications lues de plus de 90 jours et révoque les refresh tokens expirés.
     */
    @Scheduled(cron = "0 30 3 * * ?", zone = "Indian/Antananarivo")
    public void nettoyerSecurite() throws Exception {
        backupGate.withWriteAccess(() -> executionRegistry.runDaily("SECURITE_SESSIONS", this::nettoyerSecuriteInterne));
    }

    public boolean rattraperSecurite() throws Exception {
        return backupGate.withWriteAccess(() -> executionRegistry.runDailyCatchUp("SECURITE_SESSIONS", java.time.LocalTime.of(3, 30),
                this::nettoyerSecuriteInterne));
    }

    private void nettoyerSecuriteInterne() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());

            // Révocation des refresh tokens de session expirés.
            String sqlPurgeTokens = "UPDATE utilisateur_session SET refresh_token = NULL, previous_refresh_token = NULL, revoked_at = ? " +
                    "WHERE refresh_token_expiration < ? AND revoked_at IS NULL";
            try (PreparedStatement ps = c.prepareStatement(sqlPurgeTokens)) {
                ps.setTimestamp(1, now);
                ps.setTimestamp(2, now);
                int countTokens = ps.executeUpdate();
                if (countTokens > 0) {
                    log.info("[SessionSecurityScheduler] Nettoyage des refresh tokens expirés : {} révoqués.", countTokens);
                }
            }
        } catch (Exception e) {
            log.error("[SessionSecurityScheduler] Erreur lors du nettoyage de sécurité : {}", e.getMessage(), e);
            throw e;
        }
    }
}
