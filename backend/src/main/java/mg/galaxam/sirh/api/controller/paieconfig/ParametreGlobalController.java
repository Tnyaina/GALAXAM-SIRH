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
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getAllParametresGlobaux() {
        try (Connection c = dataSource.getConnection()) {
            List<ParametreGlobal> list = ParametreGlobal.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getParametreGlobalById(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            ParametreGlobal pg = new ParametreGlobal();
            ParametreGlobal found = (ParametreGlobal) pg.rechercherParId(id, c);
            if (found == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Paramètre global non trouvé.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
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
                pg.setEstActif(request.getEstActif() != null ? request.getEstActif() : true);
                pg.setEstDesactivable(request.getEstDesactivable() != null ? request.getEstDesactivable() : false);

                pg.save(c);
                AuditLog.audit(c, "CREATION", "parametre_global", String.valueOf(pg.getIdParametre()), null, pg);

                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(pg);
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
    public ResponseEntity<?> updateParametreGlobal(@PathVariable int id, @RequestBody ParametreGlobalRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                ParametreGlobal pg = new ParametreGlobal();
                ParametreGlobal existing = (ParametreGlobal) pg.rechercherParId(id, c);
                if (existing == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Paramètre global non trouvé.");
                }

                if (request.getEstActif() != null && !request.getEstActif() && !Boolean.TRUE.equals(existing.getEstDesactivable())) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Ce paramètre est une constante système indispensable et ne peut pas être désactivé.");
                }

                existing.setCodeParametre(request.getCodeParametre());
                existing.setValeur(request.getValeur());
                existing.setDescription(request.getDescription());
                existing.setCategorie(request.getCategorie());
                existing.setDateDebut(request.getDateDebut());
                existing.setDateFin(request.getDateFin());
                if (request.getEstActif() != null) {
                    existing.setEstActif(request.getEstActif());
                }
                if (request.getEstDesactivable() != null) {
                    existing.setEstDesactivable(request.getEstDesactivable());
                }

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "parametre_global", String.valueOf(id), null, existing);

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

    @RequestMapping(value = "/{id}/toggle-actif", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN') or hasAuthority('ORGANISATION_WRITE') or hasAuthority('POINTAGE_WRITE')")
    public ResponseEntity<?> toggleActif(@PathVariable int id, @RequestBody(required = false) java.util.Map<String, Boolean> body) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                ParametreGlobal pg = new ParametreGlobal();
                ParametreGlobal existing = (ParametreGlobal) pg.rechercherParId(id, c);
                if (existing == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Paramètre global non trouvé.");
                }

                if (!Boolean.TRUE.equals(existing.getEstDesactivable())) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Ce paramètre est une constante système indispensable et ne peut pas être désactivé.");
                }

                boolean nouveauStatut;
                if (body != null && body.containsKey("estActif")) {
                    nouveauStatut = Boolean.TRUE.equals(body.get("estActif"));
                } else {
                    nouveauStatut = !Boolean.TRUE.equals(existing.getEstActif());
                }

                existing.setEstActif(nouveauStatut);
                existing.update(c);
                AuditLog.audit(c, nouveauStatut ? "ACTIVATION" : "DESACTIVATION", "parametre_global", String.valueOf(id), null, existing);

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
}
