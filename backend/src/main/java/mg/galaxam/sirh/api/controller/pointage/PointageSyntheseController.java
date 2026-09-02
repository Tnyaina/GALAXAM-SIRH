package mg.galaxam.sirh.api.controller.pointage;

import mg.galaxam.sirh.api.dto.pointage.PointageSyntheseMensuelleDTO;
import mg.galaxam.sirh.model.pointage.service.PointageSyntheseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pointages/synthese")
public class PointageSyntheseController {

    @Autowired
    private PointageSyntheseService pointageSyntheseService;

    @GetMapping
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> getSynthese(
            @RequestParam(defaultValue = "0") int idPeriodePaie,
            @RequestParam(required = false) Integer idSite
    ) {
        Map<String, Object> resp = new HashMap<>();
        if (idPeriodePaie <= 0) {
            resp.put("synthese", java.util.Collections.emptyList());
            return ResponseEntity.ok(resp);
        }
        try {
            List<PointageSyntheseMensuelleDTO> data = pointageSyntheseService.getSynthesePeriode(idPeriodePaie, idSite);
            resp.put("synthese", data);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.put("error", "Erreur lors du calcul de la synthèse : " + e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }
    }

    @GetMapping("/semaines")
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> getSemaines(
            @RequestParam(defaultValue = "0") int idPeriodePaie,
            @RequestParam(required = false) Integer idSite
    ) {
        Map<String, Object> resp = new HashMap<>();
        if (idPeriodePaie <= 0) {
            resp.put("semaines", java.util.Collections.emptyList());
            return ResponseEntity.ok(resp);
        }
        try {
            List<Map<String, Object>> data = pointageSyntheseService.getSemainesPeriode(idPeriodePaie, idSite);
            resp.put("semaines", data);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.put("error", "Erreur lors du découpage des semaines : " + e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }
    }

    @GetMapping("/semaine-detail")
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> getSemaineDetail(
            @RequestParam(defaultValue = "0") int idPeriodePaie,
            @RequestParam String dateDebut,
            @RequestParam String dateFin,
            @RequestParam(required = false) Integer idSite
    ) {
        Map<String, Object> resp = new HashMap<>();
        if (idPeriodePaie <= 0 || dateDebut == null || dateFin == null) {
            resp.put("detail", java.util.Collections.emptyList());
            return ResponseEntity.ok(resp);
        }
        try {
            java.time.LocalDate dDebut = java.time.LocalDate.parse(dateDebut);
            java.time.LocalDate dFin = java.time.LocalDate.parse(dateFin);
            List<PointageSyntheseMensuelleDTO> data = pointageSyntheseService.getSyntheseSemaineDetail(idPeriodePaie, dDebut, dFin, idSite);
            resp.put("detail", data);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.put("error", "Erreur lors du calcul du détail de la semaine : " + e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }
    }

    @PostMapping("/semaines/valider")
    @PreAuthorize("hasAuthority('POINTAGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> validerSemaine(
            @RequestBody Map<String, Object> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails
    ) {
        Map<String, Object> resp = new HashMap<>();
        try {
            int idPeriode = ((Number) body.get("idPeriodePaie")).intValue();
            String dateDebutStr = (String) body.get("dateDebut");
            String dateFinStr = (String) body.get("dateFin");
            Integer idSite = body.get("idSite") != null ? ((Number) body.get("idSite")).intValue() : null;
            if (idSite != null && idSite <= 0) idSite = null;

            java.time.LocalDate dDebut = java.time.LocalDate.parse(dateDebutStr);
            java.time.LocalDate dFin = java.time.LocalDate.parse(dateFinStr);

            pointageSyntheseService.validerSemaine(idPeriode, dDebut, dFin, idSite, null);
            resp.put("success", true);
            resp.put("message", "Semaine validée et verrouillée avec succès.");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.put("error", "Erreur lors de la validation de la semaine : " + e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }
    }

    @PostMapping("/semaines/devalider")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> devaliderSemaine(
            @RequestBody Map<String, Object> body
    ) {
        Map<String, Object> resp = new HashMap<>();
        try {
            int idPeriode = ((Number) body.get("idPeriodePaie")).intValue();
            String dateDebutStr = (String) body.get("dateDebut");
            java.time.LocalDate dDebut = java.time.LocalDate.parse(dateDebutStr);

            pointageSyntheseService.devaliderSemaine(idPeriode, dDebut);
            resp.put("success", true);
            resp.put("message", "Semaine déverrouillée avec succès.");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.put("error", "Erreur lors du déverrouillage de la semaine : " + e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }
    }
}
