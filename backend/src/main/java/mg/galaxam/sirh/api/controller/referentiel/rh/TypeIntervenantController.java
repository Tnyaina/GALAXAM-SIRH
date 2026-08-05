package mg.galaxam.sirh.api.controller.referentiel.rh;

import mg.galaxam.sirh.api.dto.referentiel.rh.TypeIntervenantRequest;
import mg.galaxam.sirh.referentiel.rh.RefTypeIntervenant;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

@RestController
@RequestMapping("/referentiel/types-intervenant")
public class TypeIntervenantController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllTypesIntervenant() {
        try (Connection c = dataSource.getConnection()) {
            List<RefTypeIntervenant> list = RefTypeIntervenant.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des types d'intervenant.");
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getTypeIntervenantById(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            RefTypeIntervenant ti = new RefTypeIntervenant();
            RefTypeIntervenant found = (RefTypeIntervenant) ti.rechercherParId(id, c);
            if (found == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Type d'intervenant non trouvé.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la recherche du type d'intervenant.");
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createTypeIntervenant(@RequestBody TypeIntervenantRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeIntervenant ti = new RefTypeIntervenant();
                ti.setCode(request.getCode());
                ti.setLibelle(request.getLibelle());
                ti.setSoumisIrsa(request.isSoumisIrsa());
                ti.setSoumisIsi5(request.isSoumisIsi5());
                ti.setSoumisCnaps(request.isSoumisCnaps());
                ti.setSoumisSante(request.isSoumisSante());

                ti.save(c);
                AuditLog.audit(c, "CREATION", "ref_type_intervenant", String.valueOf(ti.getIdTypeIntervenant()), null, ti);

                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(ti);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur système.");
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateTypeIntervenant(@PathVariable int id, @RequestBody TypeIntervenantRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeIntervenant ti = new RefTypeIntervenant();
                RefTypeIntervenant existing = (RefTypeIntervenant) ti.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Type d'intervenant non trouvé.");
                }

                existing.setCode(request.getCode());
                existing.setLibelle(request.getLibelle());
                existing.setSoumisIrsa(request.isSoumisIrsa());
                existing.setSoumisIsi5(request.isSoumisIsi5());
                existing.setSoumisCnaps(request.isSoumisCnaps());
                existing.setSoumisSante(request.isSoumisSante());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_type_intervenant", String.valueOf(id), null, existing);

                c.commit();
                return ResponseEntity.ok(existing);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur système.");
        }
    }

    @PutMapping("/{id}/desactiver")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deactivateTypeIntervenant(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeIntervenant ti = new RefTypeIntervenant();
                RefTypeIntervenant existing = (RefTypeIntervenant) ti.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Type d'intervenant non trouvé.");
                }

                existing.setEstActif(false);
                existing.update(c);

                AuditLog.audit(c, "DESACTIVATION", "ref_type_intervenant", String.valueOf(id), null, existing);
                c.commit();
                return ResponseEntity.ok(existing);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur système.");
        }
    }

    @PutMapping("/{id}/activer")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> activateTypeIntervenant(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeIntervenant ti = new RefTypeIntervenant();
                RefTypeIntervenant existing = (RefTypeIntervenant) ti.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Type d'intervenant non trouvé.");
                }

                existing.setEstActif(true);
                existing.update(c);

                AuditLog.audit(c, "ACTIVATION", "ref_type_intervenant", String.valueOf(id), null, existing);
                c.commit();
                return ResponseEntity.ok(existing);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur système.");
        }
    }
}
