package mg.galaxam.sirh.cron;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Verrou et journal durable des jobs planifiés.
 *
 * <p>La base est la source de vérité : contrairement à un AtomicBoolean, ce
 * registre survit au redémarrage et coordonne les instances applicatives.
 * Une exécution RUNNING de plus de 30 minutes est considérée abandonnée et
 * peut être reprise ; une exécution FAILED est retentée au passage suivant.</p>
 */
@Service
public class SchedulerExecutionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SchedulerExecutionRegistry.class);
    private static final int STALE_MINUTES = 30;

    private final DataSource dataSource;

    public SchedulerExecutionRegistry(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @FunctionalInterface
    public interface ScheduledWork {
        void run() throws Exception;
    }

    public void runDaily(String jobCode, ScheduledWork work) {
        run(jobCode, LocalDate.now(), work);
    }

    public void runMonthly(String jobCode, ScheduledWork work) {
        run(jobCode, LocalDate.now().withDayOfMonth(1), work);
    }

    public void run(String jobCode, LocalDate executionKey, ScheduledWork work) {
        if (!tryStart(jobCode, executionKey)) return;
        try {
            work.run();
            markCompleted(jobCode, executionKey);
            log.info("[SCHEDULER] {} terminé pour la clé métier {}.", jobCode, executionKey);
        } catch (Exception exception) {
            markFailed(jobCode, executionKey, exception);
            log.error("[SCHEDULER] {} échoué pour la clé métier {} : {}", jobCode, executionKey,
                    exception.getMessage(), exception);
        }
    }

    private boolean tryStart(String jobCode, LocalDate executionKey) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String insert = "INSERT INTO scheduler_execution "
                        + "(job_code, execution_key, status) VALUES (?, ?, 'RUNNING') "
                        + "ON CONFLICT (job_code, execution_key) DO NOTHING";
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    statement.setString(1, jobCode);
                    statement.setDate(2, Date.valueOf(executionKey));
                    if (statement.executeUpdate() == 1) {
                        connection.commit();
                        return true;
                    }
                }

                String select = "SELECT status, started_at FROM scheduler_execution "
                        + "WHERE job_code = ? AND execution_key = ? FOR UPDATE";
                try (PreparedStatement statement = connection.prepareStatement(select)) {
                    statement.setString(1, jobCode);
                    statement.setDate(2, Date.valueOf(executionKey));
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return false;
                        }
                        String status = result.getString("status");
                        boolean stale = result.getTimestamp("started_at") != null
                                && result.getTimestamp("started_at").toLocalDateTime()
                                .isBefore(java.time.LocalDateTime.now().minusMinutes(STALE_MINUTES));
                        if ("COMPLETED".equals(status) || ("RUNNING".equals(status) && !stale)) {
                            connection.commit();
                            return false;
                        }
                    }
                }

                String retry = "UPDATE scheduler_execution SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP, "
                        + "completed_at = NULL, error_message = NULL, attempt_count = attempt_count + 1 "
                        + "WHERE job_code = ? AND execution_key = ?";
                try (PreparedStatement statement = connection.prepareStatement(retry)) {
                    statement.setString(1, jobCode);
                    statement.setDate(2, Date.valueOf(executionKey));
                    statement.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        } catch (Exception exception) {
            log.error("[SCHEDULER] Impossible d'acquérir le verrou {} / {} : {}", jobCode, executionKey,
                    exception.getMessage());
            return false;
        }
    }

    private void markCompleted(String jobCode, LocalDate executionKey) {
        updateResult(jobCode, executionKey, "COMPLETED", null);
    }

    private void markFailed(String jobCode, LocalDate executionKey, Exception exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        updateResult(jobCode, executionKey, "FAILED", message.length() > 1000 ? message.substring(0, 1000) : message);
    }

    private void updateResult(String jobCode, LocalDate executionKey, String status, String errorMessage) {
        String sql = "UPDATE scheduler_execution SET status = ?, completed_at = CURRENT_TIMESTAMP, error_message = ? "
                + "WHERE job_code = ? AND execution_key = ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setString(2, errorMessage);
            statement.setString(3, jobCode);
            statement.setDate(4, Date.valueOf(executionKey));
            statement.executeUpdate();
        } catch (Exception updateError) {
            log.error("[SCHEDULER] Impossible de journaliser {} / {} : {}", jobCode, executionKey,
                    updateError.getMessage());
        }
    }
}
