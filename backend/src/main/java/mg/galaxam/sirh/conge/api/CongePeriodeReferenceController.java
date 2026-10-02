package mg.galaxam.sirh.conge.api;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.transaction.TransactionRunner;
import mg.galaxam.sirh.conge.application.CongeAnnuelExcelExportService;
import mg.galaxam.sirh.conge.application.CongeGrandRecapExcelExportService;
import mg.galaxam.sirh.conge.application.CongeTauxApplicableService;
import mg.galaxam.sirh.conge.domain.TcongeConfigurationDTO;
import mg.galaxam.sirh.securite.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 2A-4C.1 — Contrôleur REST pour la gestion opérationnelle des périodes de référence TCONGE
 * et de l'activation des taux calculés.
 */
@RestController
@RequestMapping("/conges/periodes-reference")
public class CongePeriodeReferenceController {

    public record CreerPeriodeReferenceRequest(Integer idEmploye, LocalDate dateDebut, LocalDate dateFin) {}
    public record ActiverTauxCalculeRequest(LocalDate dateEffet) {}

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CongeTauxApplicableService congeTauxApplicableService;

    @Autowired
    private CongeAnnuelExcelExportService congeAnnuelExcelExportService;

    @Autowired
    private CongeGrandRecapExcelExportService congeGrandRecapExcelExportService;

    @Autowired
    private TransactionRunner transactionRunner;

    @PostMapping
    @PreAuthorize("hasAuthority('CONGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> creerPeriodeReference(@RequestBody CreerPeriodeReferenceRequest request) {
        if (request == null || request.idEmploye() == null || request.dateDebut() == null || request.dateFin() == null) {
            throw new IllegalArgumentException("idEmploye, dateDebut et dateFin sont obligatoires.");
        }

        return transactionRunner.required(c -> {
            int idRef = congeTauxApplicableService.creerPeriodeReference(
                    request.idEmploye(),
                    request.dateDebut(),
                    request.dateFin(),
                    CustomUserDetails.getCurrentUserId(),
                    c
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "idPeriodeReference", idRef,
                    "idEmploye", request.idEmploye(),
                    "dateDebut", request.dateDebut(),
                    "dateFin", request.dateFin(),
                    "statut", "OUVERTE"
            ));
        });
    }

    @GetMapping("/employe/{idEmploye}")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> listerPeriodesParEmploye(@PathVariable int idEmploye) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return ResponseEntity.ok(congeTauxApplicableService.listerPeriodesReference(idEmploye, c));
        }
    }

    @GetMapping("/employe/{idEmploye}/statut-configuration")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getStatutConfiguration(
            @PathVariable int idEmploye,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateReference) throws Exception {
        LocalDate dRef = dateReference != null ? dateReference : LocalDate.now();
        try (Connection c = dataSource.getConnection()) {
            TcongeConfigurationDTO config = congeTauxApplicableService.evaluerConfigurationTconge(idEmploye, dRef, c);
            return ResponseEntity.ok(config);
        }
    }

    @PostMapping("/{idPeriodeReference}/cloturer")
    @PreAuthorize("hasAuthority('CONGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> cloturerPeriodeReference(@PathVariable int idPeriodeReference) {
        return transactionRunner.required(c -> {
            BigDecimal tauxCalcule = congeTauxApplicableService.cloturerPeriodeReference(
                    idPeriodeReference, CustomUserDetails.getCurrentUserId(), c);
            return ResponseEntity.ok(Map.of(
                    "idPeriodeReference", idPeriodeReference,
                    "statut", "CLOTUREE",
                    "tauxCalculeCloture", tauxCalcule
            ));
        });
    }

    @PostMapping("/{idPeriodeReference}/activer-taux")
    @PreAuthorize("hasAuthority('CONGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> activerTauxCalcule(
            @PathVariable int idPeriodeReference,
            @RequestBody ActiverTauxCalculeRequest request) {
        if (request == null || request.dateEffet() == null) {
            throw new IllegalArgumentException("dateEffet est obligatoire pour activer le taux.");
        }

        return transactionRunner.required(c -> {
            int idTaux = congeTauxApplicableService.activerTauxCalcule(
                    idPeriodeReference,
                    request.dateEffet(),
                    CustomUserDetails.getCurrentUserId(),
                    c
            );
            return ResponseEntity.ok(Map.of(
                    "idTauxApplicable", idTaux,
                    "idPeriodeReference", idPeriodeReference,
                    "dateEffet", request.dateEffet(),
                    "sourceProvenance", "CALCUL_PERIODE_REFERENCE"
            ));
        });
    }

    @GetMapping("/employe/{idEmploye}/export-excel")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> exportEtatCongesExcel(
            @PathVariable int idEmploye,
            @RequestParam(required = false) Integer idPeriodeReference) throws Exception {
        byte[] bytes = congeAnnuelExcelExportService.genererEtatCongesIndividuel(idEmploye, idPeriodeReference);
        String filename = congeAnnuelExcelExportService.obtenirNomFichierExport(idEmploye, idPeriodeReference);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/grand-recap/export-excel")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> exportGrandRecap(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) throws Exception {
        byte[] bytes = congeGrandRecapExcelExportService.generer(dateDebut, dateFin);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"GRAND_RECAP_CONGES_" + dateDebut + "_" + dateFin + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/{idPeriodeReference}/export-excel")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> exportEtatCongesParPeriode(
            @PathVariable int idPeriodeReference) throws Exception {
        int idEmploye = congeAnnuelExcelExportService.resoudreEmployeDePeriode(idPeriodeReference);
        byte[] bytes = congeAnnuelExcelExportService.genererEtatCongesIndividuel(idEmploye, idPeriodeReference);
        String filename = congeAnnuelExcelExportService.obtenirNomFichierExport(idEmploye, idPeriodeReference);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
