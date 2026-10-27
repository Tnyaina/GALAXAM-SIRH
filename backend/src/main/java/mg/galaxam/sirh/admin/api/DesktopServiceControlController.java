package mg.galaxam.sirh.admin.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Local-only, one-process secret protected shutdown hook used by the Windows SCM supervisor. */
@RestController
@RequestMapping("/desktop/internal")
@Profile("desktop")
public class DesktopServiceControlController {

    private final ConfigurableApplicationContext context;
    private final byte[] serviceToken;

    public DesktopServiceControlController(
            ConfigurableApplicationContext context,
            @Value("${app.desktop.shutdown-token:}") String serviceToken) {
        this.context = context;
        this.serviceToken = serviceToken.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/shutdown")
    public ResponseEntity<Void> shutdown(
            @RequestHeader(value = "X-GALAXAM-Service-Token", required = false) String candidate) {
        if (serviceToken.length < 32
                || candidate == null
                || !MessageDigest.isEqual(
                        serviceToken, candidate.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        CompletableFuture.runAsync(
                () -> {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    context.close();
                });
        return ResponseEntity.noContent().build();
    }
}
