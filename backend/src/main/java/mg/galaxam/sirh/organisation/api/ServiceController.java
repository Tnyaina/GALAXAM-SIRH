package mg.galaxam.sirh.organisation.api;

import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.organisation.api.dto.ServiceRequest;
import mg.galaxam.sirh.organisation.persistence.RefService;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/referentiel/services")
public class ServiceController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllServices(@RequestParam(required = false) Integer idDepartement) {
        try (Connection c = dataSource.getConnection()) {
            List<RefService> list;
            if (idDepartement != null) {
                list = RefService.listByDepartement(idDepartement, c);
            } else {
                list = RefService.listAll(c);
            }
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getServiceById(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            RefService s = new RefService();
            RefService found = (RefService) s.rechercherParId(id, c);
            if (found == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Service non trouvé.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createService(@RequestBody ServiceRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefService s = new RefService();
                s.setCodeService(request.getCodeService());
                s.setLibelle(request.getLibelle());
                s.setIdDepartement(request.getIdDepartement());

                s.save(c);
                AuditLog.audit(c, "CREATION", "ref_service", String.valueOf(s.getIdService()), null, s);

                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(s);
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
    public ResponseEntity<?> updateService(@PathVariable int id, @RequestBody ServiceRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefService s = new RefService();
                RefService existing = (RefService) s.rechercherParId(id, c);
                if (existing == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Service non trouvé.");
                }

                existing.setCodeService(request.getCodeService());
                existing.setLibelle(request.getLibelle());
                existing.setIdDepartement(request.getIdDepartement());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_service", String.valueOf(id), null, existing);

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
    public ResponseEntity<?> deactivateService(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefService s = new RefService();
                RefService existing = (RefService) s.rechercherParId(id, c);
                if (existing == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Service non trouvé.");
                }

                existing.setEstActif(false);
                existing.update(c);

                AuditLog.audit(c, "DESACTIVATION", "ref_service", String.valueOf(id), null, existing);
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
    public ResponseEntity<?> activateService(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefService s = new RefService();
                RefService existing = (RefService) s.rechercherParId(id, c);
                if (existing == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Service non trouvé.");
                }

                existing.setEstActif(true);
                existing.update(c);

                AuditLog.audit(c, "ACTIVATION", "ref_service", String.valueOf(id), null, existing);
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
