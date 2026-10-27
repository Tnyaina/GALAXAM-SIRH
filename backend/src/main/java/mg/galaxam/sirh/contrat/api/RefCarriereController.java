package mg.galaxam.sirh.contrat.api;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import mg.galaxam.sirh.contrat.persistence.RefCategorieProfessionnelle;
import mg.galaxam.sirh.contrat.persistence.RefModeCalculPaie;
import mg.galaxam.sirh.contrat.persistence.RefMotifMouvement;
import mg.galaxam.sirh.contrat.persistence.RefMotifSortie;
import mg.galaxam.sirh.contrat.persistence.RefTypeDocument;
import mg.galaxam.sirh.contrat.persistence.RefTypeEvenementRh;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller unifié pour tous les référentiels et nomenclatures RH :
 * - Carrière : catégories pro, modes de paie, motifs mouvement, motifs sortie, types d'événements
 * - Documents : types de pièces justificatives et documents légaux RH
 */
@RestController
public class RefCarriereController {

    @Autowired
    private DataSource dataSource;

    // =========================================================================
    // RÉFÉRENTIELS DE CARRIÈRE & CONTRAT (/ref-carriere)
    // =========================================================================

    @GetMapping("/ref-carriere/all")
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
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/ref-carriere/types-evenements")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getTypesEvenementRh() {
        try (Connection c = dataSource.getConnection()) {
            List<RefTypeEvenementRh> list = RefTypeEvenementRh.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    // Catégories Professionnelles
    @GetMapping("/ref-carriere/categories-pro")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getCategoriesPro(@RequestParam(value = "actifUniquement", required = false, defaultValue = "false") Boolean actifUniquement) {
        try (Connection c = dataSource.getConnection()) {
            List<RefCategorieProfessionnelle> list = RefCategorieProfessionnelle.listAll(c, actifUniquement);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping("/ref-carriere/categories-pro")
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
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/ref-carriere/categories-pro/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateCategoriePro(@PathVariable int id, @RequestBody RefCategorieProfessionnelle cat) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefCategorieProfessionnelle existing = new RefCategorieProfessionnelle().rechercherParId(id, c);
                if (existing == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Catégorie professionnelle non trouvée.");
                }
                existing.setCode(cat.getCode());
                existing.setLibelle(cat.getLibelle());
                existing.setDescription(cat.getDescription());
                existing.setEstEligibleHs(cat.isEstEligibleHs());
                existing.setEstCadre(cat.isEstCadre());
                existing.setEstEligibleExonerationHs(cat.isEstEligibleExonerationHs());
                existing.setActif(cat.isActif());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_categorie_professionnelle", String.valueOf(id), null, existing);
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

    // Modes de Calcul Paie
    @GetMapping("/ref-carriere/modes-paie")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getModesPaie(@RequestParam(value = "actifUniquement", required = false, defaultValue = "false") Boolean actifUniquement) {
        try (Connection c = dataSource.getConnection()) {
            List<RefModeCalculPaie> list = RefModeCalculPaie.listAll(c, actifUniquement);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    // Motifs de Mouvement
    @GetMapping("/ref-carriere/motifs-mouvement")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMotifsMouvement(@RequestParam(value = "actifUniquement", required = false, defaultValue = "false") Boolean actifUniquement) {
        try (Connection c = dataSource.getConnection()) {
            List<RefMotifMouvement> list = RefMotifMouvement.listAll(c, actifUniquement);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    // Motifs de Sortie
    @GetMapping("/ref-carriere/motifs-sortie")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMotifsSortie(@RequestParam(value = "actifUniquement", required = false, defaultValue = "false") Boolean actifUniquement) {
        try (Connection c = dataSource.getConnection()) {
            List<RefMotifSortie> list = RefMotifSortie.listAll(c, actifUniquement);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    // =========================================================================
    // RÉFÉRENTIELS DES TYPES DE DOCUMENTS (/ref-types-documents)
    // =========================================================================

    @GetMapping("/ref-types-documents")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllTypesDocument(@RequestParam(value = "actifUniquement", required = false, defaultValue = "false") Boolean actifUniquement) {
        try (Connection c = dataSource.getConnection()) {
            List<RefTypeDocument> list = RefTypeDocument.listAll(c, actifUniquement);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping("/ref-types-documents")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> createTypeDocument(@RequestBody RefTypeDocument typeDoc) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                typeDoc.save(c);
                AuditLog.audit(c, "CREATION", "ref_type_document", String.valueOf(typeDoc.getIdTypeDocument()), null, typeDoc);
                c.commit();
                return ResponseEntity.status(HttpStatus.CREATED).body(typeDoc);
            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/ref-types-documents/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateTypeDocument(@PathVariable int id, @RequestBody RefTypeDocument typeDoc) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeDocument existing = new RefTypeDocument().rechercherParId(id, c);
                if (existing == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Type de document non trouvé.");
                }
                existing.setLibelle(typeDoc.getLibelle());
                existing.setCategorie(typeDoc.getCategorie());
                existing.setDescription(typeDoc.getDescription());
                existing.setObligatoire(typeDoc.isObligatoire());
                existing.setExtensionAutorisee(typeDoc.getExtensionAutorisee());
                existing.setTailleMaxMo(typeDoc.getTailleMaxMo());
                existing.setActif(typeDoc.isActif());

                existing.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_type_document", String.valueOf(id), null, existing);
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

    @DeleteMapping("/ref-types-documents/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deleteTypeDocument(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeDocument existing = new RefTypeDocument().rechercherParId(id, c);
                if (existing == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Type de document non trouvé.");
                }
                existing.delete(c);
                AuditLog.audit(c, "SUPPRESSION", "ref_type_document", String.valueOf(id), existing, null);
                c.commit();
                return ResponseEntity.ok("Type de document désactivé avec succès.");
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
