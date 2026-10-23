package mg.galaxam.sirh.securite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoginAttemptRateLimiterTest {

    @Test
    void limiteLesEchecsParAdresseNormaliseeEtLeSuccesReinitialiseLaFenetre() {
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter();

        assertEquals(0, limiter.retryAfterSeconds(" RH@EXAMPLE.COM "));
        for (int i = 0; i < 10; i++) limiter.recordFailure("rh@example.com");

        assertTrue(limiter.retryAfterSeconds("RH@example.com") > 0);
        limiter.recordSuccess(" rh@example.com ");
        assertEquals(0, limiter.retryAfterSeconds("rh@example.com"));
    }
}
