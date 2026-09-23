package mg.galaxam.sirh.pointage.api;

import mg.galaxam.sirh.pointage.api.dto.FichePointageEmployeDTO;
import mg.galaxam.sirh.pointage.application.PointageEmployeService;
import mg.galaxam.sirh.pointage.application.PointageExcelExportService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Contrôleur REST pour la consultation de la fiche de pointage d'un employé.
 * Délègue l'intégralité du traitement métier à PointageEmployeService.
 */
@RestController
@RequestMapping("/pointages")
public class PointageEmployeController {

    private final PointageEmployeService pointageEmployeService;
    private final mg.galaxam.sirh.pointage.application.PointageExcelExportService pointageExcelExportService;

    public PointageEmployeController(PointageEmployeService pointageEmployeService,
                                   mg.galaxam.sirh.pointage.application.PointageExcelExportService pointageExcelExportService) {
        this.pointageEmployeService = pointageEmployeService;
        this.pointageExcelExportService = pointageExcelExportService;
    }

    @GetMapping("/employes/{idEmploye}")
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<FichePointageEmployeDTO> getFichePointageEmploye(
            @PathVariable Integer idEmploye,
            @RequestParam(required = false) String date,
            @RequestParam(required = false, defaultValue = "periode") String period,
            @RequestParam(required = false) Integer idPeriode
    ) {
        FichePointageEmployeDTO result = pointageEmployeService.getFichePointageEmploye(idEmploye, date, period, idPeriode);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/employes/{idEmploye}/export/excel")
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<byte[]> exportExcelFicheEmploye(
            @PathVariable Integer idEmploye,
            @RequestParam(required = false) String date,
            @RequestParam(required = false, defaultValue = "periode") String period,
            @RequestParam(required = false) Integer idPeriode
    ) throws java.io.IOException {
        FichePointageEmployeDTO fiche = pointageEmployeService.getFichePointageEmploye(idEmploye, date, period, idPeriode);
        byte[] excelBytes = pointageExcelExportService.genererExcelFicheEmploye(fiche);
        String filename = PointageExcelExportService.buildExportFilename(fiche, "xlsx");

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }
}
