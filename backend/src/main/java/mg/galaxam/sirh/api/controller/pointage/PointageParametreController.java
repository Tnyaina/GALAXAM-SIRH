package mg.galaxam.sirh.api.controller.pointage;

import java.sql.Connection;
import java.sql.Time;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.pointage.DerogationRequest;
import mg.galaxam.sirh.api.dto.pointage.ToleranceRequest;
import mg.galaxam.sirh.model.pointage.EmployeIndemniteDerogation;
import mg.galaxam.sirh.model.pointage.RefToleranceArriveePointage;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pointages/parametres")
public class PointageParametreController {

    @Autowired
    private DataSource dataSource;

    // ----------------------------------------------------
    // TOLÉRANCE D'ARRIVÉE ANTICIPÉE PAR SITE
    // ----------------------------------------------------
    @GetMapping("/tolerances")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getTolerances() {
        try (Connection c = dataSource.getConnection()) {
            List<RefToleranceArriveePointage> list = RefToleranceArriveePointage.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping("/tolerances")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> saveTolerance(@RequestBody ToleranceRequest request) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefToleranceArriveePointage t = new RefToleranceArriveePointage();
                t.setIdSite(request.getIdSite());
                
                String timeStr = request.getHeurePlafond();
                if (timeStr != null && timeStr.length() == 5) {
                    timeStr += ":00";
                }
                t.setHeurePlafond(Time.valueOf(timeStr));
                
                t.save(c);
                c.commit();

                AuditLog.audit(c, "SAVED_TOLERANCE_SITE", "ref_tolerance_arrivee_pointage", String.valueOf(t.getIdTolerance()), null, t);
                return ResponseEntity.status(HttpStatus.CREATED).body(t);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    // ----------------------------------------------------
    // DÉROGATIONS INDIVIDUELLES D'INDEMNITÉS
    // ----------------------------------------------------
    @GetMapping("/derogations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getDerogations() {
        try (Connection c = dataSource.getConnection()) {
            List<EmployeIndemniteDerogation> list = EmployeIndemniteDerogation.listAllActive(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping("/derogations")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createDerogation(@RequestBody DerogationRequest request) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                EmployeIndemniteDerogation d = new EmployeIndemniteDerogation();
                d.setIdEmploye(request.getIdEmploye());
                d.setIdIndemnite(request.getIdIndemnite());
                d.setBeneficie(Boolean.TRUE.equals(request.getBeneficie()));
                d.setMotif(request.getMotif());
                d.setDateDebut(request.getDateDebut());
                d.setDateFin(request.getDateFin());

                d.save(c);
                c.commit();

                AuditLog.audit(c, "CREATION_DEROGATION_INDEMNITE", "employe_indemnite_derogation", String.valueOf(d.getIdDerogation()), null, d);
                return ResponseEntity.status(HttpStatus.CREATED).body(d);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @PutMapping("/derogations/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateDerogation(@PathVariable int id, @RequestBody DerogationRequest request)
            throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                EmployeIndemniteDerogation d = new EmployeIndemniteDerogation();
                EmployeIndemniteDerogation existing = d.rechercherParId(id, c);
                if (existing == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Dérogation non trouvée.");
                }

                existing.setIdEmploye(request.getIdEmploye());
                existing.setIdIndemnite(request.getIdIndemnite());
                existing.setBeneficie(Boolean.TRUE.equals(request.getBeneficie()));
                existing.setMotif(request.getMotif());
                existing.setDateDebut(request.getDateDebut());
                existing.setDateFin(request.getDateFin());

                existing.update(c);
                c.commit();

                AuditLog.audit(c, "MODIFICATION_DEROGATION_INDEMNITE", "employe_indemnite_derogation", String.valueOf(id), null, existing);
                return ResponseEntity.ok(existing);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    @DeleteMapping("/derogations/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deleteDerogation(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                EmployeIndemniteDerogation d = new EmployeIndemniteDerogation();
                EmployeIndemniteDerogation existing = d.rechercherParId(id, c);
                if (existing == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Dérogation non trouvée.");
                }

                existing.delete(c);
                c.commit();

                AuditLog.audit(c, "SUPPRESSION_DEROGATION_INDEMNITE", "employe_indemnite_derogation", String.valueOf(id), existing, null);
                return ResponseEntity.ok("Dérogation supprimée avec succès.");
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }
}
