package mg.galaxam.sirh.api.controller.organisation;

import java.sql.Connection;
import java.sql.Timestamp;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.organisation.FilialeRequest;
import mg.galaxam.sirh.model.organisation.RefFiliale;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/referentiel/filiales")
public class FilialeController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllFiliales() {
        try (Connection c = dataSource.getConnection()) {
            List<RefFiliale> list = RefFiliale.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des filiales.");
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getFilialeById(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            RefFiliale f = new RefFiliale();
            RefFiliale found = (RefFiliale) f.rechercherParId(id, c);
            if (found == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Filiale non trouvée.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la recherche de la filiale.");
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createFiliale(@RequestBody FilialeRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefFiliale f = new RefFiliale();
                f.setCodeFiliale(request.getCodeFiliale());
                f.setRaisonSociale(request.getRaisonSociale());
                f.setNif(request.getNif());
                f.setStat(request.getStat());
                if (request.getSeuilEffectifAlerte() != null) {
                    f.setSeuilEffectifAlerte(request.getSeuilEffectifAlerte());
                }

                f.save(c);
                AuditLog.audit(c, "CREATION", "ref_filiale", String.valueOf(f.getIdFiliale()), null, f);

                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(f);
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
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateFiliale(@PathVariable int id, @RequestBody FilialeRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefFiliale f = new RefFiliale();
                RefFiliale existing = (RefFiliale) f.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Filiale non trouvée.");
                }

                RefFiliale oldState = new RefFiliale();
                oldState.setIdFiliale(existing.getIdFiliale());
                oldState.setCodeFiliale(existing.getCodeFiliale());
                oldState.setRaisonSociale(existing.getRaisonSociale());

                existing.setCodeFiliale(request.getCodeFiliale());
                existing.setRaisonSociale(request.getRaisonSociale());
                existing.setNif(request.getNif());
                existing.setStat(request.getStat());
                if (request.getSeuilEffectifAlerte() != null) {
                    existing.setSeuilEffectifAlerte(request.getSeuilEffectifAlerte());
                }

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_filiale", String.valueOf(id), oldState, existing);

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
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deactivateFiliale(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefFiliale f = new RefFiliale();
                RefFiliale existing = (RefFiliale) f.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Filiale non trouvée.");
                }

                existing.setEstActif(false);
                existing.setDateDesactivation(new Timestamp(System.currentTimeMillis()));
                existing.update(c);

                AuditLog.audit(c, "DESACTIVATION", "ref_filiale", String.valueOf(id), null, existing);
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
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> activateFiliale(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefFiliale f = new RefFiliale();
                RefFiliale existing = (RefFiliale) f.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Filiale non trouvée.");
                }

                existing.setEstActif(true);
                existing.setDateDesactivation(null);
                existing.update(c);

                AuditLog.audit(c, "ACTIVATION", "ref_filiale", String.valueOf(id), null, existing);
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
