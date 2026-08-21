package mg.galaxam.sirh.api.controller.organisation;

import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.organisation.FonctionRequest;
import mg.galaxam.sirh.model.organisation.RefFonction;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/referentiel/fonctions")
public class FonctionController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllFonctions() {
        try (Connection c = dataSource.getConnection()) {
            List<RefFonction> list = RefFonction.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des fonctions.");
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getFonctionById(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            RefFonction f = new RefFonction();
            RefFonction found = (RefFonction) f.rechercherParId(id, c);
            if (found == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Fonction non trouvée.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la recherche de la fonction.");
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createFonction(@RequestBody FonctionRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefFonction f = new RefFonction();
                f.setCodeFonction(request.getCodeFonction());
                f.setLibelle(request.getLibelle());
                f.setDispensePointage(request.isDispensePointage());

                f.save(c);
                AuditLog.audit(c, "CREATION", "ref_fonction", String.valueOf(f.getIdFonction()), null, f);

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
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateFonction(@PathVariable int id, @RequestBody FonctionRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefFonction f = new RefFonction();
                RefFonction existing = (RefFonction) f.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Fonction non trouvée.");
                }

                existing.setCodeFonction(request.getCodeFonction());
                existing.setLibelle(request.getLibelle());
                existing.setDispensePointage(request.isDispensePointage());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_fonction", String.valueOf(id), null, existing);

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
    public ResponseEntity<?> deactivateFonction(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefFonction f = new RefFonction();
                RefFonction existing = (RefFonction) f.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Fonction non trouvée.");
                }

                existing.setEstActif(false);
                existing.update(c);

                AuditLog.audit(c, "DESACTIVATION", "ref_fonction", String.valueOf(id), null, existing);
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
    public ResponseEntity<?> activateFonction(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefFonction f = new RefFonction();
                RefFonction existing = (RefFonction) f.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Fonction non trouvée.");
                }

                existing.setEstActif(true);
                existing.update(c);

                AuditLog.audit(c, "ACTIVATION", "ref_fonction", String.valueOf(id), null, existing);
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
