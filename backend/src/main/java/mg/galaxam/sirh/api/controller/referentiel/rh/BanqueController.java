package mg.galaxam.sirh.api.controller.referentiel.rh;

import mg.galaxam.sirh.api.dto.referentiel.rh.BanqueRequest;
import mg.galaxam.sirh.referentiel.rh.RefBanque;
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
@RequestMapping("/referentiel/banques")
public class BanqueController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllBanques() {
        try (Connection c = dataSource.getConnection()) {
            List<RefBanque> list = RefBanque.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des banques.");
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getBanqueById(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            RefBanque b = new RefBanque();
            RefBanque found = (RefBanque) b.rechercherParId(id, c);
            if (found == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Banque non trouvée.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la recherche de la banque.");
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createBanque(@RequestBody BanqueRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefBanque b = new RefBanque();
                b.setCodeBanque(request.getCodeBanque());
                b.setNomBanque(request.getNomBanque());
                b.setCodeSwift(request.getCodeSwift());

                b.save(c);
                AuditLog.audit(c, "CREATION", "ref_banque", String.valueOf(b.getIdBanque()), null, b);

                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(b);
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
    public ResponseEntity<?> updateBanque(@PathVariable int id, @RequestBody BanqueRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefBanque b = new RefBanque();
                RefBanque existing = (RefBanque) b.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Banque non trouvée.");
                }

                existing.setCodeBanque(request.getCodeBanque());
                existing.setNomBanque(request.getNomBanque());
                existing.setCodeSwift(request.getCodeSwift());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_banque", String.valueOf(id), null, existing);

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
    public ResponseEntity<?> deactivateBanque(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefBanque b = new RefBanque();
                RefBanque existing = (RefBanque) b.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Banque non trouvée.");
                }

                existing.setEstActif(false);
                existing.update(c);

                AuditLog.audit(c, "DESACTIVATION", "ref_banque", String.valueOf(id), null, existing);
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
    public ResponseEntity<?> activateBanque(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefBanque b = new RefBanque();
                RefBanque existing = (RefBanque) b.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Banque non trouvée.");
                }

                existing.setEstActif(true);
                existing.update(c);

                AuditLog.audit(c, "ACTIVATION", "ref_banque", String.valueOf(id), null, existing);
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
