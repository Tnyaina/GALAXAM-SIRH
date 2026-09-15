package mg.galaxam.sirh.api.controller.admin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.admin.MenuRequest;
import mg.galaxam.sirh.model.admin.RefMenu;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/referentiel/menus")
public class MenuController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllMenusTree() {
        try (Connection c = dataSource.getConnection()) {
            List<RefMenu> flatMenus = RefMenu.lister(c);
            List<RefMenu> tree = buildMenuTree(flatMenus);
            return ResponseEntity.ok(tree);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/flat")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllMenusFlat() {
        try (Connection c = dataSource.getConnection()) {
            List<RefMenu> flatMenus = RefMenu.lister(c);
            return ResponseEntity.ok(flatMenus);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMenuById(@PathVariable String id) {
        try (Connection c = dataSource.getConnection()) {
            RefMenu found = RefMenu.rechercherParIdMenu(id, c);
            if (found == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Menu non trouvé.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createMenu(@RequestBody MenuRequest request) {
        if (request.getIdMenu() == null || request.getIdMenu().isBlank()) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "L'identifiant du menu est obligatoire.");
        }
        if (request.getLibelle() == null || request.getLibelle().isBlank()) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Le libellé du menu est obligatoire.");
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Vérifier si l'id existe déjà
                RefMenu existing = RefMenu.rechercherParIdMenu(request.getIdMenu(), c);
                if (existing != null) {
                    c.rollback();
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.PAYROLL_PERIOD_TRANSITION_BLOCKED, "Ce code de menu existe déjà.");
                }

                RefMenu menu = new RefMenu();
                menu.setIdMenu(request.getIdMenu());
                menu.setLibelle(request.getLibelle());
                menu.setIcone(request.getIcone());
                menu.setHref(request.getHref());
                menu.setRang(request.getRang());
                menu.setNiveau(request.getNiveau());
                menu.setIdPere(request.getIdPere());

                menu.save(c);

                // Associer automatiquement le nouveau menu à ROLE_ADMIN
                String autoAssignSql = "INSERT INTO role_menu (id_role, id_menu) SELECT id_role, ? FROM ref_role WHERE code_role = 'ROLE_ADMIN' ON CONFLICT DO NOTHING";
                try (PreparedStatement psAuto = c.prepareStatement(autoAssignSql)) {
                    psAuto.setString(1, menu.getIdMenu());
                    psAuto.executeUpdate();
                }

                AuditLog.audit(c, "INSERT", "ref_menu", menu.getIdMenu(), null, menu);

                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(menu);
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
    public ResponseEntity<?> updateMenu(@PathVariable String id, @RequestBody MenuRequest request) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefMenu existing = RefMenu.rechercherParIdMenu(id, c);
                if (existing == null) {
                    c.rollback();
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Menu non trouvé.");
                }

                RefMenu oldState = new RefMenu();
                oldState.setIdMenu(existing.getIdMenu());
                oldState.setLibelle(existing.getLibelle());
                oldState.setIcone(existing.getIcone());
                oldState.setHref(existing.getHref());
                oldState.setRang(existing.getRang());
                oldState.setNiveau(existing.getNiveau());
                oldState.setIdPere(existing.getIdPere());

                if (request.getLibelle() != null && !request.getLibelle().isBlank()) {
                    existing.setLibelle(request.getLibelle());
                }
                if (request.getIcone() != null) {
                    existing.setIcone(request.getIcone());
                }
                if (request.getHref() != null) {
                    existing.setHref(request.getHref());
                }
                if (request.getRang() != null) {
                    existing.setRang(request.getRang());
                }
                if (request.getNiveau() != null) {
                    existing.setNiveau(request.getNiveau());
                }
                if (request.getIdPere() != null) {
                    existing.setIdPere(request.getIdPere().isBlank() ? null : request.getIdPere());
                }

                existing.update(c);

                AuditLog.audit(c, "UPDATE", "ref_menu", id, oldState, existing);

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

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deleteMenu(@PathVariable String id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefMenu existing = RefMenu.rechercherParIdMenu(id, c);
                if (existing == null) {
                    c.rollback();
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Menu non trouvé.");
                }

                existing.delete(c);

                AuditLog.audit(c, "DELETE", "ref_menu", id, existing, null);

                c.commit();
                return ResponseEntity.ok("Menu supprimé avec succès.");
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

    private List<RefMenu> buildMenuTree(List<RefMenu> flatMenus) {
        List<RefMenu> rootMenus = new ArrayList<>();
        Map<String, RefMenu> menuMap = new HashMap<>();

        for (RefMenu m : flatMenus) {
            menuMap.put(m.getIdMenu(), m);
        }

        for (RefMenu m : flatMenus) {
            if (m.getIdPere() == null) {
                rootMenus.add(m);
            } else {
                RefMenu parent = menuMap.get(m.getIdPere());
                if (parent != null) {
                    parent.getSousMenus().add(m);
                } else {
                    if (m.getNiveau() == 1) {
                        rootMenus.add(m);
                    }
                }
            }
        }
        return rootMenus;
    }
}
