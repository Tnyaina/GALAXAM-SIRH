package mg.galaxam.sirh.securite.api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import mg.galaxam.sirh.admin.persistence.RefRole;
import mg.galaxam.sirh.securite.Utilisateur;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

/** One-time, capability-protected account creation for a fresh desktop install. */
@RestController
@Profile("desktop")
@RequestMapping("/desktop/setup")
public class DesktopBootstrapController {
    private static final long BOOTSTRAP_LOCK = 0x47414c4158414dL;

    private final javax.sql.DataSource dataSource;
    private final Path statePath;

    public DesktopBootstrapController(
            javax.sql.DataSource dataSource,
            @Value("${app.desktop.bootstrap-state:}") String statePath) {
        this.dataSource = dataSource;
        this.statePath = Path.of(statePath);
    }

    @PostMapping("/admin")
    public ResponseEntity<?> createInitialAdmin(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            jakarta.servlet.http.HttpServletRequest request,
            @RequestBody AdminRequest body) {
        if (authorization == null
                || !authorization.startsWith("Bearer ")
                || !validCapability(authorization.substring(7))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Amorçage non autorisé");
        }
        if (request.getRemoteAddr() == null || !request.getRemoteAddr().equals("127.0.0.1")
                && !request.getRemoteAddr().equals("::1")
                && !request.getRemoteAddr().equals("0:0:0:0:0:0:0:1")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Amorçage local uniquement");
        }
        if (body == null || body.identifiant() == null || body.email() == null
                || body.motDePasse() == null || body.identifiant().isBlank() || body.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifiant, e-mail et mot de passe requis");
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                    lock.setLong(1, BOOTSTRAP_LOCK);
                    lock.execute();
                }
                try (PreparedStatement check = connection.prepareStatement(
                        "SELECT count(*) FROM utilisateur u JOIN ref_role r ON r.id_role=u.id_role " +
                        "WHERE r.code_role='ROLE_ADMIN'");
                     ResultSet result = check.executeQuery()) {
                    result.next();
                    if (result.getLong(1) > 0) {
                        connection.rollback();
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Un administrateur existe déjà");
                    }
                }

                RefRole role = RefRole.rechercherParCode("ROLE_ADMIN", connection);
                if (role == null || !role.isEstActif()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Rôle administrateur indisponible");
                }
                Utilisateur user = new Utilisateur();
                user.setIdentifiant(body.identifiant());
                user.setEmail(body.email());
                user.setMotDePasseEnClair(body.motDePasse());
                user.setIdRole(role.getIdRole());
                user.setEstActif(true);
                user.controler(connection); // Existing domain validation and BCrypt hashing.
                user.save(connection);
                connection.commit();
                // The DB check remains authoritative if removal fails or two requests race.
                try { Files.deleteIfExists(statePath); } catch (Exception ignored) { }
                return ResponseEntity.status(HttpStatus.CREATED).body(java.util.Map.of("created", true));
            } catch (ResponseStatusException exception) {
                connection.rollback();
                throw exception;
            } catch (IllegalArgumentException exception) {
                connection.rollback();
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
            } catch (Exception exception) {
                connection.rollback();
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Création du compte impossible ; réessayez ou consultez le support");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Base locale indisponible");
        }
    }

    private boolean validCapability(String supplied) {
        if (supplied == null || supplied.length() != 64 || !supplied.matches("[0-9a-f]{64}")) return false;
        try {
            if (Files.isSymbolicLink(statePath) || !Files.isRegularFile(statePath, LinkOption.NOFOLLOW_LINKS)) return false;
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(statePath, LinkOption.NOFOLLOW_LINKS);
            if (!permissions.equals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))) return false;
            if (!Files.getOwner(statePath, LinkOption.NOFOLLOW_LINKS).equals(Files.getOwner(statePath.getParent(), LinkOption.NOFOLLOW_LINKS))) return false;
            Map<String, String> values = new HashMap<>();
            for (String line : Files.readAllLines(statePath, StandardCharsets.UTF_8)) {
                int separator = line.indexOf('=');
                if (separator <= 0 || values.putIfAbsent(line.substring(0, separator), line.substring(separator + 1)) != null) return false;
            }
            if (values.size() != 3) return false;
            long version = Long.parseLong(values.getOrDefault("version", ""));
            long expires = Long.parseLong(values.getOrDefault("expires", ""));
            String expectedFingerprint = values.getOrDefault("fingerprint", "");
            if (version < 1 || expires <= Instant.now().getEpochSecond() || !expectedFingerprint.matches("[0-9a-f]{64}")) return false;
            byte[] actual = java.security.MessageDigest.getInstance("SHA-256").digest(supplied.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte value : actual) hex.append(String.format("%02x", value));
            return MessageDigest.isEqual(expectedFingerprint.getBytes(StandardCharsets.US_ASCII), hex.toString().getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException | java.io.IOException | RuntimeException exception) {
            return false;
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleBootstrapRejection(ResponseStatusException exception) {
        String message = exception.getReason() == null ? "Amorçage refusé" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", message));
    }

    public record AdminRequest(String identifiant, String email, String motDePasse) {}
}
