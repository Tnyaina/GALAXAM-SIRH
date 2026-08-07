package mg.galaxam.sirh.api.controller.employe.affectation;

import mg.galaxam.sirh.employe.RefCategorieProfessionnelle;
import mg.galaxam.sirh.employe.RefModeCalculPaie;
import mg.galaxam.sirh.employe.RefMotifMouvement;
import mg.galaxam.sirh.employe.RefMotifSortie;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/ref-carriere")
public class RefCarriereController {

    @Autowired
    private DataSource dataSource;

    /**
     * Récupère l'ensemble des référentiels nécessaires à la création de contrats & affectations.
     */
    @GetMapping("/all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllReferentielsCarriere() {
        try (Connection c = dataSource.getConnection()) {
            Map<String, Object> result = new HashMap<>();
            result.put("motifsSortie", RefMotifSortie.listAll(c, true));
            result.put("motifsMouvement", RefMotifMouvement.listAll(c, true));
            result.put("modesPaie", RefModeCalculPaie.listAll(c, true));
            result.put("categoriesPro", RefCategorieProfessionnelle.listAll(c, true));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des référentiels de carrière.");
        }
    }

    // --- CATEGORIES PROFESSIONNELLES ---
    @GetMapping("/categories-pro")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getCategoriesPro(@RequestParam(required = false, defaultValue = "false") boolean actifUniquement) {
        try (Connection c = dataSource.getConnection()) {
            return ResponseEntity.ok(RefCategorieProfessionnelle.listAll(c, actifUniquement));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/categories-pro")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createCategoriePro(@RequestBody RefCategorieProfessionnelle cp) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                cp.save(c);
                AuditLog.audit(c, "CREATION", "ref_categorie_professionnelle", String.valueOf(cp.getIdCategorieProfessionnelle()), null, cp);
                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(cp);
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PutMapping("/categories-pro/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateCategoriePro(@PathVariable int id, @RequestBody RefCategorieProfessionnelle req) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefCategorieProfessionnelle existing = new RefCategorieProfessionnelle().rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Catégorie professionnelle non trouvée.");
                }
                existing.setCode(req.getCode());
                existing.setLibelle(req.getLibelle());
                existing.setDescription(req.getDescription());
                existing.setActif(req.isActif());
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- MODES CALCUL PAIE ---
    @GetMapping("/modes-paie")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getModesPaie(@RequestParam(required = false, defaultValue = "false") boolean actifUniquement) {
        try (Connection c = dataSource.getConnection()) {
            return ResponseEntity.ok(RefModeCalculPaie.listAll(c, actifUniquement));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- MOTIFS MOUVEMENT & SORTIE ---
    @GetMapping("/motifs-mouvement")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMotifsMouvement() {
        try (Connection c = dataSource.getConnection()) {
            return ResponseEntity.ok(RefMotifMouvement.listAll(c, true));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/motifs-sortie")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMotifsSortie() {
        try (Connection c = dataSource.getConnection()) {
            return ResponseEntity.ok(RefMotifSortie.listAll(c, true));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
}
