package mg.galaxam.sirh.api.controller.employe;

import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.model.referentiel.RefTypeDocument;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ref-types-documents")
public class RefTypeDocumentController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllTypesDocument(@RequestParam(value = "actifUniquement", required = false, defaultValue = "false") Boolean actifUniquement) {
        try (Connection c = dataSource.getConnection()) {
            List<RefTypeDocument> list = RefTypeDocument.listAll(c, actifUniquement);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des types de document.");
        }
    }

    @PostMapping
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
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la création du type de document.");
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> updateTypeDocument(@PathVariable int id, @RequestBody RefTypeDocument typeDoc) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeDocument existing = new RefTypeDocument().rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Type de document non trouvé.");
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
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la modification du type de document.");
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> deleteTypeDocument(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeDocument existing = new RefTypeDocument().rechercherParId(id, c);
                if (existing == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Type de document non trouvé.");
                }
                existing.delete(c);
                AuditLog.audit(c, "SUPPRESSION", "ref_type_document", String.valueOf(id), existing, null);
                c.commit();
                return ResponseEntity.ok("Type de document désactivé avec succès.");
            } catch (Exception ex) {
                c.rollback();
                return ResponseEntity.badRequest().body(ex.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la suppression du type de document.");
        }
    }
}
