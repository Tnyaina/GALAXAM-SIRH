package mg.galaxam.sirh.securite;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

class RefreshTokenUniquenessTest {
    @Test
    void repeatedLoginsHaveDistinctRefreshTokensEvenWithinOneSecond() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKeyString", "a".repeat(128));
        ReflectionTestUtils.setField(service, "refreshTokenExpirationMs", 604800000L);
        var user = User.withUsername("audit@example.test").password("unused").roles("ADMIN").build();
        var ids = new HashSet<String>();
        for (int i = 0; i < 30; i++) {
            String token = service.generateRefreshToken(user);
            assertEquals(user.getUsername(), service.extractValidUsername(token));
            String id = service.extractClaim(token, io.jsonwebtoken.Claims::getId);
            assertNotNull(id);
            assertTrue(ids.add(id), "Each login/rotation requires an independent token");
            assertTrue(token.length() <= 500, "Token must fit persisted session column");
        }
    }
}
