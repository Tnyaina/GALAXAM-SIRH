package mg.galaxam.sirh.api.controller.pointage;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.pointage.PointageSyntheseMensuelleDTO;
import mg.galaxam.sirh.api.dto.pointage.PointageTronconSemaineDTO;
import mg.galaxam.sirh.service.pointage.PointageSyntheseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pointages/synthese")
public class PointageSyntheseController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PointageSyntheseService pointageSyntheseService;

    /**
     * Récupère la synthèse mensuelle des pointages par employé pour une période donnée.
     * Accessible par ROLE_ADMIN et ROLE_AGENT_POINTAGE.
     */
    @GetMapping("/mensuelle")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_AGENT_POINTAGE')")
    public ResponseEntity<?> getSyntheseMensuelle(
            @RequestParam int idPeriodePaie,
            @RequestParam(required = false) Integer idSite,
            @RequestParam(required = false) String search) {
        try (Connection c = dataSource.getConnection()) {
            List<PointageSyntheseMensuelleDTO> list = pointageSyntheseService.getSyntheseMensuelle(idPeriodePaie, idSite, search, c);
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors du calcul de la synthèse mensuelle : " + e.getMessage());
        }
    }

    /**
     * Récupère la synthèse hebdomadaire (vue globale par semaine S1..S5) pour tous les employés d'une période.
     */
    @GetMapping("/hebdomadaire")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_AGENT_POINTAGE')")
    public ResponseEntity<?> getSyntheseHebdomadaire(
            @RequestParam int idPeriodePaie,
            @RequestParam(required = false) Integer numeroSemaine,
            @RequestParam(required = false) Integer idSite,
            @RequestParam(required = false) String search) {
        try (Connection c = dataSource.getConnection()) {
            List<PointageTronconSemaineDTO> list = pointageSyntheseService.getSyntheseHebdomadaire(idPeriodePaie, numeroSemaine, idSite, search, c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors du calcul de la synthèse hebdomadaire : " + e.getMessage());
        }
    }

    /**
     * Récupère le détail des tronçons hebdomadaires (avec calcul de seuil proratisé et compensation) pour un employé.
     */
    @GetMapping("/employe/{idEmploye}/semaines")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_AGENT_POINTAGE')")
    public ResponseEntity<?> getTronconsSemainesEmploye(
            @PathVariable int idEmploye,
            @RequestParam int idPeriodePaie) {
        try (Connection c = dataSource.getConnection()) {
            List<PointageTronconSemaineDTO> troncons = pointageSyntheseService.getTronconsSemainesEmploye(idEmploye, idPeriodePaie, c);
            return ResponseEntity.ok(troncons);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des tronçons de la semaine : " + e.getMessage());
        }
    }

    /**
     * Effectue une vérification préalable avant clôture/validation de la période (état des anomalies restantes).
     */
    @GetMapping("/pre-cloture-check")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_AGENT_POINTAGE')")
    public ResponseEntity<?> getPreClotureCheck(@RequestParam int idPeriodePaie) {
        try (Connection c = dataSource.getConnection()) {
            Map<String, Object> check = pointageSyntheseService.verifierPreCloture(idPeriodePaie, c);
            return ResponseEntity.ok(check);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors du contrôle de pré-clôture : " + e.getMessage());
        }
    }
}
