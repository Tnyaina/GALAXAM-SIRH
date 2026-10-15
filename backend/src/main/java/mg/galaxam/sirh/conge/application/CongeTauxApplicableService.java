package mg.galaxam.sirh.conge.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mg.galaxam.sirh.contrat.application.EmploymentContextResolver;
import mg.galaxam.sirh.conge.domain.CongeSituationEmployeDTO;
import mg.galaxam.sirh.conge.domain.TcongeConfigurationDTO;
import mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service canonique de gestion et de résolution du taux journalier de congé applicable (TCONGE).
 * Responsable unique de la résolution pour la Paie et le STC (Phase 2A-4C).
 */
@Service
public class CongeTauxApplicableService {

    private final EmploymentContextResolver employmentContextResolver = new EmploymentContextResolver();

    public enum CongeModeReprise { FULL_HISTORY, AGGREGATED_OPENING, MINIMAL_OPENING, NEW_HIRE }

    /** Contrat HTTP local au workflow de reprise/cutover TCONGE. */
    public static class CutoverOpeningRequest {
        private Integer idEmploye;
        private LocalDate dateCutover;
        private CongeModeReprise modeReprise = CongeModeReprise.AGGREGATED_OPENING;
        private BigDecimal soldeJours;
        private BigDecimal valeurSoldeOuverture;
        private BigDecimal tauxJournalierApplicable;
        private LocalDate dateDebutReference;
        private BigDecimal cumulDroitsOuverture;
        private BigDecimal cumulValorisationOuverture;
        private String sourceReprise = "REPRISE_CUTOVER";
        private String referenceArrete;
        private String commentaire;

        public Integer getIdEmploye() { return idEmploye; }
        public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }
        public LocalDate getDateCutover() { return dateCutover; }
        public void setDateCutover(LocalDate dateCutover) { this.dateCutover = dateCutover; }
        public CongeModeReprise getModeReprise() { return modeReprise; }
        public void setModeReprise(CongeModeReprise modeReprise) { this.modeReprise = modeReprise; }
        public BigDecimal getSoldeJours() { return soldeJours; }
        public void setSoldeJours(BigDecimal soldeJours) { this.soldeJours = soldeJours; }
        public BigDecimal getValeurSoldeOuverture() { return valeurSoldeOuverture; }
        public void setValeurSoldeOuverture(BigDecimal valeurSoldeOuverture) { this.valeurSoldeOuverture = valeurSoldeOuverture; }
        public BigDecimal getTauxJournalierApplicable() { return tauxJournalierApplicable; }
        public void setTauxJournalierApplicable(BigDecimal tauxJournalierApplicable) { this.tauxJournalierApplicable = tauxJournalierApplicable; }
        public LocalDate getDateDebutReference() { return dateDebutReference; }
        public void setDateDebutReference(LocalDate dateDebutReference) { this.dateDebutReference = dateDebutReference; }
        public BigDecimal getCumulDroitsOuverture() { return cumulDroitsOuverture; }
        public void setCumulDroitsOuverture(BigDecimal cumulDroitsOuverture) { this.cumulDroitsOuverture = cumulDroitsOuverture; }
        public BigDecimal getCumulValorisationOuverture() { return cumulValorisationOuverture; }
        public void setCumulValorisationOuverture(BigDecimal cumulValorisationOuverture) { this.cumulValorisationOuverture = cumulValorisationOuverture; }
        public String getSourceReprise() { return sourceReprise; }
        public void setSourceReprise(String sourceReprise) { this.sourceReprise = sourceReprise; }
        public String getReferenceArrete() { return referenceArrete; }
        public void setReferenceArrete(String referenceArrete) { this.referenceArrete = referenceArrete; }
        public String getCommentaire() { return commentaire; }
        public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
    }
    public enum TcongeApplicableRateStatus { NONE, ACTIVE }
    public enum TcongeReferenceStatus { MISSING, ACTIVE, CLOSED_AWAITING_ACTIVATION }
    public enum TcongeSourceProvenance {
        KAJY_MIGRATION, CALCUL_PERIODE_REFERENCE, INITIALISATION_BASE_30, REPRISE_CUTOVER
    }

    /** Résultat immuable d'une résolution temporelle TCONGE. */
    public static final class TauxCongeResolution {
        private final BigDecimal tauxJournalier;
        private final TcongeSourceProvenance sourceProvenance;
        private final LocalDate dateEffet;
        private final LocalDate dateFin;
        private final Integer idPeriodeReference;
        private final Integer idTauxApplicable;

        public TauxCongeResolution(BigDecimal tauxJournalier, TcongeSourceProvenance sourceProvenance,
                LocalDate dateEffet, LocalDate dateFin, Integer idPeriodeReference, Integer idTauxApplicable) {
            if (tauxJournalier == null || tauxJournalier.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Le taux journalier de congé doit être strictement positif.");
            }
            if (sourceProvenance == null) {
                throw new IllegalArgumentException("La source de provenance du TCONGE est obligatoire.");
            }
            this.tauxJournalier = tauxJournalier;
            this.sourceProvenance = sourceProvenance;
            this.dateEffet = dateEffet;
            this.dateFin = dateFin;
            this.idPeriodeReference = idPeriodeReference;
            this.idTauxApplicable = idTauxApplicable;
        }

        public BigDecimal getTauxJournalier() { return tauxJournalier; }
        public TcongeSourceProvenance getSourceProvenance() { return sourceProvenance; }
        public LocalDate getDateEffet() { return dateEffet; }
        public LocalDate getDateFin() { return dateFin; }
        public Integer getIdPeriodeReference() { return idPeriodeReference; }
        public Integer getIdTauxApplicable() { return idTauxApplicable; }
    }

    public static LocalDate calculerDateFinReference(LocalDate dateDebut) {
        if (dateDebut == null) {
            throw new IllegalArgumentException("La date de début de la période de référence est obligatoire.");
        }
        return dateDebut.plusMonths(12).minusDays(1);
    }

    private static final Logger log = LoggerFactory.getLogger(CongeTauxApplicableService.class);

    public List<Map<String, Object>> listerPeriodesReference(int idEmploye, Connection c) throws SQLException {
        String sql = "SELECT r.id_periode_reference, r.date_debut, r.date_fin, r.statut, r.taux_calcule_cloture, " +
                "r.cumul_droits_cloture, r.cumul_valorisation_cloture, " +
                "t.date_effet, t.taux_journalier, t.taux_count, " +
                "COALESCE(r.cumul_droits_ouverture, 0) + COALESCE(j.droits, 0) AS cumul_droits_courant, " +
                "COALESCE(r.cumul_valorisation_ouverture, 0) + COALESCE(j.valorisation, 0) AS cumul_valorisation_courante, " +
                "(SELECT MAX(m.date_mouvement) FROM mouvement_solde_conge m " +
                "WHERE m.id_employe=r.id_employe AND m.type_mouvement='ACQUISITION') AS derniere_acquisition " +
                "FROM conge_periode_reference r LEFT JOIN LATERAL (" +
                "SELECT SUM(jours_droit_acquis) AS droits, SUM(valorisation_mensuelle) AS valorisation " +
                "FROM conge_journal_droit WHERE id_periode_reference = r.id_periode_reference" +
                ") j ON TRUE LEFT JOIN LATERAL (" +
                "SELECT MIN(date_effet) AS date_effet, MIN(taux_journalier) AS taux_journalier, COUNT(*) AS taux_count " +
                "FROM conge_taux_applicable WHERE id_periode_reference = r.id_periode_reference" +
                ") t ON TRUE WHERE r.id_employe = ? ORDER BY r.date_debut DESC";
        List<Map<String, Object>> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> periode = new HashMap<>(Map.of(
                            "idPeriodeReference", rs.getInt("id_periode_reference"),
                            "dateDebut", rs.getDate("date_debut").toString(),
                            "dateFin", rs.getDate("date_fin").toString(),
                            "statut", rs.getString("statut"),
                            "cumulDroitsCourant", zeroSiNull(rs.getBigDecimal("cumul_droits_courant")),
                            "cumulValorisationCourante", zeroSiNull(rs.getBigDecimal("cumul_valorisation_courante")),
                            "tauxCalculeCloture", zeroSiNull(rs.getBigDecimal("taux_calcule_cloture")),
                            "cumulDroitsCloture", zeroSiNull(rs.getBigDecimal("cumul_droits_cloture")),
                            "cumulValorisationCloture", zeroSiNull(rs.getBigDecimal("cumul_valorisation_cloture"))));
                    Date derniereAcquisition = rs.getDate("derniere_acquisition");
                    if (derniereAcquisition != null) periode.put("derniereAcquisition", derniereAcquisition.toString());
                    int nombreTaux = rs.getInt("taux_count");
                    if (nombreTaux > 1) throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: plusieurs taux activés pour la référence "
                            + rs.getInt("id_periode_reference"));
                    if (nombreTaux == 1) {
                        periode.put("dateEffetActivation", rs.getDate("date_effet").toString());
                        periode.put("tauxActive", rs.getBigDecimal("taux_journalier"));
                    }
                    result.add(periode);
                }
            }
        }
        return result;
    }

    public List<Map<String, Object>> listerPeriodesEnAttenteActivation(Connection c) throws SQLException {
        String sql = "SELECT r.id_periode_reference, r.id_employe, e.nom, e.prenom, e.matricule, " +
                "r.date_debut, r.date_fin, r.statut, r.taux_calcule_cloture, " +
                "r.cumul_droits_cloture, r.cumul_valorisation_cloture, " +
                "(SELECT MAX(m.date_mouvement) FROM mouvement_solde_conge m " +
                "WHERE m.id_employe=r.id_employe AND m.type_mouvement='ACQUISITION') AS derniere_acquisition " +
                "FROM conge_periode_reference r " +
                "JOIN employe e ON e.id_employe = r.id_employe " +
                "WHERE r.statut = 'CLOTUREE' AND r.taux_calcule_cloture > 0 " +
                "AND NOT EXISTS (SELECT 1 FROM conge_taux_applicable t WHERE t.id_periode_reference = r.id_periode_reference) " +
                "ORDER BY r.date_fin DESC, e.nom ASC, e.prenom ASC";
        List<Map<String, Object>> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("idPeriodeReference", rs.getInt("id_periode_reference"));
                item.put("idEmploye", rs.getInt("id_employe"));
                item.put("nom", rs.getString("nom") != null ? rs.getString("nom") : "");
                item.put("prenom", rs.getString("prenom") != null ? rs.getString("prenom") : "");
                item.put("matricule", rs.getString("matricule") != null ? rs.getString("matricule") : "");
                item.put("dateDebut", rs.getDate("date_debut").toString());
                item.put("dateFin", rs.getDate("date_fin").toString());
                item.put("statut", rs.getString("statut"));
                item.put("tauxCalculeCloture", zeroSiNull(rs.getBigDecimal("taux_calcule_cloture")));
                item.put("cumulDroitsCloture", zeroSiNull(rs.getBigDecimal("cumul_droits_cloture")));
                item.put("cumulValorisationCloture", zeroSiNull(rs.getBigDecimal("cumul_valorisation_cloture")));
                Date derniereAcquisition = rs.getDate("derniere_acquisition");
                if (derniereAcquisition != null) {
                    item.put("derniereAcquisition", derniereAcquisition.toString());
                }
                result.add(item);
            }
        }
        return result;
    }

    private static BigDecimal zeroSiNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String decrireBornesTaux(int employe, Connection c) throws SQLException {
        List<String> versions = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT date_effet,date_fin FROM conge_taux_applicable "
                + "WHERE id_employe=? ORDER BY date_effet")) {
            ps.setInt(1, employe);
            try (ResultSet rs=ps.executeQuery()) {
                while (rs.next()) versions.add("[" + rs.getDate(1) + ", " + rs.getDate(2) + "[");
            }
        }
        return versions.toString();
    }

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
            throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: TCONGE_APPLICABLE_AMBIGUOUS: " + matches.size() +
                    " taux applicables distincts se chevauchent pour l'employé " + idEmploye +
                    " à la date " + dateReference + ". Un arbitrage explicite est requis.");
        }

        if (matches.size() == 1) {
            return matches.get(0);
        }

        // La moyenne cumulée du journal mensuel est une projection de la période en cours.
        // Seul un taux daté et activé peut être utilisé comme TCONGE applicable.
        // Cas 0 match : vérification anti-fallback silencieux pour salarié historique.
        String sqlCheckRef = "SELECT mode_reprise FROM conge_periode_reference WHERE id_employe = ? AND date_debut <= ? AND date_fin >= ?";
        try (PreparedStatement psRef = c.prepareStatement(sqlCheckRef)) {
            psRef.setInt(1, idEmploye);
            psRef.setDate(2, Date.valueOf(dateReference));
            psRef.setDate(3, Date.valueOf(dateReference));
            try (ResultSet rsRef = psRef.executeQuery()) {
                if (rsRef.next()) {
                    String mode = rsRef.getString("mode_reprise");
                    if (mode != null && !mode.equals("NEW_HIRE")) {
                        throw new IllegalStateException("LEAVE_RATE_MISSING: Aucun taux journalier TCONGE configuré pour le salarié historique (ID: " +
                                idEmploye + ") à la date " + dateReference + "; versions=" + decrireBornesTaux(idEmploye, c) + ". Le fallback Base/30 est strictement interdit pour les salariés historiques.");
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("TCONGE_REFERENCE_RESOLUTION_FAILED au " + dateReference, e);
        }

        // Vérification de tout mouvement de congé antérieur (preuve de reprise ou de passif de congés)
        String sqlHist = "SELECT 1 FROM mouvement_solde_conge WHERE id_employe = ? "
                + "UNION ALL SELECT 1 FROM conge_taux_applicable WHERE id_employe = ? LIMIT 1";
        try (PreparedStatement psH = c.prepareStatement(sqlHist)) {
            psH.setInt(1, idEmploye);
            psH.setInt(2, idEmploye);
            try (ResultSet rsH = psH.executeQuery()) {
                if (rsH.next()) {
                    throw new IllegalStateException("LEAVE_RATE_MISSING: Aucun taux journalier TCONGE configuré pour le salarié historique (ID: " +
                            idEmploye + ") à la date " + dateReference + "; versions=" + decrireBornesTaux(idEmploye, c) + ". Le fallback Base/30 est strictement interdit.");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("TCONGE_HISTORY_RESOLUTION_FAILED au " + dateReference, e);
        }

        // 4. Cas nouveau collaborateur pur (NEW_HIRE sans historique) : Amorçage contractuel canonique Base / 30
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
     * Crée l'enveloppe de l'année civile de la date d'acquisition si elle manque.
     * Le verrou employé sérialise deux clôtures concurrentes, dans la transaction de paie.
     * Une référence historique non civile chevauchante reste intacte et bloque la création.
     */
    public int ensureLeaveReferencePeriod(int idEmploye, LocalDate dateAcquisition, Connection c) throws SQLException {
        if (dateAcquisition == null) throw new IllegalArgumentException("Date métier d'acquisition obligatoire.");
        try (PreparedStatement lock = c.prepareStatement("SELECT id_employe FROM employe WHERE id_employe=? FOR UPDATE")) {
            lock.setInt(1, idEmploye);
            try (ResultSet rs = lock.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Employé inconnu : " + idEmploye);
            }
        }
        LocalDate debut = LocalDate.of(dateAcquisition.getYear(), 1, 1);
        LocalDate fin = LocalDate.of(dateAcquisition.getYear(), 12, 31);
        int duree = ParametreGlobal.requirePositiveInt(
                ParametreGlobal.loadActiveMapAtDate(Date.valueOf(debut), c), "DUREE_REFERENCE_TCONGE_MOIS");
        if (duree != 12) throw new IllegalStateException("TCONGE_REFERENCE_ANNUAL_DURATION_REQUIRED: "
                + duree + " mois configurés au " + debut + ", attendu 12.");
        String sql = "SELECT id_periode_reference,date_debut,date_fin,statut FROM conge_periode_reference "
                + "WHERE id_employe=? AND date_fin>=? AND date_debut<=? ORDER BY date_debut";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, Date.valueOf(debut));
            ps.setDate(3, Date.valueOf(fin));
            try (ResultSet rs = ps.executeQuery()) {
                Integer found = null;
                while (rs.next()) {
                    if (found != null) throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: plusieurs références de congé "
                            + "chevauchent " + dateAcquisition + " pour l'employé " + idEmploye);
                    LocalDate existingStart = rs.getDate("date_debut").toLocalDate();
                    LocalDate existingEnd = rs.getDate("date_fin").toLocalDate();
                    if (!debut.equals(existingStart) || !fin.equals(existingEnd))
                        throw new IllegalStateException("TCONGE_REFERENCE_ANNUAL_CONFLICT: référence "
                                + rs.getInt("id_periode_reference") + " [" + existingStart + ", " + existingEnd
                                + "] chevauche l'année civile [" + debut + ", " + fin + "]. Régularisation explicite requise.");
                    if (!"OUVERTE".equals(rs.getString("statut")))
                        throw new IllegalStateException("TCONGE_REFERENCE_CLOSED: référence annuelle "
                                + rs.getInt("id_periode_reference") + " " + rs.getString("statut")
                                + " au " + dateAcquisition);
                    found = rs.getInt("id_periode_reference");
                }
                if (found != null) return found;
            }
        }
        boolean historique = false;
        String historySql = "SELECT EXISTS (SELECT 1 FROM contrat_travail WHERE id_employe=? AND date_debut < ?) " +
                "OR EXISTS (SELECT 1 FROM conge_periode_reference WHERE id_employe=?) " +
                "OR EXISTS (SELECT 1 FROM mouvement_solde_conge WHERE id_employe=?) " +
                "OR EXISTS (SELECT 1 FROM conge_taux_applicable WHERE id_employe=?)";
        try (PreparedStatement ps = c.prepareStatement(historySql)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, Date.valueOf(debut));
            ps.setInt(3, idEmploye);
            ps.setInt(4, idEmploye);
            ps.setInt(5, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                historique = rs.getBoolean(1);
            }
        }
        String modeReprise = historique ? "FULL_HISTORY" : "NEW_HIRE";
        String insert = "INSERT INTO conge_periode_reference "
                + "(id_employe,date_debut,date_fin,statut,mode_reprise,created_by) "
                + "VALUES (?, ?, ?, 'OUVERTE', ?, NULL) RETURNING id_periode_reference";
        try (PreparedStatement ps = c.prepareStatement(insert)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, Date.valueOf(debut));
            ps.setDate(3, Date.valueOf(fin));
            ps.setString(4, modeReprise);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int id = rs.getInt(1);
                log.info("Référence annuelle congé créée automatiquement pour l'employé {} : {} à {} (id={})",
                        idEmploye, debut, fin, id);
                return id;
            }
        }
    }

    /**
     * Résout la période de référence active (OUVERTE) pour un employé couvrant la date de paie donnée.
     */
    public PeriodeReferenceData resoudrePeriodeReferenceActive(int idEmploye,
                                                              LocalDate dateMoisPaie,
                                                              Connection c) throws SQLException {
        String sql = "SELECT id_periode_reference, date_debut, date_fin, statut, " +
                "taux_calcule_cloture, cumul_droits_cloture, cumul_valorisation_cloture, " +
                "mode_reprise, date_cutover, cumul_droits_ouverture, cumul_valorisation_ouverture " +
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
                    Date dCut = rs.getDate("date_cutover");
                    refs.add(new PeriodeReferenceData(
                            rs.getInt("id_periode_reference"),
                            rs.getDate("date_debut").toLocalDate(),
                            rs.getDate("date_fin").toLocalDate(),
                            rs.getString("statut"),
                            rs.getBigDecimal("taux_calcule_cloture"),
                            rs.getString("mode_reprise"),
                            dCut != null ? dCut.toLocalDate() : null,
                            rs.getBigDecimal("cumul_droits_ouverture"),
                            rs.getBigDecimal("cumul_valorisation_ouverture")
                    ));
                }
            }
        }

        if (refs.size() > 1) {
            throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: TCONGE_REFERENCE_PERIOD_AMBIGUOUS: " + refs.size() +
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
                    + idEmploye + " au " + dateMoisPaie + "; références existantes=" + listerPeriodesReference(idEmploye, c) + ". Transition explicite requise avant acquisition.");
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
        String sqlCheck = "SELECT statut, taux_calcule_cloture, mode_reprise, cumul_droits_ouverture, cumul_valorisation_ouverture " +
                "FROM conge_periode_reference WHERE id_periode_reference = ? FOR UPDATE";
        String modeReprise = null;
        BigDecimal cumulDroitsOuverture = null;
        BigDecimal cumulValoOuverture = null;
        try (PreparedStatement psCheck = c.prepareStatement(sqlCheck)) {
            psCheck.setInt(1, idPeriodeReference);
            try (ResultSet rsCheck = psCheck.executeQuery()) {
                if (!rsCheck.next()) {
                    throw new IllegalArgumentException("Période de référence introuvable: ID " + idPeriodeReference);
                }
                if ("CLOTUREE".equals(rsCheck.getString("statut"))) {
                    return rsCheck.getBigDecimal("taux_calcule_cloture");
                }
                modeReprise = rsCheck.getString("mode_reprise");
                cumulDroitsOuverture = rsCheck.getBigDecimal("cumul_droits_ouverture");
                cumulValoOuverture = rsCheck.getBigDecimal("cumul_valorisation_ouverture");
            }
        }

        // La clôture manuelle ne peut pas anticiper l'acquisition du dernier mois civil.
        // Le chemin normal reste la clôture de paie de décembre, qui fige le cycle.
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM conge_journal_droit j "
                + "JOIN conge_periode_reference r ON r.id_periode_reference=j.id_periode_reference "
                + "WHERE r.id_periode_reference=? AND j.annee=EXTRACT(YEAR FROM r.date_fin) "
                + "AND j.mois=EXTRACT(MONTH FROM r.date_fin) LIMIT 1")) {
            ps.setInt(1, idPeriodeReference);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException(
                        "TCONGE_REFERENCE_INCOMPLETE: acquisition du dernier mois absente ; "
                        + "la clôture normale est déclenchée par la paie");
            }
        }

        // Calcul des cumuls depuis conge_journal_droit (post-cutover ou natifs)
        String sqlSum = "SELECT COALESCE(SUM(jours_droit_acquis), 0) AS sum_droits, " +
                "COALESCE(SUM(valorisation_mensuelle), 0) AS sum_valo " +
                "FROM conge_journal_droit WHERE id_periode_reference = ?";
        BigDecimal postDroits = BigDecimal.ZERO;
        BigDecimal postValo = BigDecimal.ZERO;
        try (PreparedStatement psSum = c.prepareStatement(sqlSum)) {
            psSum.setInt(1, idPeriodeReference);
            try (ResultSet rsSum = psSum.executeQuery()) {
                if (rsSum.next()) {
                    postDroits = rsSum.getBigDecimal("sum_droits");
                    postValo = rsSum.getBigDecimal("sum_valo");
                }
            }
        }

        // Guard MINIMAL_OPENING : historique antérieur inconnu -> Clôture automatique interdite
        if ("MINIMAL_OPENING".equals(modeReprise) && cumulDroitsOuverture == null) {
            throw new IllegalStateException("CYCLE_ACCUMULATORS_UNKNOWN: Les données antérieures au cutover sont inconnues pour la période " +
                    idPeriodeReference + " (MINIMAL_OPENING). Clôture automatique impossible sans arbitrage RH.");
        }

        BigDecimal cumulDroits = postDroits.add(cumulDroitsOuverture != null ? cumulDroitsOuverture : BigDecimal.ZERO);
        BigDecimal cumulValo = postValo.add(cumulValoOuverture != null ? cumulValoOuverture : BigDecimal.ZERO);

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
        LocalDate finReference;
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
                finReference = rsRef.getDate("date_fin").toLocalDate();
            }
        }
        if (!dateEffet.isAfter(finReference)) {
            throw new IllegalStateException("TCONGE_ACTIVATION_INVALID_DATE_EFFET: le taux calculé sur la référence "
                    + finReference.getYear() + " doit prendre effet après le " + finReference + ".");
        }

        // 2. Vérifier si cette référence a déjà été activée (taux calculé de clôture)
        String sqlCheckExisting = "SELECT id_taux_applicable, taux_journalier, date_effet " +
                "FROM conge_taux_applicable WHERE id_periode_reference = ? AND source_provenance = 'CALCUL_PERIODE_REFERENCE' FOR UPDATE";
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

        try (PreparedStatement acquis = c.prepareStatement("SELECT MIN(date_mouvement) "
                + "FROM mouvement_solde_conge WHERE id_employe=? AND type_mouvement='ACQUISITION' "
                + "AND date_mouvement>=?")) {
            acquis.setInt(1, idEmploye);
            acquis.setDate(2, Date.valueOf(dateEffet));
            try (ResultSet rs = acquis.executeQuery()) {
                rs.next();
                Date premiereAcquisition = rs.getDate(1);
                if (premiereAcquisition != null) throw new IllegalStateException(
                        "TCONGE_ACTIVATION_RETROACTIVE: acquisition déjà figée au " + premiereAcquisition
                                + " depuis la date d'effet " + dateEffet
                                + ". Choisir une date postérieure aux acquisitions clôturées et régulariser les écarts en paie.");
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
        } catch (IllegalStateException e) {
            if (!e.getMessage().startsWith("LEAVE_RATE_MISSING") && !e.getMessage().startsWith("TCONGE_CONFIGURATION_MISSING")) throw e;
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

    /**
     * Enregistre un cutover snapshot initial de manière transactionnelle et atomique (Section 17-19 & 27).
     * Crée la période de référence bornée avec ses cumuls d'ouverture,
     * le taux TCONGE applicable de cutover, et le mouvement d'ajustement du solde consommable.
     */
    public int enregistrerCutoverSnapshot(CutoverOpeningRequest req,
                                         Integer idUtilisateur,
                                         Connection c) throws SQLException {
        if (req == null) {
            throw new IllegalArgumentException("La requête de cutover est obligatoire.");
        }
        if (req.getIdEmploye() == null) {
            throw new IllegalArgumentException("L'identifiant employé est obligatoire.");
        }
        if (req.getDateCutover() == null) {
            throw new IllegalArgumentException("La date de cutover est obligatoire.");
        }
        if (req.getReferenceArrete() == null || req.getReferenceArrete().isBlank()) {
            throw new IllegalArgumentException("CONGE_CUTOVER_SOURCE_REQUIRED: indiquez la référence de l'arrêté DAF utilisé pour la reprise.");
        }
        String traceArrete = "Arrêté DAF : " + req.getReferenceArrete().trim()
                + (req.getCommentaire() == null || req.getCommentaire().isBlank()
                        ? "" : " ; " + req.getCommentaire().trim());
        if (req.getSoldeJours() == null || req.getSoldeJours().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le solde de jours initial doit être positif ou nul.");
        }
        if (req.getValeurSoldeOuverture() == null
                || req.getValeurSoldeOuverture().signum() < 0
                || (req.getSoldeJours().signum() == 0 && req.getValeurSoldeOuverture().signum() != 0)
                || (req.getSoldeJours().signum() > 0 && req.getValeurSoldeOuverture().signum() == 0)) {
            throw new IllegalArgumentException("La valeur du solde repris doit être cohérente avec les jours disponibles.");
        }
        if (req.getTauxJournalierApplicable() == null || req.getTauxJournalierApplicable().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le taux TCONGE applicable initial doit être strictement positif.");
        }
        if (req.getDateDebutReference() == null) {
            throw new IllegalArgumentException("La date de début de période de référence est obligatoire.");
        }

        CongeModeReprise mode = req.getModeReprise() != null ? req.getModeReprise() : CongeModeReprise.AGGREGATED_OPENING;

        if (mode == CongeModeReprise.AGGREGATED_OPENING) {
            if (req.getCumulDroitsOuverture() == null || req.getCumulDroitsOuverture().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Le cumul des droits à l'ouverture est obligatoire pour AGGREGATED_OPENING.");
            }
            if (req.getCumulValorisationOuverture() == null || req.getCumulValorisationOuverture().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Le cumul de valorisation à l'ouverture est obligatoire pour AGGREGATED_OPENING.");
            }
            if ((req.getCumulDroitsOuverture().signum() == 0)
                    != (req.getCumulValorisationOuverture().signum() == 0)) {
                throw new IllegalArgumentException("CONGE_CUTOVER_ACCUMULATORS_INCONSISTENT: les jours acquis de l'année "
                        + "et leur valorisation doivent être tous deux nuls ou tous deux positifs.");
            }
        }

        LocalDate dateDebut = req.getDateDebutReference();
        if (!dateDebut.equals(req.getDateCutover().withDayOfYear(1))) {
            throw new IllegalArgumentException(
                    "CONGE_CUTOVER_REFERENCE_ANNUAL_BOUNDS_REQUIRED: la reprise du " + req.getDateCutover()
                            + " appartient à la référence civile ouverte le 01/01/" + req.getDateCutover().getYear());
        }
        LocalDate dateFin = calculerDateFinReference(dateDebut);
        validerDureeReference(dateDebut, dateFin, c);
        if (req.getDateCutover().isBefore(dateDebut) || req.getDateCutover().isAfter(dateFin)) {
            throw new IllegalArgumentException("La date de bascule doit appartenir à la période de référence reprise.");
        }

        // La reprise n'est possible qu'avant la première écriture de droits dans le SIRH.
        // Une réexécution ne doit jamais doubler le stock ni écraser des lots historiques.
        try (PreparedStatement ps = c.prepareStatement("SELECT id_employe FROM employe WHERE id_employe=? FOR UPDATE")) {
            ps.setInt(1, req.getIdEmploye());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Employé introuvable pour la reprise congés.");
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM mouvement_solde_conge WHERE id_employe = ?) " +
                "OR EXISTS (SELECT 1 FROM conge_journal_droit WHERE id_employe = ?) " +
                "OR EXISTS (SELECT 1 FROM conge_taux_applicable WHERE id_employe = ?)")) {
            ps.setInt(1, req.getIdEmploye());
            ps.setInt(2, req.getIdEmploye());
            ps.setInt(3, req.getIdEmploye());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getBoolean(1)) throw new IllegalStateException(
                        "CONGE_CUTOVER_ALREADY_INITIALIZED: droits, mouvements ou taux déjà enregistrés pour l'employé "
                                + req.getIdEmploye());
            }
        }

        // 1. Vérifier l'absence de chevauchement de référence
        verifierAbsenceChevauchementReference(req.getIdEmploye(), dateDebut, dateFin, null, c);

        // 2. Insérer la période de référence
        String sqlRef = "INSERT INTO conge_periode_reference (" +
                "id_employe, date_debut, date_fin, statut, created_by, " +
                "mode_reprise, date_cutover, cumul_droits_ouverture, cumul_valorisation_ouverture" +
                ") VALUES (?, ?, ?, 'OUVERTE', ?, ?, ?, ?, ?) RETURNING id_periode_reference";

        int idPeriodeRef;
        try (PreparedStatement psRef = c.prepareStatement(sqlRef)) {
            psRef.setInt(1, req.getIdEmploye());
            psRef.setDate(2, Date.valueOf(dateDebut));
            psRef.setDate(3, Date.valueOf(dateFin));
            if (idUtilisateur != null) {
                psRef.setInt(4, idUtilisateur);
            } else {
                psRef.setNull(4, Types.INTEGER);
            }
            psRef.setString(5, mode.name());
            psRef.setDate(6, Date.valueOf(req.getDateCutover()));
            if (req.getCumulDroitsOuverture() != null) {
                psRef.setBigDecimal(7, req.getCumulDroitsOuverture());
            } else {
                psRef.setNull(7, Types.NUMERIC);
            }
            if (req.getCumulValorisationOuverture() != null) {
                psRef.setBigDecimal(8, req.getCumulValorisationOuverture());
            } else {
                psRef.setNull(8, Types.NUMERIC);
            }

            try (ResultSet rsRef = psRef.executeQuery()) {
                if (rsRef.next()) {
                    idPeriodeRef = rsRef.getInt(1);
                } else {
                    throw new IllegalStateException("Impossible d'insérer la période de référence de cutover.");
                }
            }
        }

        // 3. Premier taux de reprise. Un taux déjà présent est rejeté plus haut.
        String sqlTaux = "INSERT INTO conge_taux_applicable (" +
                "id_employe, taux_journalier, date_effet, date_fin, source_provenance, id_periode_reference, commentaire, cree_par" +
                ") VALUES (?, ?, ?, NULL, 'REPRISE_CUTOVER', ?, ?, ?)";
        try (PreparedStatement psTaux = c.prepareStatement(sqlTaux)) {
            psTaux.setInt(1, req.getIdEmploye());
            psTaux.setBigDecimal(2, req.getTauxJournalierApplicable().setScale(2, RoundingMode.HALF_UP));
            psTaux.setDate(3, Date.valueOf(req.getDateCutover()));
            psTaux.setInt(4, idPeriodeRef);
            psTaux.setString(5, traceArrete);
            if (idUtilisateur != null) {
                psTaux.setInt(6, idUtilisateur);
            } else {
                psTaux.setNull(6, Types.INTEGER);
            }
            psTaux.executeUpdate();
        }

        // 4. Insérer le mouvement de solde initial
        String sqlMvt = "INSERT INTO mouvement_solde_conge (" +
                "id_employe, type_mouvement, nb_jours, date_mouvement, commentaire, created_by, valeur_source" +
                ") VALUES (?, 'AJUSTEMENT', ?, ?, ?, ?, ?)";
        try (PreparedStatement psMvt = c.prepareStatement(sqlMvt)) {
            psMvt.setInt(1, req.getIdEmploye());
            psMvt.setBigDecimal(2, req.getSoldeJours().setScale(2, RoundingMode.HALF_UP));
            psMvt.setDate(3, Date.valueOf(req.getDateCutover()));
            psMvt.setString(4, "[SOLDE_INITIAL] Reprise cutover - Solde initial au " + req.getDateCutover() + " : " + traceArrete);
            if (idUtilisateur != null) {
                psMvt.setInt(5, idUtilisateur);
            } else {
                psMvt.setNull(5, Types.INTEGER);
            }
            psMvt.setBigDecimal(6, req.getValeurSoldeOuverture().setScale(2, RoundingMode.HALF_UP));
            psMvt.executeUpdate();
        }

        log.info("[CongeTauxApplicableService] Cutover enregistré avec succès pour l'employé {} : solde={}j, TCONGE={}Ar/j, mode={}",
                req.getIdEmploye(), req.getSoldeJours(), req.getTauxJournalierApplicable(), mode);

        return idPeriodeRef;
    }

    /** Vrai si l'acquisition à la date donnée est déjà comprise dans l'arrêté de reprise. */
    public boolean acquisitionDejaReprise(int idEmploye, LocalDate dateAcquisition, Connection c) throws SQLException {
        if (dateAcquisition == null) throw new IllegalArgumentException("Date métier d'acquisition obligatoire.");
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT date_cutover FROM conge_periode_reference WHERE id_employe=? AND date_cutover>=? "
                + "ORDER BY date_cutover LIMIT 2")) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, Date.valueOf(dateAcquisition));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                LocalDate cutover = rs.getDate(1).toLocalDate();
                if (rs.next()) throw new IllegalStateException(
                        "AMBIGUOUS_CONFIGURATION: plusieurs cutovers incluent la clôture " + dateAcquisition
                                + " pour l'employé " + idEmploye);
                return !dateAcquisition.isAfter(cutover);
            }
        }
    }

    /**
     * Récupère la situation complète d'un salarié (Correction 1 et Section 21).
     * Expose le solde consommable, le TCONGE as-of, et la période de référence avec ses accumulateurs.
     */
    public CongeSituationEmployeDTO obtenirSituationComplete(int idEmploye, LocalDate dateReference, Connection c) throws SQLException {
        if (dateReference == null) {
            dateReference = LocalDate.now();
        }
        CongeSituationEmployeDTO dto = new CongeSituationEmployeDTO();
        dto.setIdEmploye(idEmploye);

        // Informations employé
        String sqlEmp = "SELECT matricule, nom, prenom FROM employe WHERE id_employe = ?";
        try (PreparedStatement psEmp = c.prepareStatement(sqlEmp)) {
            psEmp.setInt(1, idEmploye);
            try (ResultSet rsEmp = psEmp.executeQuery()) {
                if (rsEmp.next()) {
                    dto.setMatricule(rsEmp.getString("matricule"));
                    dto.setNom(rsEmp.getString("nom"));
                    dto.setPrenom(rsEmp.getString("prenom"));
                }
            }
        }

        // Même contexte contrat/affectation que la paie, résolu à la date demandée.
        var contexteEmploi = employmentContextResolver.resolveContext(idEmploye, dateReference, c);
        BigDecimal salaireBase = contexteEmploi != null && contexteEmploi.salaireBase() != null
                ? contexteEmploi.salaireBase() : BigDecimal.ZERO;
        dto.setSalaireBase(salaireBase);

        // A. Solde consommable
        String sqlSolde = "SELECT COALESCE(SUM(nb_jours),0) AS solde, "
                + "COALESCE(SUM(nb_jours) FILTER (WHERE type_mouvement='ACQUISITION'),0) AS total_acquis, "
                + "COALESCE(-SUM(nb_jours) FILTER (WHERE type_mouvement='PRISE'),0) AS total_pris, "
                + "MAX(date_mouvement) FILTER (WHERE type_mouvement='ACQUISITION') AS derniere_acq "
                + "FROM mouvement_solde_conge WHERE id_employe=? AND date_mouvement<=?";
        try (PreparedStatement psSolde = c.prepareStatement(sqlSolde)) {
            psSolde.setInt(1, idEmploye);
            psSolde.setDate(2, Date.valueOf(dateReference));
            try (ResultSet rsSolde = psSolde.executeQuery()) {
                if (rsSolde.next()) {
                    dto.setSoldeJours(rsSolde.getBigDecimal("solde") != null ? rsSolde.getBigDecimal("solde") : BigDecimal.ZERO);
                    dto.setTotalAcquis(rsSolde.getBigDecimal("total_acquis") != null ? rsSolde.getBigDecimal("total_acquis") : BigDecimal.ZERO);
                    dto.setTotalPris(rsSolde.getBigDecimal("total_pris") != null ? rsSolde.getBigDecimal("total_pris") : BigDecimal.ZERO);
                    Date dAcq = rsSolde.getDate("derniere_acq");
                    if (dAcq != null) {
                        dto.setDerniereAcquisition(dAcq.toLocalDate());
                    }
                } else {
                    dto.setSoldeJours(BigDecimal.ZERO);
                    dto.setTotalAcquis(BigDecimal.ZERO);
                    dto.setTotalPris(BigDecimal.ZERO);
                }
            }
        }

        // B. TCONGE applicable as-of
        try {
            TauxCongeResolution resTaux = resoudreTauxCongeApplicable(idEmploye, dateReference, salaireBase, c);
            if (resTaux != null) {
                CongeSituationEmployeDTO.TauxApplicableDTO tauxDto = new CongeSituationEmployeDTO.TauxApplicableDTO();
                tauxDto.setMontant(resTaux.getTauxJournalier());
                tauxDto.setDateEffet(resTaux.getDateEffet());
                tauxDto.setDateFin(resTaux.getDateFin());
                tauxDto.setSource(resTaux.getSourceProvenance() != null ? resTaux.getSourceProvenance().name() : null);
                dto.setTauxApplicable(tauxDto);
            }
        } catch (IllegalStateException e) {
            if (!e.getMessage().startsWith("LEAVE_RATE_MISSING") && !e.getMessage().startsWith("TCONGE_CONFIGURATION_MISSING")) throw e;
            log.debug("[CongeTauxApplicableService] TCONGE non résolu au {} pour employé {}: {}", dateReference, idEmploye, e.getMessage());
        }

        // C. Période de référence & accumulateurs
        PeriodeReferenceData refActive = resoudrePeriodeReferenceActive(idEmploye, dateReference, c);
        if (refActive != null) {
            CongeSituationEmployeDTO.PeriodeReferenceDTO perDto = new CongeSituationEmployeDTO.PeriodeReferenceDTO();
            perDto.setIdPeriodeReference(refActive.getIdPeriodeReference());
            perDto.setDateDebut(refActive.getDateDebut());
            perDto.setDateFin(refActive.getDateFin());
            perDto.setStatut(refActive.getStatut());
            perDto.setModeReprise(refActive.getModeReprise());
            perDto.setDateCutover(refActive.getDateCutover());
            perDto.setCumulDroitsOuverture(refActive.getCumulDroitsOuverture());
            perDto.setCumulValorisationOuverture(refActive.getCumulValorisationOuverture());

            // Le journal ne peut pas apporter en décembre les droits acquis à la clôture de janvier.
            String sqlPost = "SELECT j.id_journal, j.jours_droit_acquis, j.valorisation_mensuelle, p.date_fin "
                    + "FROM conge_journal_droit j LEFT JOIN periode_paie p ON p.id_periode_paie=j.id_periode_paie "
                    + "WHERE j.id_periode_reference=?";
            BigDecimal postDroits = BigDecimal.ZERO, postValo = BigDecimal.ZERO;
            try (PreparedStatement psPost=c.prepareStatement(sqlPost)) {
                psPost.setInt(1,refActive.getIdPeriodeReference());
                try (ResultSet rsPost=psPost.executeQuery()) {
                    while (rsPost.next()) {
                        Date closingDate=rsPost.getDate("date_fin");
                        if (closingDate==null) throw new IllegalStateException("TCONGE_JOURNAL_DATE_MISSING: journal "
                                + rsPost.getInt("id_journal") + " sans date métier de clôture au " + dateReference);
                        if (!closingDate.toLocalDate().isAfter(dateReference)) {
                            postDroits=postDroits.add(zeroSiNull(rsPost.getBigDecimal("jours_droit_acquis")));
                            postValo=postValo.add(zeroSiNull(rsPost.getBigDecimal("valorisation_mensuelle")));
                        }
                    }
                }
            }
            perDto.setCumulDroitsPostCutover(postDroits);
            perDto.setCumulValorisationPostCutover(postValo);

            if ("MINIMAL_OPENING".equals(refActive.getModeReprise()) && refActive.getCumulDroitsOuverture() == null) {
                perDto.setOpeningComplete(false);
                perDto.setClotureAutomatiquePossible(false);
                perDto.setCumulDroitsTotal(null);
                perDto.setCumulValorisationTotal(null);
                perDto.setTauxProjection(null);
            } else {
                perDto.setOpeningComplete(true);
                perDto.setClotureAutomatiquePossible(true);
                BigDecimal totalDroits = postDroits.add(refActive.getCumulDroitsOuverture() != null ? refActive.getCumulDroitsOuverture() : BigDecimal.ZERO);
                BigDecimal totalValo = postValo.add(refActive.getCumulValorisationOuverture() != null ? refActive.getCumulValorisationOuverture() : BigDecimal.ZERO);
                perDto.setCumulDroitsTotal(totalDroits);
                perDto.setCumulValorisationTotal(totalValo);
                if (totalDroits.compareTo(BigDecimal.ZERO) > 0) {
                    perDto.setTauxProjection(totalValo.divide(totalDroits, 2, RoundingMode.HALF_UP));
                }
            }

            dto.setPeriodeReference(perDto);
        }

        return dto;
    }

    public static class PeriodeReferenceData {
        private final int idPeriodeReference;
        private final LocalDate dateDebut;
        private final LocalDate dateFin;
        private final String statut;
        private final BigDecimal tauxCalculeCloture;
        private final String modeReprise;
        private final LocalDate dateCutover;
        private final BigDecimal cumulDroitsOuverture;
        private final BigDecimal cumulValorisationOuverture;

        public PeriodeReferenceData(int idPeriodeReference, LocalDate dateDebut, LocalDate dateFin,
                                    String statut, BigDecimal tauxCalculeCloture) {
            this(idPeriodeReference, dateDebut, dateFin, statut, tauxCalculeCloture, null, null, null, null);
        }

        public PeriodeReferenceData(int idPeriodeReference, LocalDate dateDebut, LocalDate dateFin,
                                    String statut, BigDecimal tauxCalculeCloture,
                                    String modeReprise, LocalDate dateCutover,
                                    BigDecimal cumulDroitsOuverture, BigDecimal cumulValorisationOuverture) {
            this.idPeriodeReference = idPeriodeReference;
            this.dateDebut = dateDebut;
            this.dateFin = dateFin;
            this.statut = statut;
            this.tauxCalculeCloture = tauxCalculeCloture;
            this.modeReprise = modeReprise;
            this.dateCutover = dateCutover;
            this.cumulDroitsOuverture = cumulDroitsOuverture;
            this.cumulValorisationOuverture = cumulValorisationOuverture;
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
        public String getModeReprise() { return modeReprise; }
        public LocalDate getDateCutover() { return dateCutover; }
        public BigDecimal getCumulDroitsOuverture() { return cumulDroitsOuverture; }
        public BigDecimal getCumulValorisationOuverture() { return cumulValorisationOuverture; }
    }
}
