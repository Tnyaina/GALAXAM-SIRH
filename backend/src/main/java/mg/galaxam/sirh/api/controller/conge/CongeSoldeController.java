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

    @PostMapping("/purge-expiration")
    @PreAuthorize("hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> declencherPurgeManuelle() {
        try {
            purgeCongeScheduler.purgerCongesExpires();
            return ResponseEntity.ok(Map.of("success", true, "message", "Purge triennale des congés exécutée avec succès."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la purge : " + e.getMessage());
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération du solde de congé : " + e.getMessage());
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des mouvements de solde : " + e.getMessage());
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération de la consommation du pool : " + e.getMessage());
        }
    }

    @PostMapping("/solde/ajustement")
    @PreAuthorize("hasAuthority('CONGE_VALIDATE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> creerAjustementManuel(@RequestBody Map<String, Object> req) {
        if (!req.containsKey("idEmploye") || !req.containsKey("nbJours") || !req.containsKey("commentaire")) {
            return ResponseEntity.badRequest().body("L'idEmploye, le nbJours et le commentaire explicatif sont obligatoires pour un ajustement.");
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
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Erreur lors de l'enregistrement de l'ajustement de solde : " + e.getMessage());
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
