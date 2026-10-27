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

import mg.galaxam.sirh.common.dto.PageDTO;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.contrat.api.dto.EmployeRequest.DesactivationRequest;
import mg.galaxam.sirh.contrat.api.dto.EmployeFicheDetailDTO;
import mg.galaxam.sirh.contrat.api.dto.EmployeRequest;
import mg.galaxam.sirh.contrat.persistence.EmployeCPL;
import mg.galaxam.sirh.contrat.application.EmployeService;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Controller REST unique et complet pour la gestion du collaborateur / employé :
 * - Recherche, pagination, filtres et statistiques
 * - Génération et validation des matricules
 * - Fiche détaillée complète (identité, famille, coordonnées, contrats, etc.)
 * - Cycle de vie (création, modification, activation, désactivation)
 * - Gestion sécurisée de la photo d'identité (P1-05 IDOR protection)
 */
@RestController
@RequestMapping("/employes")
public class EmployeController {

    private static boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                .anyMatch(granted -> "ROLE_ADMIN".equals(granted.getAuthority())
                        || authority.equals(granted.getAuthority()));
    }

    private static void requireBankWritePermission(EmployeRequest request) {
        if (request != null && (request.getCompteBanquePrincipal() != null || request.isSupprimerCompteBanque())
                && !hasAuthority("PAIE_WRITE")) {
            throw new SirhBusinessException(SirhErrorCode.ACCESS_DENIED,
                    "La modification des coordonnées bancaires requiert le droit d'écriture Paie.");
        }
    }

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp");

    @Autowired
    private EmployeService employeService;

    @Autowired
    private DataSource dataSource;

    @Value("${app.upload.dir:uploads/photo}")
    private String uploadDir;

    // =========================================================================
    // LECTURE — déléguées à EmployeService (source unique de vérité)
    // =========================================================================

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getEmployeStats() {
        try {
            return ResponseEntity.ok(employeService.getStats(null));
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getEmployes(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) Boolean actif,
                                         @RequestParam(required = false) Integer site,
                                         @RequestParam(required = false) Integer departement,
                                         @RequestParam(required = false) Integer service,
                                         @RequestParam(defaultValue = "HIERARCHICAL") String sort,
                                         @RequestParam(defaultValue = "asc") String direction,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        try {
            PageDTO<EmployeCPL> pageDto = employeService.getEmployes(
                    q, actif, site, departement, service, sort, direction, page, size);
            return ResponseEntity.ok(pageDto);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getEmployeOptions(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "true") Boolean actif,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            var options = employeService.getOptions(q, actif, page, size);
            return ResponseEntity.ok(options);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/matricule/check")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> checkMatriculeExists(@RequestParam String matricule) {
        try {
            boolean exists = employeService.checkMatriculeExists(matricule);
            return ResponseEntity.ok(Map.of("exists", exists));
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/matricule/next")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getNextMatricule() {
        try {
            String matricule = employeService.generateNextMatricule();
            return ResponseEntity.ok(Map.of("matricule", matricule));
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getEmployeFicheDetail(@PathVariable int id) {
        try {
            EmployeFicheDetailDTO detail = employeService.getFicheDetail(id, hasAuthority("PAIE_READ"));
            if (detail == null) {
                throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Employé non trouvé.");
            }
            return ResponseEntity.ok(detail);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    // =========================================================================
    // CRÉATION — déléguée à EmployeService (évite la double notification)
    // =========================================================================

    @PostMapping
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createEmploye(@RequestBody EmployeRequest req) {
        try {
            requireBankWritePermission(req);
            EmployeCPL created = employeService.creerEmploye(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    // =========================================================================
    // MUTATIONS — déléguées à EmployeService (source unique de vérité)
    // =========================================================================

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateEmploye(@PathVariable int id, @RequestBody EmployeRequest req) {
        try {
            requireBankWritePermission(req);
            EmployeCPL updated = employeService.modifierEmploye(id, req);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/{id}/comptes-bancaires")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getComptesBancaires(@PathVariable int id) {
        try {
            return ResponseEntity.ok(employeService.getComptesBancaires(id));
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/{id}/compte-bancaire")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateCompteBancaire(@PathVariable int id, @RequestBody EmployeRequest request) {
        try {
            employeService.modifierCompteBancaire(id, request);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, e.getMessage());
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/{id}/desactiver")
    @PreAuthorize("hasAuthority('EMPLOYE_VALIDATE') or hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> desactiverEmploye(@PathVariable int id, @RequestBody(required = false) DesactivationRequest req) {
        try {
            EmployeCPL updated = employeService.desactiverEmploye(id, req);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/{id}/activer")
    @PreAuthorize("hasAuthority('EMPLOYE_VALIDATE') or hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> reactiverEmploye(@PathVariable int id) {
        try {
            EmployeCPL updated = employeService.reactiverEmploye(id);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    // =========================================================================
    // GESTION SÉCURISÉE DE LA PHOTO (P1-05 IDOR Protection)
    // =========================================================================

    @GetMapping("/{id}/photo")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getPhotoByEmployeId(@PathVariable("id") int idEmploye) {
        String photoUrl = resolvePhotoUrlFromDb(idEmploye);
        if (photoUrl == null || photoUrl.isBlank()) {
            throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND,
                    "Aucune photo enregistrée pour l'employé " + idEmploye + ".");
        }
        String filename = Paths.get(photoUrl).getFileName().toString();
        return servePhotoFile(filename);
    }

    @PostMapping("/{idEmploye}/photo")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> uploadPhotoForEmployee(
            @PathVariable int idEmploye,
            @RequestParam("file") MultipartFile file) {
        try {
            validateImage(file);
            Files.createDirectories(normalizedUploadDirectory());

            // Récupérer les informations de l'employé pour la convention de nommage lisible
            String matricule = null;
            String nom = null;
            String prenom = null;
            String oldPhotoUrl = null;
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT matricule, nom, prenom, photo_url FROM employe WHERE id_employe = ?")) {
                ps.setInt(1, idEmploye);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        matricule = rs.getString("matricule");
                        nom = rs.getString("nom");
                        prenom = rs.getString("prenom");
                        oldPhotoUrl = rs.getString("photo_url");
                    }
                }
            } catch (Exception ignored) {
                // Poursuivre le téléversement même si la récupération d'informations échoue
            }

            String extension = extensionFor(file.getContentType());
            String uniqueFilename = generatePhotoFilename(matricule, nom, prenom, extension);
            Files.copy(file.getInputStream(), safeResolve(uniqueFilename), StandardCopyOption.REPLACE_EXISTING);

            String photoUrl = "/employes/photos/" + uniqueFilename;
            persistPhotoUrl(idEmploye, photoUrl);

            if (oldPhotoUrl != null && !oldPhotoUrl.isBlank()) {
                cleanupOldPhoto(oldPhotoUrl);
            }

            return ResponseEntity.ok(Map.of(
                    "photoEndpoint", "/employes/" + idEmploye + "/photo",
                    "photoUrl", photoUrl,
                    "url", photoUrl,
                    "filename", uniqueFilename,
                    "idEmploye", idEmploye));
        } catch (IllegalArgumentException e) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, e.getMessage());
        } catch (IOException e) {
            throw new SirhBusinessException(SirhErrorCode.INTERNAL_ERROR);
        }
    }

    @GetMapping("/photos/{filename}")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getPhotoByFilename(@PathVariable String filename) {
        return servePhotoFile(filename);
    }

    @PostMapping("/upload-photo")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, String>> uploadPhoto(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "matricule", required = false) String matricule,
            @RequestParam(value = "nom", required = false) String nom,
            @RequestParam(value = "prenom", required = false) String prenom) {
        try {
            validateImage(file);
            Files.createDirectories(normalizedUploadDirectory());
            String extension = extensionFor(file.getContentType());
            String uniqueFilename = generatePhotoFilename(matricule, nom, prenom, extension);
            Files.copy(file.getInputStream(), safeResolve(uniqueFilename), StandardCopyOption.REPLACE_EXISTING);

            String photoUrl = "/employes/photos/" + uniqueFilename;

            return ResponseEntity.ok(Map.of(
                    "photoUrl", photoUrl,
                    "url", photoUrl,
                    "filename", uniqueFilename
            ));
        } catch (IllegalArgumentException e) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, e.getMessage());
        } catch (IOException e) {
            throw new SirhBusinessException(SirhErrorCode.INTERNAL_ERROR);
        }
    }

    // Helpers photo
    private String resolvePhotoUrlFromDb(int idEmploye) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT photo_url FROM employe WHERE id_employe = ? AND est_actif = true")) {
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
        if (!Files.exists(resolved)) {
            // Compatibilité ascendante avec l'ancien répertoire uploads/photos
            Path legacy = Paths.get("uploads/photos").toAbsolutePath().normalize().resolve(filename).normalize();
            if (Files.exists(legacy) && legacy.startsWith(Paths.get("uploads/photos").toAbsolutePath().normalize())) {
                return legacy;
            }
        }
        return resolved;
    }

    private void cleanupOldPhoto(String oldPhotoUrl) {
        try {
            if (oldPhotoUrl == null || oldPhotoUrl.isBlank()) return;
            String filename = Paths.get(oldPhotoUrl).getFileName().toString();
            Path filePath = safeResolve(filename);
            if (Files.isRegularFile(filePath)) {
                Files.deleteIfExists(filePath);
            }
        } catch (Exception ignored) {
            // Tolérance aux erreurs de nettoyage
        }
    }

    public static String sanitizeNamePart(String input) {
        if (input == null || input.isBlank()) return "";
        String normalized = java.text.Normalizer.normalize(input.trim(), java.text.Normalizer.Form.NFD);
        String withoutAccents = normalized.replaceAll("\\p{M}", "");
        String sanitized = withoutAccents.replaceAll("[^a-zA-Z0-9_-]+", "_");
        return sanitized.replaceAll("_+", "_").replaceAll("^_|_$", "");
    }

    public static String generatePhotoFilename(String matricule, String nom, String prenom, String extension) {
        StringBuilder sb = new StringBuilder("PHOTO");
        String cleanMat = sanitizeNamePart(matricule);
        String cleanNom = sanitizeNamePart(nom);
        String cleanPrenom = sanitizeNamePart(prenom);

        if (!cleanMat.isEmpty()) {
            sb.append("_").append(cleanMat.toUpperCase());
        }
        if (!cleanNom.isEmpty()) {
            sb.append("_").append(cleanNom.toUpperCase());
        }
        if (!cleanPrenom.isEmpty()) {
            sb.append("_").append(cleanPrenom);
        }
        if (cleanMat.isEmpty() && cleanNom.isEmpty() && cleanPrenom.isEmpty()) {
            sb.append("_COLLABORATEUR");
        }

        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String shortId = UUID.randomUUID().toString().replace("-", "").substring(0, 6);

        sb.append("_").append(timestamp).append("_").append(shortId).append(extension);
        return sb.toString();
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
