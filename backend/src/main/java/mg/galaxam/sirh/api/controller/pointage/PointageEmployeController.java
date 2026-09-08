package mg.galaxam.sirh.api.controller.pointage;

import mg.galaxam.sirh.api.dto.pointage.FichePointageEmployeDTO;
import mg.galaxam.sirh.model.pointage.service.PointageEmployeService;
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

    public PointageEmployeController(PointageEmployeService pointageEmployeService) {
        this.pointageEmployeService = pointageEmployeService;
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
}
