package mg.galaxam.sirh.api.controller.paieconfig;

import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.paieconfig.ParametreGlobalRequest;
import mg.galaxam.sirh.model.paieconfig.ParametreGlobal;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/referentiel/parametres-globaux")
public class ParametreGlobalController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllParametresGlobaux() {
        try (Connection c = dataSource.getConnection()) {
            List<ParametreGlobal> list = ParametreGlobal.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des paramètres globaux.");
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getParametreGlobalById(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            ParametreGlobal pg = new ParametreGlobal();
            ParametreGlobal found = (ParametreGlobal) pg.rechercherParId(id, c);
            if (found == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Paramètre global non trouvé.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la recherche du paramètre global.");
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createParametreGlobal(@RequestBody ParametreGlobalRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (request.getCodeParametre() != null && request.getDateDebut() != null) {
                    ParametreGlobal.cloturerPrecedent(request.getCodeParametre(), request.getDateDebut(), c);
                }

                ParametreGlobal pg = new ParametreGlobal();
                pg.setCodeParametre(request.getCodeParametre());
                pg.setValeur(request.getValeur());
                pg.setDescription(request.getDescription());
                pg.setCategorie(request.getCategorie());
                pg.setDateDebut(request.getDateDebut());
                pg.setDateFin(request.getDateFin());

                pg.save(c);
                AuditLog.audit(c, "CREATION", "parametre_global", String.valueOf(pg.getIdParametre()), null, pg);

                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(pg);
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
    public ResponseEntity<?> updateParametreGlobal(@PathVariable int id, @RequestBody ParametreGlobalRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                ParametreGlobal pg = new ParametreGlobal();
                ParametreGlobal existing = (ParametreGlobal) pg.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Paramètre global non trouvé.");
                }

                existing.setCodeParametre(request.getCodeParametre());
                existing.setValeur(request.getValeur());
                existing.setDescription(request.getDescription());
                existing.setCategorie(request.getCategorie());
                existing.setDateDebut(request.getDateDebut());
                existing.setDateFin(request.getDateFin());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "parametre_global", String.valueOf(id), null, existing);

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
