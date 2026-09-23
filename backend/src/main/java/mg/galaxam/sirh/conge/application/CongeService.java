package mg.galaxam.sirh.conge.application;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

    private int getParamGlobalInt(String code, int defaultValue, Connection c) {
        try {
            return ParametreGlobal.getInt(ParametreGlobal.loadActiveMap(c), code, defaultValue);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    public mg.galaxam.sirh.conge.domain.PurgeFifoEngine getPurgeFifoEngine() {
        return this.purgeFifoEngine;
    }

    private static final class EmployeSoldeAccumulateur {
        double stockHistoriqueInitial;
        final java.util.List<PurgeFifoEngine.DroitDate> droitsDates = new java.util.ArrayList<>();
        double totalPris;
        double totalAnnulations;
        double totalAjustementsNegatifs;
        double totalDejaExpire;

        double consommationNette() {
            return Math.max(0.0, totalPris - totalAnnulations + totalAjustementsNegatifs);
        }
    }

    private java.util.Map<Integer, EmployeSoldeAccumulateur> chargerLedgerPourImputation(
            LocalDate dateReference, Connection c) throws Exception {
        java.util.Map<Integer, EmployeSoldeAccumulateur> soldes = new java.util.HashMap<>();
        String sql = "SELECT id_employe, id_mouvement, type_mouvement, nb_jours, date_mouvement, commentaire " +
                "FROM mouvement_solde_conge ORDER BY date_mouvement ASC, id_mouvement ASC";
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int idEmploye = rs.getInt("id_employe");
                String type = rs.getString("type_mouvement");
                double jours = rs.getDouble("nb_jours");
                Date dateSql = rs.getDate("date_mouvement");
                LocalDate date = dateSql != null ? dateSql.toLocalDate() : dateReference;
                String commentaire = rs.getString("commentaire");
                EmployeSoldeAccumulateur acc = soldes.computeIfAbsent(idEmploye, ignored -> new EmployeSoldeAccumulateur());
                if ("AJUSTEMENT".equals(type) && commentaire != null && commentaire.startsWith("[SOLDE_INITIAL]")) {
                    acc.stockHistoriqueInitial += jours;
                } else if ("ACQUISITION".equals(type) || "BONIFICATION".equals(type)) {
                    acc.droitsDates.add(new PurgeFifoEngine.DroitDate(rs.getInt("id_mouvement"), date, jours));
                } else if ("PRISE".equals(type)) {
                    acc.totalPris += -jours;
                } else if ("ANNULATION".equals(type)) {
                    acc.totalAnnulations += jours;
                } else if ("AJUSTEMENT".equals(type) && jours < 0) {
                    acc.totalAjustementsNegatifs += -jours;
                } else if ("EXPIRATION".equals(type)) {
                    acc.totalDejaExpire += -jours;
                }
            }
        }
        return soldes;
    }

    public java.util.Map<Integer, Double> calculerExpirationsPotentielles(
            LocalDate seuilPrescription, Connection c) throws Exception {
        LocalDate ref = LocalDate.now();
        java.util.Map<Integer, Double> resultats = new java.util.HashMap<>();
        for (java.util.Map.Entry<Integer, EmployeSoldeAccumulateur> entry
                : chargerLedgerPourImputation(ref, c).entrySet()) {
            EmployeSoldeAccumulateur acc = entry.getValue();
            PurgeFifoEngine.ImputationResult imputation = purgeFifoEngine.calculerImputation(
                    acc.stockHistoriqueInitial, acc.droitsDates, acc.consommationNette());
            double jours = purgeFifoEngine.calculerJoursAExpierFifo(
                    imputation, seuilPrescription, acc.totalDejaExpire);
            if (jours > 0.001) resultats.put(entry.getKey(), jours);
        }
        return java.util.Map.copyOf(resultats);
    }

    /** Exécute transactionnellement la purge FIFO du ledger à une date donnée. */
    public int executerPurge(LocalDate dateReference, Connection c) throws Exception {
        LocalDate ref = dateReference != null ? dateReference : LocalDate.now();
        java.util.Map<String, String> params = ParametreGlobal.loadActiveMap(c);
        int moisPrescription = ParametreGlobal.getInt(params, "DUREE_PRESCRIPTION_SOLDE_CONGE_MOIS", 36);
        LocalDate seuilPrescription = ref.minusMonths(moisPrescription);

        java.util.Map<Integer, EmployeSoldeAccumulateur> soldes = chargerLedgerPourImputation(ref, c);

        String insert = "INSERT INTO mouvement_solde_conge " +
                "(id_employe, type_mouvement, nb_jours, date_mouvement, commentaire) " +
                "VALUES (?, 'EXPIRATION', ?, ?, ?)";
        int nbPurges = 0;
        try (PreparedStatement ps = c.prepareStatement(insert)) {
            for (java.util.Map.Entry<Integer, EmployeSoldeAccumulateur> entry : soldes.entrySet()) {
                EmployeSoldeAccumulateur acc = entry.getValue();
                PurgeFifoEngine.ImputationResult imputation = purgeFifoEngine.calculerImputation(
                        acc.stockHistoriqueInitial, acc.droitsDates, acc.consommationNette());
                double joursAExpirer = purgeFifoEngine.calculerJoursAExpierFifo(
                        imputation, seuilPrescription, acc.totalDejaExpire);
                if (joursAExpirer <= 0.001) continue;

                ps.setInt(1, entry.getKey());
                ps.setDouble(2, -joursAExpirer);
                ps.setDate(3, Date.valueOf(ref));
                ps.setString(4, "Purge légale FIFO (droits datés de +" + moisPrescription + " mois prescrits)");
                ps.executeUpdate();
                try {
                    NotificationRhService.creer(c, "ALERTE_ECHEANCE", "HAUTE", "Congés expirés purgés (FIFO)",
                            String.format("Attention, %.2f jours de congés ont expiré après %d mois.", joursAExpirer, moisPrescription),
                            "/employes/" + entry.getKey() + "?tab=conges", entry.getKey());
                } catch (Exception ignored) {
                    // Une notification ne doit pas annuler l'écriture comptable d'expiration.
                }
                nbPurges++;
            }
        }
        return nbPurges;
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
                LocalDate d = (dateRef != null) ? dateRef : LocalDate.now();
                mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale configLegale =
                        mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale.findActiveAt(c, d);
                if (configLegale != null && configLegale.getAcquisitionCongeMensuelJours() != null) {
                    tauxAcquisition = configLegale.getAcquisitionCongeMensuelJours();
                }

                // 2. Diviseur calendaire configurable
                java.util.Map<String, mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal.ParametreEtat> etatMap =
                        mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal.loadEtatMap(c);
                double divBdd = mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal.getDouble(
                        etatMap, "DIVISEUR_JOURS_CALENDAIRE_CONGE", 30.0, 30.0);
                diviseur = BigDecimal.valueOf(divBdd);
            } catch (Exception e) {
                // Fallback non bloquant sur valeurs légales par défaut
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
        LocalDate dateAcqRef = LocalDate.of(annee, mois, 1);
        BigDecimal droitMois = calculerDroitAcquis(absencesInjustifiees, dateAcqRef, c);

        // Respect du plafond légal de cumul (par défaut 90j)
        try {
            java.util.Map<String, mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal.ParametreEtat> etatMap =
                    mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal.loadEtatMap(c);
            BigDecimal plafondCumulSolde = BigDecimal.valueOf(
                    mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal.getDouble(etatMap, "PLAFOND_CUMUL_SOLDE_CONGE_JOURS", 90.0, 90.0));

            VSoldeCongeEmploye solde = VSoldeCongeEmploye.rechercherParEmploye(idEmploye, c);
            BigDecimal soldeActuel = (solde != null && solde.getSoldeActuel() != null) ? solde.getSoldeActuel() : BigDecimal.ZERO;

            if (soldeActuel.compareTo(plafondCumulSolde) >= 0) {
                droitMois = BigDecimal.ZERO;
            } else if (soldeActuel.add(droitMois).compareTo(plafondCumulSolde) > 0) {
                droitMois = plafondCumulSolde.subtract(soldeActuel);
            }
        } catch (Exception e) {
            // fallback non bloquant
        }

        // Protection anti-duplication au cutover (Phase 2A-3B) :
        // Si un solde initial arrêté couvre déjà cette période, aucune acquisition fictive ou doublée ne doit être ajoutée.
        String sqlCheckCutover = "SELECT date_mouvement, commentaire FROM mouvement_solde_conge " +
                "WHERE id_employe = ? AND type_mouvement = 'AJUSTEMENT' " +
                "AND commentaire LIKE '%[SOLDE_INITIAL]%'";
        try (PreparedStatement psCut = c.prepareStatement(sqlCheckCutover)) {
            psCut.setInt(1, idEmploye);
            try (ResultSet rsCut = psCut.executeQuery()) {
                while (rsCut.next()) {
                    Date dtMvt = rsCut.getDate("date_mouvement");
                    if (dtMvt != null) {
                        LocalDate dateArrete = dtMvt.toLocalDate();
                        String comm = rsCut.getString("commentaire");
                        LocalDate moisVise = LocalDate.of(annee, mois, 1);
                        LocalDate moisCutover = LocalDate.of(dateArrete.getYear(), dateArrete.getMonthValue(), 1);

                        // Si le mois visé est strictement antérieur au mois de cutover, il est déjà absorbé dans le solde initial
                        if (moisVise.isBefore(moisCutover)) {
                            return BigDecimal.ZERO;
                        }
                        // Si le mois visé correspond au mois du cutover et que l'acquisition était explicitement incluse
                        if (moisVise.equals(moisCutover) && comm != null && comm.contains("inclut acquisition")) {
                            return BigDecimal.ZERO;
                        }
                    }
                }
            }
        }

        String sqlCheck = "SELECT id_mouvement, nb_jours FROM mouvement_solde_conge " +
                "WHERE id_employe = ? AND type_mouvement = 'ACQUISITION' " +
                "AND EXTRACT(YEAR FROM date_mouvement) = ? AND EXTRACT(MONTH FROM date_mouvement) = ? " +
                "FOR UPDATE";
        Integer idMouvementExistant = null;
        BigDecimal ancienNbJours = null;
        try (PreparedStatement ps = c.prepareStatement(sqlCheck)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, annee);
            ps.setInt(3, mois);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    idMouvementExistant = rs.getInt("id_mouvement");
                    ancienNbJours = rs.getBigDecimal("nb_jours");
                }
            }
        }

        if (idMouvementExistant == null) {
            String sqlIns = "INSERT INTO mouvement_solde_conge (" +
                    "id_employe, type_mouvement, nb_jours, date_mouvement, commentaire, created_by" +
                    ") VALUES (?, 'ACQUISITION', ?, ?, ?, ?)";
            LocalDate dateAcquisition = java.time.LocalDate.of(annee, mois, Math.min(28, java.time.LocalDate.of(annee, mois, 1).lengthOfMonth()));
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

    private record ContratActif(int idEmploye, LocalDate dateDebut) {}
    private record MouvementDroit(String type, LocalDate date, double jours) {}

    /** Orchestration canonique de l'acquisition et de la bonification mensuelles. */
    public int executerAcquisitionMensuelle(LocalDate dateReference, Connection c) throws Exception {
        LocalDate ref = dateReference != null ? dateReference : LocalDate.now();
        boolean dernierJourMois = ref.plusDays(1).getMonth() != ref.getMonth();
        java.util.Map<String, ParametreGlobal.ParametreEtat> params = ParametreGlobal.loadEtatMap(c);
        double plafond = ParametreGlobal.getDouble(params, "PLAFOND_CUMUL_SOLDE_CONGE_JOURS", 90.0, 90.0);
        boolean bonusActif = ParametreGlobal.isActif(params, "SEUIL_ANCIENNETE_BONIFICATION_ANS", true);
        int seuil = bonusActif ? ParametreGlobal.getInt(params, "SEUIL_ANCIENNETE_BONIFICATION_ANS", 5, 999) : 999;
        int palier = ParametreGlobal.getInt(params, "PALIER_ANNEES_BONIFICATION", 5, 5);
        int joursPalier = bonusActif ? ParametreGlobal.getInt(params, "JOURS_BONIFICATION_PAR_PALIER", 1, 0) : 0;

        java.util.List<ContratActif> contrats = new java.util.ArrayList<>();
        String sqlContrats = "SELECT ct.id_employe, ct.date_debut FROM contrat_travail ct " +
                "JOIN ref_statut_rh s ON ct.id_statut_rh = s.id_statut_rh WHERE s.code = 'ACTIF'";
        try (PreparedStatement ps = c.prepareStatement(sqlContrats); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Date debut = rs.getDate("date_debut");
                if (debut != null) contrats.add(new ContratActif(rs.getInt("id_employe"), debut.toLocalDate()));
            }
        }

        String sqlMouvements = "SELECT type_mouvement, date_mouvement, nb_jours " +
                "FROM mouvement_solde_conge WHERE id_employe = ?";
        int employesTraites = 0;
        try (PreparedStatement psMvt = c.prepareStatement(sqlMouvements)) {
            for (ContratActif contrat : contrats) {
                boolean eligible = !contrat.dateDebut().isAfter(ref.minusMonths(1));
                boolean echeanceAtteinte = ref.getDayOfMonth() >= contrat.dateDebut().getDayOfMonth()
                        || (dernierJourMois && contrat.dateDebut().getDayOfMonth() > ref.getDayOfMonth());
                if (!eligible || !echeanceAtteinte) continue;

                java.util.List<MouvementDroit> mouvements = new java.util.ArrayList<>();
                psMvt.setInt(1, contrat.idEmploye());
                try (ResultSet rs = psMvt.executeQuery()) {
                    while (rs.next()) {
                        Date date = rs.getDate("date_mouvement");
                        if (date != null) mouvements.add(new MouvementDroit(
                                rs.getString("type_mouvement"), date.toLocalDate(), rs.getDouble("nb_jours")));
                    }
                }
                double solde = mouvements.stream().mapToDouble(MouvementDroit::jours).sum();
                boolean acquis = mouvements.stream().anyMatch(m -> "ACQUISITION".equals(m.type())
                        && m.date().getYear() == ref.getYear() && m.date().getMonth() == ref.getMonth());
                if (!acquis) {
                    solde += enregistrerAcquisitionMensuelle(contrat.idEmploye(), ref.getYear(), ref.getMonthValue(),
                            BigDecimal.ZERO, null, c).doubleValue();
                }

                int anciennete = java.time.Period.between(contrat.dateDebut(), ref).getYears();
                int bonus = calculateurEngine.calculerBonificationAnciennete(anciennete, seuil, palier, joursPalier);
                boolean anniversaireAtteint = ref.getMonthValue() >= contrat.dateDebut().getMonthValue();
                boolean dejaBonifie = mouvements.stream().anyMatch(m -> "BONIFICATION".equals(m.type())
                        && m.date().getYear() == ref.getYear());
                if (bonus > 0 && anniversaireAtteint && !dejaBonifie && solde < plafond) {
                    double aCrediter = Math.min(bonus, Math.max(0.0, plafond - solde));
                    if (aCrediter > 0) {
                        MouvementSoldeConge mouvement = new MouvementSoldeConge();
                        mouvement.setIdEmploye(contrat.idEmploye());
                        mouvement.setTypeMouvement("BONIFICATION");
                        mouvement.setNbJours(BigDecimal.valueOf(aCrediter));
                        mouvement.setCommentaire("Bonification d'ancienneté (" + anciennete + " ans)");
                        mouvement.save(c);
                    }
                }
                employesTraites++;
            }
        }
        return employesTraites;
    }

    public BigDecimal simulerNombreJours(Date dateDebut, Date dateFin, boolean estDemiJournee) {
        if (dateDebut == null || dateFin == null) {
            throw new IllegalArgumentException("Les dates de début et de fin sont obligatoires pour la simulation.");
        }
        return calculateurEngine.calculerNombreJours(dateDebut.toLocalDate(), dateFin.toLocalDate(), estDemiJournee);
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
            dureeFixe = BigDecimal.valueOf(getParamGlobalInt("DUREE_MAX_MATERNITE_PATHOLOGIQUE_JOURS", 119, c));
        }
        LocalDate finCalculee = calculateurEngine.calculerDateFinFixe(dateDebut.toLocalDate(), dureeFixe);
        Date dateFinEffective = finCalculee != null ? Date.valueOf(finCalculee) : dateFin;
        if (dateFinEffective == null) {
            throw new IllegalArgumentException("La date de fin est obligatoire pour un congé à durée libre.");
        }

        BigDecimal soldeAvant = BigDecimal.ZERO;
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

        int seuilLong = getParamGlobalInt("SEUIL_JOURS_PREAVIS_LONG", 3, c);
        int delaiCourt = getParamGlobalInt("DELAI_PREAVIS_COURT_JOURS", 2, c);
        int delaiLong = getParamGlobalInt("DELAI_PREAVIS_LONG_JOURS", 15, c);
        return calculateurEngine.simuler(new CongeCalculateurEngine.SimulationInput(
                dateDebut.toLocalDate(), dateFinEffective.toLocalDate(), estDemiJournee,
                soldeAvant, type.isDecompteSoldeAcquis(), derogationSoldeNegatif,
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
            java.util.Map<String, ParametreGlobal.ParametreEtat> params = ParametreGlobal.loadEtatMap(c);
            if (projete.signum() < 0) {
                if (!d.isDerogationSoldeNegatif()) {
                    throw new IllegalArgumentException("Solde de congé insuffisant. Dérogation requise.");
                }
                if (!ParametreGlobal.isActif(params, "PLAFOND_SOLDE_CONGE_NEGATIF", true)) {
                    throw new IllegalArgumentException("La prise de congés par anticipation est désactivée.");
                }
                int plafond = ParametreGlobal.getInt(params, "PLAFOND_SOLDE_CONGE_NEGATIF", -10, 0);
                if (projete.compareTo(BigDecimal.valueOf(plafond)) < 0) {
                    throw new IllegalArgumentException("Le plafond de solde négatif autorisé est dépassé.");
                }
            }
            MouvementSoldeConge prise = new MouvementSoldeConge();
            prise.setIdEmploye(d.getIdEmploye());
            prise.setTypeMouvement("PRISE");
            prise.setNbJours(d.getNbJoursDecomptes().negate());
            prise.setIdDemandeConge(idDemande);
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
            int seuil = ParametreGlobal.getInt(params, "SEUIL_ALERTE_SOLDE_CONGE_FAIBLE", 3, 0);
            if (projete.signum() < 0) {
                NotificationRhService.creer(c, "SOLDE_CONGE_NEGATIF", "HAUTE", "Solde négatif sous dérogation",
                        "Le solde est passé à " + projete + " jour(s).", "/conges/soldes", d.getIdEmploye());
            } else if (ParametreGlobal.isActif(params, "SEUIL_ALERTE_SOLDE_CONGE_FAIBLE", true)
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

        RefTypeConge type = RefTypeConge.rechercherParId(d.getIdTypeConge(), c);
        String motif = motifAnnulation != null && !motifAnnulation.isBlank() ? motifAnnulation.trim() : null;
        if ("VALIDE".equals(statut) && type != null && type.isDecompteSoldeAcquis()) {
            MouvementSoldeConge mouvement = new MouvementSoldeConge();
            mouvement.setIdEmploye(d.getIdEmploye());
            mouvement.setTypeMouvement("ANNULATION");
            mouvement.setNbJours(d.getNbJoursDecomptes());
            mouvement.setIdDemandeConge(idDemande);
            mouvement.setCommentaire("Restitution suite à l'annulation de la demande #" + idDemande
                    + (motif != null ? " : " + motif : ""));
            mouvement.setCreatedBy(currentUserId);
            mouvement.save(c);
        }
        RefStatutDemandeConge annule = RefStatutDemandeConge.rechercherParCode("ANNULE", c);
        if (annule == null) throw new IllegalStateException("Statut ANNULE non trouvé dans le référentiel.");
        d.setIdStatutDemandeConge(annule.getIdStatutDemandeConge());
        if (motif != null) d.setMotifRefus(motif);
        return (DemandeConge) d.update(c);
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
        java.util.Map<String, Object> res = new java.util.HashMap<>();
        if (dateDebut == null || dateFin == null) {
            throw new IllegalArgumentException("Les dates de début et de fin sont obligatoires.");
        }

        // 1. Décompte de base
        BigDecimal nbJours = calculateurEngine.calculerNombreJours(dateDebut.toLocalDate(), dateFin.toLocalDate(), estDemiJournee);
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
            res.put("collisionPersonnelle", false);
            res.put("collisionsPersonnelles", java.util.List.of());
            res.put("conflitPoste", false);
            res.put("colleguesAbsents", java.util.List.of());
            return res;
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

        // 4. Détection du poste, département et site de l'employé
        String sqlPoste = "SELECT a.id_fonction, f.libelle AS nom_fonction, a.id_departement, d.libelle AS nom_departement, a.id_site, s.libelle AS nom_site " +
                "FROM employe_affectation_carriere a " +
                "LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
                "LEFT JOIN ref_departement d ON a.id_departement = d.id_departement " +
                "LEFT JOIN ref_site s ON a.id_site = s.id_site " +
                "WHERE a.id_employe = ? AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
                "ORDER BY a.date_debut DESC LIMIT 1";
        Integer idFonction = null;
        Integer idSite = null;
        String nomFonction = null;
        String nomDepartement = null;
        String nomSite = null;
        try (PreparedStatement psP = c.prepareStatement(sqlPoste)) {
            psP.setInt(1, idEmploye);
            psP.setDate(2, dateFin);
            psP.setDate(3, dateDebut);
            try (ResultSet rsP = psP.executeQuery()) {
                if (rsP.next()) {
                    idFonction = (Integer) rsP.getObject("id_fonction");
                    idSite = (Integer) rsP.getObject("id_site");
                    nomFonction = rsP.getString("nom_fonction");
                    nomDepartement = rsP.getString("nom_departement");
                    nomSite = rsP.getString("nom_site");
                }
            }
        }
        res.put("idFonction", idFonction);
        res.put("idSite", idSite);
        res.put("nomFonction", nomFonction);
        res.put("nomDepartement", nomDepartement);
        res.put("nomSite", nomSite);

        // 5. Recherche des collègues du même poste et même site
        java.util.List<java.util.Map<String, Object>> colleguesAbsents = new java.util.ArrayList<>();
        int totalEmployesMemePoste = 1;
        if (idFonction != null) {
            String sqlCount = "SELECT COUNT(DISTINCT a.id_employe) AS cnt " +
                    "FROM employe_affectation_carriere a " +
                    "JOIN employe e ON a.id_employe = e.id_employe " +
                    "WHERE a.id_fonction = ? " +
                    (idSite != null && idSite > 0 ? "AND a.id_site = ? " : "") +
                    "AND e.est_actif = true " +
                    "AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?)";
            try (PreparedStatement psCnt = c.prepareStatement(sqlCount)) {
                int pIdx = 1;
                psCnt.setInt(pIdx++, idFonction);
                if (idSite != null && idSite > 0) psCnt.setInt(pIdx++, idSite);
                psCnt.setDate(pIdx++, dateFin);
                psCnt.setDate(pIdx++, dateDebut);
                try (ResultSet rsCnt = psCnt.executeQuery()) {
                    if (rsCnt.next()) totalEmployesMemePoste = Math.max(1, rsCnt.getInt("cnt"));
                }
            }

            String sqlCollegues = "SELECT e.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, " +
                    "dc.id_demande_conge, dc.date_debut, dc.date_fin, dc.est_demi_journee, dc.moment_demi_journee, " +
                    "tc.libelle AS libelle_conge, sc.code AS code_statut " +
                    "FROM demande_conge dc " +
                    "JOIN employe e ON dc.id_employe = e.id_employe " +
                    "JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
                    "JOIN ref_type_conge tc ON dc.id_type_conge = tc.id_type_conge " +
                    "JOIN ref_statut_demande_conge sc ON dc.id_statut_demande_conge = sc.id_statut_demande_conge " +
                    "WHERE a.id_fonction = ? " +
                    (idSite != null && idSite > 0 ? "AND a.id_site = ? " : "") +
                    "AND dc.id_employe != ? " +
                    "AND dc.id_demande_conge != COALESCE(?, -1) " +
                    "AND sc.code NOT IN ('REFUSE', 'ANNULE', 'REJETE') " +
                    "AND dc.date_debut <= ? AND dc.date_fin >= ? " +
                    "AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
                    "ORDER BY dc.date_debut ASC";
            try (PreparedStatement psC = c.prepareStatement(sqlCollegues)) {
                int pIdx = 1;
                psC.setInt(pIdx++, idFonction);
                if (idSite != null && idSite > 0) psC.setInt(pIdx++, idSite);
                psC.setInt(pIdx++, idEmploye);
                psC.setObject(pIdx++, idDemandeExclue != null ? idDemandeExclue : -1);
                psC.setDate(pIdx++, dateFin);
                psC.setDate(pIdx++, dateDebut);
                psC.setDate(pIdx++, dateFin);
                psC.setDate(pIdx++, dateDebut);
                try (ResultSet rsC = psC.executeQuery()) {
                    while (rsC.next()) {
                        boolean cDemi = rsC.getBoolean("est_demi_journee");
                        String cMoment = rsC.getString("moment_demi_journee");
                        Date cDeb = rsC.getDate("date_debut");
                        Date cFin = rsC.getDate("date_fin");

                        // Exclusion : demi-journées complémentaires sur la même date
                        if (estDemiJournee && cDemi && dateDebut.equals(dateFin) && cDeb.equals(cFin) && dateDebut.equals(cDeb)) {
                            if (momentDemiJournee != null && cMoment != null && !momentDemiJournee.equalsIgnoreCase(cMoment)) {
                                continue;
                            }
                        }

                        java.util.Map<String, Object> col = new java.util.HashMap<>();
                        col.put("idEmploye", rsC.getInt("id_employe"));
                        col.put("matricule", rsC.getString("matricule"));
                        col.put("nom", rsC.getString("nom"));
                        col.put("prenom", rsC.getString("prenom"));
                        col.put("photoUrl", rsC.getString("photo_url"));
                        col.put("idDemandeConge", rsC.getInt("id_demande_conge"));
                        col.put("typeConge", rsC.getString("libelle_conge"));
                        col.put("dateDebut", cDeb);
                        col.put("dateFin", cFin);
                        col.put("statut", rsC.getString("code_statut"));
                        colleguesAbsents.add(col);
                    }
                }
            }
        }

        boolean conflitPoste = !colleguesAbsents.isEmpty();
        res.put("totalEmployesPoste", totalEmployesMemePoste);
        res.put("colleguesAbsents", colleguesAbsents);
        res.put("conflitPoste", conflitPoste);
        if (conflitPoste) {
            String siteInfo = (nomSite != null && !nomSite.isEmpty()) ? " sur le site " + nomSite : "";
            res.put("messageAvertissement", colleguesAbsents.size() + " collaborateur(s) au même poste (" +
                    nomFonction + siteInfo + ") sont déjà absents sur cette période.");
        } else {
            res.put("messageAvertissement", null);
        }

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
                                case "ANNUEL" -> "#47603f";
                                case "MALADIE", "MALADIE_SANS_SOLDE" -> "#dc2626";
                                case "MATERNITE" -> "#7e22ce";
                                case "PATERNITE" -> "#6b21a8";
                                case "EXCEPTIONNEL" -> "#ea580c";
                                default -> "#5e6258";
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
        if (idEmploye == null || dateDebutPeriode == null || dateFinPeriode == null) {
            return BigDecimal.ZERO;
        }
        if (dateFinPeriode.isBefore(dateDebutPeriode)) {
            return BigDecimal.ZERO;
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

        BigDecimal total = BigDecimal.ZERO;
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
                        BigDecimal jours = calculateurEngine.calculerNbJoursCongeValorises(debutIntersection, finIntersection, estDemiJournee);
                        total = total.add(jours);
                    }
                }
            }
        }
        return total;
    }

    /**
     * Délégation unitaire de valorisation calendaire.
     */
    public BigDecimal calculerNbJoursCongeValorises(LocalDate debut, LocalDate fin, boolean estDemiJournee) {
        return calculateurEngine.calculerNbJoursCongeValorises(debut, fin, estDemiJournee);
    }

    /**
     * Initialise le solde de congé historique d'un salarié (reprise de données / migration).
     *
     * Invariants RG-CONGE (Phase 2A-3B) :
     * - Ne fabrique AUCUNE acquisition mensuelle artificielle passée (+2.5j par mois inventé).
     * - Enregistre un mouvement unitaire du grand-livre de type AJUSTEMENT avec tag [SOLDE_INITIAL].
     * - Enregistre la date d'effet (date d'arrêté) et indique formellement si l'acquisition du mois de cutover est incluse.
     * - Protège contre toute double acquisition future lors des paies ultérieures.
     */
    public MouvementSoldeConge initialiserSoldeHistorique(
            int idEmploye,
            BigDecimal soldeInitial,
            LocalDate dateArrete,
            boolean inclutAcquisitionMoisArrete,
            String motif,
            Integer currentUserId,
            Connection c) throws Exception {
        if (soldeInitial == null) {
            throw new IllegalArgumentException("Le solde initial est obligatoire.");
        }
        if (dateArrete == null) {
            throw new IllegalArgumentException("La date d'arrêté du solde initial est obligatoire.");
        }

        String tagAcq = inclutAcquisitionMoisArrete
                ? " (inclut acquisition " + dateArrete.getYear() + "-" + String.format("%02d", dateArrete.getMonthValue()) + ")"
                : " (exclut acquisition " + dateArrete.getYear() + "-" + String.format("%02d", dateArrete.getMonthValue()) + ")";
        String commentaire = "[SOLDE_INITIAL] Reprise historique arrêtée au " + dateArrete + tagAcq
                + (motif != null && !motif.isBlank() ? " : " + motif.trim() : " : Reprise initiale sans historique détaillé");

        MouvementSoldeConge mvt = new MouvementSoldeConge();
        mvt.setIdEmploye(idEmploye);
        mvt.setTypeMouvement("AJUSTEMENT");
        mvt.setNbJours(soldeInitial);
        mvt.setDateMouvement(Date.valueOf(dateArrete));
        mvt.setCommentaire(commentaire);
        mvt.setCreatedBy(currentUserId);
        mvt.save(c);

        return mvt;
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

                    if ("AJUSTEMENT".equals(typeMouvement) && commentaire != null && commentaire.startsWith("[SOLDE_INITIAL]")) {
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
