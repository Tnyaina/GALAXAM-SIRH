package mg.galaxam.sirh.paie.api;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Objects;
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
@RequestMapping({"/paie/rubriques", "/v1/paie/rubriques"})
public class PaieRubriqueController {

    private static final java.util.Set<String> MANUAL_CATEGORIES = java.util.Set.of(
            "PRIME", "INDEMNITE_IMPOSABLE", "INDEMNITE_NON_IMPOSABLE", "RETENUE_DIVERSE");

    private static void invalid(String message) {
        throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR, message);
    }

    private static PaieRubrique fromRequest(PaieRubriqueRequest req) {
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
        entity.setReferenceReglementaire(req.getReferenceReglementaire());
        entity.setDateDebutEffet(req.getDateDebutEffet());
        entity.setEtapeCalcul(req.getEtapeCalcul());
        entity.setMethodeCalcul(req.getMethodeCalcul()); entity.setPolitiqueArrondi(req.getPolitiqueArrondi());
        entity.setSoumisFmfp(req.getSoumisFmfp());
        entity.setDateFinEffet(req.getDateFinEffet());
        entity.setConditionApplication(req.getConditionApplication());
        return entity;
    }

    static void validateManualRubrique(PaieRubrique rubrique, boolean requireEvidence) {
        if (rubrique.isEstFixe() || !MANUAL_CATEGORIES.contains(rubrique.getCategorie())
                || !"MONTANT".equals(rubrique.getTypeValeur())
                || !("GAIN".equals(rubrique.getSens()) || "RETENUE".equals(rubrique.getSens()))) {
            invalid("Seules les rubriques manuelles de gain ou de retenue en montant peuvent être créées ici.");
        }
        if (!"CONFIGURABLE".equals(mg.galaxam.sirh.paie.domain.engine.PayrollCalculatorRegistry.describe(
                rubrique.getCode(),rubrique.getCategorie(),rubrique.isEstFixe(),rubrique.getEtapeCalcul()).niveau()))
            invalid("Cette rubrique appartient à un calculateur dédié. Utilisez son paramétrage métier.");
        if (rubrique.getDateFinEffet() != null && (rubrique.getDateDebutEffet() == null
                || rubrique.getDateFinEffet().isBefore(rubrique.getDateDebutEffet()))) {
            invalid("La fin de validité doit suivre le début de validité.");
        }
        if (!java.util.Set.of("TOUS", "ASSUJETTI_IRSA", "ASSUJETTI_CNAPS", "ASSUJETTI_SANTE")
                .contains(rubrique.getConditionApplication() == null ? "" : rubrique.getConditionApplication())) {
            invalid("Condition d’application non prise en charge.");
        }
        if (!java.util.Set.of("MONTANT","QUANTITE_TAUX","POURCENTAGE_SALAIRE_BASE").contains(rubrique.getMethodeCalcul()==null?"":rubrique.getMethodeCalcul())
                || !java.util.Set.of("CENTIME","ARIARY","DIZAINE").contains(rubrique.getPolitiqueArrondi()==null?"":rubrique.getPolitiqueArrondi()))
            invalid("Méthode ou politique d’arrondi non prise en charge.");
        if (!"BRUT".equals(rubrique.getEtapeCalcul()) && Boolean.TRUE.equals(rubrique.getSoumisFmfp()))
            invalid("Un ajout ou une retenue nette ne peut pas alimenter le FMFP.");
        if (rubrique.getSoumisFmfp() != null
                && rubrique.getSoumisFmfp() != rubrique.isSoumisCnaps())
            invalid("L'assiette FMFP doit suivre l'assiette sociale CNaPS pour une rubrique manuelle.");
        if (requireEvidence && (rubrique.getReferenceReglementaire() == null || rubrique.getReferenceReglementaire().isBlank()
                || rubrique.getDateDebutEffet() == null)) {
            invalid("La référence du traitement et sa date de début d'effet sont obligatoires.");
        }
        if ("GAIN".equals(rubrique.getSens())
                && !("BRUT".equals(rubrique.getEtapeCalcul()) || "GAIN_NET".equals(rubrique.getEtapeCalcul()))) {
            invalid("Choisissez l'étape du gain : brut ou ajout au net après impôt.");
        }
        if ("GAIN_NET".equals(rubrique.getEtapeCalcul())
                && (rubrique.isSoumisIrsa() || rubrique.isSoumisCnaps() || rubrique.isSoumisSante())) {
            invalid("Un gain ajouté après impôt doit être hors assiettes. Pour un gain soumis ou partiellement exonéré, choisissez l'étape brut.");
        }
        if ("RETENUE".equals(rubrique.getSens()) && !"RETENUE_NET".equals(rubrique.getEtapeCalcul())) {
            invalid("Une retenue manuelle intervient sur le net après impôt.");
        }
        if ("RETENUE".equals(rubrique.getSens())
                && (rubrique.isSoumisCnaps() || rubrique.isSoumisSante() || rubrique.isSoumisIrsa())) {
            invalid("Une retenue nette manuelle ne modifie pas les assiettes fiscales et sociales.");
        }
    }

    private static boolean hasVersions(Connection c, int id) throws Exception {
        try (var ps = c.prepareStatement("SELECT 1 FROM paie_rubrique WHERE id_rubrique_origine=? LIMIT 1")) {
            ps.setInt(1,id); try (var rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    @PostMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<PaieRubrique> creerVersion(@PathVariable int id, @RequestBody PaieRubriqueRequest req) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                var base = PaieRubrique.trouverParId(id,c);
                if (base == null || base.isEstFixe()) invalid("Rubrique manuelle introuvable.");
                int root = base.getIdRubriqueOrigine() == null ? id : base.getIdRubriqueOrigine();
                try (var lock = c.prepareStatement("SELECT id_rubrique FROM paie_rubrique WHERE id_rubrique=? FOR UPDATE")) {
                    lock.setInt(1,root); lock.executeQuery().close();
                }
                if (req.getCode()==null || req.getCode().isBlank() || req.getLibelle()==null || req.getLibelle().isBlank()
                        || req.getSoumisIrsa()==null || req.getSoumisCnaps()==null || req.getSoumisSante()==null
                        || req.getCategorie()==null || req.getSens()==null)
                    invalid("Identité et assiettes obligatoires pour la nouvelle version.");
                if (PaieRubrique.trouverParCode(req.getCode().trim().toUpperCase(),c)!=null)
                    invalid("Le code de la nouvelle version existe déjà.");
                try(var ps=c.prepareStatement("SELECT 1 FROM ref_type_mouvement_paie t JOIN paie_rubrique r ON r.id_rubrique=t.id_rubrique WHERE COALESCE(r.id_rubrique_origine,r.id_rubrique)=? AND t.nature<>'VARIABLE_DAF' LIMIT 1")) {
                    ps.setInt(1,root); try(var rs=ps.executeQuery()) {
                        if(rs.next()) invalid("Cette rubrique utilise un calculateur métier dédié ; son versionnement relève de ce circuit.");
                    }
                }
                var next = fromRequest(req);
                validateManualRubrique(next,true);
                if (!Objects.equals(base.getSens(),next.getSens()) || !Objects.equals(base.getCategorie(),next.getCategorie()))
                    invalid("Une version conserve le sens et la catégorie de sa rubrique.");
                if (next.getCode()==null || next.getCode().isBlank() || next.getLibelle()==null || next.getLibelle().isBlank())
                    invalid("Code et libellé obligatoires.");
                PaieRubrique previous;
                try (var ps = c.prepareStatement("SELECT id_rubrique FROM paie_rubrique WHERE COALESCE(id_rubrique_origine,id_rubrique)=? ORDER BY date_debut_effet DESC NULLS LAST, id_rubrique DESC LIMIT 1 FOR UPDATE")) {
                    ps.setInt(1,root); try(var rs=ps.executeQuery()) { rs.next(); previous=PaieRubrique.trouverParId(rs.getInt(1),c); }
                }
                if (previous.getDateFinEffet()!=null || (previous.getDateDebutEffet()!=null
                        && !next.getDateDebutEffet().isAfter(previous.getDateDebutEffet())))
                    invalid("La nouvelle version doit succéder à la dernière version ouverte.");
                // Une période déjà liée à un mouvement ou à un bulletin ne peut être réinterprétée.
                try(var ps=c.prepareStatement("SELECT MAX(p.date_fin) FROM periode_paie p WHERE p.id_periode_paie IN ("
                        + "SELECT b.id_periode_paie FROM bulletin_paie b JOIN bulletin_paie_ligne l ON l.id_bulletin=b.id_bulletin JOIN paie_rubrique r ON r.id_rubrique=l.id_rubrique WHERE COALESCE(r.id_rubrique_origine,r.id_rubrique)=? "
                        + "UNION SELECT a.id_periode_paie FROM paie_avance_acompte a JOIN ref_type_mouvement_paie t ON t.id_type_mouvement=a.id_type_mouvement JOIN paie_rubrique r ON r.id_rubrique=t.id_rubrique WHERE COALESCE(r.id_rubrique_origine,r.id_rubrique)=? AND a.statut<>'ANNULE')")) {
                    ps.setInt(1,root); ps.setInt(2,root); try(var rs=ps.executeQuery()) {
                        rs.next(); var last=rs.getDate(1);
                        if(last!=null && !next.getDateDebutEffet().isAfter(last.toLocalDate()))
                            invalid("La nouvelle version chevauche une période historique ou un mouvement existant.");
                    }
                }
                previous.setDateFinEffet(next.getDateDebutEffet().minusDays(1)); previous.update(c);
                next.setIdRubriqueOrigine(root); next.save(c);
                AuditLog.audit(c,"VERSION_CREATION","paie_rubrique",String.valueOf(next.getIdRubrique()),previous,next);
                c.commit(); payrollService.invalidateRubriqueCache();
                return ResponseEntity.status(HttpStatus.CREATED).body(next);
            } catch(Exception e) { c.rollback(); throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e); }
        }
    }

    private static boolean hasMovements(Connection c, int id) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM paie_avance_acompte paa JOIN ref_type_mouvement_paie t " +
                "ON t.id_type_mouvement = paa.id_type_mouvement WHERE t.id_rubrique = ? " +
                "UNION ALL SELECT 1 FROM bulletin_paie_ligne WHERE id_rubrique = ? LIMIT 1")) {
            ps.setInt(1, id);
            ps.setInt(2, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

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
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
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

                if (req.getIdRubriqueOrigine() != null) invalid("Utilisez la création de version.");
                PaieRubrique entity = fromRequest(req);
                validateManualRubrique(entity, true);

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
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> modifierRubrique(@PathVariable int id, @RequestBody PaieRubriqueRequest req) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                PaieRubrique found = PaieRubrique.trouverParId(id, c);
                if (found == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Rubrique introuvable.");
                }
                if (found.isEstFixe()) {
                    invalid("Une rubrique automatique doit être modifiée dans son circuit métier dédié.");
                }
                String oldCode = found.getCode();
                String oldCategorie = found.getCategorie();
                String oldSens = found.getSens();
                String oldTypeValeur = found.getTypeValeur();
                boolean oldCnaps = found.isSoumisCnaps();
                boolean oldSante = found.isSoumisSante();
                boolean oldIrsa = found.isSoumisIrsa();
                java.time.LocalDate oldDateEffet = found.getDateDebutEffet();
                String oldEtapeCalcul = found.getEtapeCalcul();
                var oldFin = found.getDateFinEffet();
                String oldCondition = found.getConditionApplication();
                String oldReference = found.getReferenceReglementaire();
                String oldMethod=found.getMethodeCalcul(), oldRounding=found.getPolitiqueArrondi();
                Boolean oldFmfp=found.getSoumisFmfp();
                found.setMethodeCalcul(req.getMethodeCalcul()); found.setPolitiqueArrondi(req.getPolitiqueArrondi());
                found.setSoumisFmfp(req.getSoumisFmfp());
                if (req.getIdRubriqueOrigine() != null) invalid("Utilisez la création de version pour changer la filiation.");
                found.setDateFinEffet(req.getDateFinEffet());
                if (req.getConditionApplication() != null) found.setConditionApplication(req.getConditionApplication());

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
                if (req.getReferenceReglementaire() != null) {
                    found.setReferenceReglementaire(req.getReferenceReglementaire());
                }
                if (req.getDateDebutEffet() != null) {
                    found.setDateDebutEffet(req.getDateDebutEffet());
                }
                if (req.getEtapeCalcul() != null) {
                    found.setEtapeCalcul(req.getEtapeCalcul());
                }
                if ((hasMovements(c, id) || found.getIdRubriqueOrigine() != null || hasVersions(c, id)) && (!Objects.equals(oldCode, found.getCode())
                        || !Objects.equals(oldCategorie, found.getCategorie())
                        || !Objects.equals(oldSens, found.getSens())
                        || !Objects.equals(oldTypeValeur, found.getTypeValeur())
                        || oldCnaps != found.isSoumisCnaps() || oldSante != found.isSoumisSante()
                        || oldIrsa != found.isSoumisIrsa()
                        || !Objects.equals(oldDateEffet, found.getDateDebutEffet())
                        || !Objects.equals(oldEtapeCalcul, found.getEtapeCalcul())
                        || !Objects.equals(oldFin, found.getDateFinEffet())
                        || !Objects.equals(oldCondition, found.getConditionApplication())
                        || !Objects.equals(oldReference, found.getReferenceReglementaire())
                        || !Objects.equals(oldMethod,found.getMethodeCalcul()) || !Objects.equals(oldRounding,found.getPolitiqueArrondi())
                        || !Objects.equals(oldFmfp,found.getSoumisFmfp()))) {
                    invalid("Cette rubrique possède des mouvements historiques. Créez une nouvelle version pour changer son traitement.");
                }
                validateManualRubrique(found, found.getDateDebutEffet() != null);

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
                boolean estUtilisee = found.getIdRubriqueOrigine() != null || hasVersions(c,id);
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
