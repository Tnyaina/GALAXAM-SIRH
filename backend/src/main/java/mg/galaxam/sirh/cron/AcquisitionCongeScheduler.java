package mg.galaxam.sirh.cron;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.ZoneId;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.backup.BackupConsistencyGate;
import mg.galaxam.sirh.conge.application.CongeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Vérifie les bonifications d'ancienneté du mois clos ; l'acquisition reste liée à la clôture de paie. */
@Component
public class AcquisitionCongeScheduler {

    private static final Logger log = LoggerFactory.getLogger(AcquisitionCongeScheduler.class);

    @Autowired private DataSource dataSource;
    @Autowired private CongeService congeService;
    @Autowired private SchedulerExecutionRegistry executionRegistry;
    @Autowired private BackupConsistencyGate backupGate;

    @Scheduled(cron = "0 0 1 1 * ?", zone = "Indian/Antananarivo")
    public boolean bonifierCongesAnciennete() throws Exception {
        LocalDate moisClos = LocalDate.now(ZoneId.of("Indian/Antananarivo")).withDayOfMonth(1).minusDays(1);
        return backupGate.withWriteAccess(() -> executionRegistry.run("BONIFICATION_CONGES", moisClos.withDayOfMonth(1), () -> executer(moisClos)));
    }

    public boolean rattraperBonificationMensuelle() throws Exception {
        return backupGate.withWriteAccess(this::rattraperBonificationMensuelleInterne);
    }

    private boolean rattraperBonificationMensuelleInterne() throws Exception {
        if (LocalDate.now(ZoneId.of("Indian/Antananarivo")).getDayOfMonth() == 1
                && java.time.LocalTime.now(ZoneId.of("Indian/Antananarivo")).isBefore(java.time.LocalTime.of(1, 0))) {
            return true;
        }
        LocalDate dernierMoisEchu = LocalDate.now(ZoneId.of("Indian/Antananarivo"))
                .withDayOfMonth(1).minusMonths(1);
        LocalDate premierMoisDuRattrapage = executionRegistry.firstRecordedPeriod("BONIFICATION_CONGES", dernierMoisEchu);
        for (LocalDate periode : executionRegistry.findIncompletePeriods("BONIFICATION_CONGES",
                premierMoisDuRattrapage, dernierMoisEchu.withDayOfMonth(1), 6)) {
            boolean periodeOk = executionRegistry.run("BONIFICATION_CONGES", periode,
                    () -> executer(periode.plusMonths(1).minusDays(1)));
            if (!periodeOk) return false;
        }
        return !executionRegistry.hasMoreIncompletePeriods("BONIFICATION_CONGES",
                premierMoisDuRattrapage, dernierMoisEchu.withDayOfMonth(1));
    }

    private void executer(LocalDate moisClos) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int nbEmployes = congeService.executerBonificationMensuelle(moisClos, c);
                c.commit();
                log.info("[AcquisitionCongeScheduler] Bonification vérifiée pour {} employé(s), référence {}.", nbEmployes, moisClos);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }
}
