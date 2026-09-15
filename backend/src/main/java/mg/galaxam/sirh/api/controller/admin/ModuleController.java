package mg.galaxam.sirh.api.controller.admin;

import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.admin.ModuleRequest;
import mg.galaxam.sirh.model.admin.RefModule;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/referentiel/modules")
public class ModuleController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllModules() {
        try (Connection c = dataSource.getConnection()) {
            List<RefModule> modules = RefModule.lister(c);
            return ResponseEntity.ok(modules);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getModuleById(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            RefModule module = new RefModule();
            RefModule found = (RefModule) module.findById(String.valueOf(id), c);
            if (found == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Module non trouvé.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createModule(@RequestBody ModuleRequest request) {
        if (request.getCodeModule() == null || request.getCodeModule().isBlank()) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Le code du module est obligatoire.");
        }
        if (request.getLibelle() == null || request.getLibelle().isBlank()) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Le libellé du module est obligatoire.");
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefModule module = new RefModule();
                module.setCodeModule(request.getCodeModule());
                module.setLibelle(request.getLibelle());
                module.setEstActif(true);

                module.save(c);

                AuditLog.audit(c, "INSERT", "ref_module", String.valueOf(module.getIdModule()), null, module);

                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(module);
            } catch (IllegalArgumentException e) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, e.getMessage());
            } catch (Exception e) {
                c.rollback();
                e.printStackTrace();
                throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateModule(@PathVariable int id, @RequestBody ModuleRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefModule module = new RefModule();
                RefModule existing = (RefModule) module.findById(String.valueOf(id), c);
                if (existing == null) {
                    c.rollback();
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Module non trouvé.");
                }

                RefModule oldState = new RefModule();
                oldState.setIdModule(existing.getIdModule());
                oldState.setCodeModule(existing.getCodeModule());
                oldState.setLibelle(existing.getLibelle());
                oldState.setEstActif(existing.isEstActif());

                if (request.getCodeModule() != null && !request.getCodeModule().isBlank()) {
                    existing.setCodeModule(request.getCodeModule());
                }
                if (request.getLibelle() != null && !request.getLibelle().isBlank()) {
                    existing.setLibelle(request.getLibelle());
                }

                existing.update(c);

                AuditLog.audit(c, "UPDATE", "ref_module", String.valueOf(id), oldState, existing);

                c.commit();
                return ResponseEntity.ok(existing);
            } catch (IllegalArgumentException e) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, e.getMessage());
            } catch (Exception e) {
                c.rollback();
                e.printStackTrace();
                throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/{id}/desactiver")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deactivateModule(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefModule module = new RefModule();
                RefModule existing = (RefModule) module.findById(String.valueOf(id), c);
                if (existing == null) {
                    c.rollback();
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Module non trouvé.");
                }

                if (!existing.isEstActif()) {
                    c.rollback();
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Le module est déjà désactivé.");
                }

                RefModule oldState = new RefModule();
                oldState.setIdModule(existing.getIdModule());
                oldState.setCodeModule(existing.getCodeModule());
                oldState.setLibelle(existing.getLibelle());
                oldState.setEstActif(existing.isEstActif());

                existing.setEstActif(false);

                existing.update(c);

                AuditLog.audit(c, "UPDATE", "ref_module", String.valueOf(id), oldState, existing);

                c.commit();
                return ResponseEntity.ok(existing);
            } catch (Exception e) {
                c.rollback();
                e.printStackTrace();
                throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/{id}/activer")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> activateModule(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefModule module = new RefModule();
                RefModule existing = (RefModule) module.findById(String.valueOf(id), c);
                if (existing == null) {
                    c.rollback();
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Module non trouvé.");
                }

                if (existing.isEstActif()) {
                    c.rollback();
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Le module est déjà actif.");
                }

                RefModule oldState = new RefModule();
                oldState.setIdModule(existing.getIdModule());
                oldState.setCodeModule(existing.getCodeModule());
                oldState.setLibelle(existing.getLibelle());
                oldState.setEstActif(existing.isEstActif());

                existing.setEstActif(true);

                existing.update(c);

                AuditLog.audit(c, "UPDATE", "ref_module", String.valueOf(id), oldState, existing);

                c.commit();
                return ResponseEntity.ok(existing);
            } catch (Exception e) {
                c.rollback();
                e.printStackTrace();
                throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }
}
