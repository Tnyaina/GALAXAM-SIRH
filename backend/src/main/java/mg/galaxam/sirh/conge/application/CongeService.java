package mg.galaxam.sirh.conge.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import mg.galaxam.sirh.conge.persistence.DemandeConge;
import mg.galaxam.sirh.conge.persistence.MouvementSoldeConge;
import mg.galaxam.sirh.conge.persistence.RefMotifEvenementFamilial;
import mg.galaxam.sirh.conge.persistence.RefPoolCongeAnnuel;
import mg.galaxam.sirh.conge.persistence.RefStatutDemandeConge;
import mg.galaxam.sirh.conge.persistence.RefTypeConge;
import mg.galaxam.sirh.conge.persistence.VSoldeCongeEmploye;
import mg.galaxam.sirh.conge.persistence.VDemandeCongeCpl;
import mg.galaxam.sirh.common.dto.ActionCapability;
import mg.galaxam.sirh.conge.domain.CongeCalculateurEngine;
import mg.galaxam.sirh.dashboard.application.NotificationRhService;
import mg.galaxam.sirh.conge.domain.PurgeFifoEngine;
import mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine;
import mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal;
import org.springframework.stereotype.Service;

@Service
public class CongeService {

    private final CongeCalculateurEngine calculateurEngine;
    private final CalendrierOuvrableEngine calendrierOuvrableEngine;
    private final mg.galaxam.sirh.conge.domain.PurgeFifoEngine purgeFifoEngine;

    @org.springframework.beans.factory.annotation.Autowired
    public CongeService(
            CongeCalculateurEngine calculateurEngine,
            CalendrierOuvrableEngine calendrierOuvrableEngine,
            mg.galaxam.sirh.conge.domain.PurgeFifoEngine purgeFifoEngine) {
        this.calculateurEngine = calculateurEngine;
        this.calendrierOuvrableEngine = calendrierOuvrableEngine != null ? calendrierOuvrableEngine : new CalendrierOuvrableEngine();
        this.purgeFifoEngine = purgeFifoEngine != null ? purgeFifoEngine : new mg.galaxam.sirh.conge.domain.PurgeFifoEngine();
    }

    public CongeService(CongeCalculateurEngine calculateurEngine, CalendrierOuvrableEngine calendrierOuvrableEngine) {
        this(calculateurEngine, calendrierOuvrableEngine, new mg.galaxam.sirh.conge.domain.PurgeFifoEngine());
    }

    public CongeService(CongeCalculateurEngine calculateurEngine) {
        this(calculateurEngine, new CalendrierOuvrableEngine(), new mg.galaxam.sirh.conge.domain.PurgeFifoEngine());
    }

    public CongeCalculateurEngine getCalculateurEngine() {
        return this.calculateurEngine;
    }

    public void appliquerActionsDisponibles(VDemandeCongeCpl demande,
            boolean canWrite, boolean canValidate, Connection c) throws Exception {
        if (demande == null) return;
        String statut = demande.getCodeStatutDemande();
        boolean brouillon = "BROUILLON".equalsIgnoreCase(statut);
        boolean attente = "EN_ATTENTE".equalsIgnoreCase(statut);
        boolean valide = "VALIDE".equalsIgnoreCase(statut);
        boolean future = demande.getDateDebut() != null
                && demande.getDateDebut().toLocalDate().isAfter(LocalDate.now());
        boolean periodeFermee = false;
        if (demande.getDateDebut() != null && demande.getDateFin() != null) {
            String sql = "SELECT 1 FROM periode_paie p JOIN ref_statut_periode_paie s " +
                    "ON p.id_statut_periode = s.id_statut_periode " +
                    "WHERE p.date_debut <= ? AND p.date_fin >= ? " +
                    "AND s.code IN ('CLOTUREE', 'PAIE_VALIDEE') LIMIT 1";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setDate(1, demande.getDateFin());
                ps.setDate(2, demande.getDateDebut());
                try (ResultSet rs = ps.executeQuery()) { periodeFermee = rs.next(); }
            }
        }

        java.util.Map<String, ActionCapability> actions = new java.util.LinkedHashMap<>();
        actions.put("EDIT", capability(canWrite, (brouillon || attente) && !periodeFermee,
                periodeFermee ? "PAYROLL_PERIOD_CLOSED" : "INVALID_STATE"));
        actions.put("SUBMIT", capability(canWrite, brouillon && !periodeFermee,
                periodeFermee ? "PAYROLL_PERIOD_CLOSED" : "INVALID_STATE"));
        ActionCapability validation = capability(canValidate, attente && !periodeFermee,
                periodeFermee ? "PAYROLL_PERIOD_CLOSED" : "INVALID_STATE");
        actions.put("VALIDATE", validation);
        actions.put("APPROVE", validation); // compatibilité du frontend existant
        actions.put("REJECT", capability(canValidate, attente && !periodeFermee,
                periodeFermee ? "PAYROLL_PERIOD_CLOSED" : "INVALID_STATE"));
        actions.put("CANCEL", capability(canWrite || canValidate, (brouillon || attente || valide) && !periodeFermee,
                periodeFermee ? "PAYROLL_PERIOD_CLOSED" : "INVALID_STATE"));
        actions.put("MODIFY_DATES", capability(canValidate, valide && future && !periodeFermee,
                periodeFermee ? "PAYROLL_PERIOD_CLOSED" : (valide ? "LEAVE_ALREADY_STARTED" : "INVALID_STATE")));
        demande.setAvailableActions(actions);
    }

    private static ActionCapability capability(boolean authorized, boolean stateAllowed, String reason) {
        if (!authorized) return ActionCapability.denied("FORBIDDEN");
        return stateAllowed ? ActionCapability.permit() : ActionCapability.denied(reason);
    }

    private int getParamGlobalInt(String code, Connection c) {
        return getParamGlobalInt(code, LocalDate.now(), c);
    }

    private int getParamGlobalInt(String code, LocalDate dateMetier, Connection c) {
        return ParametreGlobal.requirePositiveInt(ParametreGlobal.loadActiveMapAtDate(Date.valueOf(dateMetier), c), code);
    }

    /** Retourne null si le plafond est volontairement désactivé ; une configuration absente/invalide est bloquante. */
    private BigDecimal resoudrePlafondCumulConges(
            java.util.Map<String, ParametreGlobal.ParametreEtat> parametres) {
        var resolution = ParametreGlobal.resolve(parametres, "PLAFOND_CUMUL_SOLDE_CONGE_JOURS");
        if (resolution.isMissing()) {
            throw new IllegalStateException("Le paramètre PLAFOND_CUMUL_SOLDE_CONGE_JOURS est absent.");
        }
        if (resolution.isDisabled()) return null;
        try {
            BigDecimal plafond = new BigDecimal(resolution.valeur().trim());
            if (plafond.signum() <= 0) throw new NumberFormatException("Le plafond doit être positif.");
            return plafond;
        } catch (RuntimeException e) {
            throw new IllegalStateException("Le paramètre actif PLAFOND_CUMUL_SOLDE_CONGE_JOURS est invalide.", e);
        }
    }

    public mg.galaxam.sirh.conge.domain.PurgeFifoEngine getPurgeFifoEngine() {
        return this.purgeFifoEngine;
    }

    public java.util.Map<Integer, Double> calculerExpirationsPotentielles(
            LocalDate dateEcheanceMax, Connection c) throws Exception {
        return calculerExpirationsPotentielles(LocalDate.now(), dateEcheanceMax, c);
    }

    public java.util.Map<Integer, Double> calculerExpirationsPotentielles(
            LocalDate dateReference, LocalDate dateEcheanceMax, Connection c) throws Exception {
        LocalDate ref = dateReference != null ? dateReference : LocalDate.now();
        LocalDate echeanceMax = dateEcheanceMax != null ? dateEcheanceMax : ref.plusDays(30);
        int delaiMois = ParametreGlobal.requirePositiveInt(
                ParametreGlobal.loadActiveMapAtDate(Date.valueOf(ref), c), "DUREE_PRESCRIPTION_SOLDE_CONGE_MOIS");
        java.util.Map<Integer, java.util.List<PurgeFifoEngine.MouvementLedger>> mouvements = new java.util.HashMap<>();
        String sql = "SELECT id_employe, id_mouvement, type_mouvement, nb_jours, date_mouvement, id_demande_conge, commentaire "
                + "FROM mouvement_solde_conge ORDER BY date_mouvement, id_mouvement";
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Date dateSql = rs.getDate("date_mouvement");
                LocalDate date = dateSql == null ? ref : dateSql.toLocalDate();
                int idDemandeValue = rs.getInt("id_demande_conge");
                Integer idDemande = rs.wasNull() ? null : idDemandeValue;
                mouvements.computeIfAbsent(rs.getInt("id_employe"), ignored -> new java.util.ArrayList<>())
                        .add(new PurgeFifoEngine.MouvementLedger(rs.getInt("id_mouvement"),
                                rs.getString("type_mouvement"), rs.getDouble("nb_jours"), date,
                                idDemande, rs.getString("commentaire")));
            }
        }
        java.util.Map<Integer, Double> resultats = new java.util.HashMap<>();
        for (var entry : mouvements.entrySet()) {
            double jours = purgeFifoEngine.calculerDroitsDisponibles(entry.getValue(), ref, delaiMois).stream()
                    .filter(d -> !d.dateAcquisition().plusMonths(delaiMois).isAfter(echeanceMax))
                    .mapToDouble(PurgeFifoEngine.DroitDisponible::joursRestants).sum();
            if (jours > 0.001) resultats.put(entry.getKey(), jours);
        }
        return java.util.Map.copyOf(resultats);
    }

    /**
     * Calcule le droit à congé acquis pour une période mensuelle en fonction
     * des absences injustifiées, avec résolution dynamique du taux légal (paie_config_legale)
     * et du diviseur calendaire (parametre_global: DIVISEUR_JOURS_CALENDAIRE_CONGE).
     */
    public BigDecimal calculerDroitAcquis(BigDecimal absencesInjustifiees, LocalDate dateRef, Connection c) {
        BigDecimal abs = (absencesInjustifiees != null) ? absencesInjustifiees.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal tauxAcquisition = CongeCalculateurEngine.TAUX_ACQUISITION_MENSUEL; // 2.50
        BigDecimal diviseur = new BigDecimal("30.0");

        if (c != null) {
            try {
                // 1. Taux légal mensuel actif
                if (dateRef == null) throw new IllegalArgumentException("La date métier d'acquisition est obligatoire.");
                LocalDate d = dateRef;
                mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale configLegale =
                        mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale.findActiveAt(c, d);
                if (configLegale == null || configLegale.getAcquisitionCongeMensuelJours() == null) {
                    throw new IllegalStateException("Taux d'acquisition des congés absent de la configuration légale au " + d + ".");
                }
                tauxAcquisition = configLegale.getAcquisitionCongeMensuelJours();

                // 2. Diviseur calendaire configurable
                java.util.Map<String, String> parametres = ParametreGlobal.loadActiveMapAtDate(Date.valueOf(d), c);
                diviseur = BigDecimal.valueOf(ParametreGlobal.requirePositiveInt(
                        parametres, "DIVISEUR_JOURS_CALENDAIRE_CONGE"));
            } catch (Exception e) {
                throw new IllegalStateException("Impossible de charger les paramètres d'acquisition des congés.", e);
            }
        }

        BigDecimal formule = diviseur.subtract(abs).max(BigDecimal.ZERO);
        return calculateurEngine.calculerAcquisitionMensuelleProratisee(formule, tauxAcquisition, diviseur);
    }

    /**
     * Calcule le droit à congé acquis pour une période mensuelle (surcharge sans connexion BDD).
     */
    public BigDecimal calculerDroitAcquis(BigDecimal absencesInjustifiees) {
        return calculerDroitAcquis(absencesInjustifiees, LocalDate.now(), null);
    }

    /**
     * Enregistre de façon canonique et idempotente l'acquisition mensuelle de congés
     * dans le grand-livre mouvement_solde_conge.
     * Si un mouvement d'acquisition existe déjà pour ce mois, il est ajusté si nécessaire.
     */
    public BigDecimal enregistrerAcquisitionMensuelle(int idEmploye, int annee, int mois,
            BigDecimal absencesInjustifiees, Integer currentUserId, Connection c) throws Exception {
        LocalDate debutMois = LocalDate.of(annee, mois, 1);
        return enregistrerAcquisitionMensuelle(idEmploye, annee, mois, debutMois,
                debutMois.withDayOfMonth(debutMois.lengthOfMonth()), absencesInjustifiees, currentUserId, c);
    }

    /** Une période de paie complète vaut un mois ; l'entrée ou la sortie est bornée par le contrat. */
    public BigDecimal enregistrerAcquisitionMensuelle(int idEmploye, int annee, int mois,
            LocalDate debutPeriode, LocalDate finPeriode, BigDecimal absencesInjustifiees,
            Integer currentUserId, Connection c) throws Exception {
        return enregistrerAcquisitionMensuelle(idEmploye, annee, mois, debutPeriode, finPeriode,
                absencesInjustifiees, null, currentUserId, c);
    }

    public BigDecimal enregistrerAcquisitionMensuelle(int idEmploye, int annee, int mois,
            LocalDate debutPeriode, LocalDate finPeriode, BigDecimal absencesInjustifiees,
            BigDecimal joursTravailEffectif, Integer currentUserId, Connection c) throws Exception {
        if (debutPeriode == null || finPeriode == null || finPeriode.isBefore(debutPeriode)
                || finPeriode.getYear() != annee || finPeriode.getMonthValue() != mois) {
            throw new IllegalArgumentException("La période de service doit être valide et se terminer dans le mois d'acquisition.");
        }
        BigDecimal formule = calculerFormuleAcquisition(idEmploye, debutPeriode, finPeriode,
                absencesInjustifiees, joursTravailEffectif, c);
        BigDecimal joursNonEligibles = new BigDecimal("30").subtract(formule);
        BigDecimal droitMois = calculerDroitAcquis(joursNonEligibles, finPeriode, c);

        // Protection anti-duplication au cutover (Phase 2A-3B) :
        // Si un solde initial arrêté couvre déjà cette période, aucune acquisition fictive ou doublée ne doit être ajoutée.
        String sqlCheckCutover = "SELECT date_mouvement, commentaire FROM mouvement_solde_conge " +
                "WHERE id_employe = ? AND type_mouvement = 'AJUSTEMENT' " +
                "AND (commentaire LIKE '[SOLDE_INITIAL]%' OR commentaire LIKE 'Reprise cutover - Solde initial au %')";
        try (PreparedStatement psCut = c.prepareStatement(sqlCheckCutover)) {
            psCut.setInt(1, idEmploye);
            try (ResultSet rsCut = psCut.executeQuery()) {
                while (rsCut.next()) {
                    Date dtMvt = rsCut.getDate("date_mouvement");
                    if (dtMvt != null) {
                        LocalDate dateArrete = dtMvt.toLocalDate();
                        String comm = rsCut.getString("commentaire");
                        // L'arrêté couvre les clôtures antérieures à sa date réelle, pas tout son mois.
                        if (finPeriode.isBefore(dateArrete)) return BigDecimal.ZERO;
                        if (finPeriode.equals(dateArrete) && comm != null && comm.contains("inclut acquisition")) {
                            return BigDecimal.ZERO;
                        }
                    }
                }
            }
        }

        LocalDate dateAcquisition = calculerDateEffetAcquisition(idEmploye, debutPeriode, finPeriode, c);
        String sqlCheck = "SELECT id_mouvement, nb_jours, date_mouvement FROM mouvement_solde_conge " +
                "WHERE id_employe = ? AND type_mouvement = 'ACQUISITION' " +
                "AND date_mouvement BETWEEN ? AND ? " +
                "FOR UPDATE";
        Integer idMouvementExistant = null;
        BigDecimal ancienNbJours = null;
        try (PreparedStatement ps = c.prepareStatement(sqlCheck)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, Date.valueOf(debutPeriode));
            ps.setDate(3, Date.valueOf(finPeriode));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    idMouvementExistant = rs.getInt("id_mouvement");
                    ancienNbJours = rs.getBigDecimal("nb_jours");
                    if (!dateAcquisition.equals(rs.getDate("date_mouvement").toLocalDate())) {
                        throw new IllegalStateException("ACQUISITION_DATE_CONFLICT: le mouvement existant du mois "
                                + annee + "-" + mois + " doit être corrigé par une migration auditée.");
                    }
                    if (rs.next()) throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: ACQUISITION employé="
                            + idEmploye + " campagne=[" + debutPeriode + ", " + finPeriode + "]");
                }
            }
        }

        if (idMouvementExistant == null) {
            String sqlIns = "INSERT INTO mouvement_solde_conge (" +
                    "id_employe, type_mouvement, nb_jours, date_mouvement, commentaire, created_by" +
                    ") VALUES (?, 'ACQUISITION', ?, ?, ?, ?)";
            try (PreparedStatement psIns = c.prepareStatement(sqlIns)) {
                psIns.setInt(1, idEmploye);
                psIns.setBigDecimal(2, droitMois);
                psIns.setDate(3, Date.valueOf(dateAcquisition));
                psIns.setString(4, "Acquisition mensuelle légale (" + droitMois + "j)");
                if (currentUserId != null) {
                    psIns.setInt(5, currentUserId);
                } else {
                    psIns.setNull(5, java.sql.Types.INTEGER);
                }
                psIns.executeUpdate();
            }
        } else {
            if (ancienNbJours == null || ancienNbJours.compareTo(droitMois) != 0) {
                String sqlUp = "UPDATE mouvement_solde_conge SET nb_jours = ?, commentaire = ? WHERE id_mouvement = ?";
                try (PreparedStatement psUp = c.prepareStatement(sqlUp)) {
                    psUp.setBigDecimal(1, droitMois);
                    psUp.setString(2, "Acquisition mensuelle légale ajustée (" + droitMois + "j)");
                    psUp.setInt(3, idMouvementExistant);
                    psUp.executeUpdate();
                }
            }
        }
        return droitMois;
    }

    public BigDecimal calculerFormuleAcquisition(int idEmploye, LocalDate debut, LocalDate fin,
            BigDecimal absencesInjustifiees, Connection c) throws SQLException {
        return calculerFormuleAcquisition(idEmploye, debut, fin, absencesInjustifiees, null, c);
    }

    public BigDecimal calculerFormuleAcquisition(int idEmploye, LocalDate debut, LocalDate fin,
            BigDecimal absencesInjustifiees, BigDecimal joursTravailEffectif, Connection c) throws SQLException {
        BigDecimal joursService = calculerJoursServiceEquivalent(idEmploye, debut, fin, c);
        if (joursService.signum() > 0 && joursTravailEffectif != null
                && joursTravailEffectif.compareTo(new BigDecimal("24")) >= 0) {
            joursService = new BigDecimal("30");
        }
        BigDecimal abs = absencesInjustifiees != null ? absencesInjustifiees.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        return joursService.subtract(abs).max(BigDecimal.ZERO).min(new BigDecimal("30"));
    }

    private BigDecimal calculerJoursServiceEquivalent(int idEmploye, LocalDate debut, LocalDate fin,
            Connection c) throws SQLException {
        java.util.Set<LocalDate> joursSousContrat = new java.util.HashSet<>();
        String sql = "SELECT date_debut, date_fin FROM contrat_travail " +
                "WHERE id_employe = ? AND date_debut <= ? AND (date_fin IS NULL OR date_fin >= ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, Date.valueOf(fin));
            ps.setDate(3, Date.valueOf(debut));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate d = rs.getDate("date_debut").toLocalDate();
                    Date finContratSql = rs.getDate("date_fin");
                    LocalDate f = finContratSql == null ? fin : finContratSql.toLocalDate();
                    for (LocalDate jour = d.isBefore(debut) ? debut : d;
                            !jour.isAfter(f.isAfter(fin) ? fin : f); jour = jour.plusDays(1)) {
                        joursSousContrat.add(jour);
                    }
                }
            }
        }
        long joursPeriode = java.time.temporal.ChronoUnit.DAYS.between(debut, fin) + 1;
        if (joursSousContrat.size() >= 28) return new BigDecimal("30");
        return new BigDecimal("30").multiply(BigDecimal.valueOf(joursSousContrat.size()))
                .divide(BigDecimal.valueOf(joursPeriode), 6, RoundingMode.HALF_UP);
    }

    private LocalDate calculerDateEffetAcquisition(int idEmploye, LocalDate debut, LocalDate fin,
            Connection c) throws SQLException {
        String sql = "SELECT MAX(LEAST(COALESCE(date_fin, ?), ?)) FROM contrat_travail " +
                "WHERE id_employe = ? AND date_debut <= ? AND (date_fin IS NULL OR date_fin >= ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fin));
            ps.setDate(2, Date.valueOf(fin));
            ps.setInt(3, idEmploye);
            ps.setDate(4, Date.valueOf(fin));
            ps.setDate(5, Date.valueOf(debut));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getDate(1) != null) return rs.getDate(1).toLocalDate();
            }
        }
        return fin;
    }

    private record ContratActif(int idEmploye, LocalDate dateDebut) {}
    private record MouvementDroit(String type, LocalDate date, double jours) {}

    static int entierBonificationPositif(
            java.util.Map<String, ParametreGlobal.ParametreEtat> params, String code) {
        var etat = ParametreGlobal.resolve(params, code);
        try {
            int valeur = Integer.parseInt(etat.valeur().trim());
            if (valeur > 0) return valeur;
        } catch (RuntimeException ignored) { }
        throw new IllegalStateException("Paramètre de bonification actif invalide : " + code);
    }

    /** Bonification mensuelle ; l'acquisition est enregistrée à la clôture de la paie. */
    public int executerBonificationMensuelle(LocalDate dateReference, Connection c) throws Exception {
        LocalDate ref = dateReference != null ? dateReference : LocalDate.now();
        boolean dernierJourMois = ref.plusDays(1).getMonth() != ref.getMonth();
        java.util.Map<String, ParametreGlobal.ParametreEtat> params = ParametreGlobal.loadEtatMapAtDate(Date.valueOf(ref), c);
        BigDecimal plafond = resoudrePlafondCumulConges(params);
        String[] codesBonification = {"SEUIL_ANCIENNETE_BONIFICATION_ANS",
                "PALIER_ANNEES_BONIFICATION", "JOURS_BONIFICATION_PAR_PALIER"};
        boolean bonusActif = true;
        for (String code : codesBonification) {
            var etat = ParametreGlobal.resolve(params, code);
            if (etat.isMissing()) throw new IllegalStateException("Paramètre de bonification absent : " + code);
            if (etat.isDisabled()) bonusActif = false;
        }
        int seuil = bonusActif ? entierBonificationPositif(params, codesBonification[0]) : Integer.MAX_VALUE;
        int palier = bonusActif ? entierBonificationPositif(params, codesBonification[1]) : 1;
        int joursPalier = bonusActif ? entierBonificationPositif(params, codesBonification[2]) : 0;

        java.util.List<ContratActif> contrats = new java.util.ArrayList<>();
        String sqlContrats = "SELECT ct.id_employe, ct.date_debut FROM contrat_travail ct " +
                "JOIN ref_statut_rh s ON ct.id_statut_rh = s.id_statut_rh WHERE s.code = 'ACTIF'";
        try (PreparedStatement ps = c.prepareStatement(sqlContrats); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Date debut = rs.getDate("date_debut");
                if (debut != null) contrats.add(new ContratActif(rs.getInt("id_employe"), debut.toLocalDate()));
            }
        }

        java.util.List<ContratActif> contratsEligibles = contrats.stream()
                .filter(contrat -> !contrat.dateDebut().isAfter(ref.minusMonths(1)))
                .filter(contrat -> ref.getDayOfMonth() >= contrat.dateDebut().getDayOfMonth()
                        || (dernierJourMois && contrat.dateDebut().getDayOfMonth() > ref.getDayOfMonth()))
                .toList();
        java.util.List<Integer> idsEmployesEligibles = contratsEligibles.stream()
                .map(ContratActif::idEmploye).distinct().toList();
        java.util.Map<Integer, java.util.List<MouvementDroit>> mouvementsParEmploye = new java.util.HashMap<>();
        if (!idsEmployesEligibles.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(idsEmployesEligibles.size(), "?"));
            String sqlMouvements = "SELECT id_employe, type_mouvement, date_mouvement, nb_jours " +
                    "FROM mouvement_solde_conge WHERE id_employe IN (" + placeholders + ")";
            try (PreparedStatement psMvt = c.prepareStatement(sqlMouvements)) {
                for (int index = 0; index < idsEmployesEligibles.size(); index++) {
                    psMvt.setInt(index + 1, idsEmployesEligibles.get(index));
                }
                try (ResultSet rs = psMvt.executeQuery()) {
                    while (rs.next()) {
                        Date date = rs.getDate("date_mouvement");
                        if (date != null) mouvementsParEmploye.computeIfAbsent(rs.getInt("id_employe"),
                                ignored -> new java.util.ArrayList<>()).add(new MouvementDroit(
                                        rs.getString("type_mouvement"), date.toLocalDate(), rs.getDouble("nb_jours")));
                    }
                }
            }
        }
        int employesTraites = 0;
        for (ContratActif contrat : contratsEligibles) {
            java.util.List<MouvementDroit> mouvements = mouvementsParEmploye.computeIfAbsent(
                    contrat.idEmploye(), ignored -> new java.util.ArrayList<>());
            double solde = 0.0;
            boolean dejaBonifie = false;
            for (MouvementDroit mouvement : mouvements) {
                solde += mouvement.jours();
                dejaBonifie |= "BONIFICATION".equals(mouvement.type())
                        && mouvement.date().getYear() == ref.getYear();
            }
            int anciennete = java.time.Period.between(contrat.dateDebut(), ref).getYears();
            int bonus = calculateurEngine.calculerBonificationAnciennete(anciennete, seuil, palier, joursPalier);
            boolean anniversaireAtteint = ref.getMonthValue() >= contrat.dateDebut().getMonthValue();
            boolean plafondAtteint = plafond != null
                    && BigDecimal.valueOf(solde).compareTo(plafond) >= 0;
            if (bonus > 0 && anniversaireAtteint && !dejaBonifie && !plafondAtteint) {
                double aCrediter = bonus;
                if (plafond != null) {
                    aCrediter = Math.min(bonus, Math.max(0.0, plafond.doubleValue() - solde));
                }
                if (aCrediter > 0) {
                    MouvementSoldeConge mouvement = new MouvementSoldeConge();
                    mouvement.setIdEmploye(contrat.idEmploye());
                    mouvement.setTypeMouvement("BONIFICATION");
                    mouvement.setNbJours(BigDecimal.valueOf(aCrediter));
                    mouvement.setCommentaire("Bonification d'ancienneté (" + anciennete + " ans)");
                    mouvement.save(c);
                    mouvements.add(new MouvementDroit("BONIFICATION", ref, aCrediter));
                }
            }
            employesTraites++;
        }
        return employesTraites;
    }

    /**
     * Régularisation de congé annuel avec gestion explicite de la couverture partielle
     * selon le solde disponible, idempotence et interdiction absolue de solde négatif.
     */
    public RegularisationCongeResult regulariserCongeAnnuelDepuisPointageStructure(
            int employeeId, Date date, double days, String momentDemiJournee,
            String comment, Integer currentUserId, Connection c) throws Exception {
        return regulariserCongeAnnuelDepuisPointageStructure(employeeId, date, date, days,
                momentDemiJournee, comment, currentUserId, c);
    }

    public RegularisationCongeResult regulariserCongeAnnuelDepuisPointageStructure(
            int employeeId, Date date, Date endDate, double days, String momentDemiJournee,
            String comment, Integer currentUserId, Connection c) throws Exception {
        if (date == null || days <= 0.0) {
            throw new IllegalArgumentException("La date et une durée d'absence positive sont requises.");
        }
        if (endDate == null || endDate.before(date)) {
            throw new IllegalArgumentException("La fin du congé doit être postérieure ou égale à son début.");
        }
        if (!date.equals(endDate) &&
                BigDecimal.valueOf(days).compareTo(calculateurEngine.calculerNombreJours(
                        date.toLocalDate(), endDate.toLocalDate(), false)) != 0) {
            throw new IllegalArgumentException("Le nombre de jours doit couvrir tout l'intervalle calendaire.");
        }

        // 1. Idempotence : si une régularisation a déjà été effectuée pour cette date, la retourner sans double débit
        try (PreparedStatement statement = c.prepareStatement(
                "SELECT d.id_demande_conge, d.nb_jours_decomptes FROM demande_conge d " +
                "WHERE d.id_employe = ? AND d.date_debut = ? AND d.date_fin = ? " +
                "  AND d.commentaire LIKE '[REGULARISATION_POINTAGE]%' " +
                "  AND d.id_statut_demande_conge NOT IN (SELECT id_statut_demande_conge FROM ref_statut_demande_conge WHERE code IN ('REFUSE', 'ANNULE', 'REJETE')) " +
                "ORDER BY d.id_demande_conge DESC LIMIT 1")) {
            statement.setInt(1, employeeId);
            statement.setDate(2, date);
            statement.setDate(3, endDate);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    int existingId = result.getInt("id_demande_conge");
                    double covered = result.getDouble("nb_jours_decomptes");
                    double remaining = Math.max(0.0, java.math.BigDecimal.valueOf(days - covered).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue());
                    VSoldeCongeEmploye currentSolde = VSoldeCongeEmploye.rechercherParEmploye(employeeId, c);
                    double soldeNow = (currentSolde != null && currentSolde.getSoldeActuel() != null) ? currentSolde.getSoldeActuel().doubleValue() : 0.0;
                    return new RegularisationCongeResult(existingId, days, covered, remaining, soldeNow, soldeNow);
                }
            }
        }

        // 2. Vérification d'un conflit avec un autre congé (non régularisation) déjà présent
        try (PreparedStatement statement = c.prepareStatement(
                "SELECT 1 FROM demande_conge WHERE id_employe = ? AND date_debut <= ? AND date_fin >= ? " +
                "AND id_statut_demande_conge NOT IN (SELECT id_statut_demande_conge FROM ref_statut_demande_conge WHERE code IN ('REFUSE', 'ANNULE', 'REJETE')) LIMIT 1")) {
            statement.setInt(1, employeeId);
            statement.setDate(2, endDate);
            statement.setDate(3, date);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    throw new IllegalStateException("Une demande de congé couvre déjà cette date ; vérifiez-la avant régularisation.");
                }
            }
        }

        // 3. Calcul de la couverture maximale autorisée (solde disponible, jamais solde < 0)
        VSoldeCongeEmploye solde = VSoldeCongeEmploye.rechercherParEmploye(employeeId, c);
        java.math.BigDecimal soldeDisponible = (solde != null && solde.getSoldeActuel() != null)
                ? solde.getSoldeActuel() : java.math.BigDecimal.ZERO;
        double soldeAvant = soldeDisponible.doubleValue();

        if (!date.equals(endDate) && soldeDisponible.compareTo(BigDecimal.valueOf(days)) < 0) {
            throw new IllegalArgumentException("Solde de congé insuffisant pour couvrir toute la période de " + days + " jours.");
        }

        double joursCouverts = Math.min(days, Math.max(0.0, soldeAvant));
        joursCouverts = java.math.BigDecimal.valueOf(joursCouverts).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
        double joursRestants = java.math.BigDecimal.valueOf(days - joursCouverts).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
        double soldeApres = java.math.BigDecimal.valueOf(Math.max(0.0, soldeAvant - joursCouverts)).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();

        // 4. Si aucun solde disponible : aucune création de demande ni mouvement PRISE, reliquat complet en absence
        if (joursCouverts <= 0.0) {
            return new RegularisationCongeResult(null, days, 0.0, days, soldeAvant, soldeAvant);
        }

        RefTypeConge annualType = RefTypeConge.rechercherParCode("ANNUEL", c);
        if (annualType == null || !annualType.isEstActif() || !annualType.isDecompteSoldeAcquis()) {
            throw new IllegalStateException("Le type de congé annuel avec décompte du solde est indisponible.");
        }

        DemandeConge demande = new DemandeConge();
        demande.setIdEmploye(employeeId);
        demande.setIdTypeConge(annualType.getIdTypeConge());
        demande.setDateDebut(date);
        demande.setDateFin(endDate);
        demande.setNbJoursDecomptes(java.math.BigDecimal.valueOf(joursCouverts));
        boolean isDemi = (joursCouverts == 0.5);
        demande.setEstDemiJournee(isDemi);
        demande.setMomentDemiJournee(isDemi ? (momentDemiJournee != null ? momentDemiJournee : "MATIN") : null);
        demande.setDerogationDelaiPreavis(true);
        demande.setCommentaire(comment != null && !comment.isBlank()
                ? "[REGULARISATION_POINTAGE] " + comment
                : "[REGULARISATION_POINTAGE] Absence régularisée par congé annuel (" + joursCouverts + "j)");

        creerDemande(demande, currentUserId, c);
        validerDemande(demande.getIdDemandeConge(), currentUserId,
                "Validation de régularisation depuis Pointage", c);

        return new RegularisationCongeResult(demande.getIdDemandeConge(), days, joursCouverts, joursRestants, soldeAvant, soldeApres);
    }

    /**
     * Reproduit le chemin historique de régularisation Pointage en possédant les
     * écritures Congés. La connexion est fournie par l'appelant afin que le congé,
     * le ledger et le pointage restent dans la même transaction JDBC.
     */
    public int regulariserCongeAnnuelDepuisPointage(
            int employeeId, Date date, double days, String momentDemiJournee,
            String comment, Integer currentUserId, Connection c) throws Exception {
        RegularisationCongeResult res = regulariserCongeAnnuelDepuisPointageStructure(
                employeeId, date, days, momentDemiJournee, comment, currentUserId, c);
        if (res.demandeId() != null) {
            return res.demandeId();
        }
        // Si le solde était nul, l'ancienne signature levait une exception si solde insuffisant
        if (res.joursCouverts() <= 0.0) {
            throw new IllegalArgumentException("Solde de congé insuffisant. Dérogation requise.");
        }
        return 0;
    }

    /** Une permission oubliée suit aussi le contrôle et la validation du domaine Congés. */
    public int regulariserPermissionDepuisPointage(int employeeId, Date date, String comment,
            Integer currentUserId, Connection c) throws Exception {
        Integer permissionTypeId = null;
        try (PreparedStatement statement = c.prepareStatement(
                "SELECT id_type_conge FROM ref_type_conge WHERE code IN ('PERMISSION', 'EXCEPTIONNEL') "
                        + "AND est_actif = TRUE ORDER BY CASE WHEN code = 'PERMISSION' THEN 0 ELSE 1 END LIMIT 1");
             ResultSet result = statement.executeQuery()) {
            if (result.next()) permissionTypeId = result.getInt(1);
        }
        if (permissionTypeId == null) {
            throw new IllegalStateException("Aucun type de permission actif n'est disponible pour la régularisation.");
        }
        try (PreparedStatement statement = c.prepareStatement(
                "SELECT 1 FROM demande_conge WHERE id_employe = ? AND date_debut <= ? AND date_fin >= ? LIMIT 1")) {
            statement.setInt(1, employeeId);
            statement.setDate(2, date);
            statement.setDate(3, date);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    throw new IllegalStateException("Une demande de congé couvre déjà cette date ; vérifiez-la avant régularisation.");
                }
            }
        }
        DemandeConge demande = new DemandeConge();
        demande.setIdEmploye(employeeId);
        demande.setIdTypeConge(permissionTypeId);
        demande.setDateDebut(date);
        demande.setDateFin(date);
        demande.setDerogationDelaiPreavis(true);
        demande.setCommentaire(comment != null && !comment.isBlank()
                ? "[REGULARISATION_POINTAGE] " + comment
                : "[REGULARISATION_POINTAGE] Permission déjà accordée, oubliée dans Pointage");
        creerDemande(demande, currentUserId, c);
        validerDemande(demande.getIdDemandeConge(), currentUserId,
                "Validation de permission oubliée depuis Pointage", c);
        return demande.getIdDemandeConge();
    }

    /**
     * Simulation canonique utilisée par le formulaire et par les contrôles du workflow.
     * Le service résout les données persistées puis délègue tous les calculs purs au moteur.
     */
    public CongeCalculateurEngine.SimulationResult simulerDemande(
            Integer idEmploye, Integer idTypeConge, Integer idMotifEvenementFamilial,
            Date dateDebut, Date dateFin, boolean estDemiJournee,
            boolean derogationSoldeNegatif, boolean derogationDelaiPreavis,
            boolean extensionPathologiqueMaternite, Connection c) throws Exception {
        if (dateDebut == null || idTypeConge == null) {
            throw new IllegalArgumentException("Le type de congé et la date de début sont obligatoires.");
        }
        RefTypeConge type = RefTypeConge.rechercherParId(idTypeConge, c);
        if (type == null || !type.isEstActif()) {
            throw new IllegalArgumentException("Le type de congé est introuvable ou inactif.");
        }

        BigDecimal dureeFixe = type.getDureeFixeJours();
        if (idMotifEvenementFamilial != null) {
            RefMotifEvenementFamilial motif = RefMotifEvenementFamilial.rechercherParId(idMotifEvenementFamilial, c);
            if (motif != null) dureeFixe = motif.getNbJoursAutorises();
        }
        if (extensionPathologiqueMaternite && "MATERNITE".equals(type.getCode())) {
            dureeFixe = BigDecimal.valueOf(getParamGlobalInt("DUREE_MAX_MATERNITE_PATHOLOGIQUE_JOURS", dateDebut.toLocalDate(), c));
        }
        LocalDate finCalculee = calculateurEngine.calculerDateFinFixe(dateDebut.toLocalDate(), dureeFixe);
        Date dateFinEffective = finCalculee != null ? Date.valueOf(finCalculee) : dateFin;
        if (dateFinEffective == null) {
            throw new IllegalArgumentException("La date de fin est obligatoire pour un congé à durée libre.");
        }

        BigDecimal soldeAvant = BigDecimal.ZERO;
        BigDecimal joursDemandes = type.isDecompteSoldeAcquis() && idEmploye != null
                ? mg.galaxam.sirh.conge.application.CongeDecompteCalendaireResolver.calculer(
                        idEmploye, dateDebut.toLocalDate(), dateFinEffective.toLocalDate(), estDemiJournee, c)
                : calculateurEngine.calculerNombreJours(
                        dateDebut.toLocalDate(), dateFinEffective.toLocalDate(), estDemiJournee);
        BigDecimal joursDebites = type.isDecompteSoldeAcquis() ? joursDemandes : BigDecimal.ZERO;
        if (idEmploye != null) {
            VSoldeCongeEmploye solde = VSoldeCongeEmploye.rechercherParEmploye(idEmploye, c);
            if (solde != null && solde.getSoldeActuel() != null) soldeAvant = solde.getSoldeActuel();
        }

        BigDecimal consommationPool = null;
        BigDecimal plafondPool = null;
        if (idEmploye != null && type.getIdPoolCongeAnnuel() != null) {
            RefPoolCongeAnnuel pool = RefPoolCongeAnnuel.rechercherParId(type.getIdPoolCongeAnnuel(), c);
            plafondPool = pool != null ? pool.getPlafondJoursAn() : null;
            RefPoolCongeAnnuel.Consommation consommation = RefPoolCongeAnnuel.rechercherConsommation(
                    idEmploye, type.getIdPoolCongeAnnuel(), dateDebut.toLocalDate().getYear(), c);
            consommationPool = consommation != null ? consommation.getJoursEngages() : BigDecimal.ZERO;
        }

        LocalDate dateRefMetier = dateDebut.toLocalDate();
        int seuilLong = getParamGlobalInt("SEUIL_JOURS_PREAVIS_LONG", dateRefMetier, c);
        int delaiCourt = getParamGlobalInt("DELAI_PREAVIS_COURT_JOURS", dateRefMetier, c);
        int delaiLong = getParamGlobalInt("DELAI_PREAVIS_LONG_JOURS", dateRefMetier, c);
        return calculateurEngine.simuler(new CongeCalculateurEngine.SimulationInput(
                dateDebut.toLocalDate(), dateFinEffective.toLocalDate(), estDemiJournee,
                soldeAvant, type.isDecompteSoldeAcquis(), joursDebites, joursDemandes, derogationSoldeNegatif,
                type.isSoumisDelaiPreavis(), derogationDelaiPreavis, LocalDate.now(),
                seuilLong, delaiCourt, delaiLong, consommationPool, plafondPool));
    }

    public DemandeConge creerDemande(DemandeConge d, Integer currentUserId, Connection c) throws Exception {
        if (d.getIdEmploye() == null) {
            throw new IllegalArgumentException("L'identifiant de l'employé est obligatoire.");
        }
        if (d.getIdTypeConge() == null) {
            throw new IllegalArgumentException("Le type de congé est obligatoire.");
        }
        if (d.getDateDebut() == null || d.getDateFin() == null) {
            throw new IllegalArgumentException("Les dates de début et de fin sont obligatoires.");
        }

        RefStatutDemandeConge enAttente = RefStatutDemandeConge.rechercherParCode("EN_ATTENTE", c);
        if (enAttente == null) {
            throw new IllegalStateException("Statut EN_ATTENTE non trouvé dans le référentiel.");
        }
        d.setIdStatutDemandeConge(enAttente.getIdStatutDemandeConge());
        d.setSaisiPar(currentUserId);

        // Bascule automatique Arrêt Maladie si > 30 jours (Art. 13)
        RefTypeConge typeConge = RefTypeConge.rechercherParId(d.getIdTypeConge(), c);
        Date originalDateFin = d.getDateFin();
        BigDecimal totalJoursOrigine = calculateurEngine.calculerNombreJours(d.getDateDebut().toLocalDate(), originalDateFin.toLocalDate(),
                d.isEstDemiJournee());

        if (typeConge != null && "MALADIE".equals(typeConge.getCode())
                && calculateurEngine.doitScinderArretMaladie(totalJoursOrigine)) {
            Date dateFinT1 = Date.valueOf(d.getDateDebut().toLocalDate().plusDays(29));
            d.setDateFin(dateFinT1);
            d.controler(c);
            d.save(c);

            if (typeConge.getIdTypeCongeBascule() != null) {
                DemandeConge d2 = new DemandeConge();
                d2.setIdEmploye(d.getIdEmploye());
                d2.setIdTypeConge(typeConge.getIdTypeCongeBascule());
                d2.setIdStatutDemandeConge(enAttente.getIdStatutDemandeConge());
                d2.setDateDebut(Date.valueOf(dateFinT1.toLocalDate().plusDays(1)));
                d2.setDateFin(originalDateFin);
                d2.setJustificatifFourni(d.isJustificatifFourni());
                d2.setCommentaire(
                        "(Bascule automatique Art. 13) " + (d.getCommentaire() != null ? d.getCommentaire() : ""));
                d2.setSaisiPar(d.getSaisiPar());
                d2.controler(c);
                d2.save(c);
            }

            NotificationRhService.creer(
                    c,
                    "MALADIE_PLAFOND_EPISODE_ATTEINT",
                    "HAUTE",
                    "Bascule Automatique Arrêt Maladie (> 30j)",
                    "L'arrêt maladie continu (" + totalJoursOrigine
                            + "j) a été automatiquement scindé : 30 jours en maintien de salaire et la suite en Maladie Sans Solde (CNaPS).",
                    "/conges/demandes/employe/" + d.getIdEmploye(),
                    d.getIdEmploye());
        } else {
            d.controler(c);
            d.save(c);

            NotificationRhService.creer(
                    c,
                    "CONGE_EN_ATTENTE",
                    "NORMALE",
                    "Nouvelle demande de congé",
                    "Une nouvelle demande de congé (" + (typeConge != null ? typeConge.getLibelle() : "Absence")
                            + ") a été enregistrée et est en attente de validation.",
                    "/conges",
                    d.getIdEmploye());
        }

        return d;
    }

    public DemandeConge modifierDemande(
            int id,
            mg.galaxam.sirh.conge.domain.DemandeCongeRequests.ModifierDemandeRequest req,
            Integer currentUserId,
            boolean isAdminOrRh,
            Connection c) throws Exception {
        String statutCode;
        Date dateDebutCourante = null;
        String sqlLock = "SELECT s.code, dc.id_statut_demande_conge, dc.date_debut FROM demande_conge dc " +
                "JOIN ref_statut_demande_conge s ON dc.id_statut_demande_conge = s.id_statut_demande_conge " +
                "WHERE dc.id_demande_conge = ? FOR UPDATE";
        try (PreparedStatement psLock = c.prepareStatement(sqlLock)) {
            psLock.setInt(1, id);
            try (ResultSet rsLock = psLock.executeQuery()) {
                if (!rsLock.next()) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.CONGE_NON_TROUVE);
                }
                statutCode = rsLock.getString("code");
                dateDebutCourante = rsLock.getDate("date_debut");
            }
        }

        boolean etaitValide = "VALIDE".equals(statutCode);

        if (!"EN_ATTENTE".equals(statutCode) && !"BROUILLON".equals(statutCode)) {
            if (!etaitValide) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                        "Seules les demandes en attente, en brouillon ou validées futures peuvent être modifiées.");
            }
            if (!isAdminOrRh) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Seul un responsable RH ou Administrateur peut modifier un congé déjà validé.");
            }
            if (dateDebutCourante != null && !dateDebutCourante.toLocalDate().isAfter(java.time.LocalDate.now())) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                        "Ce congé validé a déjà commencé ou est passé. Il ne peut plus être modifié directement.");
            }
            if (req != null && req.dateDebut() != null && req.dateDebut().isBefore(java.time.LocalDate.now())) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.CONGE_DATES_INVALIDES,
                        "La nouvelle date de début ne peut pas être dans le passé.");
            }
        }

        DemandeConge d = DemandeConge.rechercherParId(id, c);
        if (d == null)
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.CONGE_NON_TROUVE);

        d.setIdDemandeConge(id);

        // Sécurisation Phase 2A-2C (RG-CONGE) : Interdire la modification sur une période de paie clôturée ou payée
        if (etaitValide && d.getDateDebut() != null && d.getDateFin() != null) {
            String sqlCheckPeriode = "SELECT p.code FROM periode_paie p " +
                    "JOIN ref_statut_periode_paie r ON p.id_statut_periode = r.id_statut_periode " +
                    "WHERE p.date_debut <= ? AND p.date_fin >= ? AND r.code IN ('CLOTUREE', 'PAIE_VALIDEE')";
            try (PreparedStatement psP = c.prepareStatement(sqlCheckPeriode)) {
                psP.setDate(1, d.getDateFin());
                psP.setDate(2, d.getDateDebut());
                try (ResultSet rsP = psP.executeQuery()) {
                    if (rsP.next()) {
                        throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                                mg.galaxam.sirh.common.exception.SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                                "Action interdite : Impossible de modifier un congé sur une période de paie déjà validée ou clôturée ("
                                        + rsP.getString("code") + ").");
                    }
                }
            }
        }

        BigDecimal ancienNbJours = d.getNbJoursDecomptes();

        if (req != null) {
            if (req.idTypeConge() != null) d.setIdTypeConge(req.idTypeConge());
            if (req.idMotifEvenementFamilial() != null) d.setIdMotifEvenementFamilial(req.idMotifEvenementFamilial());
            if (req.dateDebut() != null) d.setDateDebut(Date.valueOf(req.dateDebut()));
            if (req.dateFin() != null) d.setDateFin(Date.valueOf(req.dateFin()));
            if (req.estDemiJournee() != null) d.setEstDemiJournee(req.estDemiJournee());
            if (req.momentDemiJournee() != null && !req.momentDemiJournee().isBlank()) {
                d.setMomentDemiJournee(req.momentDemiJournee());
            }
            if (isAdminOrRh) {
                if (req.derogationSoldeNegatif() != null) d.setDerogationSoldeNegatif(req.derogationSoldeNegatif());
                if (req.derogationDelaiPreavis() != null) d.setDerogationDelaiPreavis(req.derogationDelaiPreavis());
                if (req.derogationContinuiteService() != null) d.setDerogationContinuiteService(req.derogationContinuiteService());
            }
            if (req.extensionPathologiqueMaternite() != null) {
                d.setExtensionPathologiqueMaternite(req.extensionPathologiqueMaternite());
            }
            if (req.justificatifFourni() != null) {
                d.setJustificatifFourni(req.justificatifFourni());
            }
            if (req.commentaire() != null) {
                d.setCommentaire(req.commentaire().trim());
            }
        }

        d.controler(c);
        d.update(c);

        if (etaitValide) {
            RefTypeConge typeConge = RefTypeConge.rechercherParId(d.getIdTypeConge(), c);
            if (typeConge != null && typeConge.isDecompteSoldeAcquis()) {
                BigDecimal nouveauNbJours = d.getNbJoursDecomptes();
                BigDecimal delta = nouveauNbJours.subtract(ancienNbJours != null ? ancienNbJours : BigDecimal.ZERO);
                if (delta.compareTo(BigDecimal.ZERO) != 0) {
                    MouvementSoldeConge ajustement = new MouvementSoldeConge();
                    ajustement.setIdEmploye(d.getIdEmploye());
                    ajustement.setTypeMouvement("AJUSTEMENT");
                    ajustement.setNbJours(delta.negate());
                    ajustement.setIdDemandeConge(d.getIdDemandeConge());
                    ajustement.setDateMouvement(d.getDateDebut());
                    ajustement.setCommentaire("Ajustement de solde suite modification durée de la demande #" + d.getIdDemandeConge()
                            + " (" + ancienNbJours + "j -> " + nouveauNbJours + "j)");
                    ajustement.setCreatedBy(currentUserId);
                    ajustement.save(c);
                }
            }

            NotificationRhService.creer(
                    c,
                    "MOUVEMENT_APPLIQUE",
                    "NORMALE",
                    "Dates de congé modifiées",
                    "Les dates du congé validé (#" + id + ") ont été modifiées (du " + d.getDateDebut() + " au " + d.getDateFin() + ").",
                    "/conges",
                    d.getIdEmploye());
        }

        return d;
    }

    public DemandeConge validerDemande(int idDemande, Integer currentUserId, String commentaireValidation, Connection c)
            throws Exception {
        DemandeConge d = DemandeConge.rechercherParId(idDemande, c);
        if (d == null) {
            throw new IllegalArgumentException("Demande de congé introuvable (ID: " + idDemande + ").");
        }

        String lockSql = "SELECT s.code FROM demande_conge d JOIN ref_statut_demande_conge s " +
                "ON d.id_statut_demande_conge = s.id_statut_demande_conge " +
                "WHERE d.id_demande_conge = ? FOR UPDATE";
        try (PreparedStatement ps = c.prepareStatement(lockSql)) {
            ps.setInt(1, idDemande);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Demande de congé introuvable.");
                String statut = rs.getString(1);
                if (!"EN_ATTENTE".equals(statut) && !"BROUILLON".equals(statut)) {
                    throw new IllegalStateException("Seule une demande en attente ou en brouillon peut être validée.");
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT id_employe FROM employe WHERE id_employe = ? FOR UPDATE")) {
            ps.setInt(1, d.getIdEmploye());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Employé introuvable.");
            }
        }

        d.controler(c);
        RefTypeConge type = RefTypeConge.rechercherParId(d.getIdTypeConge(), c);
        if (type == null) throw new IllegalArgumentException("Type de congé non valide.");
        if (type.isDecompteSoldeAcquis()) {
            VSoldeCongeEmploye solde = VSoldeCongeEmploye.rechercherParEmploye(d.getIdEmploye(), c);
            BigDecimal disponible = solde != null && solde.getSoldeActuel() != null
                    ? solde.getSoldeActuel() : BigDecimal.ZERO;
            BigDecimal projete = calculateurEngine.calculerSoldeProjete(disponible, d.getNbJoursDecomptes(), true);
            // Paramètres lus à la date courante (règles applicatives au moment de la validation,
            // pas à la date de la période de congé) — comportement intentionnel.
            java.util.Map<String, ParametreGlobal.ParametreEtat> params = ParametreGlobal.loadEtatMap(c);
            if (projete.signum() < 0) {
                throw new IllegalArgumentException("Solde de congé insuffisant : aucune prise par anticipation n'est autorisée.");
            }
            MouvementSoldeConge prise = new MouvementSoldeConge();
            prise.setIdEmploye(d.getIdEmploye());
            prise.setTypeMouvement("PRISE");
            prise.setNbJours(d.getNbJoursDecomptes().negate());
            prise.setIdDemandeConge(idDemande);
            prise.setDateMouvement(d.getDateDebut());
            prise.setCommentaire("Prise de congé " + type.getLibelle() + " (Demande #" + idDemande + ")");
            prise.setCreatedBy(currentUserId);
            try {
                prise.save(c);
            } catch (java.sql.SQLException e) {
                String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                if (message.contains("duplicate") || message.contains("unique")) {
                    throw new IllegalStateException("Cette demande a déjà généré un mouvement PRISE.");
                }
                throw e;
            }
            var alerte = ParametreGlobal.resolve(params, "SEUIL_ALERTE_SOLDE_CONGE_FAIBLE");
            if (alerte.isMissing()) throw new IllegalStateException("Paramètre de congé absent : SEUIL_ALERTE_SOLDE_CONGE_FAIBLE");
            int seuil = 0;
            if (alerte.isActive()) {
                try {
                    seuil = Integer.parseInt(alerte.valeur().trim());
                    if (seuil < 0) throw new NumberFormatException();
                } catch (RuntimeException e) {
                    throw new IllegalStateException("Paramètre de congé actif invalide : SEUIL_ALERTE_SOLDE_CONGE_FAIBLE", e);
                }
            }
            if (alerte.isActive()
                    && projete.compareTo(BigDecimal.valueOf(seuil)) <= 0) {
                NotificationRhService.creer(c, "SOLDE_CONGE_FAIBLE", "NORMALE", "Solde de congé faible",
                        "Le solde disponible est de " + projete + " jour(s).", "/conges/soldes", d.getIdEmploye());
            }
        }
        if (type.getIdPoolCongeAnnuel() != null) {
            RefPoolCongeAnnuel pool = RefPoolCongeAnnuel.rechercherParId(type.getIdPoolCongeAnnuel(), c);
            BigDecimal plafond = pool != null ? pool.getPlafondJoursAn() : null;
            BigDecimal engage = engagementPool(d.getIdEmploye(), type.getIdPoolCongeAnnuel(),
                    d.getDateDebut().toLocalDate().getYear(), c);
            if (plafond != null && engage.compareTo(plafond) >= 0) {
                NotificationRhService.creer(c, "POOL_EXCEPTIONNEL_EPUISE", "HAUTE", "Quota annuel atteint",
                        "Le quota annuel de " + plafond + " jours est atteint.", "/conges/soldes", d.getIdEmploye());
            }
        }
        RefStatutDemandeConge valide = RefStatutDemandeConge.rechercherParCode("VALIDE", c);
        if (valide == null) throw new IllegalStateException("Statut VALIDE non trouvé dans le référentiel.");
        d.setIdStatutDemandeConge(valide.getIdStatutDemandeConge());
        d.setValidePar(currentUserId);
        d.setDateValidation(new java.sql.Timestamp(System.currentTimeMillis()));
        return (DemandeConge) d.update(c);
    }

    private BigDecimal engagementPool(int idEmploye, int idPool, int annee, Connection c) throws Exception {
        RefPoolCongeAnnuel.Consommation consommation = RefPoolCongeAnnuel.rechercherConsommation(
                idEmploye, idPool, annee, c);
        return consommation != null ? consommation.getJoursEngages() : BigDecimal.ZERO;
    }

    public DemandeConge refuserDemande(int idDemande, Integer currentUserId, String motifRefus, Connection c)
            throws Exception {
        DemandeConge d = DemandeConge.rechercherParId(idDemande, c);
        if (d == null) {
            throw new IllegalArgumentException("Demande de congé introuvable (ID: " + idDemande + ").");
        }

        if (!"EN_ATTENTE".equals(d.getStatutCode(c))) {
            throw new IllegalStateException("Seule une demande en attente de validation peut être refusée.");
        }
        if (motifRefus == null || motifRefus.isBlank()) {
            throw new IllegalArgumentException("Le motif de refus est obligatoire.");
        }
        RefStatutDemandeConge refuse = RefStatutDemandeConge.rechercherParCode("REFUSE", c);
        if (refuse == null) throw new IllegalStateException("Statut REFUSE non trouvé dans le référentiel.");
        d.setIdStatutDemandeConge(refuse.getIdStatutDemandeConge());
        d.setValidePar(currentUserId);
        d.setDateValidation(new java.sql.Timestamp(System.currentTimeMillis()));
        d.setMotifRefus(motifRefus.trim());
        return (DemandeConge) d.update(c);
    }

    public DemandeConge annulerDemande(int idDemande, Integer currentUserId, String motifAnnulation, Connection c)
            throws Exception {
        DemandeConge d = DemandeConge.rechercherParId(idDemande, c);
        if (d == null) {
            throw new IllegalArgumentException("Demande de congé introuvable (ID: " + idDemande + ").");
        }

        String lockSql = "SELECT s.code FROM demande_conge d JOIN ref_statut_demande_conge s " +
                "ON d.id_statut_demande_conge = s.id_statut_demande_conge " +
                "WHERE d.id_demande_conge = ? FOR UPDATE";
        String statut;
        try (PreparedStatement ps = c.prepareStatement(lockSql)) {
            ps.setInt(1, idDemande);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Demande de congé introuvable.");
                statut = rs.getString(1);
            }
        }
        if ("ANNULE".equals(statut) || "REFUSE".equals(statut)) {
            throw new IllegalStateException("Une demande refusée ou annulée ne peut plus être annulée.");
        }
        verifierPeriodePaieModifiable(d.getIdEmploye(), d.getDateDebut(), d.getDateFin(), c);

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM pointage_jour WHERE id_demande_conge = ? " +
                "AND COALESCE(total_heures, 0) <> 0 LIMIT 1")) {
            ps.setInt(1, idDemande);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) throw new IllegalStateException(
                        "Un pointage lié à ce congé contient des heures travaillées. Vérifiez-le avant d'annuler la demande.");
            }
        }

        RefTypeConge type = RefTypeConge.rechercherParId(d.getIdTypeConge(), c);
        String motif = motifAnnulation != null && !motifAnnulation.isBlank() ? motifAnnulation.trim() : null;
        if ("VALIDE".equals(statut) && type != null && type.isDecompteSoldeAcquis()) {
            MouvementSoldeConge mouvement = new MouvementSoldeConge();
            mouvement.setIdEmploye(d.getIdEmploye());
            mouvement.setTypeMouvement("ANNULATION");
            mouvement.setNbJours(d.getNbJoursDecomptes());
            mouvement.setIdDemandeConge(idDemande);
            mouvement.setDateMouvement(d.getDateDebut());
            mouvement.setCommentaire("Restitution suite à l'annulation de la demande #" + idDemande
                    + (motif != null ? " : " + motif : ""));
            mouvement.setCreatedBy(currentUserId);
            mouvement.save(c);
        }
        RefStatutDemandeConge annule = RefStatutDemandeConge.rechercherParCode("ANNULE", c);
        if (annule == null) throw new IllegalStateException("Statut ANNULE non trouvé dans le référentiel.");
        d.setIdStatutDemandeConge(annule.getIdStatutDemandeConge());
        if (motif != null) d.setMotifRefus(motif);
        DemandeConge annulee = (DemandeConge) d.update(c);
        if ("VALIDE".equals(statut)) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE pointage_jour SET id_demande_conge = NULL, " +
                    "id_statut_jour = (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'ABSENCE_NON_JUSTIFIEE'), " +
                    "motif_ajustement = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP " +
                    "WHERE id_demande_conge = ? AND COALESCE(total_heures, 0) = 0")) {
                ps.setString(1, "Congé annulé" + (motif != null ? " : " + motif : ""));
                ps.setInt(2, idDemande);
                ps.executeUpdate();
            }
        }
        return annulee;
    }

    private void verifierPeriodePaieModifiable(Integer idEmploye, Date debut, Date fin, Connection c) throws Exception {
        if (debut == null || fin == null) return;
        String periodeSql = "SELECT p.code FROM periode_paie p JOIN ref_statut_periode_paie s " +
                "ON p.id_statut_periode = s.id_statut_periode WHERE p.date_debut <= ? AND p.date_fin >= ? " +
                "AND s.code IN ('CLOTUREE', 'PAIE_VALIDEE') LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(periodeSql)) {
            ps.setDate(1, fin);
            ps.setDate(2, debut);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) throw new IllegalStateException(
                        "Action interdite sur la période de paie clôturée " + rs.getString(1) + ".");
            }
        }
        String bulletinSql = "SELECT b.statut, p.code FROM bulletin_paie b JOIN periode_paie p " +
                "ON b.id_periode_paie = p.id_periode_paie WHERE b.id_employe = ? " +
                "AND p.date_debut <= ? AND p.date_fin >= ? AND b.statut IN ('VALIDE', 'CLOTURE') LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(bulletinSql)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, fin);
            ps.setDate(3, debut);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) throw new IllegalStateException(
                        "Action interdite : bulletin " + rs.getString(1) + " sur la période " + rs.getString(2) + ".");
            }
        }
    }

    public java.util.Map<String, Object> verifierConflitsDemande(Integer idEmploye, Date dateDebut, Date dateFin,
            boolean estDemiJournee, String momentDemiJournee, Integer idDemandeExclue, Connection c) throws Exception {
        return verifierConflitsDemande(idEmploye, null, dateDebut, dateFin,
                estDemiJournee, momentDemiJournee, idDemandeExclue, c);
    }

    public java.util.Map<String, Object> verifierConflitsDemande(Integer idEmploye, Integer idTypeConge,
            Date dateDebut, Date dateFin, boolean estDemiJournee,
            String momentDemiJournee, Integer idDemandeExclue, Connection c) throws Exception {
        java.util.Map<String, Object> res = new java.util.HashMap<>();
        if (dateDebut == null || dateFin == null) {
            throw new IllegalArgumentException("Les dates de début et de fin sont obligatoires.");
        }

        // 1. Décompte de base
        RefTypeConge typeDecompte = idTypeConge != null ? RefTypeConge.rechercherParId(idTypeConge, c) : null;
        BigDecimal nbJours = typeDecompte != null && typeDecompte.isDecompteSoldeAcquis() && idEmploye != null
                ? mg.galaxam.sirh.conge.application.CongeDecompteCalendaireResolver.calculer(
                        idEmploye, dateDebut.toLocalDate(), dateFin.toLocalDate(), estDemiJournee, c)
                : calculateurEngine.calculerNombreJours(dateDebut.toLocalDate(), dateFin.toLocalDate(), estDemiJournee);
        res.put("nbJoursDecomptes", nbJours);
        res.put("dateDebut", dateDebut);
        res.put("dateFin", dateFin);
        res.put("estDemiJournee", estDemiJournee);
        res.put("momentDemiJournee", momentDemiJournee);

        // 2. Décompte calendaire détaillé (ouvrés, week-ends, fériés) via le moteur canonique
        java.time.LocalDate cur = dateDebut.toLocalDate();
        java.time.LocalDate end = dateFin.toLocalDate();
        int nbWeekend = 0;
        int nbOuvres = 0;
        int nbFeries = 0;

        java.util.Set<java.time.LocalDate> feries = calendrierOuvrableEngine.getDatesFeriesEtChomeesEmploye(cur, end, idEmploye, c);
        while (!cur.isAfter(end)) {
            boolean isWe = CalendrierOuvrableEngine.isWeekend(cur);
            boolean isFerie = feries.contains(cur);

            if (isFerie) {
                nbFeries++;
            }
            if (isWe) {
                nbWeekend++;
            } else if (!isFerie) {
                nbOuvres++;
            }
            cur = cur.plusDays(1);
        }
        res.put("nbJoursOuvres", nbOuvres);
        res.put("nbJoursFeries", nbFeries);
        res.put("nbJoursWeekEnd", nbWeekend);

        if (idEmploye == null || idEmploye <= 0) {
            res.put("actifSurPeriode", true);
            res.put("collisionPersonnelle", false);
            res.put("collisionsPersonnelles", java.util.List.of());
            res.put("conflitPoste", false);
            res.put("colleguesAbsents", java.util.List.of());
            return res;
        }

        // Contrat et affectation doivent couvrir chaque jour, pas seulement se chevaucher.
        var continuite = new CongeContinuiteService().analyser(idEmploye,
                dateDebut.toLocalDate(), dateFin.toLocalDate(), idDemandeExclue,
                estDemiJournee, momentDemiJournee, c);
        res.put("actifSurPeriode", continuite.actifSurPeriode());
        if (!continuite.actifSurPeriode()) {
            res.put("alerteInactivite", "L'employé ne dispose d'aucun contrat actif couvrant l'intégralité de la période demandée.");
        }

        // 3. Collisions personnelles (autres congés de l'employé)
        java.util.List<java.util.Map<String, Object>> colPerso = new java.util.ArrayList<>();
        String sqlOverlap = "SELECT dc.id_demande_conge, dc.date_debut, dc.date_fin, dc.est_demi_journee, dc.moment_demi_journee, " +
                "tc.libelle AS libelle_conge, sc.code AS code_statut " +
                "FROM demande_conge dc " +
                "JOIN ref_type_conge tc ON dc.id_type_conge = tc.id_type_conge " +
                "JOIN ref_statut_demande_conge sc ON dc.id_statut_demande_conge = sc.id_statut_demande_conge " +
                "WHERE dc.id_employe = ? " +
                "AND dc.id_demande_conge != COALESCE(?, -1) " +
                "AND sc.code NOT IN ('REFUSE', 'ANNULE', 'REJETE') " +
                "AND dc.date_debut <= ? AND dc.date_fin >= ?";
        try (PreparedStatement psO = c.prepareStatement(sqlOverlap)) {
            psO.setInt(1, idEmploye);
            psO.setObject(2, idDemandeExclue != null ? idDemandeExclue : -1);
            psO.setDate(3, dateFin);
            psO.setDate(4, dateDebut);
            try (ResultSet rsO = psO.executeQuery()) {
                while (rsO.next()) {
                    boolean otherDemi = rsO.getBoolean("est_demi_journee");
                    String otherMoment = rsO.getString("moment_demi_journee");
                    if (estDemiJournee && otherDemi && dateDebut.equals(dateFin) &&
                            rsO.getDate("date_debut").equals(dateDebut) &&
                            momentDemiJournee != null && otherMoment != null &&
                            !momentDemiJournee.equalsIgnoreCase(otherMoment)) {
                        continue;
                    }
                    java.util.Map<String, Object> cp = new java.util.HashMap<>();
                    cp.put("idDemandeConge", rsO.getInt("id_demande_conge"));
                    cp.put("typeConge", rsO.getString("libelle_conge"));
                    cp.put("dateDebut", rsO.getDate("date_debut"));
                    cp.put("dateFin", rsO.getDate("date_fin"));
                    cp.put("statut", rsO.getString("code_statut"));
                    colPerso.add(cp);
                }
            }
        }
        res.put("collisionPersonnelle", !colPerso.isEmpty());
        res.put("collisionsPersonnelles", colPerso);

        // Les affectations successives sont exposées sans choisir arbitrairement
        // une seule affectation pour toute la demande.
        var premierSegment = continuite.segments().isEmpty() ? null : continuite.segments().get(0);
        res.put("idFonction", premierSegment != null ? premierSegment.idFonction() : null);
        res.put("idSite", premierSegment != null ? premierSegment.idSite() : null);
        res.put("nomFonction", premierSegment != null ? premierSegment.nomFonction() : null);
        res.put("nomDepartement", premierSegment != null ? premierSegment.nomDepartement() : null);
        res.put("nomSite", premierSegment != null ? premierSegment.nomSite() : null);
        res.put("affectationsSurPeriode", continuite.segments());

        java.util.List<java.util.Map<String, Object>> colleguesAbsents = new java.util.ArrayList<>();
        for (var collision : continuite.collisions()) {
            java.util.Map<String, Object> col = new java.util.HashMap<>();
            col.put("idEmploye", collision.idEmploye());
            col.put("matricule", collision.matricule());
            col.put("nom", collision.nom());
            col.put("prenom", collision.prenom());
            col.put("photoUrl", collision.photoUrl());
            col.put("idDemandeConge", collision.idDemandeConge());
            col.put("typeConge", collision.typeConge());
            col.put("dateDebut", Date.valueOf(collision.dateDebut()));
            col.put("dateFin", Date.valueOf(collision.dateFin()));
            col.put("debutConflit", Date.valueOf(collision.debutConflit()));
            col.put("finConflit", Date.valueOf(collision.finConflit()));
            col.put("statut", collision.statut());
            col.put("nomFonction", collision.nomFonction());
            col.put("nomSite", collision.nomSite());
            colleguesAbsents.add(col);
        }
        boolean conflitPoste = !colleguesAbsents.isEmpty();
        res.put("totalEmployesPoste", continuite.totalEmployesMemePoste());
        res.put("colleguesAbsents", colleguesAbsents);
        res.put("conflitPoste", conflitPoste);
        res.put("messageAvertissement", conflitPoste
                ? colleguesAbsents.size() + " collaborateur(s) au même poste et site sont déjà absents "
                        + "sur les dates réellement communes."
                : null);

        // 6. Solde actuel de congés du collaborateur
        VSoldeCongeEmploye solde = VSoldeCongeEmploye.rechercherParEmploye(idEmploye, c);
        BigDecimal soldeDisponible = (solde != null && solde.getSoldeActuel() != null)
                ? solde.getSoldeActuel()
                : BigDecimal.ZERO;
        res.put("soldeDisponible", soldeDisponible);

        return res;
    }

    public java.util.Map<String, Object> getPlanningCollaborateurs(
            Integer annee, Integer mois, java.time.LocalDate dateDebut, java.time.LocalDate dateFin,
            Integer idSite, Integer idDepartement,
            String search, String filterMode,
            Integer page, Integer size,
            Connection c) throws Exception {

        java.time.LocalDate start;
        java.time.LocalDate end;
        if (dateDebut != null && dateFin != null) {
            start = dateDebut;
            end = dateFin;
        } else {
            int y = (annee != null && annee > 2000) ? annee : java.time.LocalDate.now().getYear();
            int m = (mois != null && mois >= 1 && mois <= 12) ? mois : java.time.LocalDate.now().getMonthValue();
            start = java.time.LocalDate.of(y, m, 1);
            end = start.withDayOfMonth(start.lengthOfMonth());
        }

        Date dStart = Date.valueOf(start);
        Date dEnd = Date.valueOf(end);

        int curPage = (page != null && page >= 1) ? page : 1;
        int pageSize = (size != null && size >= 1 && size <= 100) ? size : 10;
        int offset = (curPage - 1) * pageSize;

        boolean hasSearch = (search != null && !search.trim().isEmpty());
        String searchPattern = hasSearch ? "%" + search.trim().toLowerCase() + "%" : null;
        boolean onlyAbsents = "ABSENTS".equalsIgnoreCase(filterMode);

        // 1. Décompte total des collaborateurs éligibles selon les filtres
        StringBuilder sqlCount = new StringBuilder(
                "WITH base_emp AS (" +
                "  SELECT DISTINCT ON (e.id_employe) e.id_employe, e.nom, e.prenom, e.matricule, f.libelle AS fonction, d.libelle AS departement " +
                "  FROM employe e " +
                "  JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
                "      AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
                "  LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
                "  LEFT JOIN ref_departement d ON a.id_departement = d.id_departement " +
                "  WHERE e.est_actif = true "
        );

        if (idSite != null && idSite > 0) sqlCount.append("AND a.id_site = ? ");
        if (idDepartement != null && idDepartement > 0) sqlCount.append("AND a.id_departement = ? ");
        if (hasSearch) {
            sqlCount.append("AND (LOWER(e.nom) LIKE ? OR LOWER(e.prenom) LIKE ? OR LOWER(e.matricule) LIKE ? OR LOWER(COALESCE(f.libelle, '')) LIKE ? OR LOWER(COALESCE(d.libelle, '')) LIKE ?) ");
        }
        if (onlyAbsents) {
            sqlCount.append("AND EXISTS (" +
                    "  SELECT 1 FROM demande_conge dc_sub " +
                    "  JOIN ref_statut_demande_conge sc_sub ON dc_sub.id_statut_demande_conge = sc_sub.id_statut_demande_conge " +
                    "  WHERE dc_sub.id_employe = e.id_employe " +
                    "    AND sc_sub.code NOT IN ('REFUSE', 'ANNULE', 'REJETE') " +
                    "    AND dc_sub.date_debut <= ? AND dc_sub.date_fin >= ?" +
                    ") ");
        }
        sqlCount.append("  ORDER BY e.id_employe, a.date_debut DESC" +
                ") SELECT COUNT(*) AS total FROM base_emp");

        int totalCount = 0;
        try (PreparedStatement psCnt = c.prepareStatement(sqlCount.toString())) {
            int p = 1;
            psCnt.setDate(p++, dEnd);
            psCnt.setDate(p++, dStart);
            if (idSite != null && idSite > 0) psCnt.setInt(p++, idSite);
            if (idDepartement != null && idDepartement > 0) psCnt.setInt(p++, idDepartement);
            if (hasSearch) {
                psCnt.setString(p++, searchPattern);
                psCnt.setString(p++, searchPattern);
                psCnt.setString(p++, searchPattern);
                psCnt.setString(p++, searchPattern);
                psCnt.setString(p++, searchPattern);
            }
            if (onlyAbsents) {
                psCnt.setDate(p++, dEnd);
                psCnt.setDate(p++, dStart);
            }
            try (ResultSet rsCnt = psCnt.executeQuery()) {
                if (rsCnt.next()) totalCount = rsCnt.getInt("total");
            }
        }

        // 2. Récupérer les employés pour la page courante
        StringBuilder sqlEmp = new StringBuilder(
                "WITH base_emp AS (" +
                "  SELECT DISTINCT ON (e.id_employe) e.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, " +
                "         f.id_fonction, f.libelle AS fonction, d.id_departement, d.libelle AS nom_departement, s.id_site, s.libelle AS site_nom " +
                "  FROM employe e " +
                "  JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
                "      AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
                "  LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
                "  LEFT JOIN ref_departement d ON a.id_departement = d.id_departement " +
                "  LEFT JOIN ref_site s ON a.id_site = s.id_site " +
                "  WHERE e.est_actif = true "
        );

        if (idSite != null && idSite > 0) sqlEmp.append("AND a.id_site = ? ");
        if (idDepartement != null && idDepartement > 0) sqlEmp.append("AND a.id_departement = ? ");
        if (hasSearch) {
            sqlEmp.append("AND (LOWER(e.nom) LIKE ? OR LOWER(e.prenom) LIKE ? OR LOWER(e.matricule) LIKE ? OR LOWER(COALESCE(f.libelle, '')) LIKE ? OR LOWER(COALESCE(d.libelle, '')) LIKE ?) ");
        }
        if (onlyAbsents) {
            sqlEmp.append("AND EXISTS (" +
                    "  SELECT 1 FROM demande_conge dc_sub " +
                    "  JOIN ref_statut_demande_conge sc_sub ON dc_sub.id_statut_demande_conge = sc_sub.id_statut_demande_conge " +
                    "  WHERE dc_sub.id_employe = e.id_employe " +
                    "    AND sc_sub.code NOT IN ('REFUSE', 'ANNULE', 'REJETE') " +
                    "    AND dc_sub.date_debut <= ? AND dc_sub.date_fin >= ?" +
                    ") ");
        }
        sqlEmp.append("  ORDER BY e.id_employe, a.date_debut DESC" +
                ") SELECT * FROM base_emp ORDER BY nom ASC, prenom ASC LIMIT ? OFFSET ?");

        java.util.List<java.util.Map<String, Object>> employes = new java.util.ArrayList<>();
        java.util.Map<Integer, java.util.Map<String, Object>> empMap = new java.util.LinkedHashMap<>();

        try (PreparedStatement psE = c.prepareStatement(sqlEmp.toString())) {
            int p = 1;
            psE.setDate(p++, dEnd);
            psE.setDate(p++, dStart);
            if (idSite != null && idSite > 0) psE.setInt(p++, idSite);
            if (idDepartement != null && idDepartement > 0) psE.setInt(p++, idDepartement);
            if (hasSearch) {
                psE.setString(p++, searchPattern);
                psE.setString(p++, searchPattern);
                psE.setString(p++, searchPattern);
                psE.setString(p++, searchPattern);
                psE.setString(p++, searchPattern);
            }
            if (onlyAbsents) {
                psE.setDate(p++, dEnd);
                psE.setDate(p++, dStart);
            }
            psE.setInt(p++, pageSize);
            psE.setInt(p++, offset);

            try (ResultSet rsE = psE.executeQuery()) {
                while (rsE.next()) {
                    int idEmp = rsE.getInt("id_employe");
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("idEmploye", idEmp);
                    map.put("matricule", rsE.getString("matricule"));
                    map.put("nom", rsE.getString("nom"));
                    map.put("prenom", rsE.getString("prenom"));
                    map.put("nomComplet", rsE.getString("prenom") + " " + rsE.getString("nom"));
                    map.put("photoUrl", rsE.getString("photo_url"));
                    map.put("idFonction", rsE.getObject("id_fonction"));
                    map.put("fonction", rsE.getString("fonction"));
                    map.put("departement", rsE.getString("nom_departement"));
                    map.put("site", rsE.getString("site_nom"));
                    map.put("demandes", new java.util.ArrayList<java.util.Map<String, Object>>());
                    empMap.put(idEmp, map);
                    employes.add(map);
                }
            }
        }

        // 3. Charger les demandes des employés de cette page
        int totalDemandes = 0;
        java.util.Set<Integer> employesAvecConge = new java.util.HashSet<>();

        if (!empMap.isEmpty()) {
            StringBuilder sqlDemandes = new StringBuilder(
                    "SELECT dc.id_demande_conge, dc.id_employe, dc.date_debut, dc.date_fin, " +
                    "dc.nb_jours_decomptes, dc.est_demi_journee, dc.moment_demi_journee, " +
                    "tc.code AS code_type_conge, tc.libelle AS type_conge, sc.code AS code_statut " +
                    "FROM demande_conge dc " +
                    "JOIN ref_type_conge tc ON dc.id_type_conge = tc.id_type_conge " +
                    "JOIN ref_statut_demande_conge sc ON dc.id_statut_demande_conge = sc.id_statut_demande_conge " +
                    "WHERE sc.code NOT IN ('REFUSE', 'ANNULE', 'REJETE') " +
                    "AND dc.date_debut <= ? AND dc.date_fin >= ? " +
                    "AND dc.id_employe IN ("
            );

            for (int i = 0; i < empMap.size(); i++) {
                if (i > 0) sqlDemandes.append(",");
                sqlDemandes.append("?");
            }
            sqlDemandes.append(") ORDER BY dc.date_debut ASC");

            try (PreparedStatement psD = c.prepareStatement(sqlDemandes.toString())) {
                int p = 1;
                psD.setDate(p++, dEnd);
                psD.setDate(p++, dStart);
                for (Integer id : empMap.keySet()) {
                    psD.setInt(p++, id);
                }
                try (ResultSet rsD = psD.executeQuery()) {
                    while (rsD.next()) {
                        int idEmp = rsD.getInt("id_employe");
                        java.util.Map<String, Object> emp = empMap.get(idEmp);
                        if (emp != null) {
                            java.util.Map<String, Object> item = new java.util.HashMap<>();
                            item.put("idDemandeConge", rsD.getInt("id_demande_conge"));
                            item.put("dateDebut", rsD.getDate("date_debut"));
                            item.put("dateFin", rsD.getDate("date_fin"));
                            item.put("nbJoursDecomptes", rsD.getBigDecimal("nb_jours_decomptes"));
                            item.put("estDemiJournee", rsD.getBoolean("est_demi_journee"));
                            item.put("momentDemiJournee", rsD.getString("moment_demi_journee"));
                            String codeType = rsD.getString("code_type_conge");
                            item.put("codeTypeConge", codeType);
                            item.put("typeConge", rsD.getString("type_conge"));
                            String color = switch (codeType != null ? codeType : "") {
                                case "ANNUEL" -> "#125f80";
                                case "MALADIE", "MALADIE_SANS_SOLDE" -> "#dc2626";
                                case "MATERNITE" -> "#7e22ce";
                                case "PATERNITE" -> "#6b21a8";
                                case "EXCEPTIONNEL" -> "#ea580c";
                                default -> "#62717a";
                            };
                            item.put("couleur", color);
                            item.put("statut", rsD.getString("code_statut"));
                            @SuppressWarnings("unchecked")
                            java.util.List<java.util.Map<String, Object>> list = (java.util.List<java.util.Map<String, Object>>) emp.get("demandes");
                            list.add(item);
                            employesAvecConge.add(idEmp);
                            totalDemandes++;
                        }
                    }
                }
            }
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / pageSize));

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("dateDebut", start.toString());
        result.put("dateFin", end.toString());
        result.put("total", totalCount);
        result.put("totalCollaborateurs", totalCount);
        result.put("nbCollaborateursAbsents", employesAvecConge.size());
        result.put("totalDemandes", totalDemandes);
        result.put("page", curPage);
        result.put("size", pageSize);
        result.put("totalPages", totalPages);
        result.put("employes", employes);

        return result;
    }

    public java.util.Map<String, Object> getPlanningCollaborateurs(
            Integer annee, Integer mois, java.time.LocalDate dateDebut, java.time.LocalDate dateFin,
            Integer idSite, Integer idDepartement, Connection c) throws Exception {
        return getPlanningCollaborateurs(annee, mois, dateDebut, dateFin, idSite, idDepartement, null, "TOUS", 1, 1000, c);
    }

    /**
     * Détermine le nombre de jours de congé valorisés en paie.
     *
     * Règle GALAXAM actuelle :
     * - valorisation en jours calendaires ;
     * - cohérente avec le débit du solde congé ;
     * - week-ends inclus ;
     * - jours fériés inclus ;
     * - NBCONGE = nombre de jours calendaires débités.
     *
     * Exemple :
     * congé vendredi -> dimanche = 3 jours valorisés.
     *
     * IMPORTANT :
     * Si la règle métier évolue ultérieurement vers une valorisation
     * limitée aux jours normalement travaillés (ex. vendredi uniquement),
     * modifier cette méthode / stratégie de détermination de NBCONGE,
     * et NON les règles de décompte du grand-livre des congés.
     *
     * Le solde Congés reste calendaire tant que les règles
     * RG-CONGE-01 à RG-CONGE-03 ne sont pas explicitement modifiées.
     */
    public BigDecimal calculerNbJoursCongeValorises(Integer idEmploye, LocalDate dateDebutPeriode, LocalDate dateFinPeriode, Connection c) throws Exception {
        return calculerJoursCongeValorisesParDate(idEmploye, dateDebutPeriode, dateFinPeriode, c)
                .values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Même décompte canonique, exposé par date pour la déduction salariale d'une campagne à deux salaires. */
    public java.util.Map<LocalDate, BigDecimal> calculerJoursCongeValorisesParDate(Integer idEmploye,
            LocalDate dateDebutPeriode, LocalDate dateFinPeriode, Connection c) throws Exception {
        if (idEmploye == null || dateDebutPeriode == null || dateFinPeriode == null) {
            return java.util.Map.of();
        }
        if (dateFinPeriode.isBefore(dateDebutPeriode)) {
            return java.util.Map.of();
        }

        String sql = "SELECT dc.date_debut, dc.date_fin, dc.est_demi_journee, dc.nb_jours_decomptes " +
                     "FROM demande_conge dc " +
                     "JOIN ref_statut_demande_conge sc ON dc.id_statut_demande_conge = sc.id_statut_demande_conge " +
                     "JOIN ref_type_conge tc ON dc.id_type_conge = tc.id_type_conge " +
                     "WHERE dc.id_employe = ? " +
                     "AND sc.code = 'VALIDE' " +
                     "AND tc.decompte_solde_acquis = TRUE " +
                     "AND dc.date_debut <= ? AND dc.date_fin >= ? " +
                     "ORDER BY dc.date_debut ASC";

        java.util.Map<LocalDate, BigDecimal> parDate = new java.util.LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, Date.valueOf(dateFinPeriode));
            ps.setDate(3, Date.valueOf(dateDebutPeriode));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate dDeb = rs.getDate("date_debut").toLocalDate();
                    LocalDate dFin = rs.getDate("date_fin").toLocalDate();
                    boolean estDemiJournee = rs.getBoolean("est_demi_journee");

                    LocalDate debutIntersection = dDeb.isBefore(dateDebutPeriode) ? dateDebutPeriode : dDeb;
                    LocalDate finIntersection = dFin.isAfter(dateFinPeriode) ? dateFinPeriode : dFin;

                    if (!finIntersection.isBefore(debutIntersection)) {
                        if (estDemiJournee && !debutIntersection.equals(finIntersection))
                            throw new IllegalArgumentException("Une demi-journée ne peut couvrir plusieurs dates.");
                        for (LocalDate jour = debutIntersection; !jour.isAfter(finIntersection); jour = jour.plusDays(1)) {
                            BigDecimal jours = CongeDecompteCalendaireResolver.calculer(
                                    idEmploye, jour, jour, estDemiJournee, c);
                            if (jours.signum() > 0) parDate.merge(jour, jours, BigDecimal::add);
                        }
                    }
                }
            }
        }
        return java.util.Collections.unmodifiableMap(parDate);
    }

    /**
     * Calcule la ventilation d'imputation exacte du solde d'un employé selon la règle canonique RG-CONGE-06.
     * Détermine sans altération comptable la part issue du stock historique ([SOLDE_INITIAL])
     * et la part issue des acquisitions datées (FIFO chronologique).
     */
    public mg.galaxam.sirh.conge.domain.PurgeFifoEngine.ImputationResult getImputationSolde(int idEmploye, Connection c) throws Exception {
        String sql = "SELECT id_mouvement, type_mouvement, nb_jours, date_mouvement, commentaire " +
                     "FROM mouvement_solde_conge WHERE id_employe = ? ORDER BY date_mouvement ASC, id_mouvement ASC";
        double stockHistoriqueInitial = 0.0;
        java.util.List<mg.galaxam.sirh.conge.domain.PurgeFifoEngine.DroitDate> droitsDates = new java.util.ArrayList<>();
        double totalPris = 0.0;
        double totalAnnulations = 0.0;
        double totalAjustementsNegatifs = 0.0;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String typeMouvement = rs.getString("type_mouvement");
                    double nbJours = rs.getDouble("nb_jours");
                    Date dateMvtSql = rs.getDate("date_mouvement");
                    LocalDate dateMvt = dateMvtSql != null ? dateMvtSql.toLocalDate() : LocalDate.now();
                    String commentaire = rs.getString("commentaire");

                    if ("AJUSTEMENT".equals(typeMouvement) && mg.galaxam.sirh.conge.domain.PurgeFifoEngine.estStockInitial(commentaire)) {
                        stockHistoriqueInitial += nbJours;
                    } else if ("ACQUISITION".equals(typeMouvement) || "BONIFICATION".equals(typeMouvement)) {
                        droitsDates.add(new mg.galaxam.sirh.conge.domain.PurgeFifoEngine.DroitDate(rs.getInt("id_mouvement"), dateMvt, nbJours));
                    } else if ("PRISE".equals(typeMouvement)) {
                        totalPris += (-nbJours);
                    } else if ("ANNULATION".equals(typeMouvement)) {
                        totalAnnulations += nbJours;
                    } else if ("AJUSTEMENT".equals(typeMouvement) && nbJours < 0) {
                        totalAjustementsNegatifs += (-nbJours);
                    }
                }
            }
        }

        double consommationNette = Math.max(0.0, totalPris - totalAnnulations + totalAjustementsNegatifs);
        return purgeFifoEngine.calculerImputation(stockHistoriqueInitial, droitsDates, consommationNette);
    }
}
