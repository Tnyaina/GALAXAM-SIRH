package mg.galaxam.sirh.cron;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Journal durable des jobs et propriétaire des tentatives en cours.
 *
 * <p>Une ligne RUNNING est reprise après expiration de son bail de 30 minutes.
 * Chaque tentative porte un jeton distinct : les heartbeats et la finalisation
 * ne peuvent modifier que la tentative qui détient encore ce jeton.</p>
 */
@Service
public class SchedulerExecutionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SchedulerExecutionRegistry.class);
    private static final Duration DEFAULT_LEASE = Duration.ofMinutes(30);
    private static final Duration DEFAULT_HEARTBEAT = Duration.ofMinutes(10);
    // Une tâche planifiée qui dépasse presque une journée est considérée bloquée ;
    // le bail cesse alors d'être renouvelé et une tentative ultérieure peut la reprendre.
    private static final Duration DEFAULT_MAX_RUNTIME = Duration.ofHours(23);
    private static final ScheduledExecutorService HEARTBEATS = Executors.newScheduledThreadPool(5,
            daemonThreadFactory());

    private final DataSource dataSource;
    private final Duration leaseDuration;
    private final Duration heartbeatInterval;
    private final Duration maxRuntime;

    @org.springframework.beans.factory.annotation.Autowired
    public SchedulerExecutionRegistry(DataSource dataSource) {
        this(dataSource, DEFAULT_LEASE, DEFAULT_HEARTBEAT);
    }

    SchedulerExecutionRegistry(DataSource dataSource, Duration leaseDuration, Duration heartbeatInterval) {
        this(dataSource, leaseDuration, heartbeatInterval, DEFAULT_MAX_RUNTIME);
    }

    SchedulerExecutionRegistry(DataSource dataSource, Duration leaseDuration, Duration heartbeatInterval,
            Duration maxRuntime) {
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("La durée du bail scheduler doit être positive.");
        }
        if (heartbeatInterval == null || heartbeatInterval.isZero() || heartbeatInterval.isNegative()
                || heartbeatInterval.compareTo(leaseDuration) >= 0) {
            throw new IllegalArgumentException("L'intervalle heartbeat doit être positif et inférieur au bail.");
        }
        if (maxRuntime == null || maxRuntime.compareTo(leaseDuration) <= 0) {
            throw new IllegalArgumentException("La durée maximale d'exécution doit dépasser le bail.");
        }
        this.dataSource = dataSource;
        this.leaseDuration = leaseDuration;
        this.heartbeatInterval = heartbeatInterval;
        this.maxRuntime = maxRuntime;
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
        UUID attemptToken = tryStart(jobCode, executionKey);
        if (attemptToken == null) return;

        AtomicBoolean ownsAttempt = new AtomicBoolean(true);
        ScheduledFuture<?> heartbeat = HEARTBEATS.scheduleAtFixedRate(
                () -> renewLease(jobCode, executionKey, attemptToken, ownsAttempt),
                heartbeatInterval.toMillis(), heartbeatInterval.toMillis(), TimeUnit.MILLISECONDS);
        try {
            work.run();
            if (updateResult(jobCode, executionKey, attemptToken, "COMPLETED", null)) {
                log.info("[SCHEDULER] {} terminé pour la clé métier {}.", jobCode, executionKey);
            } else {
                ownsAttempt.set(false);
                log.warn("[SCHEDULER] {} a terminé après avoir perdu sa réservation pour {} ; le statut reste détenu par la nouvelle tentative.",
                        jobCode, executionKey);
            }
        } catch (Exception exception) {
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
            if (!updateResult(jobCode, executionKey, attemptToken, "FAILED", truncate(message, 1000))) {
                ownsAttempt.set(false);
                log.warn("[SCHEDULER] {} a échoué après avoir perdu sa réservation pour {}.", jobCode, executionKey);
            }
            log.error("[SCHEDULER] {} échoué pour la clé métier {} : {}", jobCode, executionKey,
                    exception.getMessage(), exception);
        } finally {
            ownsAttempt.set(false);
            heartbeat.cancel(false);
        }
    }

    private UUID tryStart(String jobCode, LocalDate executionKey) {
        UUID token = UUID.randomUUID();
        String insert = "INSERT INTO scheduler_execution "
                + "(job_code, execution_key, status, attempt_token, lease_until) "
                + "VALUES (?, ?, 'RUNNING', ?, CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond')) "
                + "ON CONFLICT (job_code, execution_key) DO NOTHING";
        String retry = "UPDATE scheduler_execution SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP, "
                + "completed_at = NULL, error_message = NULL, attempt_count = attempt_count + 1, "
                + "attempt_token = ?, lease_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond') "
                + "WHERE job_code = ? AND execution_key = ? AND (status = 'FAILED' "
                + "OR (status = 'RUNNING' AND lease_until <= CURRENT_TIMESTAMP))";
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    statement.setString(1, jobCode);
                    statement.setDate(2, Date.valueOf(executionKey));
                    statement.setObject(3, token);
                    statement.setLong(4, leaseDuration.toMillis());
                    if (statement.executeUpdate() == 1) {
                        connection.commit();
                        return token;
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(retry)) {
                    statement.setObject(1, token);
                    statement.setLong(2, leaseDuration.toMillis());
                    statement.setString(3, jobCode);
                    statement.setDate(4, Date.valueOf(executionKey));
                    if (statement.executeUpdate() == 1) {
                        connection.commit();
                        return token;
                    }
                }
                connection.commit();
                return null;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        } catch (Exception exception) {
            log.error("[SCHEDULER] Impossible d'acquérir le verrou {} / {} : {}", jobCode, executionKey,
                    exception.getMessage());
            return null;
        }
    }

    private void renewLease(String jobCode, LocalDate executionKey, UUID token, AtomicBoolean ownsAttempt) {
        if (!ownsAttempt.get()) return;
        String sql = "UPDATE scheduler_execution SET lease_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond') "
                + "WHERE job_code = ? AND execution_key = ? AND status = 'RUNNING' "
                + "AND attempt_token = ? AND lease_until > CURRENT_TIMESTAMP "
                + "AND started_at > CURRENT_TIMESTAMP - (? * INTERVAL '1 millisecond')";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, leaseDuration.toMillis());
            statement.setString(2, jobCode);
            statement.setDate(3, Date.valueOf(executionKey));
            statement.setObject(4, token);
            statement.setLong(5, maxRuntime.toMillis());
            if (statement.executeUpdate() == 0 && ownsAttempt.compareAndSet(true, false)) {
                expirerTentativeTropLongue(jobCode, executionKey, token);
                log.warn("[SCHEDULER] Bail perdu pour {} / {} ; le worker cesse ses heartbeats.", jobCode, executionKey);
            }
        } catch (Exception exception) {
            // Une erreur transitoire n'étend pas le bail. Si le bail expire, ce worker
            // ne pourra ni le renouveler ni finaliser la tentative.
            log.error("[SCHEDULER] Heartbeat impossible pour {} / {} : {}", jobCode, executionKey,
                    exception.getMessage());
        }
    }

    private void expirerTentativeTropLongue(String jobCode, LocalDate executionKey, UUID token) {
        String sql = "UPDATE scheduler_execution SET lease_until = CURRENT_TIMESTAMP "
                + "WHERE job_code = ? AND execution_key = ? AND status = 'RUNNING' AND attempt_token = ? "
                + "AND started_at <= CURRENT_TIMESTAMP - (? * INTERVAL '1 millisecond')";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, jobCode);
            statement.setDate(2, Date.valueOf(executionKey));
            statement.setObject(3, token);
            statement.setLong(4, maxRuntime.toMillis());
            statement.executeUpdate();
        } catch (Exception exception) {
            log.error("[SCHEDULER] Impossible d'expirer le bail maximal de {} / {} : {}", jobCode, executionKey,
                    exception.getMessage());
        }
    }

    private boolean updateResult(String jobCode, LocalDate executionKey, UUID token,
            String status, String errorMessage) {
        String sql = "UPDATE scheduler_execution SET status = ?, completed_at = CURRENT_TIMESTAMP, "
                + "error_message = ?, lease_until = NULL "
                + "WHERE job_code = ? AND execution_key = ? AND status = 'RUNNING' "
                + "AND attempt_token = ? AND lease_until > CURRENT_TIMESTAMP "
                + "AND started_at > CURRENT_TIMESTAMP - (? * INTERVAL '1 millisecond')";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setString(2, errorMessage);
            statement.setString(3, jobCode);
            statement.setDate(4, Date.valueOf(executionKey));
            statement.setObject(5, token);
            statement.setLong(6, maxRuntime.toMillis());
            return statement.executeUpdate() == 1;
        } catch (Exception updateError) {
            log.error("[SCHEDULER] Impossible de journaliser {} / {} : {}", jobCode, executionKey,
                    updateError.getMessage());
            return false;
        }
    }

    private static String truncate(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "scheduler-execution-heartbeat");
            thread.setDaemon(true);
            return thread;
        };
    }
}
