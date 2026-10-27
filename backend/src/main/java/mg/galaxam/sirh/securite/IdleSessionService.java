package mg.galaxam.sirh.securite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Server-owned idle state for the current refresh-token family. */
@Service
public class IdleSessionService {
    private final Clock clock;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JwtService jwtService;

    public IdleSessionService() {
        this(Clock.systemUTC());
    }

    IdleSessionService(Clock clock) {
        this.clock = clock;
    }

    /** Idempotently upgrades existing installations before any authentication request is served. */
    @PostConstruct
    public void migrateSessionSchema() {
        String[] ddl = {
            "CREATE TABLE IF NOT EXISTS utilisateur_session (session_id UUID PRIMARY KEY, id_utilisateur INTEGER NOT NULL REFERENCES utilisateur(id_utilisateur) ON DELETE CASCADE, refresh_token VARCHAR(500) UNIQUE, previous_refresh_token VARCHAR(500), rotated_at TIMESTAMP, refresh_token_expiration TIMESTAMP NOT NULL, last_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, revoked_at TIMESTAMP)",
            "ALTER TABLE utilisateur_session ADD COLUMN IF NOT EXISTS previous_refresh_token VARCHAR(500)",
            "ALTER TABLE utilisateur_session ADD COLUMN IF NOT EXISTS rotated_at TIMESTAMP",
            "ALTER TABLE utilisateur_session ALTER COLUMN refresh_token DROP NOT NULL",
            "ALTER TABLE utilisateur_session DROP CONSTRAINT IF EXISTS utilisateur_session_id_utilisateur_key",
            "CREATE INDEX IF NOT EXISTS idx_utilisateur_session_idle ON utilisateur_session(last_activity_at) WHERE revoked_at IS NULL"
        };
        try (Connection c = dataSource.getConnection()) {
            for (String statement : ddl) {
                try (PreparedStatement ps = c.prepareStatement(statement)) { ps.execute(); }
            }
            // Migrate a legacy one-refresh-token-per-user installation once; never overwrite session families.
            String select = "SELECT id_utilisateur, refresh_token, refresh_token_expiration FROM utilisateur WHERE refresh_token IS NOT NULL AND refresh_token_expiration IS NOT NULL";
            try (PreparedStatement read = c.prepareStatement(select); ResultSet rows = read.executeQuery();
                 PreparedStatement insert = c.prepareStatement("INSERT INTO utilisateur_session(session_id,id_utilisateur,refresh_token,refresh_token_expiration,last_activity_at) VALUES (?,?,?,?,?) ON CONFLICT (refresh_token) DO NOTHING")) {
                while (rows.next()) {
                    insert.setObject(1, UUID.randomUUID());
                    insert.setInt(2, rows.getInt(1));
                    insert.setString(3, rows.getString(2));
                    insert.setTimestamp(4, rows.getTimestamp(3));
                    insert.setTimestamp(5, Timestamp.from(clock.instant()));
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de préparer le stockage des sessions", e);
        }
    }

    public String create(int userId, String refreshToken, Timestamp refreshExpiresAt, Connection connection) throws Exception {
        String sessionId = UUID.randomUUID().toString();
        String sql = "INSERT INTO utilisateur_session (session_id, id_utilisateur, refresh_token, refresh_token_expiration, last_activity_at) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(sessionId));
            ps.setInt(2, userId);
            ps.setString(3, refreshToken);
            ps.setTimestamp(4, refreshExpiresAt);
            ps.setTimestamp(5, Timestamp.from(clock.instant()));
            ps.executeUpdate();
        }
        return sessionId;
    }

    public boolean isActive(String sessionId, String username) {
        if (sessionId == null || sessionId.isBlank()) return false;
        String sql = "SELECT 1 FROM utilisateur_session s JOIN utilisateur u ON u.id_utilisateur=s.id_utilisateur "
                + "WHERE s.session_id=? AND LOWER(u.email)=LOWER(?) AND u.est_actif=TRUE AND s.revoked_at IS NULL "
                + "AND s.refresh_token_expiration > ? AND s.last_activity_at > ?";
        Instant now = clock.instant();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(sessionId));
            ps.setString(2, username);
            ps.setTimestamp(3, Timestamp.from(now));
            ps.setTimestamp(4, Timestamp.from(now.minusSeconds(jwtService.getIdleTimeoutSeconds())));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return true;
            }
        } catch (Exception e) {
            return false;
        }
        revokeExpired(sessionId);
        return false;
    }

    private void revokeExpired(String sessionId) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE utilisateur_session SET revoked_at=?, refresh_token=NULL WHERE session_id=? AND revoked_at IS NULL AND last_activity_at<=?")) {
            Instant now = clock.instant();
            ps.setTimestamp(1, Timestamp.from(now));
            ps.setObject(2, UUID.fromString(sessionId));
            ps.setTimestamp(3, Timestamp.from(now.minusSeconds(jwtService.getIdleTimeoutSeconds())));
            ps.executeUpdate();
        } catch (Exception ignored) {
            // Deny access even if best-effort revocation is unavailable.
        }
    }

    /** Activity may only extend a still-live session; it can never revive an expired one. */
    public boolean touch(String sessionId, String username) {
        if (sessionId == null || sessionId.isBlank()) return false;
        Instant now = clock.instant();
        String sql = "UPDATE utilisateur_session s SET last_activity_at=? FROM utilisateur u "
                + "WHERE s.id_utilisateur=u.id_utilisateur AND s.session_id=? AND LOWER(u.email)=LOWER(?) "
                + "AND u.est_actif=TRUE AND s.revoked_at IS NULL AND s.refresh_token_expiration>? AND s.last_activity_at>?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(now));
            ps.setObject(2, UUID.fromString(sessionId));
            ps.setString(3, username);
            ps.setTimestamp(4, Timestamp.from(now));
            ps.setTimestamp(5, Timestamp.from(now.minusSeconds(jwtService.getIdleTimeoutSeconds())));
            return ps.executeUpdate() == 1;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean rotate(String sessionId, String oldRefresh, String newRefresh, Timestamp expiresAt, Connection c) throws Exception {
        Instant now = clock.instant();
        String sql = "UPDATE utilisateur_session s SET previous_refresh_token=s.refresh_token, rotated_at=?, refresh_token=?, refresh_token_expiration=? FROM utilisateur u "
                + "WHERE s.id_utilisateur=u.id_utilisateur AND s.session_id=? AND s.refresh_token=? "
                + "AND s.revoked_at IS NULL AND u.est_actif=TRUE AND s.last_activity_at>? AND s.refresh_token_expiration>?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(now));
            ps.setString(2, newRefresh);
            ps.setTimestamp(3, expiresAt);
            ps.setObject(4, UUID.fromString(sessionId));
            ps.setString(5, oldRefresh);
            ps.setTimestamp(6, Timestamp.from(now.minusSeconds(jwtService.getIdleTimeoutSeconds())));
            ps.setTimestamp(7, Timestamp.from(now));
            return ps.executeUpdate() == 1;
        }
    }

    public void revoke(String sessionId, String refreshToken, Connection c) throws Exception {
        String sql = "UPDATE utilisateur_session SET revoked_at=?, refresh_token=NULL, previous_refresh_token=NULL WHERE revoked_at IS NULL AND (session_id=? OR refresh_token=? OR previous_refresh_token=?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(clock.instant()));
            ps.setObject(2, sessionId == null || sessionId.isBlank() ? null : UUID.fromString(sessionId));
            ps.setString(3, refreshToken);
            ps.setString(4, refreshToken);
            ps.executeUpdate();
        }
    }

    public void revokeAllForUser(int userId, Connection c) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("UPDATE utilisateur_session SET revoked_at=?, refresh_token=NULL, previous_refresh_token=NULL WHERE id_utilisateur=? AND revoked_at IS NULL")) {
            ps.setTimestamp(1, Timestamp.from(clock.instant()));
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public String findSessionIdByRefreshToken(String refreshToken, Connection c) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT session_id FROM utilisateur_session WHERE refresh_token=? AND revoked_at IS NULL")) {
            ps.setString(1, refreshToken);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getObject(1, UUID.class).toString() : null; }
        }
    }

    public boolean expireIfIdle(String refreshToken) {
        Instant now = clock.instant();
        String sql = "UPDATE utilisateur_session SET revoked_at=?, refresh_token=NULL, previous_refresh_token=NULL "
                + "WHERE (refresh_token=? OR previous_refresh_token=?) AND revoked_at IS NULL AND last_activity_at<=?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(now));
            ps.setString(2, refreshToken);
            ps.setString(3, refreshToken);
            ps.setTimestamp(4, Timestamp.from(now.minusSeconds(jwtService.getIdleTimeoutSeconds())));
            return ps.executeUpdate() == 1;
        } catch (Exception e) {
            return false;
        }
    }

    public record SessionTokens(String sessionId, String currentRefreshToken, boolean alreadyRotated) {}

    public SessionTokens findTokensByRefreshToken(String token, Connection c) throws Exception {
        String sql = "SELECT s.session_id, s.refresh_token, (s.previous_refresh_token=? AND s.rotated_at>? ) AS already_rotated "
                + "FROM utilisateur_session s JOIN utilisateur u ON u.id_utilisateur=s.id_utilisateur AND u.est_actif=TRUE "
                + "WHERE (s.refresh_token=? OR s.previous_refresh_token=?) AND s.revoked_at IS NULL "
                + "AND s.last_activity_at>? AND s.refresh_token_expiration>? FOR UPDATE";
        Instant now = clock.instant();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.setTimestamp(2, Timestamp.from(now.minusSeconds(10)));
            ps.setString(3, token);
            ps.setString(4, token);
            ps.setTimestamp(5, Timestamp.from(now.minusSeconds(jwtService.getIdleTimeoutSeconds())));
            ps.setTimestamp(6, Timestamp.from(now));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new SessionTokens(rs.getObject(1, UUID.class).toString(), rs.getString(2), rs.getBoolean(3));
            }
        }
    }

    public boolean touch(AuthenticationSession session) {
        return touch(session.sessionId(), session.username());
    }

    public long getIdleExpiresAt(String sessionId, String username) {
        String sql = "SELECT s.last_activity_at FROM utilisateur_session s JOIN utilisateur u ON u.id_utilisateur=s.id_utilisateur "
                + "WHERE s.session_id=? AND LOWER(u.email)=LOWER(?) AND u.est_actif=TRUE AND s.revoked_at IS NULL";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(sessionId));
            ps.setString(2, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getTimestamp(1).toInstant().plusSeconds(jwtService.getIdleTimeoutSeconds()).toEpochMilli() : 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    public record AuthenticationSession(String sessionId, String username) {}
}
