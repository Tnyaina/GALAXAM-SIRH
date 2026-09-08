package mg.galaxam.sirh.api.controller.paie;

import jakarta.validation.Valid;
import mg.galaxam.sirh.api.dto.paie.ActionPaiePeriodeRequest;
import mg.galaxam.sirh.api.dto.paie.CalculEmployeRequest;
import mg.galaxam.sirh.api.dto.paie.CalculPeriodeRequest;
import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.dto.BulletinPaieComplet;
import mg.galaxam.sirh.model.paie.dto.PayrollBatchResult;
import mg.galaxam.sirh.model.paie.dto.export.DntsRowDto;
import mg.galaxam.sirh.model.paie.dto.export.SafiRowDto;
import mg.galaxam.sirh.model.paie.dto.export.VirementRowDto;
import mg.galaxam.sirh.model.paie.service.PayrollService;
import mg.galaxam.sirh.model.paie.service.export.PaieExportService;
import mg.galaxam.sirh.securite.CustomUserDetails;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour l'exécution, l'orchestration et les exports déclaratifs de la Paie.
 * Base URL : /api/v1/paie
 */
@RestController
@RequestMapping({"/api/v1/paie", "/v1/paie", "/paie"})
public class PaieController {

    private final DataSource dataSource;
    private final PayrollService payrollService;
    private final PaieExportService paieExportService;

    public PaieController(DataSource dataSource,
                          PayrollService payrollService,
                          PaieExportService paieExportService) {
        this.dataSource = dataSource;
        this.payrollService = payrollService;
        this.paieExportService = paieExportService;
    }

    // =========================================================================
    // EXÉCUTION & WORKFLOW DU CALCUL DE PAIE
    // =========================================================================

    @PostMapping("/calculer-employe")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> calculerBulletinEmploye(@Valid @RequestBody CalculEmployeRequest req) {
        Integer userId = getCurrentUserId();
        try (Connection c = dataSource.getConnection()) {
            BulletinPaieComplet res = payrollService.calculerBulletinEmploye(
                    req.getIdEmploye(), req.getIdPeriodePaie(), req.isSimulation(), userId, c);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(createErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur lors du calcul du bulletin : " + e.getMessage()));
        }
    }

    @PostMapping("/calculer-periode")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> calculerPaiePeriode(@Valid @RequestBody CalculPeriodeRequest req) {
        Integer userId = getCurrentUserId();
        try (Connection c = dataSource.getConnection()) {
            PayrollBatchResult res = payrollService.calculerPaiePeriode(
                    req.getIdPeriodePaie(), req.isSimulation(), userId, c);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(createErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur lors du calcul collectif : " + e.getMessage()));
        }
    }

    @PostMapping("/valider-periode")
    @PreAuthorize("hasAuthority('PAIE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> validerPaiePeriode(@Valid @RequestBody ActionPaiePeriodeRequest req) {
        Integer userId = getCurrentUserId();
        try (Connection c = dataSource.getConnection()) {
            PayrollBatchResult res = payrollService.validerPaiePeriode(req.getIdPeriodePaie(), userId, c);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(createErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur lors de la validation : " + e.getMessage()));
        }
    }

    @PostMapping("/cloturer-periode")
    @PreAuthorize("hasAuthority('PAIE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> cloturerPaiePeriode(@Valid @RequestBody ActionPaiePeriodeRequest req) {
        Integer userId = getCurrentUserId();
        try (Connection c = dataSource.getConnection()) {
            payrollService.cloturerPaiePeriode(req.getIdPeriodePaie(), userId, c);
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "Période de paie clôturée avec succès.");
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(createErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur lors de la clôture : " + e.getMessage()));
        }
    }

    @PostMapping("/rouvrir-periode")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> rouvrirPaiePeriode(@Valid @RequestBody ActionPaiePeriodeRequest req) {
        Integer userId = getCurrentUserId();
        if (req.getMotif() == null || req.getMotif().trim().length() < 5) {
            return ResponseEntity.badRequest().body(createErrorResponse("Le motif de réouverture est obligatoire (min 5 caractères)."));
        }
        try (Connection c = dataSource.getConnection()) {
            payrollService.rouvrirPaiePeriode(req.getIdPeriodePaie(), req.getMotif().trim(), userId, c);
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "Période de paie réouverte avec succès.");
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur lors de la réouverture : " + e.getMessage()));
        }
    }

    // =========================================================================
    // CONSULTATION DES BULLETINS
    // =========================================================================

    @GetMapping("/bulletin/{id}")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> obtenirBulletinComplet(@PathVariable("id") Integer idBulletin) {
        try (Connection c = dataSource.getConnection()) {
            BulletinPaieComplet bp = payrollService.obtenirBulletinComplet(idBulletin, c);
            if (bp == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(createErrorResponse("Bulletin introuvable."));
            }
            return ResponseEntity.ok(bp);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur lors de la consultation : " + e.getMessage()));
        }
    }

    @GetMapping("/periode/{idPeriode}/bulletins")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> listerBulletinsPeriode(@PathVariable("idPeriode") Integer idPeriode) {
        try (Connection c = dataSource.getConnection()) {
            List<BulletinPaie> list = BulletinPaie.listerParPeriode(idPeriode, c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur lors de la récupération des bulletins : " + e.getMessage()));
        }
    }

    // =========================================================================
    // RESTITUTIONS & EXPORTS OFFICIELS (SAFI 25 COL, EDI ISI XML, DNTS, VIREMENT)
    // =========================================================================

    @GetMapping("/periode/{idPeriode}/export/safi")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> telechargerSafiCsv(@PathVariable("idPeriode") Integer idPeriode) {
        try {
            String csv = paieExportService.exporterSafiCsv(idPeriode);
            byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"etat_301_safi_periode_" + idPeriode + ".csv\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur lors de l'export SAFI : " + e.getMessage()));
        }
    }

    @GetMapping("/periode/{idPeriode}/export/safi-ods")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> telechargerSafiOds(@PathVariable("idPeriode") Integer idPeriode) {
        try {
            byte[] odsBytes = paieExportService.exporterSafiOds(idPeriode);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modele_declaration_irsa_safi_periode_" + idPeriode + ".ods\"")
                    .contentType(MediaType.parseMediaType("application/vnd.oasis.opendocument.spreadsheet"))
                    .body(odsBytes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur lors de l'export SAFI ODS : " + e.getMessage()));
        }
    }

    @GetMapping("/periode/{idPeriode}/export/safi-data")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> obtenirDonneesSafi(@PathVariable("idPeriode") Integer idPeriode) {
        try {
            List<SafiRowDto> data = paieExportService.obtenirDonneesSafi(idPeriode);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur lors de la génération des données SAFI : " + e.getMessage()));
        }
    }

    @GetMapping("/periode/{idPeriode}/export/edi-isi")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> telechargerEdiIsiXml(@PathVariable("idPeriode") Integer idPeriode) {
        try {
            String xml = paieExportService.exporterEdiXml(idPeriode);
            byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"annexe_edi_isi_periode_" + idPeriode + ".xml\"")
                    .contentType(MediaType.APPLICATION_XML)
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur lors de l'export EDI ISI : " + e.getMessage()));
        }
    }

    @GetMapping("/periode/{idPeriode}/export/dnts")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> telechargerDntsCsv(@PathVariable("idPeriode") Integer idPeriode) {
        try {
            String csv = paieExportService.exporterDntsCsv(idPeriode);
            byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dnts_cnaps_fmfp_periode_" + idPeriode + ".csv\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur lors de l'export DNTS : " + e.getMessage()));
        }
    }

    @GetMapping("/periode/{idPeriode}/export/dnts-data")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> obtenirDonneesDnts(@PathVariable("idPeriode") Integer idPeriode) {
        try {
            List<DntsRowDto> data = paieExportService.obtenirDonneesDnts(idPeriode);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur lors de la génération des données DNTS : " + e.getMessage()));
        }
    }

    @GetMapping("/periode/{idPeriode}/export/virement")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> telechargerVirementCsv(@PathVariable("idPeriode") Integer idPeriode) {
        try {
            String csv = paieExportService.exporterVirementCsv(idPeriode);
            byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ordre_virement_periode_" + idPeriode + ".csv\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur lors de l'export du virement : " + e.getMessage()));
        }
    }

    @GetMapping("/periode/{idPeriode}/export/virement-data")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> obtenirDonneesVirement(@PathVariable("idPeriode") Integer idPeriode) {
        try {
            List<VirementRowDto> data = paieExportService.obtenirDonneesVirement(idPeriode);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Erreur lors de la génération des données virement : " + e.getMessage()));
        }
    }

    // =========================================================================
    // UTILITAIRES PRIVÉS
    // =========================================================================

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails ud) {
            return ud.getIdUtilisateur();
        }
        return 1; // ID fallback système / admin
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", false);
        resp.put("error", message);
        return resp;
    }
}
