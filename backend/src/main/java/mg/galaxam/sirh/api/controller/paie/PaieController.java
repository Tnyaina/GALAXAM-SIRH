package mg.galaxam.sirh.api.controller.paie;

import jakarta.validation.Valid;
import mg.galaxam.sirh.api.dto.paie.ActionPaiePeriodeRequest;
import mg.galaxam.sirh.api.dto.paie.CalculEmployeRequest;
import mg.galaxam.sirh.api.dto.paie.CalculPeriodeRequest;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.dto.BulletinPaieComplet;
import mg.galaxam.sirh.model.paie.dto.PayrollBatchResult;
import mg.galaxam.sirh.model.paie.dto.export.DntsRowDto;
import mg.galaxam.sirh.model.paie.dto.export.SafiRowDto;
import mg.galaxam.sirh.model.paie.dto.export.VirementRowDto;
import mg.galaxam.sirh.model.paie.service.PayrollService;
import mg.galaxam.sirh.model.paie.workflow.PayrollAction;
import mg.galaxam.sirh.model.paie.workflow.PayrollPeriodUserContext;
import mg.galaxam.sirh.model.paie.workflow.PayrollPeriodWorkflowService;
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
    private final PayrollPeriodWorkflowService payrollPeriodWorkflowService;

    public PaieController(DataSource dataSource,
                          PayrollService payrollService,
                          PaieExportService paieExportService,
                          PayrollPeriodWorkflowService payrollPeriodWorkflowService) {
        this.dataSource = dataSource;
        this.payrollService = payrollService;
        this.paieExportService = paieExportService;
        this.payrollPeriodWorkflowService = payrollPeriodWorkflowService;
    }

    // =========================================================================
    // EXÉCUTION & WORKFLOW DU CALCUL DE PAIE
    // =========================================================================

    @PostMapping("/calculer-employe")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> calculerBulletinEmploye(
            @Valid @RequestBody CalculEmployeRequest req, Authentication authentication) throws Exception {
        payrollPeriodWorkflowService.requirePayrollAction(
                req.getIdPeriodePaie(), req.isSimulation() ? PayrollAction.SIMULATE : PayrollAction.CALCULATE,
                PayrollPeriodUserContext.from(authentication));
        Integer userId = getCurrentUserId();
        BulletinPaieComplet res = payrollService.calculerBulletinEmploye(
                req.getIdEmploye(), req.getIdPeriodePaie(), req.isSimulation(), userId);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/calculer-periode")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> calculerPaiePeriode(
            @Valid @RequestBody CalculPeriodeRequest req, Authentication authentication) throws Exception {
        payrollPeriodWorkflowService.requirePayrollAction(
                req.getIdPeriodePaie(), req.isSimulation() ? PayrollAction.SIMULATE : PayrollAction.CALCULATE,
                PayrollPeriodUserContext.from(authentication));
        Integer userId = getCurrentUserId();
        PayrollBatchResult res = payrollService.calculerPaiePeriode(
                req.getIdPeriodePaie(), req.isSimulation(), userId);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/valider-periode")
    @PreAuthorize("hasAuthority('PAIE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> validerPaiePeriode(
            @Valid @RequestBody ActionPaiePeriodeRequest req, Authentication authentication) throws Exception {
        payrollPeriodWorkflowService.requirePayrollAction(
                req.getIdPeriodePaie(), PayrollAction.VALIDATE,
                PayrollPeriodUserContext.from(authentication));
        Integer userId = getCurrentUserId();
        PayrollBatchResult res = payrollService.validerPaiePeriode(req.getIdPeriodePaie(), userId);
        return ResponseEntity.ok(res);
    }

    // =========================================================================
    // CONSULTATION DES BULLETINS
    // =========================================================================

    @GetMapping("/bulletin/{id}")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> obtenirBulletinComplet(@PathVariable("id") Integer idBulletin) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            BulletinPaieComplet bp = payrollService.obtenirBulletinComplet(idBulletin, c);
            if (bp == null) {
                throw new SirhBusinessException(SirhErrorCode.PAYROLL_PERIOD_NOT_FOUND,
                        "Bulletin de paie introuvable.");
            }
            return ResponseEntity.ok(bp);
        }
    }

    @GetMapping("/periode/{idPeriode}/cockpit")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> obtenirCockpitPeriode(@PathVariable("idPeriode") Integer idPeriode) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            Map<String, Object> cockpit = payrollService.obtenirCockpitPeriode(idPeriode, c);
            return ResponseEntity.ok(cockpit);
        }
    }

    @GetMapping("/periode/{idPeriode}/bulletins")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> listerBulletinsPeriode(@PathVariable("idPeriode") Integer idPeriode) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            List<BulletinPaie> list = BulletinPaie.listerParPeriode(idPeriode, c);
            return ResponseEntity.ok(list);
        }
    }

    // =========================================================================
    // RESTITUTIONS & EXPORTS OFFICIELS (SAFI 25 COL, EDI ISI XML, DNTS, VIREMENT)
    // =========================================================================

    @GetMapping("/periode/{idPeriode}/export/safi")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> telechargerSafiCsv(@PathVariable("idPeriode") Integer idPeriode) throws Exception {
        String csv = paieExportService.exporterSafiCsv(idPeriode);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"etat_301_safi_periode_" + idPeriode + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping("/periode/{idPeriode}/export/safi-ods")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> telechargerSafiOds(@PathVariable("idPeriode") Integer idPeriode) throws Exception {
        byte[] odsBytes = paieExportService.exporterSafiOds(idPeriode);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modele_declaration_irsa_safi_periode_" + idPeriode + ".ods\"")
                .contentType(MediaType.parseMediaType("application/vnd.oasis.opendocument.spreadsheet"))
                .body(odsBytes);
    }

    @GetMapping("/periode/{idPeriode}/export/safi-data")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> obtenirDonneesSafi(@PathVariable("idPeriode") Integer idPeriode) throws Exception {
        return ResponseEntity.ok(paieExportService.obtenirDonneesSafi(idPeriode));
    }

    @GetMapping("/periode/{idPeriode}/export/edi-isi")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> telechargerEdiIsiXml(@PathVariable("idPeriode") Integer idPeriode) throws Exception {
        String xml = paieExportService.exporterEdiXml(idPeriode);
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"annexe_edi_isi_periode_" + idPeriode + ".xml\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(bytes);
    }

    @GetMapping("/periode/{idPeriode}/export/dnts")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> telechargerDntsCsv(@PathVariable("idPeriode") Integer idPeriode) throws Exception {
        String csv = paieExportService.exporterDntsCsv(idPeriode);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dnts_cnaps_fmfp_periode_" + idPeriode + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping("/periode/{idPeriode}/export/dnts-data")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> obtenirDonneesDnts(@PathVariable("idPeriode") Integer idPeriode) throws Exception {
        return ResponseEntity.ok(paieExportService.obtenirDonneesDnts(idPeriode));
    }

    @GetMapping("/periode/{idPeriode}/export/virement")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> telechargerVirementCsv(@PathVariable("idPeriode") Integer idPeriode) throws Exception {
        String csv = paieExportService.exporterVirementCsv(idPeriode);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ordre_virement_periode_" + idPeriode + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping("/periode/{idPeriode}/export/virement-data")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> obtenirDonneesVirement(@PathVariable("idPeriode") Integer idPeriode) throws Exception {
        return ResponseEntity.ok(paieExportService.obtenirDonneesVirement(idPeriode));
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

}
