package mg.galaxam.sirh.cron;

import java.sql.Connection;
import java.time.LocalDate;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.application.CongeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler nocturne pour la purge triennale des droits aux congés selon la règle FIFO stricte.
 * 100% de la logique d'agrégation et de calcul FIFO est exécutée en mémoire Java.
 */
@Component
public class PurgeCongeScheduler {

    private static final Logger log = LoggerFactory.getLogger(PurgeCongeScheduler.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CongeService congeService;

    @Autowired
    private SchedulerExecutionRegistry executionRegistry;

    @Scheduled(cron = "0 0 2 * * ?", zone = "Indian/Antananarivo")
    public void purgerCongesExpires() {
        executionRegistry.runDaily("PURGE_CONGES", () -> purgerCongesExpiresInterne(LocalDate.now()));
    }

    public void purgerCongesExpires(LocalDate dateReference) {
        executionRegistry.run("PURGE_CONGES", dateReference != null ? dateReference : LocalDate.now(),
                () -> purgerCongesExpiresInterne(dateReference));
    }

    private void purgerCongesExpiresInterne(LocalDate dateReference) throws Exception {
        LocalDate ref = dateReference != null ? dateReference : LocalDate.now();
        log.info("[PurgeCongeScheduler] Lancement de la purge triennale FIFO (Moteur Java) au {}...", ref);
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                congeService.executerPurge(ref, c);
                c.commit();
                log.info("[PurgeCongeScheduler] Purge triennale FIFO effectuée avec succès.");
            } catch (Exception e) {
                c.rollback();
                log.error("[PurgeCongeScheduler] Erreur lors de la purge des congés : {}", e.getMessage(), e);
                throw e;
            }
        } catch (Exception e) {
            log.error("[PurgeCongeScheduler] Erreur connexion BDD : {}", e.getMessage(), e);
            throw e;
        }
    }

}
