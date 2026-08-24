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
 * Scheduler pour la maintenance de la sécurité et des notifications lues.
 * S'exécute chaque nuit à 03:30:00.
 */
@Component
public class SessionSecurityScheduler {

    @Autowired
    private DataSource dataSource;

    @EventListener(ApplicationReadyEvent.class)
    public void auDemarrageApplication() {
        nettoyerSecurite();
    }

    /**
     * Exécuté chaque nuit à 03:30:00.
     * Nettoie les anciennes notifications lues de plus de 90 jours.
     */
    @Scheduled(cron = "0 30 3 * * ?")
    public void nettoyerSecurite() {
        try (Connection c = dataSource.getConnection()) {
            String sqlPurgeNotif = "DELETE FROM notification_rh WHERE est_lue = TRUE AND date_creation < CURRENT_TIMESTAMP - INTERVAL '90 days'";
            try (PreparedStatement ps = c.prepareStatement(sqlPurgeNotif)) {
                int count = ps.executeUpdate();
                if (count > 0) {
                    System.out.println("[SessionSecurityScheduler] Nettoyage des anciennes notifications lues : " + count + " supprimées.");
                }
            }
        } catch (Exception e) {
            System.err.println("[SessionSecurityScheduler] Erreur lors du nettoyage de sécurité : " + e.getMessage());
        }
    }
}
