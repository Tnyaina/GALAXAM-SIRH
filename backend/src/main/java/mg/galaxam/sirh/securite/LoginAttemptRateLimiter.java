package mg.galaxam.sirh.securite;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Limits failed credential checks per normalized email to slow password guessing.
 * State is intentionally bounded and local to this single-instance deployment.
 */
@Component
public final class LoginAttemptRateLimiter {

    private static final int MAX_FAILURES = 10;
    private static final int MAX_TRACKED_EMAILS = 20_000;
    private static final long WINDOW_NANOS = Duration.ofMinutes(15).toNanos();

    private final LinkedHashMap<String, AttemptWindow> attempts = new LinkedHashMap<>(256, 0.75f, true);

    public synchronized long retryAfterSeconds(String email) {
        long now = System.nanoTime();
        AttemptWindow window = attempts.get(key(email));
        if (window == null) return 0;
        if (now - window.startedAtNanos >= WINDOW_NANOS) {
            attempts.remove(key(email));
            return 0;
        }
        if (window.failures < MAX_FAILURES) return 0;
        long remainingNanos = WINDOW_NANOS - (now - window.startedAtNanos);
        return Math.max(1, (remainingNanos + 999_999_999L) / 1_000_000_000L);
    }

    public synchronized void recordFailure(String email) {
        long now = System.nanoTime();
        String key = key(email);
        AttemptWindow window = attempts.get(key);
        if (window == null || now - window.startedAtNanos >= WINDOW_NANOS) {
            window = new AttemptWindow(now);
            attempts.put(key, window);
        }
        window.failures++;
        evictExpiredAndOldest(now);
    }

    public synchronized void recordSuccess(String email) {
        attempts.remove(key(email));
    }

    private void evictExpiredAndOldest(long now) {
        Iterator<Map.Entry<String, AttemptWindow>> iterator = attempts.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().startedAtNanos >= WINDOW_NANOS) iterator.remove();
        }
        while (attempts.size() > MAX_TRACKED_EMAILS) {
            iterator = attempts.entrySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            } else {
                return;
            }
        }
    }

    private static String key(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private static final class AttemptWindow {
        private final long startedAtNanos;
        private int failures;

        private AttemptWindow(long startedAtNanos) {
            this.startedAtNanos = startedAtNanos;
        }
    }
}
