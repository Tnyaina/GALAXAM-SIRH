package mg.galaxam.sirh.api.controller.contrat;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.model.referentiel.RefCategorieProfessionnelle;
import mg.galaxam.sirh.model.referentiel.RefModeCalculPaie;
import mg.galaxam.sirh.model.referentiel.RefMotifMouvement;
import mg.galaxam.sirh.model.referentiel.RefMotifSortie;
import mg.galaxam.sirh.model.referentiel.RefTypeEvenementRh;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ref-carriere")
public class RefCarriereController {

    @Autowired
    private DataSource dataSource;

    @GetMapping("/all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllReferentielsCarriere() {
        try (Connection c = dataSource.getConnection()) {
            Map<String, Object> result = new HashMap<>();
            result.put("categoriesPro", RefCategorieProfessionnelle.listAll(c, true));
            result.put("modesPaie", RefModeCalculPaie.listAll(c, true));
            result.put("motifsMouvement", RefMotifMouvement.listAll(c, true));
            result.put("motifsSortie", RefMotifSortie.listAll(c, true));
            result.put("typesEvenement", RefTypeEvenementRh.listAll(c));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des référentiels carrière.");
        }
    }

    @GetMapping("/types-evenements")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getTypesEvenementRh() {
        try (Connection c = dataSource.getConnection()) {
            List<RefTypeEvenementRh> list = RefTypeEvenementRh.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des types d'événements RH.");
        }
    }

    // Catégories Professionnelles
    @GetMapping("/categories-pro")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getCategoriesPro(@RequestParam(value = "actifUniquement", required = false, defaultValue = "false") Boolean actifUniquement) {
        try (Connection c = dataSource.getConnection()) {
            List<RefCategorieProfessionnelle> list = RefCategorieProfessionnelle.listAll(c, actifUniquement);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des catégories professionnelles.");
        }
    }

    @PostMapping("/categories-pro")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createCategoriePro(@RequestBody RefCategorieProfessionnelle cat) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                cat.save(c);
                AuditLog.audit(c, "CREATION", "ref_categorie_professionnelle", String.valueOf(cat.getIdCategorieProfessionnelle()), null, cat);
                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(cat);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la création de la catégorie professionnelle.");
        }
    }

    @PutMapping("/categories-pro/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateCategoriePro(@PathVariable int id, @RequestBody RefCategorieProfessionnelle cat) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefCategorieProfessionnelle existing = new RefCategorieProfessionnelle().rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Catégorie professionnelle non trouvée.");
                }
                existing.setCode(cat.getCode());
                existing.setLibelle(cat.getLibelle());
                existing.setDescription(cat.getDescription());
                existing.setEstEligibleHs(cat.isEstEligibleHs());
                existing.setActif(cat.isActif());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_categorie_professionnelle", String.valueOf(id), null, existing);
                c.commit();
                return ResponseEntity.ok(existing);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la modification de la catégorie professionnelle.");
        }
    }

    // Modes de Calcul Paie
    @GetMapping("/modes-paie")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getModesPaie(@RequestParam(value = "actifUniquement", required = false, defaultValue = "false") Boolean actifUniquement) {
        try (Connection c = dataSource.getConnection()) {
            List<RefModeCalculPaie> list = RefModeCalculPaie.listAll(c, actifUniquement);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des modes de calcul de paie.");
        }
    }

    // Motifs de Mouvement
    @GetMapping("/motifs-mouvement")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMotifsMouvement(@RequestParam(value = "actifUniquement", required = false, defaultValue = "false") Boolean actifUniquement) {
        try (Connection c = dataSource.getConnection()) {
            List<RefMotifMouvement> list = RefMotifMouvement.listAll(c, actifUniquement);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des motifs de mouvement.");
        }
    }

    // Motifs de Sortie
    @GetMapping("/motifs-sortie")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMotifsSortie(@RequestParam(value = "actifUniquement", required = false, defaultValue = "false") Boolean actifUniquement) {
        try (Connection c = dataSource.getConnection()) {
            List<RefMotifSortie> list = RefMotifSortie.listAll(c, actifUniquement);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des motifs de sortie.");
        }
    }
}
