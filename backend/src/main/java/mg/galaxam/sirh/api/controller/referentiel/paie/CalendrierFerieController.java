package mg.galaxam.sirh.api.controller.referentiel.paie;

import mg.galaxam.sirh.api.dto.referentiel.paie.CalendrierFerieRequest;
import mg.galaxam.sirh.api.dto.referentiel.paie.SiteCalendrierFerieRequest;
import mg.galaxam.sirh.referentiel.paie.RefCalendrierFerie;
import mg.galaxam.sirh.referentiel.paie.SiteCalendrierFerie;
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
@RequestMapping("/referentiel/calendrier-ferie")
public class CalendrierFerieController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllCalendrierFerie() {
        try (Connection c = dataSource.getConnection()) {
            List<RefCalendrierFerie> list = RefCalendrierFerie.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération du calendrier des jours fériés.");
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getCalendrierFerieById(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            RefCalendrierFerie cf = new RefCalendrierFerie();
            RefCalendrierFerie found = (RefCalendrierFerie) cf.rechercherParId(id, c);
            if (found == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Jour férié non trouvé.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la recherche du jour férié.");
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createCalendrierFerie(@RequestBody CalendrierFerieRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefCalendrierFerie cf = new RefCalendrierFerie();
                cf.setDateFerie(request.getDateFerie());
                cf.setLibelle(request.getLibelle());
                cf.setEstChome(request.isEstChome());
                cf.setEstNational(request.isEstNational());

                cf.save(c);
                AuditLog.audit(c, "CREATION", "ref_calendrier_ferie", String.valueOf(cf.getIdFerie()), null, cf);

                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(cf);
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
    public ResponseEntity<?> updateCalendrierFerie(@PathVariable int id, @RequestBody CalendrierFerieRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefCalendrierFerie cf = new RefCalendrierFerie();
                RefCalendrierFerie existing = (RefCalendrierFerie) cf.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Jour férié non trouvé.");
                }

                existing.setDateFerie(request.getDateFerie());
                existing.setLibelle(request.getLibelle());
                existing.setEstChome(request.isEstChome());
                existing.setEstNational(request.isEstNational());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_calendrier_ferie", String.valueOf(id), null, existing);

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

    // Config Fériés par Site
    @GetMapping("/site-config")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllSiteFeries() {
        try (Connection c = dataSource.getConnection()) {
            List<SiteCalendrierFerie> list = SiteCalendrierFerie.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des jours fériés par site.");
        }
    }

    @PostMapping("/site-config")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createSiteFerie(@RequestBody SiteCalendrierFerieRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                SiteCalendrierFerie scf = new SiteCalendrierFerie();
                scf.setIdSite(request.getIdSite());
                scf.setIdFerie(request.getIdFerie());

                scf.save(c);
                AuditLog.audit(c, "CREATION", "site_calendrier_ferie", String.valueOf(scf.getIdSiteFerie()), null, scf);

                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(scf);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur système.");
        }
    }

    @DeleteMapping("/site-config/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deleteSiteFerie(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                SiteCalendrierFerie scf = new SiteCalendrierFerie();
                SiteCalendrierFerie existing = (SiteCalendrierFerie) scf.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Affectation de jour férié non trouvée.");
                }

                existing.delete(c);
                AuditLog.audit(c, "SUPPRESSION", "site_calendrier_ferie", String.valueOf(id), existing, null);

                c.commit();
                return ResponseEntity.ok("Affectation supprimée.");
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
