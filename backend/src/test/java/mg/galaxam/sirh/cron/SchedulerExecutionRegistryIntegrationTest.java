package mg.galaxam.sirh.cron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import mg.galaxam.sirh.conge.persistence.MouvementSoldeConge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SchedulerExecutionRegistryIntegrationTest {

    @Autowired
    private DataSource dataSource;

    private String jobCode;
    private LocalDate executionKey;
    private Integer employeeId;

    @BeforeEach
    void setUp() {
        jobCode = "IT_SCHED_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        executionKey = LocalDate.now();
    }

    @AfterEach
    void cleanUp() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM scheduler_execution WHERE job_code = ? AND execution_key = ?")) {
                ps.setString(1, jobCode);
                ps.setDate(2, Date.valueOf(executionKey));
                ps.executeUpdate();
            }
            if (employeeId != null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM mouvement_solde_conge WHERE id_employe = ? AND type_mouvement = 'BONIFICATION'")) {
                    ps.setInt(1, employeeId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM employe WHERE id_employe = ?")) {
                    ps.setInt(1, employeeId);
                    ps.executeUpdate();
                }
            }
        }
    }

    @Test
    void deuxWorkersNePeuventReserverLaMemeCleEnMemeTemps() throws Exception {
        SchedulerExecutionRegistry registry = registry(Duration.ofSeconds(3), Duration.ofMillis(500), Duration.ofSeconds(10));
        CountDownLatch travailCommence = new CountDownLatch(1);
        CountDownLatch libererTravail = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = workers.submit(() -> registry.run(jobCode, executionKey, () -> {
                executions.incrementAndGet();
                travailCommence.countDown();
                if (!libererTravail.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("timeout test");
            }));
            assertTrue(travailCommence.await(3, TimeUnit.SECONDS));

            registry.run(jobCode, executionKey, executions::incrementAndGet);
            assertEquals(1, executions.get());
            assertEquals("RUNNING", state().status());

            libererTravail.countDown();
            first.get(3, TimeUnit.SECONDS);
            assertEquals("COMPLETED", state().status());
            assertEquals(1, state().attemptCount());
        } finally {
            libererTravail.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void executionNormaleEtEchecSontJournalisesEtUnEchecEstRejouable() throws Exception {
        SchedulerExecutionRegistry registry = registry(Duration.ofSeconds(2), Duration.ofMillis(300), Duration.ofSeconds(5));
        registry.run(jobCode, executionKey, () -> { });
        assertEquals("COMPLETED", state().status());
        assertNotNull(state().attemptToken());
        assertNull(state().leaseUntil());

        jobCode = jobCode + "_FAIL";
        registry.run(jobCode, executionKey, () -> { throw new IllegalStateException("échec attendu"); });
        assertEquals("FAILED", state().status());
        registry.run(jobCode, executionKey, () -> { });
        assertEquals("COMPLETED", state().status());
        assertEquals(2, state().attemptCount());
    }

    @Test
    void ligneRunSansWorkerSimuleUnCrashEtEstReprise() throws Exception {
        UUID ancienJeton = UUID.randomUUID();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO scheduler_execution (job_code, execution_key, status, attempt_count, attempt_token, lease_until) "
                        + "VALUES (?, ?, 'RUNNING', 1, ?, CURRENT_TIMESTAMP - INTERVAL '1 second')")) {
            ps.setString(1, jobCode);
            ps.setDate(2, Date.valueOf(executionKey));
            ps.setObject(3, ancienJeton);
            ps.executeUpdate();
        }

        registry(Duration.ofSeconds(2), Duration.ofMillis(300), Duration.ofSeconds(5))
                .run(jobCode, executionKey, () -> { });
        State state = state();
        assertEquals("COMPLETED", state.status());
        assertEquals(2, state.attemptCount());
        assertNotEquals(ancienJeton, state.attemptToken());
    }

    @Test
    void ancienWorkerNePeutNiRenouvelerNiFinaliserApresReprise() throws Exception {
        SchedulerExecutionRegistry registry = registry(Duration.ofSeconds(2), Duration.ofMillis(200), Duration.ofSeconds(5));
        CountDownLatch travailCommence = new CountDownLatch(1);
        CountDownLatch libererAncien = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> old = worker.submit(() -> registry.run(jobCode, executionKey, () -> {
                travailCommence.countDown();
                if (!libererAncien.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("timeout test");
            }));
            assertTrue(travailCommence.await(3, TimeUnit.SECONDS));
            UUID oldToken = state().attemptToken();
            expireLease();

            registry.run(jobCode, executionKey, () -> { });
            State afterTakeover = state();
            assertEquals("COMPLETED", afterTakeover.status());
            assertEquals(2, afterTakeover.attemptCount());
            assertNotEquals(oldToken, afterTakeover.attemptToken());

            Thread.sleep(250); // laisse passer le heartbeat de l'ancien worker
            assertEquals("COMPLETED", state().status());
            assertNull(state().leaseUntil());

            libererAncien.countDown();
            old.get(3, TimeUnit.SECONDS);
            assertEquals("COMPLETED", state().status());
        } finally {
            libererAncien.countDown();
            worker.shutdownNow();
        }
    }

    @Test
    void heartbeatMaintientUnTraitementAuDelaDePlusieursBaux() throws Exception {
        SchedulerExecutionRegistry registry = registry(Duration.ofMillis(300), Duration.ofMillis(60), Duration.ofSeconds(3));
        CountDownLatch travailCommence = new CountDownLatch(1);
        CountDownLatch libererTravail = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> run = worker.submit(() -> registry.run(jobCode, executionKey, () -> {
                travailCommence.countDown();
                if (!libererTravail.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("timeout test");
            }));
            assertTrue(travailCommence.await(3, TimeUnit.SECONDS));
            Thread.sleep(700); // plus de deux durées de bail ; intervalle accéléré pour le test
            assertTrue(state().leaseUntil().after(new java.sql.Timestamp(System.currentTimeMillis())));
            libererTravail.countDown();
            run.get(3, TimeUnit.SECONDS);
            assertEquals("COMPLETED", state().status());
        } finally {
            libererTravail.countDown();
            worker.shutdownNow();
        }
    }

    @Test
    void dureeMaximaleFaitExpirerLeBailDUnWorkerBloque() throws Exception {
        SchedulerExecutionRegistry registry = registry(Duration.ofMillis(150), Duration.ofMillis(30), Duration.ofMillis(300));
        registry.run(jobCode, executionKey, () -> Thread.sleep(500));
        assertEquals("RUNNING", state().status());
        assertTrue(!state().leaseUntil().after(new java.sql.Timestamp(System.currentTimeMillis())));

        registry(Duration.ofSeconds(1), Duration.ofMillis(200), Duration.ofSeconds(5))
                .run(jobCode, executionKey, () -> { });
        assertEquals("COMPLETED", state().status());
        assertEquals(2, state().attemptCount());
    }

    @Test
    void repriseApresEffetPartielNeDupliquePasUneBonificationProtegeeParPostgres() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            employeeId = CongeTestFixtureHelper.createEmploye(c, "SCH-" + UUID.randomUUID().toString().substring(0, 8), "TEST", "Scheduler");
        }
        SchedulerExecutionRegistry registry = registry(Duration.ofSeconds(2), Duration.ofMillis(200), Duration.ofSeconds(5));
        CountDownLatch premierEffetCommitte = new CountDownLatch(1);
        CountDownLatch finirPremierWorker = new CountDownLatch(1);
        AtomicInteger insertionsMetier = new AtomicInteger();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> old = worker.submit(() -> registry.run(jobCode, executionKey, () -> {
                if (insererBonificationIdempotente()) insertionsMetier.incrementAndGet();
                premierEffetCommitte.countDown();
                if (!finirPremierWorker.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("timeout test");
            }));
            assertTrue(premierEffetCommitte.await(3, TimeUnit.SECONDS));
            expireLease();
            registry.run(jobCode, executionKey, () -> {
                if (insererBonificationIdempotente()) insertionsMetier.incrementAndGet();
            });
            assertEquals(1, countBonifications());
            assertEquals(1, insertionsMetier.get());
            assertEquals("COMPLETED", state().status());
            finirPremierWorker.countDown();
            old.get(3, TimeUnit.SECONDS);
            assertEquals(1, countBonifications());
            assertEquals("COMPLETED", state().status());
        } finally {
            finirPremierWorker.countDown();
            worker.shutdownNow();
        }
    }

    private SchedulerExecutionRegistry registry(Duration lease, Duration heartbeat, Duration maxRuntime) {
        return new SchedulerExecutionRegistry(dataSource, lease, heartbeat, maxRuntime);
    }

    private void expireLease() throws Exception {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE scheduler_execution SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 second' "
                        + "WHERE job_code = ? AND execution_key = ?")) {
            ps.setString(1, jobCode);
            ps.setDate(2, Date.valueOf(executionKey));
            assertEquals(1, ps.executeUpdate());
        }
    }

    private State state() throws Exception {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT status, attempt_count, attempt_token, lease_until FROM scheduler_execution "
                        + "WHERE job_code = ? AND execution_key = ?")) {
            ps.setString(1, jobCode);
            ps.setDate(2, Date.valueOf(executionKey));
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "L'entrée scheduler doit exister.");
                return new State(rs.getString(1), rs.getInt(2), rs.getObject(3, UUID.class), rs.getTimestamp(4));
            }
        }
    }

    private boolean insererBonificationIdempotente() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            MouvementSoldeConge movement = new MouvementSoldeConge();
            movement.setIdEmploye(employeeId);
            movement.setTypeMouvement("BONIFICATION");
            movement.setNbJours(BigDecimal.ONE);
            movement.setDateMouvement(Date.valueOf(executionKey));
            movement.setCommentaire("Scheduler integration test");
            return movement.saveBonificationAnnuelleUneFois(c);
        }
    }

    private int countBonifications() throws Exception {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM mouvement_solde_conge WHERE id_employe = ? AND type_mouvement = 'BONIFICATION'")) {
            ps.setInt(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private record State(String status, int attemptCount, UUID attemptToken, java.sql.Timestamp leaseUntil) { }
}
