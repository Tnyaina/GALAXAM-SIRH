package mg.galaxam.sirh.paieconfig.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.paieconfig.api.dto.RefTypeMouvementPaieRequest;
import mg.galaxam.sirh.paieconfig.persistence.RefTypeMouvementPaie;
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
    private mg.galaxam.sirh.paie.application.PayrollService payrollService;

    @GetMapping("/catalogue")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getCatalogue() {
        try (Connection c = dataSource.getConnection()) {
            List<Map<String, Object>> types = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id_type_mouvement, code, libelle, sens, categorie, id_rubrique, " +
                    "nature, est_actif, est_proratise, est_plafonne_dixieme, saisie_manuelle_autorisee, ordre_affichage, description " +
                    "FROM ref_type_mouvement_paie ORDER BY ordre_affichage, code");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getInt("id_type_mouvement"));
                    item.put("code", rs.getString("code"));
                    item.put("libelle", rs.getString("libelle"));
                    item.put("sens", rs.getString("sens"));
                    item.put("categorie", rs.getString("categorie"));
                    item.put("idRubrique", rs.getObject("id_rubrique"));
                    item.put("nature", rs.getString("nature"));
                    item.put("estActif", rs.getBoolean("est_actif"));
                    item.put("estProratise", rs.getBoolean("est_proratise"));
                    item.put("estPlafonneDixieme", rs.getBoolean("est_plafonne_dixieme"));
                    item.put("saisieManuelleAutorisee", rs.getBoolean("saisie_manuelle_autorisee"));
                    item.put("ordreAffichage", rs.getInt("ordre_affichage"));
                    item.put("description", rs.getString("description"));
                    types.add(item);
                }
            }
            List<Map<String, Object>> rubriques = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id_rubrique, code, libelle, categorie, sens, type_valeur, soumis_irsa, " +
                    "soumis_cnaps, est_fixe, est_actif FROM paie_rubrique ORDER BY ordre_affichage, code");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getInt("id_rubrique"));
                    item.put("code", rs.getString("code"));
                    item.put("libelle", rs.getString("libelle"));
                    item.put("categorie", rs.getString("categorie"));
                    item.put("sens", rs.getString("sens"));
                    item.put("typeValeur", rs.getString("type_valeur"));
                    item.put("soumisIrsa", rs.getBoolean("soumis_irsa"));
                    item.put("soumisCnaps", rs.getBoolean("soumis_cnaps"));
                    item.put("estFixe", rs.getBoolean("est_fixe"));
                    item.put("estActif", rs.getBoolean("est_actif"));
                    rubriques.add(item);
                }
            }
            return ResponseEntity.ok(Map.of("types", types, "rubriques", rubriques));
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

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
                validateRequest(c, req, null);
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
                entity.setNature("VARIABLE_DAF");
                entity.setOwnerCode("DAF_PAIE");
                entity.setSaisieManuelleAutorisee(true);
                entity.setPeriodeObligatoire(true);
                entity.setMotifObligatoire(true);
                entity.setProvenanceDefaut("VARIABLE_DAF");

                entity.save(c);
                AuditLog.audit(c, "CREATION", "ref_type_mouvement_paie", String.valueOf(entity.getIdTypeMouvement()), null, entity);

                c.commit();
                payrollService.invalidateRubriqueCache();
                return ResponseEntity.status(HttpStatus.CREATED).body(entity);
            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                        ex instanceof IllegalArgumentException ? ex.getMessage() : "Impossible de créer ce type de mouvement de paie.");
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

                validateRequest(c, req, found);

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
                        ex instanceof IllegalArgumentException ? ex.getMessage() : "Impossible de modifier ce type de mouvement de paie.");
            }
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    private void validateRequest(Connection c, RefTypeMouvementPaieRequest req, RefTypeMouvementPaie current) throws Exception {
        if (req == null || req.getCode() == null || !req.getCode().matches("[A-Z][A-Z0-9_]{2,39}")
                || req.getLibelle() == null || req.getLibelle().isBlank()
                || req.getCategorie() == null || req.getCategorie().isBlank()
                || req.getIdRubrique() == null || !("GAIN".equals(req.getSens()) || "RETENUE".equals(req.getSens()))) {
            throw new IllegalArgumentException("Code, libellé, sens et rubrique sont obligatoires.");
        }
        if (current != null && (!current.getCode().equals(req.getCode()) || !current.getSens().equals(req.getSens()))) {
            throw new IllegalArgumentException("Le code et le sens d’un type existant ne peuvent pas être modifiés.");
        }
        if (current == null && RefTypeMouvementPaie.trouverParCode(req.getCode(), c) != null) {
            throw new IllegalArgumentException("Ce code de variable existe déjà.");
        }
        if (current == null && !List.of("PRIME", "RETENUE_DIVERSE").contains(req.getCategorie())) {
            throw new IllegalArgumentException("Un nouveau type manuel doit être une prime ou une retenue diverse.");
        }
        if (current == null && !("GAIN".equals(req.getSens()) && "PRIME".equals(req.getCategorie())
                || "RETENUE".equals(req.getSens()) && "RETENUE_DIVERSE".equals(req.getCategorie()))) {
            throw new IllegalArgumentException("La catégorie doit correspondre au sens du nouveau type.");
        }
        boolean rubriqueChanged = current == null || !Objects.equals(current.getIdRubrique(), req.getIdRubrique());
        try (PreparedStatement ps = c.prepareStatement("SELECT sens, est_actif, est_fixe, type_valeur FROM paie_rubrique WHERE id_rubrique = ?")) {
            ps.setInt(1, req.getIdRubrique());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || (!req.getSens().equals(rs.getString("sens"))
                        && !(current != null && Objects.equals(current.getIdRubrique(), req.getIdRubrique())))
                        || (rubriqueChanged && (!rs.getBoolean("est_actif") || rs.getBoolean("est_fixe")
                        || !"MONTANT".equals(rs.getString("type_valeur"))))) {
                    throw new IllegalArgumentException("La rubrique doit être active et avoir le même sens que la variable.");
                }
            }
        }
        if (current != null && rubriqueChanged) {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM paie_avance_acompte WHERE id_type_mouvement = ? LIMIT 1")) {
                ps.setInt(1, current.getIdTypeMouvement());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) throw new IllegalArgumentException("Ce type possède déjà des mouvements : sa rubrique ne peut plus être changée.");
                }
            }
        }
        if (current != null && !"VARIABLE_DAF".equals(current.getNature())
                && !Objects.equals(current.getIdRubrique(), req.getIdRubrique())) {
            throw new IllegalArgumentException("La rubrique d’un type métier dédié ne peut pas être modifiée ici.");
        }
    }
}
