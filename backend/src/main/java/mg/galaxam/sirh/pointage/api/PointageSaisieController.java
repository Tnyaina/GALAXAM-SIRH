package mg.galaxam.sirh.pointage.api;

import mg.galaxam.sirh.pointage.api.dto.SaisiePointageJourDTO;
import mg.galaxam.sirh.pointage.application.PointageSaisieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pointages/saisie")
public class PointageSaisieController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PointageSaisieService pointageSaisieService;

    @GetMapping
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> getGrilleSaisie(
            @RequestParam(defaultValue = "0") int idSite,
            @RequestParam String date
    ) throws Exception {
        Map<String, Object> data = pointageSaisieService.getGrilleSaisie(idSite, date);
        return ResponseEntity.ok(data);
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('POINTAGE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> enregistrerBatch(@RequestBody List<SaisiePointageJourDTO> pointages)
            throws Exception {
        Map<String, Object> resp = new HashMap<>();
        if (pointages == null || pointages.isEmpty()) {
            resp.put("success", true);
            resp.put("message", "Aucun pointage à enregistrer.");
            return ResponseEntity.ok(resp);
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Map<String, Object> result = pointageSaisieService.enregistrerPointagesBatch(pointages, c);
                c.commit();
                return ResponseEntity.ok(result);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }
}
