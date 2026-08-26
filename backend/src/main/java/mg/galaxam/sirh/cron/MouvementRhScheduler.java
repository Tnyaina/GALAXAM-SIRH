package mg.galaxam.sirh.cron;

import java.sql.Connection;
import javax.sql.DataSource;
import mg.galaxam.sirh.model.contrat.WorkflowRhService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler nocturne et au démarrage pour la gestion automatisée des mouvements de carrière RH.
 * S'exécute au démarrage de l'application et chaque nuit à Minuit pile (00:00:00).
 */
@Component
public class MouvementRhScheduler {

    private static final Logger log = LoggerFactory.getLogger(MouvementRhScheduler.class);

    @Autowired
    private DataSource dataSource;

    /**
     * Rattrapage automatique lors du démarrage du serveur Spring Boot.
     * Applique immédiatement tout mouvement RH dont la date d'effet est atteinte ou dépassée.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void auDemarrageApplication() {
        log.info("[SCHEDULER] Rattrapage des mouvements RH échus au démarrage...");
        effectuerBasculementMouvementsPlanifies();
    }

    /**
     * Exécution automatique chaque jour à minuit (00:00:00).
     * Vérifie et applique tous les mouvements RH validés dont la date d'effet est atteinte.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void effectuerBasculementMouvementsPlanifies() {
        try (Connection c = dataSource.getConnection()) {
            WorkflowRhService.autoAppliquerMouvementsEchus(c);
            log.info("[SCHEDULER] Mouvements RH échus traités avec succès.");
        } catch (Exception e) {
            log.error("[SCHEDULER] Erreur lors de l'exécution du Scheduler MouvementRhScheduler : {}", e.getMessage(), e);
        }
    }
}
