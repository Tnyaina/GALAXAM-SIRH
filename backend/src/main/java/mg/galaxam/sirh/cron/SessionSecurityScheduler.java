package mg.galaxam.sirh.cron;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import javax.sql.DataSource;
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

    /**
     * Exécuté chaque nuit à 03:30:00.
     * Nettoie les anciennes notifications lues de plus de 90 jours et révoque les refresh tokens expirés.
     */
    @Scheduled(cron = "0 30 3 * * ?")
    public void nettoyerSecurite() {
        try (Connection c = dataSource.getConnection()) {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            Timestamp seuil90j = Timestamp.valueOf(LocalDateTime.now().minusDays(90));

            // 1. Purge des notifications lues anciennes (> 90 jours)
            String sqlPurgeNotif = "DELETE FROM notification_rh WHERE est_lue = TRUE AND date_creation < ?";
            try (PreparedStatement ps = c.prepareStatement(sqlPurgeNotif)) {
                ps.setTimestamp(1, seuil90j);
                int count = ps.executeUpdate();
                if (count > 0) {
                    log.info("[SessionSecurityScheduler] Nettoyage des anciennes notifications lues : {} supprimées.", count);
                }
            }

            // 2. Révocation des Refresh Tokens expirés
            String sqlPurgeTokens = "UPDATE utilisateur SET refresh_token = NULL, refresh_token_expiration = NULL " +
                    "WHERE refresh_token_expiration IS NOT NULL AND refresh_token_expiration < ?";
            try (PreparedStatement ps = c.prepareStatement(sqlPurgeTokens)) {
                ps.setTimestamp(1, now);
                int countTokens = ps.executeUpdate();
                if (countTokens > 0) {
                    log.info("[SessionSecurityScheduler] Nettoyage des refresh tokens expirés : {} révoqués.", countTokens);
                }
            }
        } catch (Exception e) {
            log.error("[SessionSecurityScheduler] Erreur lors du nettoyage de sécurité : {}", e.getMessage(), e);
        }
    }
}
