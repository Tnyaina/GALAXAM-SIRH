package mg.galaxam.sirh.api.controller.employe;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/employes")
public class EmployePhotoController {

    @Value("${app.upload.dir:uploads/photos}")
    private String uploadDir;

    /**
     * Upload de la photo d'un employé.
     * Accepte un fichier image (JPEG, PNG, WEBP) de maximum 5 Mo.
     * Retourne l'URL relative vers la photo uploadée.
     */
    @PostMapping("/upload-photo")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> uploadPhoto(@RequestParam("file") MultipartFile file) {
        try {
            // Validation du fichier
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("Aucun fichier sélectionné.");
            }

            // Vérifier le type MIME
            String contentType = file.getContentType();
            if (contentType == null || (!contentType.startsWith("image/"))) {
                return ResponseEntity.badRequest().body("Seuls les fichiers image sont autorisés (JPEG, PNG, WEBP).");
            }

            // Vérifier la taille (max 5 Mo)
            if (file.getSize() > 5 * 1024 * 1024) {
                return ResponseEntity.badRequest().body("La taille du fichier ne doit pas dépasser 5 Mo.");
            }

            // Créer le répertoire d'upload si nécessaire
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Générer un nom de fichier unique
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String uniqueFilename = UUID.randomUUID().toString() + extension;

            // Sauvegarder le fichier
            Path filePath = uploadPath.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Retourner l'URL relative
            String photoUrl = "/uploads/photos/" + uniqueFilename;
            return ResponseEntity.ok(Map.of("photoUrl", photoUrl));

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de l'upload de la photo.");
        }
    }
}
