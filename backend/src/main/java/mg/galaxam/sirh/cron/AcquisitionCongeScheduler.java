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

/** Déclencheur technique : les règles et le SQL d'acquisition appartiennent à CongeService. */
@Component
public class AcquisitionCongeScheduler {

    private static final Logger log = LoggerFactory.getLogger(AcquisitionCongeScheduler.class);

    @Autowired private DataSource dataSource;
    @Autowired private CongeService congeService;
    @Autowired private SchedulerExecutionRegistry executionRegistry;

    @Scheduled(cron = "0 0 1 * * ?", zone = "Indian/Antananarivo")
    public void acquerirCongesMensuels() {
        executionRegistry.runMonthly("ACQUISITION_CONGES", this::executer);
    }

    private void executer() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int nbEmployes = congeService.executerAcquisitionMensuelle(LocalDate.now(), c);
                c.commit();
                log.info("[AcquisitionCongeScheduler] Acquisition terminée pour {} employé(s).", nbEmployes);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }
}
