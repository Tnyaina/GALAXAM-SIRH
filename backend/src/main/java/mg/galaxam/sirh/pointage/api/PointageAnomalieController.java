package mg.galaxam.sirh.pointage.api;

import mg.galaxam.sirh.pointage.application.PointageAnomalieService;
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
    ) throws SQLException {
        Integer per = (idPeriode != null && idPeriode > 0) ? idPeriode : idPeriodePaie;
        Map<String, Object> data = pointageAnomalieService.getAnomalies(idSite, per, datePointage, search);
        return ResponseEntity.ok(data);
    }

    @PutMapping("/{idPointage}/regulariser")
    @PreAuthorize("hasAuthority('POINTAGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> regulariser(
            @PathVariable int idPointage,
            @RequestBody Map<String, Object> payload
    ) throws Exception {
        Map<String, Object> resp = new HashMap<>();
        boolean ok = pointageAnomalieService.regulariserPointage(idPointage, payload);
        resp.put("success", ok);
        resp.put("message", ok ? "Pointage régularisé avec succès." : "Échec de la régularisation.");
        return ResponseEntity.ok(resp);
    }
}
