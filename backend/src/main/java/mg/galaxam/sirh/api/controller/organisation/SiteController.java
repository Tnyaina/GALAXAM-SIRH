package mg.galaxam.sirh.api.controller.organisation;

import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.organisation.SiteRequest;
import mg.galaxam.sirh.model.organisation.RefSite;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/referentiel/sites")
public class SiteController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllSites() {
        try (Connection c = dataSource.getConnection()) {
            List<RefSite> list = RefSite.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des sites.");
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getSiteById(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            RefSite s = new RefSite();
            RefSite found = (RefSite) s.rechercherParId(id, c);
            if (found == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Site non trouvé.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la recherche du site.");
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createSite(@RequestBody SiteRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefSite s = new RefSite();
                s.setCodeSite(request.getCodeSite());
                s.setLibelle(request.getLibelle());
                s.setIdFilialeRattachement(request.getIdFilialeRattachement());
                s.setHoraireContinu(request.isHoraireContinu());
                s.setHeureDebutMatin(request.getHeureDebutMatin());
                s.setHeureFinMatin(request.getHeureFinMatin());
                s.setHeureDebutApresMidi(request.getHeureDebutApresMidi());
                s.setHeureFinApresMidi(request.getHeureFinApresMidi());

                s.save(c);
                AuditLog.audit(c, "CREATION", "ref_site", String.valueOf(s.getIdSite()), null, s);

                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(s);
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
    public ResponseEntity<?> updateSite(@PathVariable int id, @RequestBody SiteRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefSite s = new RefSite();
                RefSite existing = (RefSite) s.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Site non trouvé.");
                }

                existing.setCodeSite(request.getCodeSite());
                existing.setLibelle(request.getLibelle());
                existing.setIdFilialeRattachement(request.getIdFilialeRattachement());
                existing.setHoraireContinu(request.isHoraireContinu());
                existing.setHeureDebutMatin(request.getHeureDebutMatin());
                existing.setHeureFinMatin(request.getHeureFinMatin());
                existing.setHeureDebutApresMidi(request.getHeureDebutApresMidi());
                existing.setHeureFinApresMidi(request.getHeureFinApresMidi());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_site", String.valueOf(id), null, existing);

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
    public ResponseEntity<?> deactivateSite(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefSite s = new RefSite();
                RefSite existing = (RefSite) s.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Site non trouvé.");
                }

                existing.setEstActif(false);
                existing.update(c);

                AuditLog.audit(c, "DESACTIVATION", "ref_site", String.valueOf(id), null, existing);
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
    public ResponseEntity<?> activateSite(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefSite s = new RefSite();
                RefSite existing = (RefSite) s.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Site non trouvé.");
                }

                existing.setEstActif(true);
                existing.update(c);

                AuditLog.audit(c, "ACTIVATION", "ref_site", String.valueOf(id), null, existing);
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
