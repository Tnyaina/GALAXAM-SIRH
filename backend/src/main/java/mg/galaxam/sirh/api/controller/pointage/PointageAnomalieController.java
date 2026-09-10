package mg.galaxam.sirh.api.controller.pointage;

import mg.galaxam.sirh.model.pointage.service.PointageAnomalieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/pointages/anomalies")
public class PointageAnomalieController {

    @Autowired
    private PointageAnomalieService pointageAnomalieService;

    @GetMapping
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> getAnomalies(
            @RequestParam(required = false) Integer idSite,
            @RequestParam(required = false) Integer idPeriode,
            @RequestParam(required = false) Integer idPeriodePaie,
            @RequestParam(required = false) String datePointage,
            @RequestParam(required = false) String search
    ) {
        try {
            Integer per = (idPeriode != null && idPeriode > 0) ? idPeriode : idPeriodePaie;
            Map<String, Object> data = pointageAnomalieService.getAnomalies(
                    idSite, per, datePointage, search
            );
            return ResponseEntity.ok(data);
        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{idPointage}/regulariser")
    @PreAuthorize("hasAuthority('POINTAGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> regulariser(
            @PathVariable int idPointage,
            @RequestBody Map<String, Object> payload
    ) {
        Map<String, Object> resp = new HashMap<>();
        try {
            boolean ok = pointageAnomalieService.regulariserPointage(idPointage, payload);
            resp.put("success", ok);
            resp.put("message", ok ? "Pointage régularisé avec succès." : "Échec de la régularisation.");
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException | IllegalStateException e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        } catch (SQLException e) {
            e.printStackTrace();
            resp.put("success", false);
            resp.put("message", "Erreur base de données lors de la régularisation : " + e.getMessage());
            resp.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.put("success", false);
            resp.put("message", "Erreur inattendue : " + e.getMessage());
            resp.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(resp);
        }
    }
}
