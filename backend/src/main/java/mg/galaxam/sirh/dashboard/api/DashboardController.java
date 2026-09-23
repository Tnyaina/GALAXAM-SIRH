package mg.galaxam.sirh.dashboard.api;

import mg.galaxam.sirh.dashboard.application.DashboardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * DashboardController — Endpoint d'agrégation RH pour le tableau de bord.
 * Délégué entièrement au service métier DashboardService.
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * GET /dashboard/summary
     * Retourne la synthèse globale (KPIs, alertes RH, brouillons, absences et activités récentes).
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getDashboardSummary() {
        try {
            Map<String, Object> summary = dashboardService.getDashboardSummary();
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }
}
