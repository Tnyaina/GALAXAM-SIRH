package mg.galaxam.sirh.organisation.api;

import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.organisation.persistence.RefTypeContrat;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/referentiel/types-contrat")
public class TypeContratController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllTypesContrat() {
        try (Connection c = dataSource.getConnection()) {
            List<RefTypeContrat> list = RefTypeContrat.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createTypeContrat(@RequestBody RefTypeContrat request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                request.save(c);
                AuditLog.audit(c, "CREATION", "ref_type_contrat", String.valueOf(request.getIdTypeContrat()), null, request);
                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(request);
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
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateTypeContrat(@PathVariable int id, @RequestBody RefTypeContrat request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeContrat existing = (RefTypeContrat) new RefTypeContrat().rechercherParId(id, c);
                if (existing == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Type de contrat non trouvé.");
                }

                existing.setCode(request.getCode());
                existing.setLibelle(request.getLibelle());
                existing.setExigeDateFin(request.isExigeDateFin());
                existing.setEstActif(request.isEstActif());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_type_contrat", String.valueOf(id), null, existing);

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
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deactivateTypeContrat(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeContrat existing = (RefTypeContrat) new RefTypeContrat().rechercherParId(id, c);
                if (existing == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Type de contrat non trouvé.");
                }

                existing.setEstActif(false);
                existing.update(c);

                AuditLog.audit(c, "DESACTIVATION", "ref_type_contrat", String.valueOf(id), null, existing);
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
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> activateTypeContrat(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeContrat existing = (RefTypeContrat) new RefTypeContrat().rechercherParId(id, c);
                if (existing == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Type de contrat non trouvé.");
                }

                existing.setEstActif(true);
                existing.update(c);

                AuditLog.audit(c, "ACTIVATION", "ref_type_contrat", String.valueOf(id), null, existing);
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
