package mg.galaxam.sirh.api.controller.conge;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.model.conge.MouvementSoldeConge;
import mg.galaxam.sirh.model.conge.VPoolCongeAnnuelConsommation;
import mg.galaxam.sirh.model.conge.VSoldeCongeEmploye;
import mg.galaxam.sirh.securite.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/conges")
public class CongeSoldeController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private mg.galaxam.sirh.cron.PurgeCongeScheduler purgeCongeScheduler;

    @Autowired
    private mg.galaxam.sirh.model.conge.service.CongeService congeService;

    @GetMapping("/planning")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getPlanning(
            @RequestParam(required = false) Integer annee,
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dateDebut,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dateFin,
            @RequestParam(required = false) Integer idSite,
            @RequestParam(required = false) Integer idDepartement,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "TOUS") String filterMode,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        try (Connection c = dataSource.getConnection()) {
            Map<String, Object> planning = congeService.getPlanningCollaborateurs(
                    annee, mois, dateDebut, dateFin, idSite, idDepartement,
                    search, filterMode, page, size, c);
            return ResponseEntity.ok(planning);
        } catch (Exception e) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.INTERNAL_ERROR);
        }
    }

    @PostMapping("/purge-expiration")
    @PreAuthorize("hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> declencherPurgeManuelle() {
        try {
            purgeCongeScheduler.purgerCongesExpires();
            return ResponseEntity.ok(Map.of("success", true, "message", "Purge triennale des congés exécutée avec succès."));
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/solde/employe/{idEmploye}")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getSoldeEmploye(@PathVariable Integer idEmploye) {
        try (Connection c = dataSource.getConnection()) {
            VSoldeCongeEmploye solde = VSoldeCongeEmploye.rechercherParEmploye(idEmploye, c);
            return ResponseEntity.ok(solde);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/mouvements/employe/{idEmploye}")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getMouvementsEmploye(@PathVariable Integer idEmploye) {
        try (Connection c = dataSource.getConnection()) {
            List<MouvementSoldeConge> list = MouvementSoldeConge.listParEmploye(idEmploye, c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/pool-consommation/employe/{idEmploye}")
    @PreAuthorize("hasAuthority('CONGE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getConsommationPool(
            @PathVariable Integer idEmploye,
            @RequestParam(value = "annee", required = false) Integer annee) {
        try (Connection c = dataSource.getConnection()) {
            List<VPoolCongeAnnuelConsommation> list = VPoolCongeAnnuelConsommation.listParEmployeEtAnnee(idEmploye, annee, c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping("/solde/ajustement")
    @PreAuthorize("hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> creerAjustementManuel(@RequestBody Map<String, Object> req) {
        if (!req.containsKey("idEmploye") || !req.containsKey("nbJours") || !req.containsKey("commentaire")) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "L'idEmploye, le nbJours et le commentaire explicatif sont obligatoires pour un ajustement.");
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Integer idEmploye = Integer.parseInt(req.get("idEmploye").toString());
                BigDecimal nbJours = new BigDecimal(req.get("nbJours").toString());
                String commentaire = req.get("commentaire").toString();

                Integer currentUserId = getCurrentUserId();

                MouvementSoldeConge mvt = new MouvementSoldeConge();
                mvt.setIdEmploye(idEmploye);
                mvt.setTypeMouvement("AJUSTEMENT");
                mvt.setNbJours(nbJours);
                mvt.setCommentaire(commentaire);
                mvt.setCreatedBy(currentUserId);
                mvt.save(c);

                c.commit();

                VSoldeCongeEmploye soldeMisAJour = VSoldeCongeEmploye.rechercherParEmploye(idEmploye, c);
                return ResponseEntity.ok(soldeMisAJour);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                    "Impossible d'enregistrer cet ajustement de solde.");
        }
    }

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails ud) {
            return ud.getIdUtilisateur();
        }
        return null;
    }
}
