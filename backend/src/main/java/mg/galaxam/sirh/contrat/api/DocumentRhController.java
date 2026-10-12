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
import org.springframework.http.ContentDisposition;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import mg.galaxam.sirh.contrat.api.dto.DocumentRhDTO.ContratTravailDocumentDTO;
import mg.galaxam.sirh.contrat.api.dto.DocumentRhDTO.AttestationTravailDTO;
import mg.galaxam.sirh.contrat.api.dto.DocumentRhDTO.CertificatTravailDTO;
import mg.galaxam.sirh.contrat.application.DocumentRhService;

@RestController
public class DocumentRhController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DocumentRhService documentRhService;

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
            @RequestParam(value = "idContrat", required = false) Integer idContrat,
            @RequestParam(value = "idWorkflow", required = false) Integer idWorkflow,
            Authentication auth
    ) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            Path storedFile = null;
            try {
                if (file.isEmpty()) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Aucun fichier sélectionné.");
                }

                // 1. Vérification du type de document
                RefTypeDocument typeDoc = new RefTypeDocument().rechercherParId(idTypeDocument, c);
                if (typeDoc == null || !typeDoc.isActif()) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Type de document invalide ou inactif.");
                }

                mg.galaxam.sirh.contrat.application.DocumentRhService.validerLiaisonsDocument(idEmploye, idTypeDocument, idContrat, idWorkflow, c);

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
                    extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase(java.util.Locale.ROOT);
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

                // Récupération de l'employé pour convention de nommage descriptive
                mg.galaxam.sirh.contrat.persistence.Employe emp = new mg.galaxam.sirh.contrat.persistence.Employe().rechercherParId(idEmploye, c);
                String mat = emp != null && emp.getMatricule() != null ? emp.getMatricule().replaceAll("[^a-zA-Z0-9_-]", "") : ("EMP" + idEmploye);
                String typeCode = typeDoc.getLibelle().toUpperCase().replaceAll("[^A-Z0-9]+", "_");
                String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String shortUuid = UUID.randomUUID().toString().substring(0, 8);
                String uniqueFilename = "DOC_" + mat + "_" + typeCode + "_" + timestamp + "_" + shortUuid + "." + extension;

                Path filePath = uploadPath.resolve(uniqueFilename);
                storedFile = filePath;
                try (var input = file.getInputStream()) {
                    Files.copy(input, filePath, StandardCopyOption.REPLACE_EXISTING);
                }

                // 4. Enregistrement en BDD
                DocumentRh doc = new DocumentRh();
                doc.setIdEmploye(idEmploye);
                doc.setIdTypeDocument(idTypeDocument);
                doc.setIdContrat(idContrat);
                doc.setIdWorkflow(idWorkflow);
                doc.setNomOriginal(originalFilename);
                doc.setNomStockage(uniqueFilename);
                doc.setCheminFichier("/uploads/documents/" + uniqueFilename);
                doc.setExtension(extension);
                doc.setTailleOctet(file.getSize());
                if (dateDocumentStr != null && !dateDocumentStr.isBlank()) {
                    Date parsedDocDate = mg.galaxam.sirh.contrat.api.dto.EmployeRequest.parseFlexibleDate(dateDocumentStr);
                    if (parsedDocDate != null) {
                        doc.setDateDocument(parsedDocDate);
                    }
                }
                doc.setCommentaire(commentaire);

                if (auth != null && auth.getPrincipal() instanceof CustomUserDetails) {
                    doc.setUploadedBy(((CustomUserDetails) auth.getPrincipal()).getIdUtilisateur());
                }

                doc.save(c);
                AuditLog.audit(c, "CREATION", "document_rh", String.valueOf(doc.getIdDocument()), null, doc);
                DocumentRhCPL createdDocView = new DocumentRhCPL().rechercherParId(doc.getIdDocument(), c);
                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(createdDocView);

            } catch (Exception ex) {
                c.rollback();
                if (storedFile != null) {
                    try { Files.deleteIfExists(storedFile); } catch (IOException cleanup) { ex.addSuppressed(cleanup); }
                }
                throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(ex);
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
            DocumentRhCPL doc = new DocumentRhCPL().rechercherParId(idDocument, c);
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
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.inline().filename(doc.getNomAffichage(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
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

    // =========================================================================
    // DOCUMENTS OFFICIELS RH (CONTRATS, ATTESTATIONS, CERTIFICATS)
    // =========================================================================

    @GetMapping({"/documents/contrat/{idContrat}", "/api/documents/contrat/{idContrat}"})
    @PreAuthorize("(hasAuthority('EMPLOYE_READ') and hasAuthority('PAIE_READ')) or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ContratTravailDocumentDTO> getContratData(@PathVariable Integer idContrat) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(documentRhService.preparerContrat(idContrat, c));
        }
    }

    @GetMapping({"/documents/contrat/{idContrat}/docx", "/api/documents/contrat/{idContrat}/docx"})
    @PreAuthorize("(hasAuthority('EMPLOYE_READ') and hasAuthority('PAIE_READ')) or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> downloadContratDocx(@PathVariable Integer idContrat) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            var dto = documentRhService.preparerContrat(idContrat, c);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            documentRhService.genererContratDocx(dto, output);
            String filename = "Contrat_" + dto.numeroContrat().replaceAll("[^a-zA-Z0-9_-]", "_") + ".docx";
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(output.toByteArray());
        }
    }

    @GetMapping({"/documents/attestation/{idEmploye}", "/api/documents/attestation/{idEmploye}"})
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<AttestationTravailDTO> getAttestationData(
            @PathVariable Integer idEmploye,
            @RequestParam(defaultValue = "contrat") String typeAnciennete,
            @RequestParam(defaultValue = "false") boolean inclureSalaire,
            Authentication authentication) throws Exception {
        requirePayrollReadForSalary(inclureSalaire, authentication);
        try (Connection c = dataSource.getConnection()) {
            AttestationTravailDTO dto = documentRhService.preparerAttestation(idEmploye, typeAnciennete, inclureSalaire, c);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dto);
        }
    }

    @GetMapping({"/documents/attestation/{idEmploye}/docx", "/api/documents/attestation/{idEmploye}/docx"})
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> downloadAttestationDocx(
            @PathVariable Integer idEmploye,
            @RequestParam(defaultValue = "contrat") String typeAnciennete,
            @RequestParam(defaultValue = "false") boolean inclureSalaire,
            Authentication authentication) throws Exception {
        requirePayrollReadForSalary(inclureSalaire, authentication);
        try (Connection c = dataSource.getConnection()) {
            AttestationTravailDTO dto = documentRhService.preparerAttestation(idEmploye, typeAnciennete, inclureSalaire, c);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            documentRhService.genererAttestationDocx(dto, baos);

            String filename = "Attestation_Travail_" + dto.getEmploye().getMatricule() + ".docx";
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(baos.toByteArray());
        }
    }

    @GetMapping({"/documents/certificat/{idContrat}", "/api/documents/certificat/{idContrat}"})
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<CertificatTravailDTO> getCertificatData(
            @PathVariable Integer idContrat,
            @RequestParam(defaultValue = "contrat") String typeAnciennete) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CertificatTravailDTO dto = documentRhService.preparerCertificat(idContrat, typeAnciennete, c);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dto);
        }
    }

    @GetMapping({"/documents/certificat/{idContrat}/docx", "/api/documents/certificat/{idContrat}/docx"})
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> downloadCertificatDocx(
            @PathVariable Integer idContrat,
            @RequestParam(defaultValue = "contrat") String typeAnciennete) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CertificatTravailDTO dto = documentRhService.preparerCertificat(idContrat, typeAnciennete, c);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            documentRhService.genererCertificatDocx(dto, baos);

            String filename = "Certificat_Travail_" + dto.getEmploye().getMatricule() + ".docx";
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(baos.toByteArray());
        }
    }

    private void requirePayrollReadForSalary(boolean includeSalary, Authentication authentication) {
        if (!includeSalary) return;
        boolean allowed = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "PAIE_READ".equals(authority.getAuthority())
                        || "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!allowed) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "L'accès au salaire exige l'habilitation PAIE_READ.");
        }
    }
}
