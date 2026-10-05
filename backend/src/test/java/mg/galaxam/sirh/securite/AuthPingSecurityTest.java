package mg.galaxam.sirh.securite;

import mg.galaxam.sirh.common.dto.ApiErrorResponse;
import mg.galaxam.sirh.config.GlobalExceptionHandler;
import mg.galaxam.sirh.securite.api.AuthController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class AuthPingSecurityTest {

    @Autowired
    private AuthController authController;

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    @Test
    @DisplayName("L'endpoint /auth/ping retourne un statut UP en HTTP 200")
    void testAuthPingReturnsUp() {
        ResponseEntity<Map<String, String>> response = authController.ping();
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
    }

    @Test
    @DisplayName("Une méthode non supportée déclenche un statut propre 405 Method Not Allowed")
    void testMethodNotSupportedHandledCleanly() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/login");
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("GET", java.util.List.of("POST"));

        ResponseEntity<ApiErrorResponse> response = globalExceptionHandler.handleMethodNotSupported(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertEquals("METHOD_NOT_ALLOWED", response.getBody().getCode());
        assertEquals("/api/auth/login", response.getBody().getPath());
    }
}
