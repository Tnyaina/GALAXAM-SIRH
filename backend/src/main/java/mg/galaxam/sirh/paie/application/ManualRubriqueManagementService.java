package mg.galaxam.sirh.paie.application;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.paie.api.dto.PaieRubriqueRequest;
import mg.galaxam.sirh.paie.domain.ManualRubriquePolicy;
import mg.galaxam.sirh.paie.persistence.PaieRubrique;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.stereotype.Service;

/** Création et versionnement des rubriques manuelles avec règles de cohérence transactionnelles. */
@Service
public class ManualRubriqueManagementService {
    private final DataSource dataSource;
    private final PayrollService payrollService;

    public ManualRubriqueManagementService(DataSource dataSource, PayrollService payrollService) {
        this.dataSource = dataSource; this.payrollService = payrollService;
    }

    public PaieRubrique create(PaieRubriqueRequest request) throws Exception {
        requireIdentityAndBases(request);
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (PaieRubrique.trouverParCode(request.getCode().trim().toUpperCase(), c) != null)
                    throw new SirhBusinessException(SirhErrorCode.DATA_DUPLICATE,
                            "Une rubrique avec le code '" + request.getCode() + "' existe déjà.");
                if (request.getIdRubriqueOrigine() != null) invalid("Utilisez la création de version.");
                PaieRubrique entity = fromRequest(request);
                ManualRubriquePolicy.validate(entity, true);
                entity.save(c);
                AuditLog.audit(c, "CREATION", "paie_rubrique", String.valueOf(entity.getIdRubrique()), null, entity);
                c.commit(); payrollService.invalidateRubriqueCache(); return entity;
            } catch (Exception error) {
                c.rollback(); throw error;
            }
        }
    }

    public PaieRubrique createVersion(int id, PaieRubriqueRequest request) throws Exception {
        if (request == null) invalid("Les données de la nouvelle version sont obligatoires.");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                PaieRubrique base = PaieRubrique.trouverParId(id, c);
                if (base == null || base.isEstFixe()) invalid("Rubrique manuelle introuvable.");
                int root = base.getIdRubriqueOrigine() == null ? id : base.getIdRubriqueOrigine();
                try (var lock = c.prepareStatement("SELECT id_rubrique FROM paie_rubrique WHERE id_rubrique=? FOR UPDATE")) {
                    lock.setInt(1, root); lock.executeQuery().close();
                }
                if (request.getCode() == null || request.getCode().isBlank() || request.getLibelle() == null || request.getLibelle().isBlank()
                        || request.getSoumisIrsa() == null || request.getSoumisCnaps() == null || request.getSoumisSante() == null
                        || request.getCategorie() == null || request.getSens() == null)
                    invalid("Identité et assiettes obligatoires pour la nouvelle version.");
                if (PaieRubrique.trouverParCode(request.getCode().trim().toUpperCase(), c) != null)
                    invalid("Le code de la nouvelle version existe déjà.");
                try (var ps = c.prepareStatement("SELECT 1 FROM ref_type_mouvement_paie t JOIN paie_rubrique r ON r.id_rubrique=t.id_rubrique WHERE COALESCE(r.id_rubrique_origine,r.id_rubrique)=? AND t.nature<>'VARIABLE_DAF' LIMIT 1")) {
                    ps.setInt(1, root);
                    try (var rs = ps.executeQuery()) {
                        if (rs.next()) invalid("Cette rubrique utilise un calculateur métier dédié ; son versionnement relève de ce circuit.");
                    }
                }
                PaieRubrique next = fromRequest(request);
                ManualRubriquePolicy.validate(next, true);
                if (!Objects.equals(base.getSens(), next.getSens()) || !Objects.equals(base.getCategorie(), next.getCategorie()))
                    invalid("Une version conserve le sens et la catégorie de sa rubrique.");
                PaieRubrique previous;
                try (var ps = c.prepareStatement("SELECT id_rubrique FROM paie_rubrique WHERE COALESCE(id_rubrique_origine,id_rubrique)=? ORDER BY date_debut_effet DESC NULLS LAST, id_rubrique DESC LIMIT 1 FOR UPDATE")) {
                    ps.setInt(1, root);
                    try (var rs = ps.executeQuery()) { rs.next(); previous = PaieRubrique.trouverParId(rs.getInt(1), c); }
                }
                if (previous == null || previous.getDateFinEffet() != null || (previous.getDateDebutEffet() != null
                        && !next.getDateDebutEffet().isAfter(previous.getDateDebutEffet())))
                    invalid("La nouvelle version doit succéder à la dernière version ouverte.");
                String cancelledAdjustment = mg.galaxam.sirh.paie.domain.PayrollStatuses.cancelledAdjustmentSql("a.statut");
                try (var ps = c.prepareStatement("SELECT MAX(p.date_fin) FROM periode_paie p WHERE p.id_periode_paie IN ("
                        + "SELECT b.id_periode_paie FROM bulletin_paie b JOIN bulletin_paie_ligne l ON l.id_bulletin=b.id_bulletin JOIN paie_rubrique r ON r.id_rubrique=l.id_rubrique WHERE COALESCE(r.id_rubrique_origine,r.id_rubrique)=? "
                        + "UNION SELECT a.id_periode_paie FROM paie_avance_acompte a JOIN ref_type_mouvement_paie t ON t.id_type_mouvement=a.id_type_mouvement JOIN paie_rubrique r ON r.id_rubrique=t.id_rubrique WHERE COALESCE(r.id_rubrique_origine,r.id_rubrique)=? AND NOT (" + cancelledAdjustment + "))")) {
                    ps.setInt(1, root); ps.setInt(2, root);
                    try (var rs = ps.executeQuery()) {
                        rs.next(); var last = rs.getDate(1);
                        if (last != null && !next.getDateDebutEffet().isAfter(last.toLocalDate()))
                            invalid("La nouvelle version chevauche une période historique ou un mouvement existant.");
                    }
                }
                previous.setDateFinEffet(next.getDateDebutEffet().minusDays(1)); previous.update(c);
                next.setIdRubriqueOrigine(root); next.save(c);
                AuditLog.audit(c, "VERSION_CREATION", "paie_rubrique", String.valueOf(next.getIdRubrique()), previous, next);
                c.commit(); payrollService.invalidateRubriqueCache(); return next;
            } catch (Exception error) {
                c.rollback(); throw error;
            }
        }
    }

    public PaieRubrique update(int id, PaieRubriqueRequest req) throws Exception {
        if (req == null) invalid("Les données de la rubrique sont obligatoires.");
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
                ManualRubriquePolicy.validate(found, found.getDateDebutEffet() != null);

                found.update(c);

                AuditLog.audit(c, "MODIFICATION", "paie_rubrique",
                        String.valueOf(id), null, found);

                c.commit();

                // Invalidation immédiate du cache en mémoire de PayrollService
                payrollService.invalidateRubriqueCache();

                return found;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }


    public String delete(int id) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                PaieRubrique found = PaieRubrique.trouverParId(id, c);
                if (found == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Rubrique introuvable.");
                }

                // Vérification de l'intégrité référentielle comptable avant suppression physique
                boolean estUtilisee = found.getIdRubriqueOrigine() != null || hasVersions(c, id);
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
                    return "La rubrique est déjà associée à des bulletins ou mouvements historiques. Elle a été désactivée avec succès pour préserver l'historique comptable.";
                } else {
                    found.delete(c);
                    AuditLog.audit(c, "SUPPRESSION", "paie_rubrique",
                            String.valueOf(id), found, null);
                    c.commit();
                    payrollService.invalidateRubriqueCache();
                    return "Rubrique supprimée avec succès.";
                }
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    public boolean hasVersions(Connection connection, int id) throws Exception {
        try (var statement = connection.prepareStatement("SELECT 1 FROM paie_rubrique WHERE id_rubrique_origine=? LIMIT 1")) {
            statement.setInt(1, id); try (var result = statement.executeQuery()) { return result.next(); }
        }
    }

    private static boolean hasMovements(Connection connection, int id) throws Exception {
        try (var statement = connection.prepareStatement("SELECT 1 FROM paie_avance_acompte movement JOIN ref_type_mouvement_paie type ON type.id_type_mouvement=movement.id_type_mouvement WHERE type.id_rubrique=? UNION ALL SELECT 1 FROM bulletin_paie_ligne WHERE id_rubrique=? LIMIT 1")) {
            statement.setInt(1, id); statement.setInt(2, id); try (var result = statement.executeQuery()) { return result.next(); }
        }
    }

    public static PaieRubrique fromRequest(PaieRubriqueRequest req) {
        PaieRubrique entity = new PaieRubrique();
        entity.setCode(req.getCode().trim().toUpperCase()); entity.setLibelle(req.getLibelle().trim());
        entity.setCategorie(req.getCategorie().trim().toUpperCase()); entity.setTypeValeur(req.getTypeValeur() != null ? req.getTypeValeur() : "MONTANT");
        entity.setSens(req.getSens().trim().toUpperCase()); entity.setSoumisCnaps(req.getSoumisCnaps()); entity.setSoumisSante(req.getSoumisSante());
        entity.setSoumisIrsa(req.getSoumisIrsa()); entity.setEstFixe(req.getEstFixe() != null ? req.getEstFixe() : false);
        entity.setOrdreAffichage(req.getOrdreAffichage() != null ? req.getOrdreAffichage() : 10); entity.setEstActif(req.getEstActif() != null ? req.getEstActif() : true);
        entity.setDescription(req.getDescription()); entity.setReferenceReglementaire(req.getReferenceReglementaire());
        entity.setDateDebutEffet(req.getDateDebutEffet()); entity.setEtapeCalcul(req.getEtapeCalcul()); entity.setMethodeCalcul(req.getMethodeCalcul());
        entity.setPolitiqueArrondi(req.getPolitiqueArrondi()); entity.setSoumisFmfp(req.getSoumisFmfp()); entity.setDateFinEffet(req.getDateFinEffet());
        entity.setConditionApplication(req.getConditionApplication()); return entity;
    }

    private static void requireIdentityAndBases(PaieRubriqueRequest req) {
        if (req == null || blank(req.getCode()) || blank(req.getLibelle()) || blank(req.getCategorie()) || blank(req.getSens()))
            invalid("Code, libellé, catégorie et sens de la rubrique sont obligatoires.");
        if (req.getSoumisCnaps() == null || req.getSoumisSante() == null || req.getSoumisIrsa() == null)
            throw new SirhBusinessException(SirhErrorCode.BLOCKING_CONFIGURATION_ERROR,
                    "Les assujettissements CNaPS, santé et IRSA doivent être renseignés explicitement.");
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static void invalid(String message) { throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, message); }
}
