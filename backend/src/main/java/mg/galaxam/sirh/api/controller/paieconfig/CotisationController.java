package mg.galaxam.sirh.api.controller.paieconfig;

import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.paieconfig.CotisationRequest;
import mg.galaxam.sirh.api.dto.paieconfig.SiteCotisationRequest;
import mg.galaxam.sirh.model.paieconfig.RefTypeCotisation;
import mg.galaxam.sirh.model.paieconfig.SiteCotisation;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/referentiel/cotisations")
public class CotisationController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAllCotisations() {
        try (Connection c = dataSource.getConnection()) {
            List<RefTypeCotisation> list = RefTypeCotisation.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des types de cotisation.");
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getCotisationById(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            RefTypeCotisation cot = new RefTypeCotisation();
            RefTypeCotisation found = (RefTypeCotisation) cot.rechercherParId(id, c);
            if (found == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Type de cotisation non trouvé.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la recherche du type de cotisation.");
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createCotisation(@RequestBody CotisationRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeCotisation cot = new RefTypeCotisation();
                cot.setCode(request.getCode());
                cot.setLibelle(request.getLibelle());
                cot.setTauxSalarial(request.getTauxSalarial());
                cot.setTauxPatronal(request.getTauxPatronal());
                cot.setDateDebut(request.getDateDebut());
                cot.setDateFin(request.getDateFin());

                cot.save(c);
                AuditLog.audit(c, "CREATION", "ref_type_cotisation", String.valueOf(cot.getIdTypeCotisation()), null, cot);

                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(cot);
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
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateCotisation(@PathVariable int id, @RequestBody CotisationRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeCotisation cot = new RefTypeCotisation();
                RefTypeCotisation existing = (RefTypeCotisation) cot.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Type de cotisation non trouvé.");
                }

                existing.setCode(request.getCode());
                existing.setLibelle(request.getLibelle());
                existing.setTauxSalarial(request.getTauxSalarial());
                existing.setTauxPatronal(request.getTauxPatronal());
                existing.setDateDebut(request.getDateDebut());
                existing.setDateFin(request.getDateFin());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_type_cotisation", String.valueOf(id), null, existing);

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
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deactivateCotisation(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeCotisation cot = new RefTypeCotisation();
                RefTypeCotisation existing = (RefTypeCotisation) cot.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Type de cotisation non trouvé.");
                }
                existing.desactiver(c);
                AuditLog.audit(c, "DESACTIVATION", "ref_type_cotisation", String.valueOf(id), null, existing);
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
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> activateCotisation(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeCotisation cot = new RefTypeCotisation();
                RefTypeCotisation existing = (RefTypeCotisation) cot.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Type de cotisation non trouvé.");
                }
                existing.activer(c);
                AuditLog.audit(c, "ACTIVATION", "ref_type_cotisation", String.valueOf(id), null, existing);
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

    // Config Cotisations par Site
    @GetMapping("/site-config")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAllSiteCotisations() {
        try (Connection c = dataSource.getConnection()) {
            List<SiteCotisation> list = SiteCotisation.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des cotisations par site.");
        }
    }

    @PostMapping("/site-config")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createSiteCotisation(@RequestBody SiteCotisationRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                SiteCotisation sc = new SiteCotisation();
                sc.setIdSite(request.getIdSite());
                sc.setIdTypeCotisation(request.getIdTypeCotisation());
                sc.setEstActif(true);

                sc.save(c);
                AuditLog.audit(c, "CREATION", "site_cotisation", String.valueOf(sc.getIdSiteCotisation()), null, sc);

                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(sc);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur système.");
        }
    }

    @PutMapping("/site-config/{id}/desactiver")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deactivateSiteCotisation(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                SiteCotisation sc = new SiteCotisation();
                SiteCotisation existing = (SiteCotisation) sc.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Association cotisation site non trouvée.");
                }

                existing.setEstActif(false);
                existing.update(c);
                AuditLog.audit(c, "DESACTIVATION", "site_cotisation", String.valueOf(id), null, existing);

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

    @PutMapping("/site-config/{id}/activer")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> activateSiteCotisation(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                SiteCotisation sc = new SiteCotisation();
                SiteCotisation existing = (SiteCotisation) sc.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Association cotisation site non trouvée.");
                }

                existing.setEstActif(true);
                existing.update(c);
                AuditLog.audit(c, "ACTIVATION", "site_cotisation", String.valueOf(id), null, existing);

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

    @DeleteMapping("/site-config/{id}")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deleteSiteCotisation(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                SiteCotisation sc = new SiteCotisation();
                SiteCotisation existing = (SiteCotisation) sc.rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Association cotisation site non trouvée.");
                }

                existing.setEstActif(false);
                existing.update(c);
                existing.delete(c);

                AuditLog.audit(c, "SUPPRESSION", "site_cotisation", String.valueOf(id), existing, null);

                c.commit();
                return ResponseEntity.ok("Association supprimée.");
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
