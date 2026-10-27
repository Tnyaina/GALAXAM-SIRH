package mg.galaxam.sirh.admin.api;

import java.sql.Connection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.admin.api.dto.PermissionUpdateRequest;
import mg.galaxam.sirh.admin.api.dto.RoleMenusRequest;
import mg.galaxam.sirh.admin.api.dto.RolePermissionsRequest;
import mg.galaxam.sirh.admin.api.dto.RoleRequest;
import mg.galaxam.sirh.admin.persistence.RefRole;
import mg.galaxam.sirh.admin.persistence.RoleMenu;
import mg.galaxam.sirh.admin.persistence.RolePermission;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/referentiel/roles")
public class RoleController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllRoles() {
        try (Connection c = dataSource.getConnection()) {
            List<RefRole> roles = RefRole.lister(c);
            return ResponseEntity.ok(roles);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getRoleById(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            RefRole role = new RefRole();
            RefRole found = (RefRole) role.findById(String.valueOf(id), c);
            if (found == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Rôle non trouvé.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createRole(@RequestBody RoleRequest request) {
        if (request.getCodeRole() == null || request.getCodeRole().isBlank()) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Le code du rôle est obligatoire.");
        }
        if (request.getLibelle() == null || request.getLibelle().isBlank()) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Le libellé du rôle est obligatoire.");
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefRole role = new RefRole();
                role.setCodeRole(request.getCodeRole());
                role.setLibelle(request.getLibelle());
                role.setEstActif(true);

                role.save(c);

                AuditLog.audit(c, "INSERT", "ref_role", String.valueOf(role.getIdRole()), null, role);

                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(role);
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
    public ResponseEntity<?> updateRole(@PathVariable int id, @RequestBody RoleRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefRole role = new RefRole();
                RefRole existing = (RefRole) role.findById(String.valueOf(id), c);
                if (existing == null) {
                    c.rollback();
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Rôle non trouvé.");
                }

                // Pour l'audit
                RefRole oldState = new RefRole();
                oldState.setIdRole(existing.getIdRole());
                oldState.setCodeRole(existing.getCodeRole());
                oldState.setLibelle(existing.getLibelle());
                oldState.setEstActif(existing.isEstActif());
                oldState.setDateDesactivation(existing.getDateDesactivation());

                if (request.getCodeRole() != null && !request.getCodeRole().isBlank()) {
                    existing.setCodeRole(request.getCodeRole());
                }
                if (request.getLibelle() != null && !request.getLibelle().isBlank()) {
                    existing.setLibelle(request.getLibelle());
                }

                existing.update(c);

                AuditLog.audit(c, "UPDATE", "ref_role", String.valueOf(id), oldState, existing);

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
    public ResponseEntity<?> deactivateRole(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefRole role = new RefRole();
                RefRole existing = (RefRole) role.findById(String.valueOf(id), c);
                if (existing == null) {
                    c.rollback();
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Rôle non trouvé.");
                }

                if (!existing.isEstActif()) {
                    c.rollback();
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Le rôle est déjà désactivé.");
                }

                RefRole oldState = new RefRole();
                oldState.setIdRole(existing.getIdRole());
                oldState.setCodeRole(existing.getCodeRole());
                oldState.setLibelle(existing.getLibelle());
                oldState.setEstActif(existing.isEstActif());
                oldState.setDateDesactivation(existing.getDateDesactivation());

                existing.setEstActif(false);
                existing.setDateDesactivation(new Timestamp(System.currentTimeMillis()));

                existing.update(c);

                AuditLog.audit(c, "UPDATE", "ref_role", String.valueOf(id), oldState, existing);

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
    public ResponseEntity<?> activateRole(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefRole role = new RefRole();
                RefRole existing = (RefRole) role.findById(String.valueOf(id), c);
                if (existing == null) {
                    c.rollback();
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Rôle non trouvé.");
                }

                if (existing.isEstActif()) {
                    c.rollback();
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Le rôle est déjà actif.");
                }

                RefRole oldState = new RefRole();
                oldState.setIdRole(existing.getIdRole());
                oldState.setCodeRole(existing.getCodeRole());
                oldState.setLibelle(existing.getLibelle());
                oldState.setEstActif(existing.isEstActif());
                oldState.setDateDesactivation(existing.getDateDesactivation());

                existing.setEstActif(true);
                existing.setDateDesactivation(null);

                existing.update(c);

                AuditLog.audit(c, "UPDATE", "ref_role", String.valueOf(id), oldState, existing);

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

    @GetMapping("/{id}/permissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getRolePermissions(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            List<RolePermission> permissions = RolePermission.rechercherParRole(id, c);
            return ResponseEntity.ok(permissions);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateRolePermissions(@PathVariable int id, @RequestBody RolePermissionsRequest request) {
        if (request.getPermissions() == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "La liste des permissions est obligatoire.");
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefRole role = new RefRole();
                RefRole existing = (RefRole) role.findById(String.valueOf(id), c);
                if (existing == null) {
                    c.rollback();
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Rôle non trouvé.");
                }

                List<RolePermission> oldPermissions = RolePermission.rechercherParRole(id, c);

                RolePermission.supprimerParRole(id, c);

                List<RolePermission> newPermissions = new ArrayList<>();
                for (PermissionUpdateRequest pReq : request.getPermissions()) {
                    RolePermission rp = new RolePermission();
                    rp.setIdRole(id);
                    rp.setIdModule(pReq.getIdModule());
                    rp.setDroitLecture(pReq.isDroitLecture());
                    rp.setDroitEcriture(pReq.isDroitEcriture());
                    rp.setDroitValidation(pReq.isDroitValidation());

                    rp.controler(c);
                    rp.save(c);
                    newPermissions.add(rp);
                }

                AuditLog.audit(c, "UPDATE", "role_permission", String.valueOf(id), oldPermissions, newPermissions);

                c.commit();
                return ResponseEntity.ok(newPermissions);
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

    @GetMapping("/{id}/menus")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getRoleMenus(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            List<RoleMenu> roleMenus = RoleMenu.listerParRole(id, c);
            return ResponseEntity.ok(roleMenus);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateRoleMenus(@PathVariable int id, @RequestBody RoleMenusRequest request) {
        if (request.getIdMenus() == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "La liste des identifiants de menus est obligatoire.");
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefRole role = new RefRole();
                RefRole existing = (RefRole) role.findById(String.valueOf(id), c);
                if (existing == null) {
                    c.rollback();
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Rôle non trouvé.");
                }

                List<RoleMenu> oldMenus = RoleMenu.listerParRole(id, c);

                RoleMenu.supprimerParRole(id, c);

                List<RoleMenu> newMenus = new ArrayList<>();
                for (String menuId : request.getIdMenus()) {
                    RoleMenu rm = new RoleMenu();
                    rm.setIdRole(id);
                    rm.setIdMenu(menuId);

                    rm.save(c);
                    newMenus.add(rm);
                }

                AuditLog.audit(c, "UPDATE", "role_menu", String.valueOf(id), oldMenus, newMenus);

                c.commit();
                return ResponseEntity.ok(newMenus);
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
