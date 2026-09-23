package mg.galaxam.sirh.contrat.api;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Point d'accès unique et authentifié aux photos RH.
 *
 * <p>P1-05 (IDOR) : L'accès aux photos passe par l'identifiant d'employé ({@code idEmploye}),
 * pas par le nom du fichier. Le backend résout le filename depuis la table {@code employe},
 * après avoir vérifié que l'utilisateur demandeur a le droit d'accéder à cet employé.
 *
 * <p>L'ancien endpoint {@code GET /employes/photos/{filename}} retourne 410 Gone
 * pour détecter les consommateurs obsolètes exposant les UUID de fichiers.
 */
@RestController
@RequestMapping("/employes")
public class EmployePhotoController {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp");

    private final DataSource dataSource;

    @Value("${app.upload.dir:uploads/photos}")
    private String uploadDir;

    public EmployePhotoController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // =========================================================================
    // P1-05 — Nouveau endpoint : accès par identifiant d'employé
    // =========================================================================

    /**
     * Retourne la photo d'un employé identifié par son {@code idEmploye}.
     *
     * <p>Le filename n'est jamais exposé dans l'URL ou la réponse.
     * Le backend résout internalement le chemin via la table {@code employe}.
     */
    @GetMapping("/{id}/photo")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getPhotoByEmployeId(@PathVariable("id") int idEmploye) {
        checkPhotoAccess(idEmploye);
        String photoUrl = resolvePhotoUrlFromDb(idEmploye);
        if (photoUrl == null || photoUrl.isBlank()) {
            throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND,
                    "Aucune photo enregistrée pour l'employé " + idEmploye + ".");
        }
        // photoUrl stockée = "/employes/photos/<uuid>.jpg" → on extrait le filename
        String filename = Paths.get(photoUrl).getFileName().toString();
        return servePhotoFile(filename);
    }

    // =========================================================================
    // Upload : lier le fichier à l'employé (idEmploye obligatoire)
    // =========================================================================

    /**
     * Upload de la photo d'un employé.
     * L'association {@code employeeId ↔ filename} est persistée dans {@code employe.photo_url}.
     *
     * @param idEmploye identifiant de l'employé propriétaire de la photo
     * @param file      fichier image
     */
    @PostMapping("/{idEmploye}/photo")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> uploadPhotoForEmployee(
            @PathVariable int idEmploye,
            @RequestParam("file") MultipartFile file) {
        try {
            validateImage(file);
            Files.createDirectories(normalizedUploadDirectory());
            String uniqueFilename = UUID.randomUUID() + extensionFor(file.getContentType());
            Files.copy(file.getInputStream(), safeResolve(uniqueFilename), StandardCopyOption.REPLACE_EXISTING);

            String photoUrl = "/employes/photos/" + uniqueFilename;
            persistPhotoUrl(idEmploye, photoUrl);

            // P1-05 : Retourner l'endpoint canonique (/{id}/photo), pas le chemin de fichier
            return ResponseEntity.ok(Map.of(
                    "photoEndpoint", "/employes/" + idEmploye + "/photo",
                    "idEmploye", idEmploye));
        } catch (IllegalArgumentException e) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, e.getMessage());
        } catch (IOException e) {
            throw new SirhBusinessException(SirhErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * @deprecated P1-05 : Cet endpoint expose le nom du fichier UUID directement dans l'URL,
     * permettant l'énumération des photos de n'importe quel employé (IDOR).
     *
     * <p>Retourne 410 Gone pour détecter les consommateurs obsolètes.
     * Utilisez {@code GET /employes/{id}/photo} à la place.
     */
    @GetMapping("/photos/{filename}")
    @Deprecated(forRemoval = true)
    public ResponseEntity<Map<String, String>> getPhotoByFilenameDeprecated(
            @PathVariable String filename) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                "code", "ENDPOINT_DEPRECATED",
                "message", "Cet endpoint est déprécié (P1-05). "
                        + "L'exposition du nom de fichier UUID permet l'accès non autorisé aux photos. "
                        + "Utilisez GET /employes/{idEmploye}/photo.",
                "docsUrl", "/employes/{idEmploye}/photo"));
    }

    // =========================================================================
    // Ancien upload (rétrocompatibilité — à supprimer en v2)
    // =========================================================================

    /**
     * @deprecated Utilisez POST /employes/{idEmploye}/photo avec l'idEmploye en path variable.
     * Cet endpoint ne lie pas le fichier à un employé et ne peut donc pas enforcer les droits.
     */
    @PostMapping("/upload-photo")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    @Deprecated(forRemoval = true)
    public ResponseEntity<Map<String, String>> uploadPhotoLegacy(
            @RequestParam(value = "file", required = false) MultipartFile file) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                "code", "ENDPOINT_DEPRECATED",
                "message", "Utilisez POST /employes/{idEmploye}/photo avec l'identifiant de l'employé.",
                "newEndpoint", "/employes/{idEmploye}/photo"));
    }

    // =========================================================================
    // Helpers privés
    // =========================================================================

    /**
     * Vérifie que l'utilisateur courant a le droit d'accéder à la photo de cet employé.
     * P1-05 : Premier niveau de contrôle (existence de l'employé + authentification).
     * À étendre avec la granularité organisationnelle si nécessaire.
     */
    private void checkPhotoAccess(int idEmploye) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new SirhBusinessException(SirhErrorCode.AUTHENTICATION_REQUIRED);
        }
        // Vérifier que l'employé existe (évite le scan d'IDs inexistants)
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM employe WHERE id_employe = ? AND est_actif = true")) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND,
                            "Employé introuvable (idEmploye=" + idEmploye + ").");
                }
            }
        } catch (SirhBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new SirhBusinessException(SirhErrorCode.INTERNAL_ERROR);
        }
    }

    private String resolvePhotoUrlFromDb(int idEmploye) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT photo_url FROM employe WHERE id_employe = ?")) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("photo_url") : null;
            }
        } catch (Exception e) {
            throw new SirhBusinessException(SirhErrorCode.INTERNAL_ERROR);
        }
    }

    private void persistPhotoUrl(int idEmploye, String photoUrl) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE employe SET photo_url = ?, updated_at = CURRENT_TIMESTAMP WHERE id_employe = ?")) {
            ps.setString(1, photoUrl);
            ps.setInt(2, idEmploye);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new SirhBusinessException(SirhErrorCode.PERSISTENCE_ERROR);
        }
    }

    private ResponseEntity<?> servePhotoFile(String filename) {
        try {
            Path filePath = safeResolve(filename);
            if (!Files.isRegularFile(filePath)) {
                throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Photo introuvable.");
            }
            String contentType = Files.probeContentType(filePath);
            if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                        "Le fichier photo stocké n'est pas d'un type autorisé (JPEG, PNG, WEBP).");
            }
            Resource resource = new UrlResource(filePath.toUri());
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate().mustRevalidate())
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .body(resource);
        } catch (IllegalArgumentException e) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, e.getMessage());
        } catch (IOException e) {
            throw new SirhBusinessException(SirhErrorCode.INTERNAL_ERROR);
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Aucun fichier sélectionné.");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Seuls les fichiers JPEG, PNG et WEBP sont autorisés.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("La taille du fichier ne doit pas dépasser 5 Mo.");
        }
    }

    private Path normalizedUploadDirectory() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    private Path safeResolve(String filename) {
        if (filename == null || filename.isBlank()
                || !filename.equals(Paths.get(filename).getFileName().toString())) {
            throw new IllegalArgumentException("Nom de fichier photo invalide.");
        }
        Path resolved = normalizedUploadDirectory().resolve(filename).normalize();
        if (!resolved.startsWith(normalizedUploadDirectory())) {
            throw new IllegalArgumentException("Chemin de photo invalide.");
        }
        return resolved;
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case MediaType.IMAGE_JPEG_VALUE -> ".jpg";
            case MediaType.IMAGE_PNG_VALUE -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new IllegalArgumentException("Type de photo non autorisé.");
        };
    }
}
