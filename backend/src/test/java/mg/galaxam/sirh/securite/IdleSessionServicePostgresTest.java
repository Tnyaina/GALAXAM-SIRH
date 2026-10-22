package mg.galaxam.sirh.securite;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Runs only against an explicitly supplied, disposable PostgreSQL database. */
class IdleSessionServicePostgresTest {
    private static final String JDBC_URL = System.getenv("IDLE_TEST_JDBC_URL");
    private static final String DB_USER = System.getenv().getOrDefault("IDLE_TEST_DB_USER", "postgres");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("IDLE_TEST_DB_PASSWORD", "");
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private MutableClock clock;
    private DataSource dataSource;
    private IdleSessionService sessions;

    @BeforeEach
    void setUp() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(JDBC_URL != null && !JDBC_URL.isBlank(),
                "Provide IDLE_TEST_JDBC_URL for an isolated PostgreSQL instance");
        clock = new MutableClock(START);
        dataSource = new DriverManagerDataSource(JDBC_URL, DB_USER, DB_PASSWORD);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS utilisateur_session");
            s.execute("DROP TABLE IF EXISTS utilisateur");
            s.execute("CREATE TABLE utilisateur (id_utilisateur INTEGER PRIMARY KEY, email VARCHAR(254) NOT NULL, est_actif BOOLEAN NOT NULL)");
            s.execute("CREATE TABLE utilisateur_session (session_id UUID PRIMARY KEY, id_utilisateur INTEGER NOT NULL REFERENCES utilisateur(id_utilisateur) ON DELETE CASCADE, refresh_token VARCHAR(500) UNIQUE, previous_refresh_token VARCHAR(500), rotated_at TIMESTAMP, refresh_token_expiration TIMESTAMP NOT NULL, last_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, revoked_at TIMESTAMP)");
            s.execute("INSERT INTO utilisateur VALUES (41, 'agent@example.test', TRUE)");
        }
        JwtService jwt = mock(JwtService.class);
        when(jwt.getIdleTimeoutSeconds()).thenReturn(900L);
        sessions = new IdleSessionService(clock);
        ReflectionTestUtils.setField(sessions, "dataSource", dataSource);
        ReflectionTestUtils.setField(sessions, "jwtService", jwt);
    }

    @Test
    void humanActivityExtendsDeadlineButRefreshRotationDoesNot() throws Exception {
        String sid = create("refresh-a");
        clock.advanceSeconds(600);
        assertTrue(sessions.touch(sid, "agent@example.test"));
        clock.advanceSeconds(899);
        assertTrue(sessions.isActive(sid, "agent@example.test"));
        clock.advanceSeconds(1);
        assertFalse(sessions.isActive(sid, "agent@example.test"));
    }

    @Test
    void exactIdleBoundaryRejectsAndRevokesJwtSession() throws Exception {
        String sid = create("refresh-b");
        clock.advanceSeconds(900);
        assertFalse(sessions.isActive(sid, "agent@example.test"));
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
             var rs = s.executeQuery("SELECT revoked_at IS NOT NULL AND refresh_token IS NULL FROM utilisateur_session WHERE session_id='" + sid + "'")) {
            assertTrue(rs.next());
            assertTrue(rs.getBoolean(1));
        }
    }

    @Test
    void staleRefreshIsRefusedAndCannotTouchExpiredFamily() throws Exception {
        String sid = create("refresh-c");
        clock.advanceSeconds(901);
        assertFalse(sessions.touch(sid, "agent@example.test"));
        assertTrue(sessions.expireIfIdle("refresh-c"));
    }

    @Test
    void concurrentRefreshRetryUsesAlreadyRotatedFamilyWithoutExtendingIdleDeadline() throws Exception {
        String sid = create("refresh-d");
        Timestamp newRefreshExpiry = Timestamp.from(START.plusSeconds(604800));
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            var first = sessions.findTokensByRefreshToken("refresh-d", c);
            assertNotNull(first);
            assertFalse(first.alreadyRotated());
            assertTrue(sessions.rotate(sid, "refresh-d", "refresh-d2", newRefreshExpiry, c));
            c.commit();
        }
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            var retry = sessions.findTokensByRefreshToken("refresh-d", c);
            assertNotNull(retry);
            assertTrue(retry.alreadyRotated());
            assertEquals("refresh-d2", retry.currentRefreshToken());
            c.rollback();
        }
        clock.advanceSeconds(900);
        assertFalse(sessions.isActive(sid, "agent@example.test"));
    }

    @Test
    void disabledAccountAndManualLogoutInvalidateTheSession() throws Exception {
        String sid = create("refresh-e");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("UPDATE utilisateur SET est_actif=FALSE WHERE id_utilisateur=41");
        }
        assertFalse(sessions.isActive(sid, "agent@example.test"));
        try (Connection c = dataSource.getConnection()) { sessions.revoke(sid, "refresh-e", c); }
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
             var rs = s.executeQuery("SELECT revoked_at IS NOT NULL FROM utilisateur_session WHERE session_id='" + sid + "'")) {
            assertTrue(rs.next());
            assertTrue(rs.getBoolean(1));
        }
    }

    @Test
    void multipleDevicesHaveIndependentSessionFamilies() throws Exception {
        String first = create("refresh-f");
        String second = create("refresh-g");
        assertNotEquals(first, second);
        clock.advanceSeconds(600);
        assertTrue(sessions.touch(first, "agent@example.test"));
        clock.advanceSeconds(301);
        assertFalse(sessions.isActive(second, "agent@example.test"));
        assertTrue(sessions.isActive(first, "agent@example.test"));
    }

    @Test
    void startupMigrationImportsLegacyRefreshTokenIdempotently() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE utilisateur_session");
            s.execute("ALTER TABLE utilisateur ADD COLUMN refresh_token VARCHAR(500)");
            s.execute("ALTER TABLE utilisateur ADD COLUMN refresh_token_expiration TIMESTAMP");
            s.execute("UPDATE utilisateur SET refresh_token='legacy-refresh', refresh_token_expiration='2026-01-08 00:00:00'");
        }
        sessions.migrateSessionSchema();
        sessions.migrateSessionSchema();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
             var rs = s.executeQuery("SELECT COUNT(*) FROM utilisateur_session WHERE refresh_token='legacy-refresh'")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    private String create(String refresh) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return sessions.create(41, refresh, Timestamp.from(START.plusSeconds(604800)), c);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        MutableClock(Instant instant) { this.instant = instant; }
        void advanceSeconds(long seconds) { instant = instant.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
