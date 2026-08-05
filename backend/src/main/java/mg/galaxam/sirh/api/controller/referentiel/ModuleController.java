package mg.galaxam.sirh.api.controller.referentiel;

import mg.galaxam.sirh.api.dto.referentiel.ModuleRequest;
import mg.galaxam.sirh.referentiel.RefModule;
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des modules.");
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getModuleById(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            RefModule module = new RefModule();
            RefModule found = (RefModule) module.findById(String.valueOf(id), c);
            if (found == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Module non trouvé.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération du module.");
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createModule(@RequestBody ModuleRequest request) {
        if (request.getCodeModule() == null || request.getCodeModule().isBlank()) {
            return ResponseEntity.badRequest().body("Le code du module est obligatoire.");
        }
        if (request.getLibelle() == null || request.getLibelle().isBlank()) {
            return ResponseEntity.badRequest().body("Le libellé du module est obligatoire.");
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
                return ResponseEntity.badRequest().body(e.getMessage());
            } catch (Exception e) {
                c.rollback();
                e.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Erreur lors de la création du module.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur de connexion base de données.");
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
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Module non trouvé.");
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
                return ResponseEntity.badRequest().body(e.getMessage());
            } catch (Exception e) {
                c.rollback();
                e.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Erreur lors de la modification du module.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur de connexion base de données.");
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
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Module non trouvé.");
                }

                if (!existing.isEstActif()) {
                    c.rollback();
                    return ResponseEntity.badRequest().body("Le module est déjà désactivé.");
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
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Erreur lors de la désactivation du module.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur de connexion base de données.");
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
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Module non trouvé.");
                }

                if (existing.isEstActif()) {
                    c.rollback();
                    return ResponseEntity.badRequest().body("Le module est déjà actif.");
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
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Erreur lors de l'activation du module.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur de connexion base de données.");
        }
    }
}
