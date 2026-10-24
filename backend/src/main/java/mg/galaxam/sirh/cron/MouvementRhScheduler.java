package mg.galaxam.sirh.cron;

import java.sql.Connection;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.backup.BackupConsistencyGate;
import mg.galaxam.sirh.contrat.application.WorkflowRhService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler nocturne et au démarrage pour la gestion automatisée des mouvements de carrière RH.
 * S'exécute au démarrage de l'application et chaque nuit à 00:10 (heure de Madagascar).
 */
@Component
public class MouvementRhScheduler {

    private static final Logger log = LoggerFactory.getLogger(MouvementRhScheduler.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private SchedulerExecutionRegistry executionRegistry;

    @Autowired
    private BackupConsistencyGate backupGate;

    /**
     * Exécution automatique chaque jour à 00:10 (heure de Madagascar).
     * Vérifie et applique tous les mouvements RH validés dont la date d'effet est atteinte.
     */
    @Scheduled(cron = "0 10 0 * * ?", zone = "Indian/Antananarivo")
    public void effectuerBasculementMouvementsPlanifies() throws Exception {
        backupGate.withWriteAccess(() -> executionRegistry.runDaily("MOUVEMENTS_RH", this::effectuerBasculementMouvementsPlanifiesInterne));
    }

    public boolean rattraperMouvements() throws Exception {
        return backupGate.withWriteAccess(() -> executionRegistry.runDailyCatchUp("MOUVEMENTS_RH", java.time.LocalTime.of(0, 10),
                this::effectuerBasculementMouvementsPlanifiesInterne));
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
