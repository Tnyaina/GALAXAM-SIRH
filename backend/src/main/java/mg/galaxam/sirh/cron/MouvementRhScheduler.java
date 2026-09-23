package mg.galaxam.sirh.cron;

import java.sql.Connection;
import javax.sql.DataSource;
import mg.galaxam.sirh.contrat.application.WorkflowRhService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

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

    @Autowired
    private SchedulerExecutionRegistry executionRegistry;

    /**
     * Exécution automatique chaque jour à minuit (00:00:00).
     * Vérifie et applique tous les mouvements RH validés dont la date d'effet est atteinte.
     */
    @Scheduled(cron = "0 10 0 * * ?", zone = "Indian/Antananarivo")
    public void effectuerBasculementMouvementsPlanifies() {
        executionRegistry.runDaily("MOUVEMENTS_RH", this::effectuerBasculementMouvementsPlanifiesInterne);
    }

    private void effectuerBasculementMouvementsPlanifiesInterne() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            WorkflowRhService.autoAppliquerMouvementsEchus(c);
            log.info("[SCHEDULER] Mouvements RH échus traités avec succès.");
        } catch (Exception e) {
            log.error("[SCHEDULER] Erreur lors de l'exécution du Scheduler MouvementRhScheduler : {}", e.getMessage(), e);
            throw e;
        }
    }
}
