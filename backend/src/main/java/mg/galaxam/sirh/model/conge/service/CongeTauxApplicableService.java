package mg.galaxam.sirh.model.conge.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mg.galaxam.sirh.model.conge.engine.TauxCongeResolution;
import mg.galaxam.sirh.model.conge.engine.TcongeApplicableRateStatus;
import mg.galaxam.sirh.model.conge.engine.TcongeConfigurationDTO;
import mg.galaxam.sirh.model.conge.engine.TcongeReferenceStatus;
import mg.galaxam.sirh.model.conge.engine.TcongeSourceProvenance;
import mg.galaxam.sirh.model.paieconfig.ParametreGlobal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service canonique de gestion et de résolution du taux journalier de congé applicable (TCONGE).
 * Responsable unique de la résolution pour la Paie et le STC (Phase 2A-4C).
 */
@Service
public class CongeTauxApplicableService {

    private static final Logger log = LoggerFactory.getLogger(CongeTauxApplicableService.class);

    /**
     * Résolution stricte as-of du taux applicable à une date donnée.
     * Détecte et interdit toute ambiguïté (si plusieurs taux applicables existent simultanément).
     */
    public TauxCongeResolution resoudreTauxCongeApplicable(int idEmploye,
                                                          LocalDate dateReference,
                                                          BigDecimal salaireBaseFallback,
                                                          Connection c) {
        if (dateReference == null) {
            throw new IllegalArgumentException("La date de référence temporelle est obligatoire pour résoudre TCONGE.");
        }

        // Requête sans LIMIT 1 pour détecter explicitement les ambiguïtés / chevauchements
        String sql = "SELECT id_taux_applicable, taux_journalier, date_effet, date_fin, " +
                "source_provenance, id_periode_reference " +
                "FROM conge_taux_applicable " +
                "WHERE id_employe = ? " +
                "  AND date_effet <= ? " +
                "  AND (date_fin IS NULL OR date_fin > ?) " +
                "ORDER BY date_effet DESC, id_taux_applicable DESC";

        List<TauxCongeResolution> matches = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, Date.valueOf(dateReference));
            ps.setDate(3, Date.valueOf(dateReference));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal taux = rs.getBigDecimal("taux_journalier");
                    Date dEffet = rs.getDate("date_effet");
                    Date dFin = rs.getDate("date_fin");
                    String srcStr = rs.getString("source_provenance");
                    int idRef = rs.getInt("id_periode_reference");
                    Integer idRefObj = rs.wasNull() ? null : idRef;
                    int idTaux = rs.getInt("id_taux_applicable");

                    TcongeSourceProvenance src = TcongeSourceProvenance.valueOf(srcStr);
                    matches.add(new TauxCongeResolution(
                            taux,
                            src,
                            dEffet != null ? dEffet.toLocalDate() : null,
                            dFin != null ? dFin.toLocalDate() : null,
                            idRefObj,
                            idTaux
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erreur SQL lors de la résolution TCONGE pour l'employé " + idEmploye + " au " + dateReference, e);
        }

        if (matches.size() > 1) {
            throw new IllegalStateException("TCONGE_APPLICABLE_AMBIGUOUS: " + matches.size() +
                    " taux applicables distincts se chevauchent pour l'employé " + idEmploye +
                    " à la date " + dateReference + ". Un arbitrage explicite est requis.");
        }

        if (matches.size() == 1) {
            return matches.get(0);
        }

        // 2. Si aucun taux applicable explicite dans conge_taux_applicable :
        // Recherche défensive d'un historique conge_journal_droit antérieur à dateReference (données historiques / legacy)
        String sqlLegacy = "SELECT taux_journalier_conge, annee, mois FROM conge_journal_droit " +
                "WHERE id_employe = ? AND (annee < ? OR (annee = ? AND mois < ?)) " +
                "AND taux_journalier_conge IS NOT NULL AND taux_journalier_conge > 0 " +
                "ORDER BY annee DESC, mois DESC LIMIT 1";
        try (PreparedStatement psLeg = c.prepareStatement(sqlLegacy)) {
            psLeg.setInt(1, idEmploye);
            psLeg.setInt(2, dateReference.getYear());
            psLeg.setInt(3, dateReference.getYear());
            psLeg.setInt(4, dateReference.getMonthValue());
            try (ResultSet rsLeg = psLeg.executeQuery()) {
                if (rsLeg.next()) {
                    BigDecimal tauxLeg = rsLeg.getBigDecimal("taux_journalier_conge");
                    if (tauxLeg != null && tauxLeg.compareTo(BigDecimal.ZERO) > 0) {
                        return new TauxCongeResolution(
                                tauxLeg,
                                TcongeSourceProvenance.KAJY_MIGRATION,
                                dateReference,
                                null,
                                null,
                                null
                        );
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("[CongeTauxApplicableService] Erreur recherche taux historique legacy pour employé {}: {}", idEmploye, e.getMessage());
        }

        // 3. Cas 0 match : Amorçage contractuel canonique pour nouvel employé sans historique
        if (salaireBaseFallback != null && salaireBaseFallback.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal tauxBase30 = salaireBaseFallback.divide(new BigDecimal("30.0"), 2, RoundingMode.HALF_UP);
            return new TauxCongeResolution(
                    tauxBase30,
                    TcongeSourceProvenance.INITIALISATION_BASE_30,
                    dateReference,
                    null,
                    null,
                    null
            );
        }

        throw new IllegalStateException("TCONGE_CONFIGURATION_MISSING: aucun taux applicable trouvé pour l'employé "
                + idEmploye + " au " + dateReference + " et salaire de base non disponible.");
    }

    /**
     * Enregistre un taux applicable initial issu de la migration KAJY pour un salarié existant,
     * sans fabriquer de faux bulletins ni de fausses acquisitions.
     */
    public int enregistrerTauxApplicableMigration(int idEmploye,
                                                 BigDecimal tauxJournalier,
                                                 LocalDate dateEffet,
                                                 LocalDate dateFin,
                                                 String commentaire,
                                                 Integer idUtilisateur,
                                                 Connection c) throws SQLException {
        if (tauxJournalier == null || tauxJournalier.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le taux journalier de migration doit être strictement positif.");
        }
        if (dateEffet == null) {
            throw new IllegalArgumentException("La date d'effet de migration est obligatoire.");
        }
        if (dateFin != null && !dateFin.isAfter(dateEffet)) {
            throw new IllegalArgumentException("La date de fin doit être strictement postérieure à la date d'effet (convention [effet, fin[).");
        }

        verifierAbsenceChevauchement(idEmploye, dateEffet, dateFin, null, c);

        String sql = "INSERT INTO conge_taux_applicable (" +
                "id_employe, taux_journalier, date_effet, date_fin, source_provenance, commentaire, cree_par" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id_taux_applicable";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setBigDecimal(2, tauxJournalier.setScale(2, RoundingMode.HALF_UP));
            ps.setDate(3, Date.valueOf(dateEffet));
            if (dateFin != null) {
                ps.setDate(4, Date.valueOf(dateFin));
            } else {
                ps.setNull(4, Types.DATE);
            }
            ps.setString(5, TcongeSourceProvenance.KAJY_MIGRATION.name());
            ps.setString(6, commentaire);
            if (idUtilisateur != null) {
                ps.setInt(7, idUtilisateur);
            } else {
                ps.setNull(7, Types.INTEGER);
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    log.info("[CongeTauxApplicableService] TCONGE migration enregistré pour employé {} : {} Ar/j au {}",
                            idEmploye, tauxJournalier, dateEffet);
                    return id;
                }
            }
        }
        throw new IllegalStateException("Impossible de récupérer l'identifiant du taux applicable créé.");
    }

    /**
     * Enregistre l'activation datée d'un taux calculé à la suite de la clôture d'une référence de 12 mois.
     */
    public int activerTauxCalculeReference(int idEmploye,
                                          int idPeriodeReference,
                                          BigDecimal tauxJournalier,
                                          LocalDate dateEffet,
                                          LocalDate dateFin,
                                          String commentaire,
                                          Integer idUtilisateur,
                                          Connection c) throws SQLException {
        if (tauxJournalier == null || tauxJournalier.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le taux calculé doit être strictement positif.");
        }
        if (dateEffet == null) {
            throw new IllegalArgumentException("La date d'effet est obligatoire pour activer le taux calculé.");
        }
        if (dateFin != null && !dateFin.isAfter(dateEffet)) {
            throw new IllegalArgumentException("La date de fin doit être strictement postérieure à la date d'effet.");
        }

        verifierAbsenceChevauchement(idEmploye, dateEffet, dateFin, null, c);

        String sql = "INSERT INTO conge_taux_applicable (" +
                "id_employe, taux_journalier, date_effet, date_fin, source_provenance, " +
                "id_periode_reference, commentaire, cree_par" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_taux_applicable";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setBigDecimal(2, tauxJournalier.setScale(2, RoundingMode.HALF_UP));
            ps.setDate(3, Date.valueOf(dateEffet));
            if (dateFin != null) {
                ps.setDate(4, Date.valueOf(dateFin));
            } else {
                ps.setNull(4, Types.DATE);
            }
            ps.setString(5, TcongeSourceProvenance.CALCUL_PERIODE_REFERENCE.name());
            ps.setInt(6, idPeriodeReference);
            ps.setString(7, commentaire);
            if (idUtilisateur != null) {
                ps.setInt(8, idUtilisateur);
            } else {
                ps.setNull(8, Types.INTEGER);
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new IllegalStateException("Impossible d'activer le taux calculé de référence.");
    }

    /**
     * Valide que la durée de la période de référence correspond exactement à DUREE_REFERENCE_TCONGE_MOIS (12 mois).
     */
    public void validerDureeReference(LocalDate debut, LocalDate fin, Connection c) throws SQLException {
        if (debut == null || fin == null) {
            throw new IllegalArgumentException("Les dates de début et de fin de référence sont obligatoires.");
        }
        if (!fin.isAfter(debut)) {
            throw new IllegalArgumentException("La date de fin de référence doit être strictement postérieure à la date de début.");
        }
        Map<String, String> params = ParametreGlobal.loadActiveMapAtDate(Date.valueOf(debut), c);
        int dureeAttendue = ParametreGlobal.requirePositiveInt(params, "DUREE_REFERENCE_TCONGE_MOIS");

        // Convention calendaire inclusive [debut, fin] :
        // 01/01/2026 au 31/12/2026 (12 mois) = debut.plusMonths(12).minusDays(1)
        LocalDate finAttendue = debut.plusMonths(dureeAttendue).minusDays(1);
        if (!fin.equals(finAttendue)) {
            throw new IllegalArgumentException("TCONGE_REFERENCE_DURATION_MISMATCH: la période de référence doit durer exactement " +
                    dureeAttendue + " mois (attendu du " + debut + " au " + finAttendue + ", reçu: " + fin + ").");
        }
    }

    private void verifierAbsenceChevauchement(int idEmploye,
                                             LocalDate dateEffet,
                                             LocalDate dateFin,
                                             Integer idTauxExclu,
                                             Connection c) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT id_taux_applicable, date_effet, date_fin FROM conge_taux_applicable WHERE id_employe = ? "
        );
        if (idTauxExclu != null) {
            sql.append("AND id_taux_applicable != ? ");
        }
        sql.append("AND (date_fin IS NULL OR date_fin > ?) ");
        if (dateFin != null) {
            sql.append("AND date_effet < ? ");
        }

        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int p = 1;
            ps.setInt(p++, idEmploye);
            if (idTauxExclu != null) {
                ps.setInt(p++, idTauxExclu);
            }
            ps.setDate(p++, Date.valueOf(dateEffet));
            if (dateFin != null) {
                ps.setDate(p++, Date.valueOf(dateFin));
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalStateException("TCONGE_OVERLAP_DETECTED: le taux chevauche un taux existant (ID: " +
                            rs.getInt("id_taux_applicable") + ", effet: " + rs.getDate("date_effet") +
                            ", fin: " + rs.getDate("date_fin") + ") pour l'employé " + idEmploye + ".");
                }
            }
        }
    }

    /**
     * Vérifie l'absence de chevauchement temporel pour les références d'un même employé.
     */
    public void verifierAbsenceChevauchementReference(int idEmploye,
                                                     LocalDate debut,
                                                     LocalDate fin,
                                                     Integer idPeriodeExclue,
                                                     Connection c) throws SQLException {
        String sqlCheck = "SELECT id_periode_reference, date_debut, date_fin FROM conge_periode_reference " +
                "WHERE id_employe = ? " +
                "  AND (? IS NULL OR id_periode_reference != ?) " +
                "  AND date_fin >= ? " +
                "  AND date_debut <= ?";
        try (PreparedStatement ps = c.prepareStatement(sqlCheck)) {
            ps.setInt(1, idEmploye);
            if (idPeriodeExclue != null) {
                ps.setInt(2, idPeriodeExclue);
                ps.setInt(3, idPeriodeExclue);
            } else {
                ps.setNull(2, Types.INTEGER);
                ps.setNull(3, Types.INTEGER);
            }
            ps.setDate(4, Date.valueOf(debut));
            ps.setDate(5, Date.valueOf(fin));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalStateException("TCONGE_REFERENCE_PERIOD_OVERLAP: une période de référence (ID: " +
                            rs.getInt("id_periode_reference") + " du " + rs.getDate("date_debut") + " au " + rs.getDate("date_fin") +
                            ") chevauche déjà l'intervalle [" + debut + ", " + fin + "].");
                }
            }
        }
    }

    /**
     * Détecte une discontinuité temporelle (trou) avec la référence précédente.
     * Informationnel : ne bouche aucun trou automatiquement.
     */
    public String detecterDiscontinuiteReference(int idEmploye, LocalDate debut, Connection c) throws SQLException {
        String sql = "SELECT date_fin FROM conge_periode_reference WHERE id_employe = ? AND date_fin < ? ORDER BY date_fin DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, Date.valueOf(debut));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    LocalDate finPrec = rs.getDate("date_fin").toLocalDate();
                    if (debut.isAfter(finPrec.plusDays(1))) {
                        return "TCONGE_REFERENCE_PERIOD_GAP";
                    }
                }
            }
        }
        return null;
    }

    /**
     * Crée une période de référence bornée (exactement 12 mois) pour un employé.
     */
    public int creerPeriodeReference(int idEmploye,
                                     LocalDate dateDebut,
                                     LocalDate dateFin,
                                     Integer idUtilisateur,
                                     Connection c) throws SQLException {
        validerDureeReference(dateDebut, dateFin, c);
        verifierAbsenceChevauchementReference(idEmploye, dateDebut, dateFin, null, c);

        String sqlInsert = "INSERT INTO conge_periode_reference (" +
                "id_employe, date_debut, date_fin, statut, created_by" +
                ") VALUES (?, ?, ?, 'OUVERTE', ?) RETURNING id_periode_reference";

        try (PreparedStatement ps = c.prepareStatement(sqlInsert)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, Date.valueOf(dateDebut));
            ps.setDate(3, Date.valueOf(dateFin));
            if (idUtilisateur != null) {
                ps.setInt(4, idUtilisateur);
            } else {
                ps.setNull(4, Types.INTEGER);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new IllegalStateException("Impossible de créer la période de référence.");
    }

    /**
     * Résout la période de référence active (OUVERTE) pour un employé couvrant la date de paie donnée.
     */
    public PeriodeReferenceData resoudrePeriodeReferenceActive(int idEmploye,
                                                              LocalDate dateMoisPaie,
                                                              Connection c) throws SQLException {
        String sql = "SELECT id_periode_reference, date_debut, date_fin, statut, " +
                "taux_calcule_cloture, cumul_droits_cloture, cumul_valorisation_cloture " +
                "FROM conge_periode_reference " +
                "WHERE id_employe = ? " +
                "  AND statut = 'OUVERTE' " +
                "  AND date_debut <= ? " +
                "  AND date_fin >= ? " +
                "ORDER BY date_debut ASC";

        List<PeriodeReferenceData> refs = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, Date.valueOf(dateMoisPaie));
            ps.setDate(3, Date.valueOf(dateMoisPaie));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    refs.add(new PeriodeReferenceData(
                            rs.getInt("id_periode_reference"),
                            rs.getDate("date_debut").toLocalDate(),
                            rs.getDate("date_fin").toLocalDate(),
                            rs.getString("statut"),
                            rs.getBigDecimal("taux_calcule_cloture")
                    ));
                }
            }
        }

        if (refs.size() > 1) {
            throw new IllegalStateException("TCONGE_REFERENCE_PERIOD_AMBIGUOUS: " + refs.size() +
                    " périodes de référence actives couvrent la date " + dateMoisPaie + " pour l'employé " + idEmploye + ".");
        }
        return refs.isEmpty() ? null : refs.get(0);
    }

    public PeriodeReferenceData resoudrePeriodeReferenceStrict(int idEmploye,
                                                               LocalDate dateMoisPaie,
                                                               Connection c) throws SQLException {
        PeriodeReferenceData ref = resoudrePeriodeReferenceActive(idEmploye, dateMoisPaie, c);
        if (ref == null) {
            throw new IllegalStateException("TCONGE_REFERENCE_PERIOD_MISSING: aucune période de référence de congé active configurée pour l'employé "
                    + idEmploye + " au " + dateMoisPaie + ".");
        }
        return ref;
    }

    /**
     * Clôture idempotente d'une période de référence avec cumuls explicites.
     */
    public void cloturerPeriodeReference(int idPeriodeReference,
                                         BigDecimal cumulDroits,
                                         BigDecimal cumulValorisation,
                                         BigDecimal tauxCalcule,
                                         Connection c) throws SQLException {
        // Idempotence : vérifier si déjà clôturée avec les mêmes valeurs
        String sqlCheck = "SELECT statut, taux_calcule_cloture FROM conge_periode_reference WHERE id_periode_reference = ? FOR UPDATE";
        try (PreparedStatement psCheck = c.prepareStatement(sqlCheck)) {
            psCheck.setInt(1, idPeriodeReference);
            try (ResultSet rsCheck = psCheck.executeQuery()) {
                if (rsCheck.next()) {
                    if ("CLOTUREE".equals(rsCheck.getString("statut"))) {
                        BigDecimal existingTaux = rsCheck.getBigDecimal("taux_calcule_cloture");
                        if (existingTaux != null && existingTaux.compareTo(tauxCalcule) == 0) {
                            return; // Idempotent
                        }
                    }
                }
            }
        }

        String sql = "UPDATE conge_periode_reference SET " +
                "statut = 'CLOTUREE', " +
                "cumul_droits_cloture = ?, " +
                "cumul_valorisation_cloture = ?, " +
                "taux_calcule_cloture = ? " +
                "WHERE id_periode_reference = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBigDecimal(1, cumulDroits);
            ps.setBigDecimal(2, cumulValorisation);
            ps.setBigDecimal(3, tauxCalcule);
            ps.setInt(4, idPeriodeReference);
            ps.executeUpdate();
            log.info("[CongeTauxApplicableService] Période de référence {} clôturée : TCONGE calculé = {} Ar/j",
                    idPeriodeReference, tauxCalcule);
        }
    }

    /**
     * Clôture idempotente backend-driven d'une période de référence en calculant les cumuls depuis conge_journal_droit.
     */
    public BigDecimal cloturerPeriodeReference(int idPeriodeReference, Integer idUtilisateur, Connection c) throws SQLException {
        // Idempotence check
        String sqlCheck = "SELECT statut, taux_calcule_cloture FROM conge_periode_reference WHERE id_periode_reference = ? FOR UPDATE";
        try (PreparedStatement psCheck = c.prepareStatement(sqlCheck)) {
            psCheck.setInt(1, idPeriodeReference);
            try (ResultSet rsCheck = psCheck.executeQuery()) {
                if (!rsCheck.next()) {
                    throw new IllegalArgumentException("Période de référence introuvable: ID " + idPeriodeReference);
                }
                if ("CLOTUREE".equals(rsCheck.getString("statut"))) {
                    return rsCheck.getBigDecimal("taux_calcule_cloture");
                }
            }
        }

        // Calcul des cumuls depuis conge_journal_droit
        String sqlSum = "SELECT COALESCE(SUM(jours_droit_acquis), 0) AS sum_droits, " +
                "COALESCE(SUM(valorisation_mensuelle), 0) AS sum_valo " +
                "FROM conge_journal_droit WHERE id_periode_reference = ?";
        BigDecimal cumulDroits = BigDecimal.ZERO;
        BigDecimal cumulValo = BigDecimal.ZERO;
        try (PreparedStatement psSum = c.prepareStatement(sqlSum)) {
            psSum.setInt(1, idPeriodeReference);
            try (ResultSet rsSum = psSum.executeQuery()) {
                if (rsSum.next()) {
                    cumulDroits = rsSum.getBigDecimal("sum_droits");
                    cumulValo = rsSum.getBigDecimal("sum_valo");
                }
            }
        }

        if (cumulDroits.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("TCONGE_CLOSURE_DROITS_ZERO: impossible de clôturer une référence sans droits acquis.");
        }

        BigDecimal tauxCalcule = cumulValo.divide(cumulDroits, 2, RoundingMode.HALF_UP);
        cloturerPeriodeReference(idPeriodeReference, cumulDroits, cumulValo, tauxCalcule, c);
        return tauxCalcule;
    }

    /**
     * Active explicitement le taux calculé issu d'une période de référence clôturée.
     * Transactionnel, atomique, avec verrouillage FOR UPDATE, bornage de l'ancien taux sans trou [dateA, D[ et [D, NULL[.
     */
    public int activerTauxCalcule(int idPeriodeReference,
                                 LocalDate dateEffet,
                                 Integer idUtilisateur,
                                 Connection c) throws SQLException {
        if (dateEffet == null) {
            throw new IllegalArgumentException("La date d'effet est obligatoire pour activer le taux applicable.");
        }

        // 1. Verrouiller la période de référence concernée
        String sqlRef = "SELECT id_periode_reference, id_employe, date_debut, date_fin, statut, taux_calcule_cloture " +
                "FROM conge_periode_reference WHERE id_periode_reference = ? FOR UPDATE";
        int idEmploye;
        BigDecimal tauxCalcule;
        try (PreparedStatement psRef = c.prepareStatement(sqlRef)) {
            psRef.setInt(1, idPeriodeReference);
            try (ResultSet rsRef = psRef.executeQuery()) {
                if (!rsRef.next()) {
                    throw new IllegalArgumentException("Période de référence introuvable: ID " + idPeriodeReference);
                }
                String statut = rsRef.getString("statut");
                if (!"CLOTUREE".equals(statut)) {
                    throw new IllegalStateException("TCONGE_ACTIVATION_PERIOD_NOT_CLOSED: la période " + idPeriodeReference +
                            " doit être CLOTUREE avant activation (statut actuel: " + statut + ").");
                }
                tauxCalcule = rsRef.getBigDecimal("taux_calcule_cloture");
                if (tauxCalcule == null || tauxCalcule.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalStateException("TCONGE_ACTIVATION_RATE_INVALID: aucun taux calculé valide sur la période " + idPeriodeReference);
                }
                idEmploye = rsRef.getInt("id_employe");
            }
        }

        // 2. Vérifier si cette référence a déjà été activée
        String sqlCheckExisting = "SELECT id_taux_applicable, taux_journalier, date_effet " +
                "FROM conge_taux_applicable WHERE id_periode_reference = ? FOR UPDATE";
        try (PreparedStatement psCheck = c.prepareStatement(sqlCheckExisting)) {
            psCheck.setInt(1, idPeriodeReference);
            try (ResultSet rsCheck = psCheck.executeQuery()) {
                if (rsCheck.next()) {
                    int existingId = rsCheck.getInt("id_taux_applicable");
                    LocalDate existingDateEffet = rsCheck.getDate("date_effet").toLocalDate();
                    BigDecimal existingTaux = rsCheck.getBigDecimal("taux_journalier");

                    // Idempotence : même période, même dateEffet, même taux
                    if (existingDateEffet.equals(dateEffet) && existingTaux.compareTo(tauxCalcule) == 0) {
                        return existingId;
                    }
                    // Conflit : dateEffet différente ou taux divergent
                    throw new IllegalStateException("TCONGE_ACTIVATION_CONFLICT: la période " + idPeriodeReference +
                            " a déjà été activée avec une date d'effet différente (" + existingDateEffet + " vs " + dateEffet + ").");
                }
            }
        }

        // 3. Verrouiller et analyser les taux applicables existants du collaborateur
        String sqlLockRates = "SELECT id_taux_applicable, date_effet, date_fin, taux_journalier " +
                "FROM conge_taux_applicable WHERE id_employe = ? ORDER BY date_effet ASC FOR UPDATE";
        Integer idTauxPrecedentABorner = null;
        try (PreparedStatement psRates = c.prepareStatement(sqlLockRates)) {
            psRates.setInt(1, idEmploye);
            try (ResultSet rsRates = psRates.executeQuery()) {
                while (rsRates.next()) {
                    int rateId = rsRates.getInt("id_taux_applicable");
                    LocalDate dEffet = rsRates.getDate("date_effet").toLocalDate();
                    Date dFinSql = rsRates.getDate("date_fin");
                    LocalDate dFin = dFinSql != null ? dFinSql.toLocalDate() : null;

                    // Incohérence temporelle : un taux ne peut pas avoir une date d'effet antérieure ou égale à un taux existant ultérieur
                    if (dFin != null && dEffet.isBefore(dateEffet) && dFin.isAfter(dateEffet)) {
                        throw new IllegalStateException("TCONGE_ACTIVATION_INVALID_DATE_EFFET: la date d'effet " + dateEffet +
                                " coupe un intervalle de taux déjà fermé [" + dEffet + ", " + dFin + "[.");
                    }
                    if (!dEffet.isBefore(dateEffet)) {
                        throw new IllegalStateException("TCONGE_ACTIVATION_INVALID_DATE_EFFET: la date d'effet " + dateEffet +
                                " entre en conflit avec un taux existant effectif au " + dEffet + ".");
                    }

                    if (dFin == null && dEffet.isBefore(dateEffet)) {
                        // Taux ouvert précédent à borner
                        idTauxPrecedentABorner = rateId;
                    }
                }
            }
        }

        // 4. Borner atomiquement le taux précédent ouvert [dateA, NULL[ -> [dateA, dateEffet[
        if (idTauxPrecedentABorner != null) {
            String sqlCap = "UPDATE conge_taux_applicable SET date_fin = ? WHERE id_taux_applicable = ?";
            try (PreparedStatement psCap = c.prepareStatement(sqlCap)) {
                psCap.setDate(1, Date.valueOf(dateEffet));
                psCap.setInt(2, idTauxPrecedentABorner);
                psCap.executeUpdate();
            }
        }

        // 5. Insérer le nouveau taux applicable [dateEffet, NULL[
        String sqlInsert = "INSERT INTO conge_taux_applicable (" +
                "id_employe, taux_journalier, date_effet, date_fin, source_provenance, " +
                "id_periode_reference, commentaire, cree_par" +
                ") VALUES (?, ?, ?, NULL, 'CALCUL_PERIODE_REFERENCE', ?, ?, ?) RETURNING id_taux_applicable";
        try (PreparedStatement psIns = c.prepareStatement(sqlInsert)) {
            psIns.setInt(1, idEmploye);
            psIns.setBigDecimal(2, tauxCalcule.setScale(2, RoundingMode.HALF_UP));
            psIns.setDate(3, Date.valueOf(dateEffet));
            psIns.setInt(4, idPeriodeReference);
            psIns.setString(5, "Activation explicite de la période " + idPeriodeReference);
            if (idUtilisateur != null) {
                psIns.setInt(6, idUtilisateur);
            } else {
                psIns.setNull(6, Types.INTEGER);
            }
            try (ResultSet rsIns = psIns.executeQuery()) {
                if (rsIns.next()) {
                    return rsIns.getInt(1);
                }
            }
        }
        throw new IllegalStateException("Échec de l'insertion du nouveau taux applicable.");
    }

    /**
     * Évalue de manière multidimensionnelle la situation TCONGE d'un collaborateur à une date de référence.
     * 100% dérivé, non persisté.
     */
    public TcongeConfigurationDTO evaluerConfigurationTconge(int idEmploye, LocalDate dateReference, Connection c) throws SQLException {
        TcongeConfigurationDTO dto = new TcongeConfigurationDTO();
        dto.setIdEmploye(idEmploye);
        dto.setDateReference(dateReference);

        // 1. Résolution du taux applicable (si existant)
        try {
            TauxCongeResolution resTaux = resoudreTauxCongeApplicable(idEmploye, dateReference, null, c);
            dto.setApplicableRateStatus(TcongeApplicableRateStatus.ACTIVE);
            dto.setTauxApplicable(resTaux.getTauxJournalier());
            dto.setSourceProvenance(resTaux.getSourceProvenance());
            dto.setIdTauxApplicable(resTaux.getIdTauxApplicable());
        } catch (Exception e) {
            dto.setApplicableRateStatus(TcongeApplicableRateStatus.NONE);
        }

        // 2. Résolution des références : active et clôturée en attente
        PeriodeReferenceData refActive = resoudrePeriodeReferenceActive(idEmploye, dateReference, c);
        dto.setReferenceActive(refActive);

        // Recherche d'une référence clôturée non encore activée
        String sqlClosedAwaiting = "SELECT r.id_periode_reference, r.date_debut, r.date_fin, r.statut, r.taux_calcule_cloture " +
                "FROM conge_periode_reference r " +
                "WHERE r.id_employe = ? AND r.statut = 'CLOTUREE' " +
                "  AND NOT EXISTS (SELECT 1 FROM conge_taux_applicable t WHERE t.id_periode_reference = r.id_periode_reference) " +
                "ORDER BY r.date_fin DESC LIMIT 1";
        PeriodeReferenceData refClosed = null;
        try (PreparedStatement psClosed = c.prepareStatement(sqlClosedAwaiting)) {
            psClosed.setInt(1, idEmploye);
            try (ResultSet rsClosed = psClosed.executeQuery()) {
                if (rsClosed.next()) {
                    refClosed = new PeriodeReferenceData(
                            rsClosed.getInt("id_periode_reference"),
                            rsClosed.getDate("date_debut").toLocalDate(),
                            rsClosed.getDate("date_fin").toLocalDate(),
                            rsClosed.getString("statut"),
                            rsClosed.getBigDecimal("taux_calcule_cloture")
                    );
                }
            }
        }
        dto.setReferenceClosedAwaitingActivation(refClosed);

        // 3. Détermination de referenceStatus
        if (refActive != null) {
            dto.setReferenceStatus(TcongeReferenceStatus.ACTIVE);
        } else if (refClosed != null) {
            dto.setReferenceStatus(TcongeReferenceStatus.CLOSED_AWAITING_ACTIVATION);
        } else {
            dto.setReferenceStatus(TcongeReferenceStatus.MISSING);
        }

        // 4. Détermination des warnings (REFERENCE_SETUP_REQUIRED, etc.)
        if (dto.getReferenceStatus() == TcongeReferenceStatus.MISSING) {
            dto.addWarning("REFERENCE_SETUP_REQUIRED");
        }
        if (dto.getReferenceStatus() == TcongeReferenceStatus.CLOSED_AWAITING_ACTIVATION) {
            dto.addWarning("REFERENCE_CLOSED_AWAITING_RATE_ACTIVATION");
        }

        // Vérifier discontinuité si référence active
        if (refActive != null) {
            String gap = detecterDiscontinuiteReference(idEmploye, refActive.getDateDebut(), c);
            if (gap != null) {
                dto.addWarning(gap);
            }
        }

        return dto;
    }

    public static class PeriodeReferenceData {
        private final int idPeriodeReference;
        private final LocalDate dateDebut;
        private final LocalDate dateFin;
        private final String statut;
        private final BigDecimal tauxCalculeCloture;

        public PeriodeReferenceData(int idPeriodeReference, LocalDate dateDebut, LocalDate dateFin,
                                    String statut, BigDecimal tauxCalculeCloture) {
            this.idPeriodeReference = idPeriodeReference;
            this.dateDebut = dateDebut;
            this.dateFin = dateFin;
            this.statut = statut;
            this.tauxCalculeCloture = tauxCalculeCloture;
        }

        public int getIdPeriodeReference() { return idPeriodeReference; }
        public int idPeriodeReference() { return idPeriodeReference; }
        public LocalDate getDateDebut() { return dateDebut; }
        public LocalDate dateDebut() { return dateDebut; }
        public LocalDate getDateFin() { return dateFin; }
        public LocalDate dateFin() { return dateFin; }
        public String getStatut() { return statut; }
        public String statut() { return statut; }
        public BigDecimal getTauxCalculeCloture() { return tauxCalculeCloture; }
        public BigDecimal tauxCalculeCloture() { return tauxCalculeCloture; }
    }
}
