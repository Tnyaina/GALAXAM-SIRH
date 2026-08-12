package mg.galaxam.sirh.api.controller.employe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/employes")
public class EmployePhotoController {

    @Value("${app.imgbb.api-key:16da31a095830083321b07cab6e52c92}")
    private String imgbbApiKey;

    @Value("${app.imgbb.upload-url:https://api.imgbb.com/1/upload}")
    private String imgbbUploadUrl;

    @Value("${app.upload.dir:uploads/photos}")
    private String uploadDir;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Upload de la photo d'un employé vers ImgBB (stockage cloud permanent).
     * En cas d'échec ou d'absence de réseau, utilise le stockage local de secours.
     */
    @PostMapping("/upload-photo")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> uploadPhoto(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("Aucun fichier sélectionné.");
            }

            String contentType = file.getContentType();
            if (contentType == null || (!contentType.startsWith("image/"))) {
                return ResponseEntity.badRequest().body("Seuls les fichiers image sont autorisés (JPEG, PNG, WEBP).");
            }

            if (file.getSize() > 5 * 1024 * 1024) {
                return ResponseEntity.badRequest().body("La taille du fichier ne doit pas dépasser 5 Mo.");
            }

            // 1. Essai d'upload vers ImgBB Cloud API
            if (imgbbApiKey != null && !imgbbApiKey.isBlank()) {
                try {
                    String cloudPhotoUrl = uploadToImgBB(file);
                    if (cloudPhotoUrl != null && !cloudPhotoUrl.isBlank()) {
                        return ResponseEntity.ok(Map.of("photoUrl", cloudPhotoUrl));
                    }
                } catch (Exception e) {
                    System.err.println("Échec d'upload ImgBB, bascule vers stockage local : " + e.getMessage());
                }
            }

            // 2. Fallback stockage local
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String uniqueFilename = UUID.randomUUID().toString() + extension;

            Path filePath = uploadPath.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String photoUrl = "/uploads/photos/" + uniqueFilename;
            return ResponseEntity.ok(Map.of("photoUrl", photoUrl));

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de l'upload de la photo.");
        }
    }

    /**
     * Envoie l'image binaire à l'API ImgBB.
     * Retourne l'URL directe du CDN ImgBB (ex: https://i.ibb.co/w04gMT2/image.png)
     */
    private String uploadToImgBB(MultipartFile file) throws Exception {
        String url = imgbbUploadUrl + "?key=" + imgbbApiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.jpg";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", fileResource);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map<?, ?> responseBody = response.getBody();
            if (Boolean.TRUE.equals(responseBody.get("success"))) {
                Map<?, ?> data = (Map<?, ?>) responseBody.get("data");
                if (data != null) {
                    return (String) data.get("url");
                }
            }
        }
        return null;
    }
}
