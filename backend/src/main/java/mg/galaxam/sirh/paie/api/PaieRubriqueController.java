package mg.galaxam.sirh.paie.api;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.paie.api.dto.PaieRubriqueRequest;
import mg.galaxam.sirh.paie.persistence.PaieRubrique;
import mg.galaxam.sirh.paie.application.PayrollService;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/paie/rubriques", "/api/v1/paie/rubriques"})
public class PaieRubriqueController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PayrollService payrollService;

    @GetMapping
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> listerRubriques(@RequestParam(name = "tous", defaultValue = "false") boolean tous) {
        try (Connection c = dataSource.getConnection()) {
            List<PaieRubrique> list = tous ? PaieRubrique.listerTous(c) : PaieRubrique.listerActifs(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getParId(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            PaieRubrique found = PaieRubrique.trouverParId(id, c);
            if (found == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Rubrique introuvable.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> creerRubrique(@RequestBody PaieRubriqueRequest req) {
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Le code de la rubrique est obligatoire.");
        }
        if (req.getLibelle() == null || req.getLibelle().isBlank()) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Le libellé de la rubrique est obligatoire.");
        }
        if (req.getCategorie() == null || req.getCategorie().isBlank()) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "La catégorie de la rubrique est obligatoire.");
        }
        if (req.getSens() == null || req.getSens().isBlank()) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, "Le sens de la rubrique (GAIN / RETENUE) est obligatoire.");
        }
        if (req.getSoumisCnaps() == null || req.getSoumisSante() == null || req.getSoumisIrsa() == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.BLOCKING_CONFIGURATION_ERROR,
                    "Les assujettissements CNaPS, santé et IRSA doivent être renseignés explicitement.");
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                PaieRubrique existing = PaieRubrique.trouverParCode(req.getCode().trim().toUpperCase(), c);
                if (existing != null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                            mg.galaxam.sirh.common.exception.SirhErrorCode.DATA_DUPLICATE,
                            "Une rubrique avec le code '" + req.getCode() + "' existe déjà.");
                }

                PaieRubrique entity = new PaieRubrique();
                entity.setCode(req.getCode().trim().toUpperCase());
                entity.setLibelle(req.getLibelle().trim());
                entity.setCategorie(req.getCategorie().trim().toUpperCase());
                entity.setTypeValeur(req.getTypeValeur() != null ? req.getTypeValeur() : "MONTANT");
                entity.setSens(req.getSens().trim().toUpperCase());
                entity.setSoumisCnaps(req.getSoumisCnaps());
                entity.setSoumisSante(req.getSoumisSante());
                entity.setSoumisIrsa(req.getSoumisIrsa());
                entity.setEstFixe(req.getEstFixe() != null ? req.getEstFixe() : false);
                entity.setOrdreAffichage(req.getOrdreAffichage() != null ? req.getOrdreAffichage() : 10);
                entity.setEstActif(req.getEstActif() != null ? req.getEstActif() : true);
                entity.setDescription(req.getDescription());

                entity.save(c);

                AuditLog.audit(c, "CREATION", "paie_rubrique",
                        String.valueOf(entity.getIdRubrique()), null, entity);

                c.commit();

                // Invalidation immédiate du cache en mémoire de PayrollService
                payrollService.invalidateRubriqueCache();

                return ResponseEntity.status(HttpStatus.CREATED).body(entity);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> modifierRubrique(@PathVariable int id, @RequestBody PaieRubriqueRequest req) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                PaieRubrique found = PaieRubrique.trouverParId(id, c);
                if (found == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Rubrique introuvable.");
                }

                if (req.getCode() != null && !req.getCode().isBlank()) {
                    found.setCode(req.getCode().trim().toUpperCase());
                }
                if (req.getLibelle() != null && !req.getLibelle().isBlank()) {
                    found.setLibelle(req.getLibelle().trim());
                }
                if (req.getCategorie() != null) {
                    found.setCategorie(req.getCategorie().trim().toUpperCase());
                }
                if (req.getTypeValeur() != null) {
                    found.setTypeValeur(req.getTypeValeur());
                }
                if (req.getSens() != null) {
                    found.setSens(req.getSens().trim().toUpperCase());
                }
                if (req.getSoumisCnaps() != null) {
                    found.setSoumisCnaps(req.getSoumisCnaps());
                }
                if (req.getSoumisSante() != null) {
                    found.setSoumisSante(req.getSoumisSante());
                }
                if (req.getSoumisIrsa() != null) {
                    found.setSoumisIrsa(req.getSoumisIrsa());
                }
                if (req.getEstFixe() != null) {
                    found.setEstFixe(req.getEstFixe());
                }
                if (req.getOrdreAffichage() != null) {
                    found.setOrdreAffichage(req.getOrdreAffichage());
                }
                if (req.getEstActif() != null) {
                    found.setEstActif(req.getEstActif());
                }
                if (req.getDescription() != null) {
                    found.setDescription(req.getDescription());
                }

                found.update(c);

                AuditLog.audit(c, "MODIFICATION", "paie_rubrique",
                        String.valueOf(id), null, found);

                c.commit();

                // Invalidation immédiate du cache en mémoire de PayrollService
                payrollService.invalidateRubriqueCache();

                return ResponseEntity.ok(found);
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> supprimerRubrique(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                PaieRubrique found = PaieRubrique.trouverParId(id, c);
                if (found == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Rubrique introuvable.");
                }

                // Vérification de l'intégrité référentielle comptable avant suppression physique
                boolean estUtilisee = false;
                String[] checkTables = {"bulletin_paie_ligne", "paie_mouvement_rubrique", "ref_type_mouvement_paie"};
                for (String table : checkTables) {
                    try (PreparedStatement psCheck = c.prepareStatement("SELECT 1 FROM " + table + " WHERE id_rubrique = ? LIMIT 1")) {
                        psCheck.setInt(1, id);
                        try (ResultSet rsCheck = psCheck.executeQuery()) {
                            if (rsCheck.next()) {
                                estUtilisee = true;
                                break;
                            }
                        }
                    } catch (Exception ignored) {
                        // table non présente ou sans colonne id_rubrique
                    }
                }

                if (estUtilisee) {
                    // Soft delete : Désactivation logique pour garantir la reproductibilité des bulletins passés
                    found.setEstActif(false);
                    found.update(c);
                    AuditLog.audit(c, "DESACTIVATION_LOGIQUE", "paie_rubrique",
                            String.valueOf(id), null, found);
                    c.commit();
                    payrollService.invalidateRubriqueCache();
                    return ResponseEntity.ok("La rubrique est déjà associée à des bulletins ou mouvements historiques. Elle a été désactivée avec succès pour préserver l'historique comptable.");
                } else {
                    found.delete(c);
                    AuditLog.audit(c, "SUPPRESSION", "paie_rubrique",
                            String.valueOf(id), found, null);
                    c.commit();
                    payrollService.invalidateRubriqueCache();
                    return ResponseEntity.ok("Rubrique supprimée avec succès.");
                }
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }
}
