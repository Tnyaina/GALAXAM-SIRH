package mg.galaxam.sirh.contrat.api;

import mg.galaxam.sirh.contrat.persistence.*;
import mg.galaxam.sirh.contrat.domain.*;
import mg.galaxam.sirh.contrat.application.*;
import mg.galaxam.sirh.contrat.api.dto.*;

import mg.galaxam.sirh.contrat.api.dto.DocumentRhDTO.*;
import mg.galaxam.sirh.contrat.application.DocumentRhService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;

@RestController
@RequestMapping({"/documents", "/api/documents"})
public class DocumentOfficielRhController {

    private final DataSource dataSource;
    private final DocumentRhService documentRhService;

    public DocumentOfficielRhController(DataSource dataSource, DocumentRhService documentRhService) {
        this.dataSource = dataSource;
        this.documentRhService = documentRhService;
    }

    @GetMapping("/attestation/{idEmploye}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AttestationTravailDTO> getAttestationData(
            @PathVariable Integer idEmploye,
            @RequestParam(defaultValue = "contrat") String typeAnciennete,
            @RequestParam(defaultValue = "false") boolean inclureSalaire) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            AttestationTravailDTO dto = documentRhService.preparerAttestation(idEmploye, typeAnciennete, inclureSalaire, c);
            return ResponseEntity.ok(dto);
        }
    }

    @GetMapping("/attestation/{idEmploye}/docx")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadAttestationDocx(
            @PathVariable Integer idEmploye,
            @RequestParam(defaultValue = "contrat") String typeAnciennete,
            @RequestParam(defaultValue = "false") boolean inclureSalaire) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            AttestationTravailDTO dto = documentRhService.preparerAttestation(idEmploye, typeAnciennete, inclureSalaire, c);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            documentRhService.genererAttestationDocx(dto, baos);

            String filename = "Attestation_Travail_" + dto.getEmploye().getMatricule() + ".docx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(baos.toByteArray());
        }
    }

    @GetMapping("/certificat/{idContrat}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CertificatTravailDTO> getCertificatData(
            @PathVariable Integer idContrat,
            @RequestParam(defaultValue = "contrat") String typeAnciennete) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CertificatTravailDTO dto = documentRhService.preparerCertificat(idContrat, typeAnciennete, c);
            return ResponseEntity.ok(dto);
        }
    }

    @GetMapping("/certificat/{idContrat}/docx")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadCertificatDocx(
            @PathVariable Integer idContrat,
            @RequestParam(defaultValue = "contrat") String typeAnciennete) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CertificatTravailDTO dto = documentRhService.preparerCertificat(idContrat, typeAnciennete, c);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            documentRhService.genererCertificatDocx(dto, baos);

            String filename = "Certificat_Travail_" + dto.getEmploye().getMatricule() + ".docx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(baos.toByteArray());
        }
    }
}
