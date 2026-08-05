package mg.galaxam.sirh.api.controller.referentiel;

import mg.galaxam.sirh.api.dto.referentiel.MenuRequest;
import mg.galaxam.sirh.referentiel.RefMenu;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération de l'arborescence des menus.");
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération de la liste à plat des menus.");
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMenuById(@PathVariable String id) {
        try (Connection c = dataSource.getConnection()) {
            RefMenu found = RefMenu.rechercherParIdMenu(id, c);
            if (found == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Menu non trouvé.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération du menu.");
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createMenu(@RequestBody MenuRequest request) {
        if (request.getIdMenu() == null || request.getIdMenu().isBlank()) {
            return ResponseEntity.badRequest().body("L'identifiant du menu est obligatoire.");
        }
        if (request.getLibelle() == null || request.getLibelle().isBlank()) {
            return ResponseEntity.badRequest().body("Le libellé du menu est obligatoire.");
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Vérifier si l'id existe déjà
                RefMenu existing = RefMenu.rechercherParIdMenu(request.getIdMenu(), c);
                if (existing != null) {
                    c.rollback();
                    return ResponseEntity.status(HttpStatus.CONFLICT).body("Ce code de menu existe déjà.");
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

                AuditLog.audit(c, "INSERT", "ref_menu", menu.getIdMenu(), null, menu);

                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(menu);
            } catch (IllegalArgumentException e) {
                c.rollback();
                return ResponseEntity.badRequest().body(e.getMessage());
            } catch (Exception e) {
                c.rollback();
                e.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Erreur lors de la création du menu.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur de connexion base de données.");
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
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Menu non trouvé.");
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
                return ResponseEntity.badRequest().body(e.getMessage());
            } catch (Exception e) {
                c.rollback();
                e.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Erreur lors de la modification du menu.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur de connexion base de données.");
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
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Menu non trouvé.");
                }

                existing.delete(c);

                AuditLog.audit(c, "DELETE", "ref_menu", id, existing, null);

                c.commit();
                return ResponseEntity.ok("Menu supprimé avec succès.");
            } catch (Exception e) {
                c.rollback();
                e.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Erreur lors de la suppression du menu.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur de connexion base de données.");
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
