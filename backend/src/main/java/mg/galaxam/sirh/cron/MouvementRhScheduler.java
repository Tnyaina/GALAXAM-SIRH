package mg.galaxam.sirh.cron;

import java.sql.Connection;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.controller.contrat.WorkflowRhController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler nocturne pour la gestion automatisée des mouvements de carrière RH.
 * S'exécute chaque nuit à Minuit pile (00:00:00).
 */
@Component
public class MouvementRhScheduler {

    @Autowired
    private DataSource dataSource;

    /**
     * Exécution automatique chaque jour à minuit (00:00:00).
     * Vérifie et applique tous les mouvements RH validés dont la date d'effet est atteinte.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void effectuerBasculementMouvementsPlanifies() {
        try (Connection c = dataSource.getConnection()) {
            WorkflowRhController.autoAppliquerMouvementsEchus(c);
        } catch (Exception e) {
            System.err.println("Erreur lors de l'exécution du Scheduler MouvementRhScheduler : " + e.getMessage());
            e.printStackTrace();
        }
    }
}
