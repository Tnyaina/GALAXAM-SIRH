package mg.galaxam.sirh.paieconfig.api;

import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.paieconfig.api.dto.CotisationRequest;
import mg.galaxam.sirh.paieconfig.api.dto.SiteCotisationRequest;
import mg.galaxam.sirh.paieconfig.persistence.RefTypeCotisation;
import mg.galaxam.sirh.paieconfig.persistence.SiteCotisation;
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
    public ResponseEntity<?> getAllCotisations(
            @RequestParam(value = "at", required = false) String at) {
        try (Connection c = dataSource.getConnection()) {
            List<RefTypeCotisation> list;
            if (at != null && !at.isBlank()) {
                // Résolution temporelle : taux en vigueur à la date donnée.
                // Utiliser la date de début de la période de paie pour les déclarations historiques.
                java.sql.Date refDate = java.sql.Date.valueOf(at);
                list = RefTypeCotisation.listActiveAt(refDate, c);
            } else {
                list = RefTypeCotisation.listAll(c);
            }
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getCotisationById(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            RefTypeCotisation cot = new RefTypeCotisation();
            RefTypeCotisation found = (RefTypeCotisation) cot.rechercherParId(id, c);
            if (found == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Type de cotisation non trouvé.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
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
                cot.setPortee(request.getPortee() != null ? request.getPortee() : "SITE");
                cot.setCategorie(request.getCategorie() != null ? request.getCategorie() : "SANTE");
                cot.setIdOrganisme(request.getIdOrganisme());

                cot.save(c);
                AuditLog.audit(c, "CREATION", "ref_type_cotisation", String.valueOf(cot.getIdTypeCotisation()), null, cot);

                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(cot);
            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
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
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Type de cotisation non trouvé.");
                }

                existing.setCode(request.getCode());
                existing.setLibelle(request.getLibelle());
                existing.setTauxSalarial(request.getTauxSalarial());
                existing.setTauxPatronal(request.getTauxPatronal());
                existing.setDateDebut(request.getDateDebut());
                existing.setDateFin(request.getDateFin());
                if (request.getPortee() != null) existing.setPortee(request.getPortee());
                if (request.getCategorie() != null) existing.setCategorie(request.getCategorie());
                existing.setIdOrganisme(request.getIdOrganisme());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_type_cotisation", String.valueOf(id), null, existing);

                c.commit();
                return ResponseEntity.ok(existing);
            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
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
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Type de cotisation non trouvé.");
                }
                existing.desactiver(c);
                AuditLog.audit(c, "DESACTIVATION", "ref_type_cotisation", String.valueOf(id), null, existing);
                c.commit();
                return ResponseEntity.ok(existing);
            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
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
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Type de cotisation non trouvé.");
                }
                existing.activer(c);
                AuditLog.audit(c, "ACTIVATION", "ref_type_cotisation", String.valueOf(id), null, existing);
                c.commit();
                return ResponseEntity.ok(existing);
            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
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
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
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
                sc.setIdOrganisme(request.getIdOrganisme());
                sc.setEstActif(true);

                sc.save(c);
                AuditLog.audit(c, "CREATION", "site_cotisation", String.valueOf(sc.getIdSiteCotisation()), null, sc);

                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(sc);
            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
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
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Association cotisation site non trouvée.");
                }

                existing.setEstActif(false);
                existing.update(c);
                AuditLog.audit(c, "DESACTIVATION", "site_cotisation", String.valueOf(id), null, existing);

                c.commit();
                return ResponseEntity.ok(existing);
            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
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
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Association cotisation site non trouvée.");
                }

                existing.setEstActif(true);
                existing.update(c);
                AuditLog.audit(c, "ACTIVATION", "site_cotisation", String.valueOf(id), null, existing);

                c.commit();
                return ResponseEntity.ok(existing);
            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
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
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Association cotisation site non trouvée.");
                }

                existing.setEstActif(false);
                existing.update(c);
                existing.delete(c);

                AuditLog.audit(c, "SUPPRESSION", "site_cotisation", String.valueOf(id), existing, null);

                c.commit();
                return ResponseEntity.ok("Association supprimée.");
            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }
}
