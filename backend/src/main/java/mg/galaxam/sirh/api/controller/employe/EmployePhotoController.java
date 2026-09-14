package mg.galaxam.sirh.api.controller.employe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Point d'accès unique et authentifié aux photos RH. */
@RestController
@RequestMapping("/employes")
public class EmployePhotoController {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp");

    @Value("${app.upload.dir:uploads/photos}")
    private String uploadDir;

    @PostMapping("/upload-photo")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> uploadPhoto(@RequestParam("file") MultipartFile file) {
        try {
            validateImage(file);
            Files.createDirectories(normalizedUploadDirectory());
            String uniqueFilename = UUID.randomUUID() + extensionFor(file.getContentType());
            Files.copy(file.getInputStream(), safeResolve(uniqueFilename), StandardCopyOption.REPLACE_EXISTING);
            return ResponseEntity.ok(Map.of("photoUrl", "/employes/photos/" + uniqueFilename));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors du stockage sécurisé de la photo.");
        }
    }

    @GetMapping("/photos/{filename}")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getPhoto(@PathVariable String filename) {
        try {
            Path filePath = safeResolve(filename);
            if (!Files.isRegularFile(filePath)) {
                return ResponseEntity.notFound().build();
            }
            String contentType = Files.probeContentType(filePath);
            if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
            }
            Resource resource = new UrlResource(filePath.toUri());
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate().mustRevalidate())
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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
