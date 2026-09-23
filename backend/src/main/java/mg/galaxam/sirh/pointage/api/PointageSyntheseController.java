package mg.galaxam.sirh.pointage.api;

import mg.galaxam.sirh.pointage.api.dto.PointageSyntheseMensuelleDTO;
import mg.galaxam.sirh.pointage.application.PointageSyntheseService;
import mg.galaxam.sirh.securite.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
            @RequestParam(required = false) Integer idSite,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "ALL") String filtre,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "25") int size
    ) throws Exception {
        Map<String, Object> resp = new HashMap<>();
        if (idPeriodePaie <= 0) {
            resp.put("synthese", java.util.Collections.emptyList());
            resp.put("totalElements", 0);
            resp.put("totalPages", 0);
            resp.put("currentPage", 1);
            resp.put("pageSize", size);
            return ResponseEntity.ok(resp);
        }
        if (page <= 0 && size <= 0) {
            // Mode non-paginé pour compatibilité ascendante si demandé
            List<PointageSyntheseMensuelleDTO> data = pointageSyntheseService.getSynthesePeriode(idPeriodePaie, idSite);
            resp.put("synthese", data);
            resp.put("totalElements", data.size());
            resp.put("totalPages", 1);
            resp.put("currentPage", 1);
            resp.put("pageSize", data.size());
            return ResponseEntity.ok(resp);
        }
        Map<String, Object> data = pointageSyntheseService.getSynthesePeriodePaginee(
                idPeriodePaie, idSite, search, filtre, page, size);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/semaines")
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> getSemaines(
            @RequestParam(defaultValue = "0") int idPeriodePaie,
            @RequestParam(required = false) Integer idSite
    ) throws Exception {
        Map<String, Object> resp = new HashMap<>();
        if (idPeriodePaie <= 0) {
            resp.put("semaines", java.util.Collections.emptyList());
            return ResponseEntity.ok(resp);
        }
        List<Map<String, Object>> data = pointageSyntheseService.getSemainesPeriode(idPeriodePaie, idSite);
        resp.put("semaines", data);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/semaine-detail")
    @PreAuthorize("hasAuthority('POINTAGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> getSemaineDetail(
            @RequestParam(defaultValue = "0") int idPeriodePaie,
            @RequestParam String dateDebut,
            @RequestParam String dateFin,
            @RequestParam(required = false) Integer idSite
    ) throws Exception {
        Map<String, Object> resp = new HashMap<>();
        if (idPeriodePaie <= 0 || dateDebut == null || dateFin == null) {
            resp.put("detail", java.util.Collections.emptyList());
            return ResponseEntity.ok(resp);
        }
        java.time.LocalDate dDebut = java.time.LocalDate.parse(dateDebut);
        java.time.LocalDate dFin = java.time.LocalDate.parse(dateFin);
        List<PointageSyntheseMensuelleDTO> data = pointageSyntheseService.getSyntheseSemaineDetail(idPeriodePaie, dDebut, dFin, idSite);
        resp.put("detail", data);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/semaines/valider")
    @PreAuthorize("hasAuthority('POINTAGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> validerSemaine(
            @RequestBody Map<String, Object> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails
    ) throws Exception {
        Map<String, Object> resp = new HashMap<>();
        Integer idPeriode = parseInteger(body.get("idPeriodePaie"));
        if (idPeriode == null || idPeriode <= 0) {
            throw new IllegalArgumentException("L'identifiant de la période de paie est obligatoire.");
        }
        String dateDebutStr = (String) body.get("dateDebut");
        String dateFinStr = (String) body.get("dateFin");
        Integer idSite = parseInteger(body.get("idSite"));
        if (idSite != null && idSite <= 0) idSite = null;

        java.time.LocalDate dDebut = java.time.LocalDate.parse(dateDebutStr);
        java.time.LocalDate dFin = java.time.LocalDate.parse(dateFinStr);

        Integer currentUserId = resolveCurrentUserId();
        pointageSyntheseService.validerSemaine(idPeriode, dDebut, dFin, idSite, currentUserId);
        resp.put("success", true);
        resp.put("message", "Semaine validée et verrouillée avec succès.");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/semaines/devalider")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> devaliderSemaine(
            @RequestBody Map<String, Object> body
    ) throws Exception {
        Map<String, Object> resp = new HashMap<>();
        Integer idPeriode = parseInteger(body.get("idPeriodePaie"));
        if (idPeriode == null || idPeriode <= 0) {
            throw new IllegalArgumentException("L'identifiant de la période de paie est obligatoire.");
        }
        String dateDebutStr = (String) body.get("dateDebut");
        java.time.LocalDate dDebut = java.time.LocalDate.parse(dateDebutStr);

        pointageSyntheseService.devaliderSemaine(idPeriode, dDebut);
        resp.put("success", true);
        resp.put("message", "Semaine déverrouillée avec succès.");
        return ResponseEntity.ok(resp);
    }

    /**
     * Calcule et actualise toutes les semaines de la periode en 1 action transactionnelle.
     * La periode reste EN_COURS pour consultation et corrections par l'agent.
     */
    @PostMapping("/calculer-tout")
    @PreAuthorize("hasAuthority('POINTAGE_WRITE') or hasAuthority('POINTAGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> calculerTout(
            @RequestBody Map<String, Object> body
    ) throws Exception {
        Integer idPeriode = parseInteger(body.get("idPeriodePaie"));
        if (idPeriode == null || idPeriode <= 0) {
            throw new IllegalArgumentException("L'identifiant de la période de paie est obligatoire.");
        }
        Integer idSite = parseInteger(body.get("idSite"));
        if (idSite != null && idSite <= 0) idSite = null;

        Integer currentUserId = resolveCurrentUserId();
        Map<String, Object> res = pointageSyntheseService.calculerToutesSemainesPeriode(idPeriode, idSite, currentUserId);
        return ResponseEntity.ok(res);
    }

    private Integer parseInteger(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) return null;
            try {
                return Integer.parseInt(trimmed);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Integer resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails ud) {
            return ud.getIdUtilisateur();
        }
        return null;
    }
}
