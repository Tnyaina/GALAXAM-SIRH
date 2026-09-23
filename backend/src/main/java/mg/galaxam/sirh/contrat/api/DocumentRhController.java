package mg.galaxam.sirh.contrat.api;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import mg.galaxam.sirh.contrat.persistence.DocumentRh;
import mg.galaxam.sirh.contrat.persistence.DocumentRhCPL;
import mg.galaxam.sirh.contrat.persistence.RefTypeDocument;
import mg.galaxam.sirh.securite.AuditLog;
import mg.galaxam.sirh.securite.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class DocumentRhController {

    @Autowired
    private DataSource dataSource;

    @Value("${app.upload.doc-dir:uploads/documents}")
    private String docUploadDir;

    /**
     * Récupère la liste des documents RH d'un employé + la synthèse des documents obligatoires manquants.
     */
    @GetMapping("/employes/{idEmploye}/documents")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getDocumentsByEmploye(@PathVariable int idEmploye) {
        try (Connection c = dataSource.getConnection()) {
            List<DocumentRhCPL> docs = DocumentRhCPL.listByEmployeId(idEmploye, c);
            List<RefTypeDocument> typesActifs = RefTypeDocument.listAll(c, true);

            // Identifier les types obligatoires non encore fournis
            Set<Integer> typesFournis = new HashSet<>();
            for (DocumentRhCPL d : docs) {
                typesFournis.add(d.getIdTypeDocument());
            }

            List<RefTypeDocument> obligatoiresManquants = new ArrayList<>();
            for (RefTypeDocument t : typesActifs) {
                if (t.isObligatoire() && !typesFournis.contains(t.getIdTypeDocument())) {
                    obligatoiresManquants.add(t);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("documents", docs);
            result.put("obligatoiresManquants", obligatoiresManquants);
            result.put("totalDocuments", docs.size());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    /**
     * Téléversement d'un document RH pour un employé.
     */
    @PostMapping(value = "/employes/{idEmploye}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> uploadDocument(
            @PathVariable int idEmploye,
            @RequestParam("file") MultipartFile file,
            @RequestParam("idTypeDocument") int idTypeDocument,
            @RequestParam(value = "dateDocument", required = false) String dateDocumentStr,
            @RequestParam(value = "commentaire", required = false) String commentaire,
            Authentication auth
    ) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (file.isEmpty()) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Aucun fichier sélectionné.");
                }

                // 1. Vérification du type de document
                RefTypeDocument typeDoc = new RefTypeDocument().rechercherParId(idTypeDocument, c);
                if (typeDoc == null || !typeDoc.isActif()) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Type de document invalide ou inactif.");
                }

                // 2. Vérification de la taille max
                if (typeDoc.getTailleMaxMo() != null && file.getSize() > (long) typeDoc.getTailleMaxMo() * 1024 * 1024) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                            mg.galaxam.sirh.common.exception.SirhErrorCode.FILE_TOO_LARGE,
                            "La taille du fichier dépasse la limite autorisée (" + typeDoc.getTailleMaxMo() + " Mo).");
                }

                // 3. Stockage sur le disque
                Path uploadPath = Paths.get(docUploadDir);
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }

                String originalFilename = file.getOriginalFilename();
                String extension = "";
                if (originalFilename != null && originalFilename.contains(".")) {
                    extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
                }

                // 2.bis Vérification de l'extension autorisée (whitelist stricte)
                String allowedExtensions = typeDoc.getExtensionAutorisee() != null && !typeDoc.getExtensionAutorisee().isBlank()
                        ? typeDoc.getExtensionAutorisee().toLowerCase()
                        : "pdf,jpg,jpeg,png";
                java.util.List<String> allowedList = java.util.Arrays.stream(allowedExtensions.split(","))
                        .map(String::trim)
                        .toList();
                if (extension.isEmpty() || !allowedList.contains(extension)) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                            mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                            "Format de fichier non autorisé (." + extension + "). Formats acceptés : " + allowedExtensions);
                }

                String uniqueFilename = UUID.randomUUID().toString() + "." + extension;
                Path filePath = uploadPath.resolve(uniqueFilename);
                Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

                // 4. Enregistrement en BDD
                DocumentRh doc = new DocumentRh();
                doc.setIdEmploye(idEmploye);
                doc.setIdTypeDocument(idTypeDocument);
                doc.setNomOriginal(originalFilename);
                doc.setNomStockage(uniqueFilename);
                doc.setCheminFichier("/uploads/documents/" + uniqueFilename);
                doc.setExtension(extension);
                doc.setTailleOctet(file.getSize());
                if (dateDocumentStr != null && !dateDocumentStr.isBlank()) {
                    doc.setDateDocument(Date.valueOf(dateDocumentStr));
                }
                doc.setCommentaire(commentaire);

                if (auth != null && auth.getPrincipal() instanceof CustomUserDetails) {
                    doc.setUploadedBy(((CustomUserDetails) auth.getPrincipal()).getIdUtilisateur());
                }

                doc.save(c);
                AuditLog.audit(c, "CREATION", "document_rh", String.valueOf(doc.getIdDocument()), null, doc);
                c.commit();

                DocumentRhCPL createdDocView = new DocumentRhCPL().rechercherParId(doc.getIdDocument(), c);
                return ResponseEntity.status(HttpStatus.CREATED).body(createdDocView);

            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    /**
     * Téléchargement / Visualisation d'un document RH par son ID.
     */
    @GetMapping({"/documents-rh/{idDocument}/telecharger", "/documents-rh/{idDocument}/download"})
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> downloadDocument(@PathVariable int idDocument) {
        try (Connection c = dataSource.getConnection()) {
            DocumentRh doc = new DocumentRh().rechercherParId(idDocument, c);
            if (doc == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Document non trouvé.");
            }

            Path filePath = Paths.get(docUploadDir).resolve(doc.getNomStockage());
            if (!Files.exists(filePath)) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Le fichier physique n'existe plus sur le serveur.");
            }

            Resource resource = new UrlResource(filePath.toUri());
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore().cachePrivate())
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getNomOriginal() + "\"")
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    /**
     * Suppression d'un document RH.
     */
    @DeleteMapping("/documents-rh/{idDocument}")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deleteDocument(@PathVariable int idDocument) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                DocumentRh doc = new DocumentRh().rechercherParId(idDocument, c);
                if (doc == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Document non trouvé.");
                }

                // Suppression physique du fichier
                Path filePath = Paths.get(docUploadDir).resolve(doc.getNomStockage());
                try {
                    Files.deleteIfExists(filePath);
                } catch (IOException e) {
                    System.err.println("Erreur suppression fichier physique: " + e.getMessage());
                }

                // Suppression BDD
                doc.delete(c);
                AuditLog.audit(c, "SUPPRESSION", "document_rh", String.valueOf(idDocument), doc, null);
                c.commit();

                return ResponseEntity.ok(Map.of("message", "Document supprimé avec succès."));
            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }
}
