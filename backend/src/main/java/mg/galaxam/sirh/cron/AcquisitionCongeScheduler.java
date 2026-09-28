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

/** Déclencheur de bonification ; l'acquisition des congés est effectuée à la clôture de paie. */
@Component
public class AcquisitionCongeScheduler {

    private static final Logger log = LoggerFactory.getLogger(AcquisitionCongeScheduler.class);

    @Autowired private DataSource dataSource;
    @Autowired private CongeService congeService;
    @Autowired private SchedulerExecutionRegistry executionRegistry;

    @Scheduled(cron = "0 0 1 1 * ?", zone = "Indian/Antananarivo")
    public void acquerirCongesMensuels() {
        LocalDate moisClos = LocalDate.now().withDayOfMonth(1).minusDays(1);
        executionRegistry.run("BONIFICATION_CONGES", moisClos.withDayOfMonth(1),
                () -> executer(moisClos));
    }

    private void executer(LocalDate moisClos) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int nbEmployes = congeService.executerBonificationMensuelle(moisClos, c);
                c.commit();
                log.info("[AcquisitionCongeScheduler] Bonification vérifiée pour {} employé(s).", nbEmployes);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }
}
