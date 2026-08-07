package mg.galaxam.sirh.api.controller.employe;

import mg.galaxam.sirh.employe.EmployeAffectationCarriere;
import mg.galaxam.sirh.employe.EmployeAffectationCarriereCPL;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
public class EmployeAffectationCarriereController {

    @Autowired
    private DataSource dataSource;

    /**
     * Historique des affectations de carrière d'un employé.
     */
    @GetMapping("/employes/{idEmploye}/affectations")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAffectationsByEmploye(@PathVariable int idEmploye) {
        try (Connection c = dataSource.getConnection()) {
            List<EmployeAffectationCarriereCPL> list = EmployeAffectationCarriereCPL.listByEmployeId(idEmploye, c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des affectations de carrière.");
        }
    }

    /**
     * Affectation & poste courant d'un employé.
     */
    @GetMapping("/employes/{idEmploye}/affectation-courante")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAffectationCourante(@PathVariable int idEmploye) {
        try (Connection c = dataSource.getConnection()) {
            EmployeAffectationCarriereCPL aff = EmployeAffectationCarriereCPL.getAffectationCourante(idEmploye, c);
            return ResponseEntity.ok(aff);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération de l'affectation courante.");
        }
    }

    /**
     * Création d'une nouvelle affectation de carrière (Promotion, Mutation, Avenant).
     * Clôture automatiquement l'ancienne affectation active la veille du début de la nouvelle.
     */
    @PostMapping("/employes/{idEmploye}/affectations")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createAffectation(@PathVariable int idEmploye, @RequestBody EmployeAffectationCarriere req) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                req.setIdEmploye(idEmploye);

                // Clôturer l'affectation courante s'il en existe une
                if (req.getDateDebut() != null) {
                    LocalDate veille = req.getDateDebut().toLocalDate().minusDays(1);
                    EmployeAffectationCarriere.cloturerAffectationCourante(idEmploye, Date.valueOf(veille), c);
                }

                req.save(c);
                AuditLog.audit(c, "CREATION", "employe_affectation_carriere", String.valueOf(req.getIdAffectation()), null, req);
                c.commit();

                EmployeAffectationCarriereCPL createdView = new EmployeAffectationCarriereCPL().rechercherParId(req.getIdAffectation(), c);
                return ResponseEntity.status(HttpStatus.CREATED).body(createdView);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la création de l'affectation de carrière.");
        }
    }

    /**
     * Modification d'une affectation de carrière.
     */
    @PutMapping("/affectations-carriere/{idAffectation}")
    @PreAuthorize("hasAuthority('EMPLOYE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateAffectation(@PathVariable int idAffectation, @RequestBody EmployeAffectationCarriere req) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                EmployeAffectationCarriere existing = new EmployeAffectationCarriere().rechercherParId(idAffectation, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Affectation de carrière non trouvée.");
                }

                existing.setIdMotifMouvement(req.getIdMotifMouvement());
                existing.setIdSite(req.getIdSite());
                existing.setIdDepartement(req.getIdDepartement());
                existing.setIdService(req.getIdService());
                existing.setIdFonction(req.getIdFonction());
                existing.setIdResponsableHierarchique(req.getIdResponsableHierarchique());
                existing.setIdCategorieProfessionnelle(req.getIdCategorieProfessionnelle());
                existing.setIdModeCalculPaie(req.getIdModeCalculPaie());
                existing.setSalaireBase(req.getSalaireBase());
                existing.setDateDebut(req.getDateDebut());
                existing.setDateFin(req.getDateFin());
                existing.setCommentaire(req.getCommentaire());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "employe_affectation_carriere", String.valueOf(idAffectation), null, existing);
                c.commit();

                EmployeAffectationCarriereCPL updatedView = new EmployeAffectationCarriereCPL().rechercherParId(idAffectation, c);
                return ResponseEntity.ok(updatedView);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la modification de l'affectation.");
        }
    }
}
