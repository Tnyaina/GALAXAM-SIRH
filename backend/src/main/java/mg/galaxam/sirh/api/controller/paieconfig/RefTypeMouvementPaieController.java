package mg.galaxam.sirh.api.controller.paieconfig;

import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.paieconfig.RefTypeMouvementPaieRequest;
import mg.galaxam.sirh.model.paieconfig.RefTypeMouvementPaie;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/referentiel/types-mouvement-paie", "/api/v1/referentiel/types-mouvement-paie"})
public class RefTypeMouvementPaieController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private mg.galaxam.sirh.model.paie.service.PayrollService payrollService;

    @GetMapping
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> listerTypesMouvement(@RequestParam(name = "tous", defaultValue = "false") boolean tous) {
        try (Connection c = dataSource.getConnection()) {
            List<RefTypeMouvementPaie> list = tous ? RefTypeMouvementPaie.listerTous(c) : RefTypeMouvementPaie.listerActifs(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getParId(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            RefTypeMouvementPaie found = RefTypeMouvementPaie.trouverParId(id, c);
            if (found == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Type de mouvement introuvable.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> creerTypeMouvement(@RequestBody RefTypeMouvementPaieRequest req) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeMouvementPaie entity = new RefTypeMouvementPaie();
                entity.setCode(req.getCode());
                entity.setLibelle(req.getLibelle());
                entity.setSens(req.getSens());
                entity.setCategorie(req.getCategorie());
                entity.setIdRubrique(req.getIdRubrique());
                entity.setEstProratise(req.isEstProratise());
                entity.setEstPlafonneDixieme(req.isEstPlafonneDixieme());
                entity.setEstActif(req.isEstActif());
                entity.setOrdreAffichage(req.getOrdreAffichage());
                entity.setDescription(req.getDescription());

                entity.save(c);
                AuditLog.audit(c, "CREATION", "ref_type_mouvement_paie", String.valueOf(entity.getIdTypeMouvement()), null, entity);

                c.commit();
                payrollService.invalidateRubriqueCache();
                return ResponseEntity.status(HttpStatus.CREATED).body(entity);
            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                        "Impossible de créer ce type de mouvement de paie.");
            }
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> modifierTypeMouvement(@PathVariable int id, @RequestBody RefTypeMouvementPaieRequest req) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeMouvementPaie found = RefTypeMouvementPaie.trouverParId(id, c);
                if (found == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Type de mouvement introuvable.");
                }

                found.setCode(req.getCode());
                found.setLibelle(req.getLibelle());
                found.setSens(req.getSens());
                found.setCategorie(req.getCategorie());
                found.setIdRubrique(req.getIdRubrique());
                found.setEstProratise(req.isEstProratise());
                found.setEstPlafonneDixieme(req.isEstPlafonneDixieme());
                found.setEstActif(req.isEstActif());
                found.setOrdreAffichage(req.getOrdreAffichage());
                found.setDescription(req.getDescription());

                found.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_type_mouvement_paie", String.valueOf(id), null, found);

                c.commit();
                payrollService.invalidateRubriqueCache();
                return ResponseEntity.ok(found);
            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                        "Impossible de modifier ce type de mouvement de paie.");
            }
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }
}
