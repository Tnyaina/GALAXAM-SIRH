package mg.galaxam.sirh.paie.application;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.persistence.VSoldeCongeEmploye;
import mg.galaxam.sirh.conge.persistence.MouvementSoldeConge;
import mg.galaxam.sirh.contrat.domain.AncienneteEngine;
import mg.galaxam.sirh.contrat.persistence.EmployeEnfant;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import mg.galaxam.sirh.paie.persistence.PaiePret;
import mg.galaxam.sirh.paie.persistence.PaieStc;
import mg.galaxam.sirh.paie.application.export.PayrollEmployerSnapshotService;
import mg.galaxam.sirh.paie.domain.engine.SocialContributionCalculator;
import mg.galaxam.sirh.paie.domain.engine.PayrollTaxCalculations;
import mg.galaxam.sirh.paie.domain.engine.MoneyRoundingPolicy;
import mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale;
import mg.galaxam.sirh.paieconfig.application.SocialContributionContextResolver;
import mg.galaxam.sirh.paie.domain.engine.PayrollTaxCalculations.Tranche;
import mg.galaxam.sirh.paie.api.dto.stc.StcCalculRequest;
import mg.galaxam.sirh.paie.api.dto.stc.StcCalculResultDTO;
import mg.galaxam.sirh.paie.api.dto.stc.StcPreparationDTO;
import mg.galaxam.sirh.paie.api.dto.stc.StcRecuDTO;
import mg.galaxam.sirh.paie.api.dto.stc.StcReglementRequest;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.domain.engine.SpecialPremiumCollisionPolicy;
import mg.galaxam.sirh.paie.domain.engine.OvertimePayCalculator;
import mg.galaxam.sirh.pointage.application.PointageSyntheseService;
import mg.galaxam.sirh.pointage.api.dto.PointageSyntheseMensuelleDTO;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.paie.api.dto.stc.AnnulationReglementRequest;
import mg.galaxam.sirh.paie.api.dto.stc.StcReglementDTO;
import mg.galaxam.sirh.paie.api.dto.stc.StcSituationReglementDTO;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.stereotype.Service;
import java.util.ArrayList;

/**
 * Service Métier pour le traitement, la simulation, la validation et l'émission
 * du solde de tout compte et de son reçu détaillé.
 */
@Service
public class StcService {

    private record BulletinFiscalSnapshot(BigDecimal revenuNetImposable, BigDecimal irsaNet,
            BigDecimal baseCnaps, BigDecimal cnapsSal, BigDecimal cnapsPat,
            BigDecimal baseSante, BigDecimal santeSal, BigDecimal santePat,
            BigDecimal baseFmfp, BigDecimal fmfpPat) {}

    private BulletinFiscalSnapshot chargerBulletinFiscal(Integer idBulletin, Connection c) throws Exception {
        BulletinPaie bulletin = BulletinPaie.trouverParId(idBulletin, c);
        if (bulletin == null) throw new IllegalArgumentException("Le bulletin mensuel lié est introuvable.");
        BigDecimal[] valeurs = new BigDecimal[8];
        java.util.Arrays.fill(valeurs, BigDecimal.ZERO);
        boolean lignesSocialesTrouvees = false;
        BigDecimal exclusionsIrsa = BigDecimal.ZERO;
        String sql = "SELECT code_rubrique, sens, categorie, imposable, base_calcul, montant_salarial, montant_patronal "
                + "FROM bulletin_paie_ligne WHERE id_bulletin = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idBulletin);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("code_rubrique");
                    String sens = rs.getString("sens");
                    if (code == null) continue;
                    BigDecimal base = zeroIfNull(rs.getBigDecimal("base_calcul"));
                    BigDecimal salarial = zeroIfNull(rs.getBigDecimal("montant_salarial"));
                    BigDecimal patronal = zeroIfNull(rs.getBigDecimal("montant_patronal"));
                    if ("HS_EXONEREE_IRSA".equals(code)) exclusionsIrsa = exclusionsIrsa.add(salarial);
                    if ("GAIN".equals(sens) && !rs.getBoolean("imposable")
                            && !"INDEMNITE_NON_IMPOSABLE".equals(rs.getString("categorie"))) {
                        exclusionsIrsa = exclusionsIrsa.add(salarial);
                    }
                    if (code.startsWith("COTISATION_") && ("RETENUE".equals(sens) || "PATRONAL".equals(sens))) {
                        lignesSocialesTrouvees = true;
                    }
                    if ("COTISATION_CNAPS_SAL".equals(code)) {
                        valeurs[0] = base; valeurs[1] = salarial;
                    } else if ("COTISATION_CNAPS_PAT".equals(code)) {
                        valeurs[2] = patronal;
                    } else if (code.startsWith("COTISATION_") && code.endsWith("_SAL")
                            && !"COTISATION_CNAPS_SAL".equals(code) && !"COTISATION_FMFP_SAL".equals(code)) {
                        valeurs[3] = base; valeurs[4] = salarial;
                    } else if (code.startsWith("COTISATION_") && code.endsWith("_PAT")
                            && !"COTISATION_CNAPS_PAT".equals(code) && !"COTISATION_FMFP_PAT".equals(code)) {
                        valeurs[5] = patronal;
                    } else if ("COTISATION_FMFP_PAT".equals(code) && "PATRONAL".equals(sens)) {
                        valeurs[6] = base; valeurs[7] = patronal;
                    }
                }
            }
        }
        if ((zeroIfNull(bulletin.getTotalCotisationsSalariales()).signum() > 0
                || zeroIfNull(bulletin.getTotalCotisationsPatronales()).signum() > 0) && !lignesSocialesTrouvees) {
            throw new IllegalArgumentException("Les lignes de cotisation du bulletin mensuel sont nécessaires au calcul différentiel du STC.");
        }
        return new BulletinFiscalSnapshot(zeroIfNull(bulletin.getRevenuNetImposable()).subtract(exclusionsIrsa).max(BigDecimal.ZERO),
                zeroIfNull(bulletin.getIrsaNet()), valeurs[0], valeurs[1], valeurs[2],
                valeurs[3], valeurs[4], valeurs[5], valeurs[6], valeurs[7]);
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StcService.class);

    private final AncienneteEngine ancienneteEngine;
    private final mg.galaxam.sirh.conge.application.CongeLotValorisationService lotsConges =
            new mg.galaxam.sirh.conge.application.CongeLotValorisationService();
    private final mg.galaxam.sirh.paie.domain.engine.OvertimePayCalculator overtimePayCalculator =
            new mg.galaxam.sirh.paie.domain.engine.OvertimePayCalculator();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private mg.galaxam.sirh.pointage.application.PointageSyntheseService pointageSyntheseService;

    public StcService(DataSource dataSource, Object ignoredLegacyCalculator, AncienneteEngine ancienneteEngine) {
        this.ancienneteEngine = ancienneteEngine != null ? ancienneteEngine : new AncienneteEngine();
    }

    @org.springframework.beans.factory.annotation.Autowired
    public StcService(DataSource dataSource) {
        this(dataSource, null, new AncienneteEngine());
    }

    public StcService() {
        this(null, null, new AncienneteEngine());
    }

    public void setPointageSyntheseService(PointageSyntheseService pointageSyntheseService) {
        this.pointageSyntheseService = pointageSyntheseService;
    }


    // =========================================================================
    // 1. PRÉPARATION ADMINISTRATIVE & COLLECTE DES DROITS
    // =========================================================================

    public StcPreparationDTO preparerStc(Integer idEmploye, Date dateDepart, String motif, Connection c) throws Exception {
        if (idEmploye == null) {
            throw new IllegalArgumentException("L'identifiant de l'employé est obligatoire.");
        }
        if (dateDepart == null) {
            throw new IllegalArgumentException("La date de départ métier est obligatoire.");
        }
        Date dateDepartEffective = dateDepart;

        StcPreparationDTO prep = new StcPreparationDTO();
        prep.setIdEmploye(idEmploye);
        prep.setDateDepart(dateDepartEffective);
        prep.setMotifRupture(motif != null ? motif : "DEMISSION");

        // La date de départ est la date métier du contexte Emploi pour le STC.
        var emploi = new mg.galaxam.sirh.contrat.application.EmploymentContextResolver()
                .resolveContext(idEmploye, dateDepartEffective.toLocalDate(), c);
        if (emploi == null || emploi.idContrat() == null || emploi.idAffectation() == null) {
            throw PayrollConfigurationGuard.missing("AFFECTATION_CONTRAT", idEmploye,
                    dateDepartEffective.toString());
        }
        prep.setIdContrat(emploi.idContrat());
        prep.setMatricule(emploi.matricule());
        prep.setNomComplet(emploi.nomComplet());
        prep.setCinNumero(emploi.cinNumero());
        prep.setCinDateDelivrance(emploi.cinDateDelivrance() == null ? null : Date.valueOf(emploi.cinDateDelivrance()));
        prep.setCinLieuDelivrance(emploi.cinLieuDelivrance());
        prep.setNumeroCnaps(emploi.numAffiliationCnaps());
        prep.setIntitulePoste(emploi.fonctionNom() == null ? "Collaborateur" : emploi.fonctionNom());
        prep.setDepartement(emploi.departementNom());
        prep.setSite(emploi.nomSite());
        prep.setCodeSite(emploi.codeSite());
        String catPro = emploi.categorieProCode();
        if (catPro == null || catPro.isBlank())
            throw PayrollConfigurationGuard.missing("CATEGORIE_PROFESSIONNELLE", idEmploye,
                    dateDepartEffective.toString());
        prep.setCategorieProfessionnelle(catPro);
        if ("PRESTATION".equalsIgnoreCase(emploi.typeContratCode())
                || "CONSULTANT".equalsIgnoreCase(emploi.regimeCode()) || emploi.soumisIsi5()) {
            throw new IllegalArgumentException("Le STC est réservé aux salariés. Une prestation indépendante se règle sur facture.");
        }
        prep.setRegimeFiscal("IRSA");
        BigDecimal salaireBase = emploi.salaireBase();
        if (salaireBase == null || salaireBase.signum() <= 0)
            throw PayrollConfigurationGuard.missing("SALAIRE", idEmploye, dateDepartEffective.toString());
        prep.setSalaireBase(salaireBase);
        Date dateEmbauche = emploi.dateEmbaucheInitiale() == null ? null
                : Date.valueOf(emploi.dateEmbaucheInitiale());
        if (dateEmbauche == null)
            throw PayrollConfigurationGuard.missing("DATE_EMBAUCHE", idEmploye,
                    dateDepartEffective.toString());
        prep.setDateEmbauche(dateEmbauche);

        // Nombre d'enfants à charge éligibles à la déduction IRSA
        try {
            int nbEnfants = EmployeEnfant.nbEnfantsEligibles(idEmploye, dateDepartEffective, c);
            prep.setNbEnfantsACharge(nbEnfants);
        } catch (Exception e) {
            log.warn("[StcService] Impossible de récupérer les enfants à charge pour employé {}: {}", idEmploye, e.getMessage());
        }


        // Taux horaire légal résolu depuis la configuration légale applicable
        PaieConfigLegale cfgLegale = chargerConfigLegale(dateDepartEffective, c);
        BigDecimal diviseurHoraire = cfgLegale.getDiviseurMensuelHeures();
        if (diviseurHoraire == null || diviseurHoraire.signum() <= 0)
            throw PayrollConfigurationGuard.missing("DIVISEUR_MENSUEL_HEURES", idEmploye,
                    dateDepartEffective.toString());
        BigDecimal tauxHoraire = salaireBase.divide(diviseurHoraire, 4, RoundingMode.HALF_UP);
        prep.setTauxHoraire(tauxHoraire);

        // 2. Ancienneté via AncienneteEngine (décompte complet des contrats successifs)
        AncienneteEngine.AncienneteBilan bilan = calculerAncienneteEmploye(idEmploye, dateDepartEffective, c);
        if (bilan != null && (bilan.ansEffectifs() > 0 || bilan.moisEffectifs() > 0 || bilan.dateOrigineChaine() != null)) {
            prep.setAncienneteAnnees(bilan.ansEffectifs());
            prep.setAncienneteMois(bilan.moisEffectifs());
            if (bilan.dateOrigineChaine() != null) {
                dateEmbauche = Date.valueOf(bilan.dateOrigineChaine());
                prep.setDateEmbauche(dateEmbauche);
            }
        } else if (dateEmbauche != null) {
            LocalDate dDebut = dateEmbauche.toLocalDate();
            LocalDate dFin = dateDepartEffective.toLocalDate();
            if (!dFin.isBefore(dDebut)) {
                Period p = Period.between(dDebut, dFin);
                prep.setAncienneteAnnees(p.getYears());
                prep.setAncienneteMois(p.getYears() * 12 + p.getMonths());
            }
        }

        // 3. Période de paie correspondante
        String sqlPer = "SELECT id_periode_paie, code, date_debut, date_fin FROM periode_paie " +
                "WHERE ? BETWEEN date_debut AND date_fin ORDER BY date_debut DESC, id_periode_paie DESC LIMIT 2";
        LocalDate dDebutPeriode = null;
        LocalDate dFinPeriode = null;
        try (PreparedStatement psPer = c.prepareStatement(sqlPer)) {
            psPer.setDate(1, dateDepartEffective);
            try (ResultSet rsPer = psPer.executeQuery()) {
                if (rsPer.next()) {
                    prep.setIdPeriodePaie(rsPer.getInt("id_periode_paie"));
                    prep.setCodePeriodePaie(rsPer.getString("code"));
                    Date dd = rsPer.getDate("date_debut");
                    Date df = rsPer.getDate("date_fin");
                    if (dd != null) dDebutPeriode = dd.toLocalDate();
                    if (df != null) dFinPeriode = df.toLocalDate();
                    if (rsPer.next()) {
                        throw new IllegalStateException("Plusieurs périodes de paie couvrent la date de départ " + dateDepartEffective + ".");
                    }
                } else {
                    // Pour un départ historique ou anticipé (hors calendrier de paie actif),
                    // on ne bloque pas la préparation / simulation STC : l'imputation se fera sur la période de régularisation
                    prep.setIdPeriodePaie(null);
                    prep.setCodePeriodePaie("HORS_CALENDRIER");
                }
            }
        }

        // 4. Estimation du salaire au prorata du dernier mois (avec intégration du pointage réel si disponible)
        LocalDate dDep = dateDepartEffective.toLocalDate();
        LocalDate startPeriode = dDebutPeriode != null ? dDebutPeriode : dDep.withDayOfMonth(1);
        LocalDate endPeriode = dFinPeriode != null ? dFinPeriode : dDep;

        var empResolver = new mg.galaxam.sirh.contrat.application.EmploymentContextResolver();
        mg.galaxam.sirh.contrat.application.EmploymentContextResolver.Coverage coverage = null;
        try {
            coverage = empResolver.resolveCoverage(idEmploye, startPeriode, dDep, c);
        } catch (Exception ignored) {}
        LocalDate debutCouverture = coverage != null ? coverage.firstDay() : startPeriode;
        LocalDate finCouverture = coverage != null ? coverage.lastDay() : dDep;

        String sqlPointageReel = "SELECT COUNT(*) AS lignes_pointage, " +
                "COUNT(*) FILTER (WHERE r.decompte_absence = FALSE AND (pj.total_heures > 0 OR pj.heures_matin > 0 OR pj.heures_am > 0)) AS jours_travailles_reels, " +
                "COALESCE(SUM(pj.total_heures), 0) AS total_heures_reelles " +
                "FROM pointage_jour pj " +
                "JOIN ref_statut_pointage_jour r ON r.id_statut_jour = pj.id_statut_jour " +
                "WHERE pj.id_employe = ? AND pj.date_pointage >= ? AND pj.date_pointage <= ?";

        BigDecimal joursPointage = BigDecimal.ZERO;
        BigDecimal heuresPointage = BigDecimal.ZERO;
        boolean pointageTrouve = false;
        try (PreparedStatement psPtg = c.prepareStatement(sqlPointageReel)) {
            psPtg.setInt(1, idEmploye);
            psPtg.setDate(2, Date.valueOf(debutCouverture));
            psPtg.setDate(3, Date.valueOf(finCouverture));
            try (ResultSet rsPtg = psPtg.executeQuery()) {
                if (rsPtg.next()) {
                    int countJours = rsPtg.getInt("jours_travailles_reels");
                    BigDecimal sumHeures = rsPtg.getBigDecimal("total_heures_reelles");
                    if (rsPtg.getInt("lignes_pointage") > 0) {
                        joursPointage = new BigDecimal(countJours);
                        heuresPointage = sumHeures != null ? sumHeures : BigDecimal.ZERO;
                        pointageTrouve = true;
                    }
                }
            }
        }

        // Le pointage ne représente pas à lui seul les jours rémunérés (congés,
        // fériés, repos, dispense). Le salaire mensuel suit les jours sous contrat.
        if (pointageTrouve) {
            prep.setSourcePointage("POINTAGE_REEL");
            prep.setJoursPointageReels(joursPointage);
            prep.setTotalHeuresPointage(heuresPointage);
        }

        // Détermination canonique des jours sous contrat et du prorata
        boolean isPeriodeComplete = (dFinPeriode != null)
                && !debutCouverture.isAfter(startPeriode)
                && !dDep.isBefore(dFinPeriode);

        BigDecimal joursTravaillesEstimes;
        if (isPeriodeComplete) {
            joursTravaillesEstimes = new BigDecimal("30");
        } else if (coverage != null) {
            int coveredDays = Math.min(coverage.days(), 30);
            joursTravaillesEstimes = new BigDecimal(Math.max(0, coveredDays));
        } else {
            int coveredDays = Math.min((int) java.time.temporal.ChronoUnit.DAYS.between(startPeriode, dDep) + 1, 30);
            joursTravaillesEstimes = new BigDecimal(Math.max(0, coveredDays));
        }
        prep.setJoursTravaillesEstimes(joursTravaillesEstimes);
        BigDecimal prorataEstime = isPeriodeComplete
                ? salaireBase
                : MoneyRoundingPolicy.amount(
                    salaireBase.multiply(joursTravaillesEstimes)
                        .divide(new BigDecimal("30"), 4, RoundingMode.HALF_UP));
        prep.setSalaireProrataEstime(prorataEstime);

        // 5. Congés restants & Taux valorisé dynamique (conge_journal_droit)
        VSoldeCongeEmploye solde = VSoldeCongeEmploye.rechercherParEmploye(idEmploye, c);
        BigDecimal soldeJours = solde != null && solde.getSoldeActuel() != null
                ? solde.getSoldeActuel()
                : BigDecimal.ZERO;
        prep.setSoldeCongesRestant(soldeJours);

        // Un solde positif porte déjà la valeur historisée de ses lots FIFO.
        // Le taux global ne doit pas bloquer ni revaloriser ces droits à la date du départ.
        BigDecimal tauxConge;
        BigDecimal iccpEstimee;
        if (soldeJours.signum() > 0) {
            var situationLots = lotsConges.situation(idEmploye, dDep, c);
            if (situationLots.soldeJours().compareTo(soldeJours) != 0) {
                throw new IllegalStateException("CONGE_STC_DATE_SOLDE_MISMATCH: rapprocher le solde à la date du départ.");
            }
            iccpEstimee = situationLots.soldeValorisation();
            tauxConge = iccpEstimee.divide(soldeJours, 8, RoundingMode.HALF_UP);
        } else if (soldeJours.signum() < 0) {
            tauxConge = getTauxJournalierApplicable(idEmploye, dDep, salaireBase, c);
            iccpEstimee = MoneyRoundingPolicy.amount(soldeJours.multiply(tauxConge));
        } else {
            tauxConge = BigDecimal.ZERO;
            iccpEstimee = BigDecimal.ZERO;
        }
        prep.setTauxJournalierConge(tauxConge);
        prep.setIccpEstimee(iccpEstimee);

        // Le préavis dépend de la catégorie réglementaire exacte, de la convention
        // et du mode de rupture. Aucune suggestion automatique approximative.
        prep.setMoisPreavisSuggere(BigDecimal.ZERO);
        prep.setIndemnitePreavisEstimee(BigDecimal.ZERO);
        prep.setIndemniteLicenciementEstimee(BigDecimal.ZERO);

        // 7. Engagements financiers (Prêts & Avances)
        BigDecimal totalPrets = PaiePret.calculerCapitalRestantTotal(idEmploye, c);
        prep.setCapitalRestantPrets(totalPrets);

        String sqlAvances = "SELECT COALESCE(SUM(a.solde_restant), 0) FROM paie_avance_acompte a " +
                "JOIN ref_type_mouvement_paie t ON t.id_type_mouvement = a.id_type_mouvement " +
                "WHERE a.id_employe = ? AND t.categorie IN ('ACOMPTE','AVANCE') " +
                "AND a.versement_confirme_at IS NOT NULL AND a.statut = 'APPROUVE'";
        try (PreparedStatement psAv = c.prepareStatement(sqlAvances)) {
            psAv.setInt(1, idEmploye);
            try (ResultSet rsAv = psAv.executeQuery()) {
                if (rsAv.next()) {
                    prep.setAvancesEnAttente(rsAv.getBigDecimal(1));
                }
            }
        }

        if (prep.getIdPeriodePaie() != null) {
            List<String> controles = new java.util.ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT 1 FROM pointage_cumul_periode WHERE id_employe = ? AND id_periode_paie = ? "
                    + "AND statut_snapshot IN ('GELE', 'CONSOMME') LIMIT 1")) {
                ps.setInt(1, idEmploye);
                ps.setInt(2, prep.getIdPeriodePaie());
                try (ResultSet rs = ps.executeQuery()) {
                    prep.setPointageGele(rs.next());
                }
            }
            if (!prep.isPointageGele()) {
                controles.add("Pointage final non gelé : vérifier les absences, heures et indemnités avant application.");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id_bulletin, statut, net_a_payer FROM bulletin_paie WHERE id_employe = ? AND id_periode_paie = ? "
                    + "AND type_bulletin = 'MENSUEL' AND statut <> 'ANNULE' LIMIT 1")) {
                ps.setInt(1, idEmploye);
                ps.setInt(2, prep.getIdPeriodePaie());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        prep.setIdBulletinMensuel(rs.getInt("id_bulletin"));
                        prep.setStatutBulletinMensuel(rs.getString("statut"));
                        prep.setNetBulletinMensuel(rs.getBigDecimal("net_a_payer"));
                        controles.add("Bulletin mensuel " + rs.getString("statut")
                                + " : renseigner le paiement réel pour inclure uniquement le solde encore dû au STC.");
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT 1 FROM paie_avance_acompte a "
                    + "LEFT JOIN ref_type_mouvement_paie t ON t.id_type_mouvement = a.id_type_mouvement "
                    + "WHERE a.id_employe = ? AND a.id_periode_paie = ? "
                    + "AND a.statut IN ('EN_ATTENTE', 'APPROUVE') "
                    + "AND COALESCE(t.categorie, '') NOT IN ('ACOMPTE', 'AVANCE') LIMIT 1")) {
                ps.setInt(1, idEmploye);
                ps.setInt(2, prep.getIdPeriodePaie());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) controles.add("Variables de paie présentes : les intégrer au calcul final avant le STC.");
                }
            }
            prep.setControlesApplication(controles);
        } else {
            prep.setControlesApplication(List.of("Aucune période de paie ne couvre le départ : créer ou choisir la période de régularisation."));
        }

        return prep;
    }

    // =========================================================================
    // 2. SIMULATION DU CALCUL DU STC
    // =========================================================================

    public StcCalculResultDTO simulerStc(StcCalculRequest req, Connection c) throws Exception {
        verifierMontantsNonNegatifs(req);
        StcPreparationDTO prep = preparerStc(req.getIdEmploye(), req.getDateDepart(), req.getMotifRupture(), c);
        return simulerStc(req, prep, c);
    }

    private StcCalculResultDTO simulerStc(StcCalculRequest req, StcPreparationDTO prep, Connection c) throws Exception {

        StcCalculResultDTO res = new StcCalculResultDTO();
        res.setIdEmploye(prep.getIdEmploye());
        res.setIdContrat(prep.getIdContrat());
        res.setSourcePointage(prep.getSourcePointage());
        res.setMatricule(prep.getMatricule());
        res.setNomComplet(prep.getNomComplet());
        res.setIntitulePoste(prep.getIntitulePoste());
        res.setDepartement(prep.getDepartement());
        res.setSite(prep.getSite());
        res.setDateEmbauche(prep.getDateEmbauche());
        res.setDateDepart(req.getDateDepart());
        res.setMotifRupture(req.getMotifRupture());
        res.setAncienneteAnnees(prep.getAncienneteAnnees());
        res.setAncienneteMois(prep.getAncienneteMois());
        res.setSalaireBase(prep.getSalaireBase());
        res.setTauxHoraire(prep.getTauxHoraire());

        boolean bulletinMensuelValide = "VALIDE".equals(prep.getStatutBulletinMensuel())
                || "CLOTURE".equals(prep.getStatutBulletinMensuel());
        if (prep.getIdBulletinMensuel() != null && !bulletinMensuelValide) {
            throw new IllegalStateException("Le bulletin mensuel final doit être validé avant son rapprochement avec le STC.");
        }
        BigDecimal montantDejaPaye = zeroIfNull(req.getMontantBulletinDejaPaye());
        if (montantDejaPaye.signum() < 0) throw new IllegalArgumentException("Le montant déjà payé ne peut pas être négatif.");
        BigDecimal netBulletinMensuel = bulletinMensuelValide ? zeroIfNull(prep.getNetBulletinMensuel()) : BigDecimal.ZERO;
        if (montantDejaPaye.compareTo(netBulletinMensuel) > 0) {
            throw new IllegalArgumentException("Le paiement déclaré dépasse le net du bulletin mensuel.");
        }
        if (montantDejaPaye.signum() == 0 && (req.getDatePaiementBulletin() != null
                || (req.getReferencePaiementBulletin() != null
                && !req.getReferencePaiementBulletin().isBlank()))) {
            throw new IllegalArgumentException("Ne renseignez une preuve de paiement que pour un montant déjà payé.");
        }
        if (montantDejaPaye.signum() > 0 && (req.getDatePaiementBulletin() == null
                || req.getReferencePaiementBulletin() == null
                || req.getReferencePaiementBulletin().isBlank())) {
            throw new IllegalArgumentException("La date et la référence du paiement réel du bulletin sont obligatoires.");
        }
        if (montantDejaPaye.signum() > 0 && req.getDatePaiementBulletin().toLocalDate()
                .isAfter(mg.galaxam.sirh.paie.domain.PayrollBusinessTime.today())) {
            throw new IllegalArgumentException("Un paiement futur ne peut pas être déclaré comme déjà effectué.");
        }
        res.setIdBulletinMensuel(bulletinMensuelValide ? prep.getIdBulletinMensuel() : null);
        res.setNetBulletinMensuel(netBulletinMensuel);
        res.setMontantBulletinDejaPaye(montantDejaPaye);
        res.setSoldeBulletinARegler(netBulletinMensuel.subtract(montantDejaPaye));

        // 1. Salaire prorata dernier mois
        BigDecimal joursTravailles = req.getJoursTravaillesDernierMois() != null
                ? req.getJoursTravaillesDernierMois()
                : prep.getJoursTravaillesEstimes();
        BigDecimal salaireProrata;
        if (joursTravailles.compareTo(new BigDecimal("30")) == 0) {
            salaireProrata = prep.getSalaireBase();
        } else {
            salaireProrata = MoneyRoundingPolicy.amount(
                    prep.getSalaireBase().multiply(joursTravailles)
                            .divide(new BigDecimal("30"), 4, RoundingMode.HALF_UP));
        }
        if (bulletinMensuelValide) salaireProrata = BigDecimal.ZERO;
        res.setJoursTravaillesDernierMois(joursTravailles);
        res.setSalaireProrata(salaireProrata);
        if (bulletinMensuelValide) {
            res.ajouterLigne("BULLETIN_MENSUEL_RAPPROCHE", "Bulletin mensuel final déjà calculé : net restant à régler",
                    "INFO", netBulletinMensuel, null, res.getSoldeBulletinARegler());
        } else {
            res.ajouterLigne("SALAIRE_BASE_PRORATA", "Salaire au prorata du dernier mois (" + joursTravailles + "j/30)",
                    "GAIN", prep.getSalaireBase(), null, salaireProrata);
        }

        // Phase 2.1 : Variables et absences du pointage pour départ en cours de mois (si pas de bulletin mensuel scellé)
        StcPointageVariables ptgVars = StcPointageVariables.zero();
        if (!bulletinMensuelValide && req.getDateDepart() != null) {
            LocalDate dDep = req.getDateDepart().toLocalDate();
            LocalDate dDebutPlage;
            LocalDate dFinPlage = dDep;

            if (prep.getIdPeriodePaie() != null) {
                String sqlPerDates = "SELECT date_debut FROM periode_paie WHERE id_periode_paie = ?";
                LocalDate startPer = null;
                try (PreparedStatement psPD = c.prepareStatement(sqlPerDates)) {
                    psPD.setInt(1, prep.getIdPeriodePaie());
                    try (ResultSet rsPD = psPD.executeQuery()) {
                        if (rsPD.next() && rsPD.getDate("date_debut") != null) {
                            startPer = rsPD.getDate("date_debut").toLocalDate();
                        }
                    }
                }
                if (startPer != null) {
                    var empResolver = new mg.galaxam.sirh.contrat.application.EmploymentContextResolver();
                    mg.galaxam.sirh.contrat.application.EmploymentContextResolver.Coverage coverage = null;
                    try {
                        coverage = empResolver.resolveCoverage(req.getIdEmploye(), startPer, dDep, c);
                    } catch (Exception ignored) {}
                    dDebutPlage = coverage != null ? coverage.firstDay() : startPer;
                } else {
                    dDebutPlage = dDep.withDayOfMonth(1);
                }
            } else {
                dDebutPlage = dDep.withDayOfMonth(1);
            }

            ptgVars = calculerPointageVariables(req.getIdEmploye(), dDebutPlage, dFinPlage, prep.getTauxHoraire(), c);
            if (ptgVars.montantRetenueAbsence().signum() > 0) {
                res.setMontantRetenueAbsence(ptgVars.montantRetenueAbsence());
                res.ajouterLigne("RETENUE_ABSENCE", "Retenue pour Absences Décomptées (" + ptgVars.heuresAbsence() + " h)",
                        "RETENUE", prep.getTauxHoraire(), null, ptgVars.montantRetenueAbsence());
            }
            if (ptgVars.montantHs30().signum() > 0) {
                res.setMontantHeuresSup(res.getMontantHeuresSup().add(ptgVars.montantHs30()));
                res.ajouterLigne("HEURES_SUP_30", "Heures Supplémentaires (+30%) (" + ptgVars.heuresHs30() + " h)",
                        "GAIN", prep.getTauxHoraire(), new BigDecimal("130"), ptgVars.montantHs30());
            }
            if (ptgVars.montantHs50().signum() > 0) {
                res.setMontantHeuresSup(res.getMontantHeuresSup().add(ptgVars.montantHs50()));
                res.ajouterLigne("HEURES_SUP_50", "Heures Supplémentaires (+50%) (" + ptgVars.heuresHs50() + " h)",
                        "GAIN", prep.getTauxHoraire(), new BigDecimal("150"), ptgVars.montantHs50());
            }
            if (ptgVars.montantNuit().signum() > 0) {
                res.setMontantMajorationNuit(ptgVars.montantNuit());
                res.ajouterLigne("MAJORATION_NUIT", "Majoration Travail de Nuit (" + ptgVars.heuresNuit() + " h)",
                        "GAIN", prep.getTauxHoraire(), new BigDecimal("30"), ptgVars.montantNuit());
            }
            if (ptgVars.montantDimanche().signum() > 0) {
                res.setMontantMajorationDimanche(ptgVars.montantDimanche());
                res.ajouterLigne("HEURES_DIMANCHE", "Majoration Travail Dimanche (" + ptgVars.heuresDimanche() + " h)",
                        "GAIN", prep.getTauxHoraire(), new BigDecimal("140"), ptgVars.montantDimanche());
            }
            if (ptgVars.montantFerie().signum() > 0) {
                res.setMontantMajorationFerie(ptgVars.montantFerie());
                res.ajouterLigne("MAJORATION_FERIE", "Majoration Travail Jour Férié (" + ptgVars.heuresFerie() + " h)",
                        "GAIN", prep.getTauxHoraire(), new BigDecimal("200"), ptgVars.montantFerie());
            }
            if (ptgVars.montantRepas().signum() > 0) {
                res.setMontantIndemnitesPointage(res.getMontantIndemnitesPointage().add(ptgVars.montantRepas()));
                res.ajouterLigne("INDEMNITE_REPAS", "Indemnité de Panier / Repas",
                        "GAIN", null, null, ptgVars.montantRepas());
            }
            if (ptgVars.montantDeplacement().signum() > 0) {
                res.setMontantIndemnitesPointage(res.getMontantIndemnitesPointage().add(ptgVars.montantDeplacement()));
                res.ajouterLigne("INDEMNITE_DEPLACEMENT", "Indemnité de Déplacement",
                        "GAIN", null, null, ptgVars.montantDeplacement());
            }
        }

        // 2. ICCP (Indemnité Compensatrice de Congés Payés) ou Retenue pour congés par anticipation
        if (req.getSoldeConges() != null && req.getSoldeConges().compareTo(prep.getSoldeCongesRestant()) != 0) {
            throw new IllegalArgumentException("Le solde de congés doit être corrigé dans le journal des congés avant le STC.");
        }
        BigDecimal soldeConges = prep.getSoldeCongesRestant();
        BigDecimal tauxConge = prep.getTauxJournalierConge();
        BigDecimal iccp = soldeConges.signum() != 0
                ? lotsConges.situation(req.getIdEmploye(), req.getDateDepart().toLocalDate(), c).soldeValorisation()
                : BigDecimal.ZERO;
        res.setSoldeCongesRestant(soldeConges);
        res.setTauxJournalierConge(tauxConge);
        res.setIndemniteCongesPayes(iccp);

        BigDecimal gainConge = BigDecimal.ZERO;
        if (iccp.compareTo(BigDecimal.ZERO) > 0) {
            gainConge = iccp;
            res.ajouterLigne("INDEMNITE_COMPENSATRICE_CONGE", "Indemnité Compensatrice de Congés Payés (" + soldeConges
                    + "j, valeurs historiques des lots)",
                    "GAIN", tauxConge, null, iccp);
        } else if (iccp.compareTo(BigDecimal.ZERO) < 0) {
            // Un solde négatif appelle un examen RH ; il n'autorise pas une retenue automatique.
            res.ajouterLigne("CONGE_ANTICIPE_A_EXAMINER", "Congés pris par anticipation à examiner", "INFO",
                    tauxConge, null, iccp.abs());
        }

        // 3. Indemnités de rupture (Préavis & Licenciement)
        BigDecimal moisPreavis = req.getMoisPreavis() != null ? req.getMoisPreavis() : prep.getMoisPreavisSuggere();
        BigDecimal indemnitePreavis = req.getIndemnitePreavis() != null
                ? req.getIndemnitePreavis() : BigDecimal.ZERO;
        res.setMoisPreavis(moisPreavis);
        res.setIndemnitePreavis(indemnitePreavis);
        if (indemnitePreavis.compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("INDEMNITE_PREAVIS", "Indemnité Compensatrice de Préavis (" + moisPreavis + " mois)",
                    "GAIN", prep.getSalaireBase(), null, indemnitePreavis);
        }

        BigDecimal indemniteLicenciement = req.getIndemniteLicenciement() != null
                ? req.getIndemniteLicenciement()
                : BigDecimal.ZERO;
        res.setIndemniteLicenciement(indemniteLicenciement);
        if (indemniteLicenciement.compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("INDEMNITE_LICENCIEMENT", "Indemnité Légale de Rupture / Licenciement",
                    "GAIN", null, null, indemniteLicenciement);
        }

        BigDecimal autresIndemnites = req.getAutresIndemnites() != null ? req.getAutresIndemnites() : BigDecimal.ZERO;
        res.setAutresIndemnites(autresIndemnites);
        res.setMotifAutresIndemnites(req.getMotifAutresIndemnites());
        if (autresIndemnites.compareTo(BigDecimal.ZERO) > 0) {
            String libAutre = req.getMotifAutresIndemnites() != null && !req.getMotifAutresIndemnites().isBlank()
                    ? req.getMotifAutresIndemnites()
                    : "Autres indemnités exceptionnelles de départ";
            res.ajouterLigne("AUTRES_INDEMNITES_DEPART", libAutre, "GAIN", null, null, autresIndemnites);
        }

        // Total Brut STC (Somme des gains bruts)
        BigDecimal totalGainsVariables = ptgVars.totalGainsVariables();
        BigDecimal salaireNetPresence = salaireProrata.subtract(ptgVars.montantRetenueAbsence()).max(BigDecimal.ZERO);
        BigDecimal totalBrut = salaireNetPresence.add(totalGainsVariables).add(gainConge)
                .add(indemnitePreavis).add(indemniteLicenciement).add(autresIndemnites);
        res.setTotalBrutStc(totalBrut);

        // 4. Cotisations sociales et assiette fiscale.
        BigDecimal assietteBruteSoumise = salaireNetPresence.add(totalGainsVariables).add(gainConge).add(autresIndemnites).max(BigDecimal.ZERO);
        BigDecimal assietteSociale = assietteBruteSoumise.add(indemnitePreavis);
        BigDecimal assietteFiscaleBrute = assietteSociale.add(indemniteLicenciement);
        BulletinFiscalSnapshot bulletinFiscal = bulletinMensuelValide
                ? chargerBulletinFiscal(prep.getIdBulletinMensuel(), c) : null;

        // Chargement dynamique des règles légales et plafonds
        PaieConfigLegale configLegale = chargerConfigLegale(req.getDateDepart(), c);
        // P1-03 : Supprimer le fallback 262680.00. Si aucune config légale datée n'est trouvée,
        // BLOQUER le calcul plutôt que d'utiliser une valeur inventée. LEGAL_REVIEW_REQUIRED.
        if (configLegale == null || configLegale.getSmeNational() == null) {
            throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                    mg.galaxam.sirh.common.exception.SirhErrorCode.LEGAL_CONFIG_MISSING,
                    "Aucun barème réglementaire (SME/SMIG) n'est configuré pour la date du "
                    + req.getDateDepart() + ". Veuillez renseigner l'exercice réglementaire correspondant dans les Paramètres de Paie (LEGAL_REVIEW_REQUIRED).");
        }
        BigDecimal plafondCotisation = configLegale.getPlafondCotisationMensuel();
        if (plafondCotisation == null || plafondCotisation.signum() <= 0)
            throw new IllegalStateException("SOCIAL_CONFIGURATION_MISSING: plafond de cotisation mensuel au départ");

        // Même référentiel social que le bulletin, résolu ici à la date de départ.
        var socialRates = new SocialContributionContextResolver()
                .companyRates(req.getDateDepart().toLocalDate(), c);
        BigDecimal tauxCnapsSal = socialRates.cnapsEmployee();
        BigDecimal tauxCnapsPat = socialRates.cnapsEmployer();
        BigDecimal tauxFmfp = socialRates.fmfpEmployer();
        BigDecimal ancienneBaseCnaps = bulletinFiscal == null ? BigDecimal.ZERO : bulletinFiscal.baseCnaps();
        BigDecimal ancienneBaseSante = bulletinFiscal == null ? BigDecimal.ZERO : bulletinFiscal.baseSante();
        BigDecimal ancienneBaseFmfp = bulletinFiscal == null ? BigDecimal.ZERO : bulletinFiscal.baseFmfp();
        SocialContributionCalculator.Result socialResult = new SocialContributionCalculator().calculate(
                ancienneBaseCnaps.add(assietteSociale), ancienneBaseSante.add(assietteSociale),
                ancienneBaseFmfp.add(assietteSociale), new SocialContributionCalculator.Rules(tauxCnapsSal, tauxCnapsPat,
                        BigDecimal.ZERO, BigDecimal.ZERO, tauxFmfp, plafondCotisation, true));
        var resCnaps = socialResult.cnaps();
        BigDecimal baseCnaps = resCnaps.assiette().subtract(ancienneBaseCnaps).max(BigDecimal.ZERO);
        BigDecimal cnapsSal = resCnaps.partSalariale().subtract(bulletinFiscal == null ? BigDecimal.ZERO : bulletinFiscal.cnapsSal()).max(BigDecimal.ZERO);
        res.setCotisationCnaps(cnapsSal);
        if (cnapsSal.compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("COTISATION_CNAPS_SAL", "Cotisation CNaPS Salariale (" + tauxCnapsSal.movePointRight(2).stripTrailingZeros().toPlainString() + "%)", "RETENUE", baseCnaps, tauxCnapsSal.movePointRight(2), cnapsSal);
        }

        // Santé : Organisme et taux récupérés dynamiquement selon le site du collaborateur
        String codeSante = "OSTIE";
        BigDecimal tauxSanteSal = null;
        BigDecimal tauxSantePat = null;
        HealthContributionRate healthRate = resolveHealthContributionRate(
                prep.getCodeSite(), prep.getSite(), req.getDateDepart().toLocalDate(), c);
        if (healthRate != null) {
            codeSante = healthRate.code();
            tauxSanteSal = healthRate.employeeRatePercent().movePointLeft(2);
            tauxSantePat = healthRate.employerRatePercent().movePointLeft(2);
        }

        if (tauxSanteSal == null || tauxSantePat == null) {
            throw new IllegalStateException("SOCIAL_CONFIGURATION_MISSING: santé au " + req.getDateDepart());
        }

        var resSante = new SocialContributionCalculator().calculate(ancienneBaseCnaps.add(assietteSociale),
                ancienneBaseSante.add(assietteSociale), ancienneBaseFmfp.add(assietteSociale),
                new SocialContributionCalculator.Rules(tauxCnapsSal, tauxCnapsPat,
                        tauxSanteSal, tauxSantePat, tauxFmfp, plafondCotisation, true)).health();
        BigDecimal baseSante = resSante.assiette().subtract(ancienneBaseSante).max(BigDecimal.ZERO);
        BigDecimal santeSal = resSante.partSalariale().subtract(bulletinFiscal == null ? BigDecimal.ZERO : bulletinFiscal.santeSal()).max(BigDecimal.ZERO);
        res.setCotisationSante(santeSal);
        if (santeSal.compareTo(BigDecimal.ZERO) > 0) {
            String libelleSante = "Cotisation " + codeSante + " Salariale (" + tauxSanteSal.movePointRight(2) + "%)";
            res.ajouterLigne("COTISATION_SANTE_SAL", libelleSante, "RETENUE", baseSante, tauxSanteSal.movePointRight(2), santeSal);
        }

        BigDecimal totalCotis = cnapsSal.add(santeSal);
        res.setTotalCotisationsSociales(totalCotis);

        // Cotisations patronales (CNaPS 13%, Santé patronale, FMFP 1%)
        BigDecimal cnapsPat = resCnaps.partPatronale().subtract(bulletinFiscal == null ? BigDecimal.ZERO : bulletinFiscal.cnapsPat()).max(BigDecimal.ZERO);
        BigDecimal santePat = resSante.partPatronale().subtract(bulletinFiscal == null ? BigDecimal.ZERO : bulletinFiscal.santePat()).max(BigDecimal.ZERO);
        BigDecimal fmfpPat = MoneyRoundingPolicy.contribution(socialResult.fmfp()).subtract(bulletinFiscal == null ? BigDecimal.ZERO : bulletinFiscal.fmfpPat()).max(BigDecimal.ZERO);
        BigDecimal totalCotisPat = cnapsPat.add(santePat).add(fmfpPat);
        res.setTotalCotisationsPatronales(totalCotisPat);

        if (cnapsPat.compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("COTISATION_CNAPS_PAT", "Cotisation CNaPS Patronale (" + tauxCnapsPat.movePointRight(2).stripTrailingZeros().toPlainString() + "%)", "PATRONAL", baseCnaps, tauxCnapsPat.movePointRight(2), cnapsPat);
        }
        if (santePat.compareTo(BigDecimal.ZERO) > 0) {
            String libelleSantePat = "Cotisation " + codeSante + " Patronale (" + tauxSantePat.movePointRight(2) + "%)";
            res.ajouterLigne("COTISATION_SANTE_PAT", libelleSantePat, "PATRONAL", baseSante, tauxSantePat.movePointRight(2), santePat);
        }
        if (fmfpPat.compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("COTISATION_FMFP_PAT", "Cotisation FMFP Patronale (" + tauxFmfp.movePointRight(2).stripTrailingZeros().toPlainString() + "%)", "PATRONAL", ancienneBaseFmfp.add(assietteSociale).min(plafondCotisation).subtract(ancienneBaseFmfp).max(BigDecimal.ZERO), tauxFmfp.movePointRight(2), fmfpPat);
        }

        // Assiette IRSA
        BigDecimal netImposable = assietteFiscaleBrute.subtract(totalCotis).max(BigDecimal.ZERO);
        res.setNetImposable(netImposable);

        BigDecimal irsaNet = BigDecimal.ZERO;
        // Calcul IRSA selon barème progressif dynamique unifié
        {
        LocalDate dateRefIrsa = req.getDateDepart().toLocalDate();
        List<Tranche> tranches = chargerTranchesIrsa(configLegale, dateRefIrsa, c);
        int nbEnfants = prep.getNbEnfantsACharge();
        if (configLegale == null || configLegale.getDeductionChargeFamille() == null
                || configLegale.getMinimumPerceptionIrsa() == null) {
            throw new IllegalStateException("IRSA_CONFIGURATION_MISSING");
        }
        BigDecimal deducParEnfant = configLegale.getDeductionChargeFamille();
        BigDecimal minPercep = configLegale.getMinimumPerceptionIrsa();

        BigDecimal assietteMensuelle = bulletinFiscal == null ? BigDecimal.ZERO : bulletinFiscal.revenuNetImposable();
        var irsaRes = new PayrollTaxCalculations().calculateIrsa(
                assietteMensuelle.add(netImposable), tranches, nbEnfants, deducParEnfant, minPercep
        );
        irsaNet = MoneyRoundingPolicy.tax(irsaRes.irsaNet().subtract(
                bulletinFiscal == null ? BigDecimal.ZERO : bulletinFiscal.irsaNet()).max(BigDecimal.ZERO).min(netImposable));
        res.setIrsaNet(irsaNet);
        if (irsaNet.compareTo(BigDecimal.ZERO) > 0) {
            String libelleIrsa = "Retenue à la Source IRSA (Barème Progressif " +
                    (configLegale != null && configLegale.getCodeVersion() != null ? configLegale.getCodeVersion() : "LF") + ")";
            res.ajouterLigne("IMPOT_IRSA_NET", libelleIrsa, "RETENUE", irsaRes.baseIrsaArrondie(), null, irsaNet);
        }
        if (irsaRes.reductionEnfants().compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("DEDUCTION_CHARGE_FAMILLE", "Déduction IRSA Charges Familiales (" + nbEnfants + " enfant(s))", "INFO", null, null, irsaRes.reductionEnfants());
        }
        }

        // Net avant déductions d'engagements personnels
        BigDecimal netAvantEngagements = totalBrut.subtract(totalCotis).subtract(irsaNet).max(BigDecimal.ZERO);

        // 5. Engagements financiers (Acomptes versés & Prêts d'entreprise)
        BigDecimal soldeAvances = calculerAcomptesVersesNonImputes(req.getIdEmploye(), c);
        BigDecimal avancesADeduire = MoneyRoundingPolicy.amount(soldeAvances.min(netAvantEngagements));
        res.setSoldeAvancesDeduit(avancesADeduire);
        if (avancesADeduire.compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("SOLDE_AVANCES_STC", "Imputation des acomptes déjà versés", "RETENUE", soldeAvances, null, avancesADeduire);
        }

        BigDecimal netApresAvances = netAvantEngagements.subtract(avancesADeduire).max(BigDecimal.ZERO);

        // Apurement optionnel des prêts d'entreprise au STC (accord/solde libératoire)
        BigDecimal pretsADeduire = BigDecimal.ZERO;
        if (req.isApurerPretDansStc() || (req.getSoldePretsADeduire() != null && req.getSoldePretsADeduire().signum() > 0)) {
            throw new IllegalArgumentException("Une retenue de prêt exige une cession ou un titre vérifié dans le dossier. L'apurement automatique du STC est indisponible.");
        }
        res.setSoldePretsDeduit(pretsADeduire);

        BigDecimal netApresPrets = netApresAvances.subtract(pretsADeduire).max(BigDecimal.ZERO);

        // Les retenues diverses ne sont plus acceptées par ce moteur.
        BigDecimal autresADeduire = BigDecimal.ZERO;
        res.setAutresDeductions(autresADeduire);
        res.setMotifAutresDeductions(null);

        // 6. Net Final à Payer (arrondi bancaire officiel Ariary à la dizaine la plus proche)
        BigDecimal netFinalNonArrondi = netApresPrets.subtract(autresADeduire)
                .add(res.getSoldeBulletinARegler()).max(BigDecimal.ZERO);
        BigDecimal netFinal = BulletinPaie.arrondirDizaineAriary(netFinalNonArrondi);
        BigDecimal ecartArrondi = netFinal.subtract(netFinalNonArrondi);
        if (ecartArrondi.compareTo(BigDecimal.ZERO) != 0) {
            if (ecartArrondi.compareTo(BigDecimal.ZERO) > 0) {
                res.ajouterLigne("AJUSTEMENT_ARRONDI", "Ajustement d'arrondi monétaire", "GAIN", null, null, ecartArrondi);
            } else {
                res.ajouterLigne("AJUSTEMENT_ARRONDI", "Ajustement d'arrondi monétaire", "RETENUE", null, null, ecartArrondi.abs());
            }
        }
        res.setNetAPayerStc(netFinal);

        res.setStatut(req.isSimulation() ? "SIMULATION" : "VALIDE");
        res.setModeReglement(req.getModeReglement() != null ? req.getModeReglement() : "VIREMENT");
        res.setCommentaire(req.getCommentaire());

        return res;
    }

    // =========================================================================
    // 3. VALIDATION & ÉMISSION FORMELLE DU SOLDE DE TOUT COMPTE
    // =========================================================================

    public StcCalculResultDTO validerStc(StcCalculRequest req, Integer userId, Connection c) throws Exception {
        StcPreparationDTO preparation = preparerStc(req.getIdEmploye(), req.getDateDepart(), req.getMotifRupture(), c);
        verifierValidationJuridique(req, preparation, c);
        if (req.getDateDepart() != null
                && req.getDateDepart().toLocalDate().isAfter(mg.galaxam.sirh.paie.domain.PayrollBusinessTime.today())) {
            throw new IllegalArgumentException(
                    "La validation officielle du STC est possible à partir de la date de départ effective. " +
                    "La simulation reste disponible avant cette date.");
        }
        req.setSimulation(false);
        StcCalculResultDTO simulation = simulerStc(req, preparation, c);

        // 1. Génération du numéro de reçu officiel
        LocalDate dDepRecu = req.getDateDepart() != null ? req.getDateDepart().toLocalDate() : null;
        String numRecu = PaieStc.genererNumeroRecu(dDepRecu, c);
        simulation.setNumeroRecu(numRecu);
        simulation.setStatut("VALIDE");

        // 2. Enregistrement en base de données dans paie_stc
        PaieStc entity = new PaieStc();
        entity.setIdEmploye(req.getIdEmploye());

        // Résolution dynamique de la période si non spécifiée
        Integer idPeriode = req.getIdPeriodePaie();
        if (idPeriode == null && req.getDateDepart() != null) {
            String sqlPer = "SELECT id_periode_paie FROM periode_paie WHERE ? BETWEEN date_debut AND date_fin";
            try (PreparedStatement psPer = c.prepareStatement(sqlPer)) {
                psPer.setDate(1, req.getDateDepart());
                try (ResultSet rsPer = psPer.executeQuery()) {
                    if (rsPer.next()) {
                        idPeriode = rsPer.getInt(1);
                        if (rsPer.next()) throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: PERIODE_PAIE au " + req.getDateDepart());
                    }
                }
            }
        }
        if (idPeriode == null) throw new IllegalStateException("MISSING_CONFIGURATION: PERIODE_PAIE au " + req.getDateDepart());
        if ("POINTAGE_REEL".equals(preparation.getSourcePointage()) && !preparation.isPointageGele()
                && (req.getJustificationSalaireDernierMois() == null || req.getJustificationSalaireDernierMois().isBlank())) {
            throw new IllegalStateException(
                    "Le pointage final existe mais n'est pas gelé. Clôturez et contrôlez le pointage "
                    + "avant de valider le STC, ou fournissez une justification RH explicite.");
        }
        if (simulation.getIdBulletinMensuel() == null
                && !"POINTAGE_REEL".equals(preparation.getSourcePointage())
                && (req.getJustificationSalaireDernierMois() == null
                || req.getJustificationSalaireDernierMois().isBlank())) {
            throw new IllegalStateException(
                    "Aucun pointage n'est disponible pour la période finale : justifiez les jours rémunérés "
                    + "et confirmez les absences et variables avant de valider le STC.");
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id_bulletin, id_snapshot_pointage FROM bulletin_paie WHERE id_employe = ? AND id_periode_paie = ? "
                + "AND COALESCE(type_bulletin, 'MENSUEL') <> 'STC' "
                + "AND statut IN ('VALIDE', 'CLOTURE') LIMIT 1")) {
            ps.setInt(1, req.getIdEmploye());
            ps.setInt(2, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    if (!Integer.valueOf(rs.getInt("id_bulletin")).equals(simulation.getIdBulletinMensuel())) {
                        throw new IllegalStateException("Le bulletin mensuel a changé depuis la simulation : relancez le rapprochement.");
                    }
                    if (preparation.isPointageGele()) {
                        Integer snapshotBulletin = (Integer) rs.getObject("id_snapshot_pointage");
                        try (PreparedStatement psDernier = c.prepareStatement(
                                "SELECT id_cumul FROM pointage_cumul_periode WHERE id_employe = ? "
                                + "AND id_periode_paie = ? AND statut_snapshot IN ('GELE', 'CONSOMME') "
                                + "ORDER BY version_snapshot DESC LIMIT 1")) {
                            psDernier.setInt(1, req.getIdEmploye());
                            psDernier.setInt(2, idPeriode);
                            try (ResultSet dernier = psDernier.executeQuery()) {
                                if (!dernier.next() || !Integer.valueOf(dernier.getInt(1)).equals(snapshotBulletin)) {
                                    throw new IllegalStateException("Le bulletin mensuel ne correspond pas au dernier pointage gelé : recalculez la paie finale.");
                                }
                            }
                        }
                    }
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM pointage_jour pj JOIN ref_statut_pointage_jour s "
                + "ON s.id_statut_jour = pj.id_statut_jour "
                + "WHERE pj.id_employe = ? AND pj.id_periode_paie = ? AND pj.date_pointage <= ? "
                + "AND (s.decompte_absence = TRUE OR s.code = 'ANOMALIE_SAISIE') LIMIT 1")) {
            ps.setInt(1, req.getIdEmploye());
            ps.setInt(2, idPeriode);
            ps.setDate(3, req.getDateDepart());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && simulation.getIdBulletinMensuel() == null
                        && (req.getJoursTravaillesDernierMois() == null
                        || req.getJustificationSalaireDernierMois() == null
                        || req.getJustificationSalaireDernierMois().isBlank())) {
                    throw new IllegalStateException(
                            "Le pointage contient une absence ou une anomalie : saisissez les jours rémunérés corrigés et leur justification.");
                }
            }
        }
        // Contrôle des variables de pointage non intégrées ou non arbitrées
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM pointage_jour WHERE id_employe = ? AND id_periode_paie = ? "
                + "AND date_pointage <= ? AND (COALESCE(heures_nuit, 0) > 0 OR COALESCE(montant_total_indemnites, 0) > 0) LIMIT 1")) {
            ps.setInt(1, req.getIdEmploye());
            ps.setInt(2, idPeriode);
            ps.setDate(3, req.getDateDepart());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && simulation.getIdBulletinMensuel() == null
                        && (req.getJustificationSalaireDernierMois() == null || req.getJustificationSalaireDernierMois().isBlank())) {
                    throw new IllegalStateException(
                            "Le pointage contient des heures de nuit ou des indemnités variables non intégrées. La validation est suspendue pour éviter de les omettre.");
                }
            }
        }
        // Contrôle des anomalies bloquantes non arbitrées sur la période
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM pointage_jour pj JOIN ref_statut_pointage_jour s "
                + "ON s.id_statut_jour = pj.id_statut_jour "
                + "WHERE pj.id_employe = ? AND pj.id_periode_paie = ? AND pj.date_pointage <= ? "
                + "AND (s.code = 'ANOMALIE_SAISIE' OR pj.motif_ajustement = 'ANOMALIE_NON_RESOLUE') LIMIT 1")) {
            ps.setInt(1, req.getIdEmploye());
            ps.setInt(2, idPeriode);
            ps.setDate(3, req.getDateDepart());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && simulation.getIdBulletinMensuel() == null
                        && (req.getJustificationSalaireDernierMois() == null || req.getJustificationSalaireDernierMois().isBlank())) {
                    throw new IllegalStateException(
                            "Le pointage contient des anomalies non arbitrées sur la période. Le scellement est bloqué tant que ces éléments ne sont pas justifiés.");
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM paie_avance_acompte a "
                + "WHERE a.id_employe = ? AND a.id_periode_paie = ? "
                + "AND a.statut = 'EN_ATTENTE' LIMIT 1")) {
            ps.setInt(1, req.getIdEmploye());
            ps.setInt(2, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalStateException(
                            "Des éléments financiers (acomptes, avances ou variables de paie) sont en attente d'arbitrage sur cette période. "
                            + "Arbitrez-les (validation ou rejet) avant de sceller le STC.");
                }
            }
        }
        if (simulation.getIdBulletinMensuel() == null && req.getJoursTravaillesDernierMois() != null
                && req.getJoursTravaillesDernierMois().compareTo(preparation.getJoursTravaillesEstimes()) != 0
                && (req.getJustificationSalaireDernierMois() == null
                || req.getJustificationSalaireDernierMois().isBlank())) {
            throw new IllegalArgumentException("Une correction des jours rémunérés exige une justification RH.");
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM paie_stc WHERE id_employe = ? AND id_periode_paie = ? AND statut <> 'ANNULE' LIMIT 1")) {
            ps.setInt(1, req.getIdEmploye());
            ps.setInt(2, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) throw new IllegalStateException("Un STC existe déjà pour cet employé et cette période.");
            }
        }
        entity.setIdPeriodePaie(idPeriode);
        entity.setNumeroRecu(numRecu);
        entity.setDateDepart(req.getDateDepart());
        entity.setMotifRupture(req.getMotifRupture());

        entity.setSalaireBase(simulation.getSalaireBase());
        entity.setTauxHoraire(simulation.getTauxHoraire());
        entity.setAncienneteMois(simulation.getAncienneteMois());

        entity.setJoursTravaillesDernierMois(simulation.getJoursTravaillesDernierMois());
        entity.setSalaireProrata(simulation.getSalaireProrata());

        entity.setSoldeCongesRestant(simulation.getSoldeCongesRestant());
        entity.setTauxJournalierConge(simulation.getTauxJournalierConge());
        entity.setIndemniteCongesPayes(simulation.getIndemniteCongesPayes());

        entity.setMoisPreavis(simulation.getMoisPreavis());
        entity.setIndemnitePreavis(simulation.getIndemnitePreavis());
        entity.setIndemniteLicenciement(simulation.getIndemniteLicenciement());
        entity.setAutresIndemnites(simulation.getAutresIndemnites());
        entity.setMotifAutresIndemnites(simulation.getMotifAutresIndemnites());

        entity.setTotalBrutStc(simulation.getTotalBrutStc());
        entity.setTotalCotisationsSociales(simulation.getTotalCotisationsSociales());
        entity.setTotalIrsa(simulation.getIrsaNet());
        entity.setNetImposable(simulation.getNetImposable());

        entity.setSoldePretsDeduit(simulation.getSoldePretsDeduit());
        entity.setSoldeAvancesDeduit(simulation.getSoldeAvancesDeduit());
        entity.setAutresDeductions(simulation.getAutresDeductions());
        entity.setMotifAutresDeductions(simulation.getMotifAutresDeductions());

        entity.setNetAPayerStc(simulation.getNetAPayerStc());
        entity.setStatut("VALIDE");
        entity.setModeReglement(simulation.getModeReglement());
        entity.setCommentaire((simulation.getCommentaire() == null ? "" : simulation.getCommentaire() + "\n")
                + "Justification salaire final : " + (req.getJustificationSalaireDernierMois() == null ? "Aucune correction" : req.getJustificationSalaireDernierMois().trim()) + "; "
                + "Justification indemnités : " + (req.getJustificationIndemnites() == null ? "Sans indemnité manuelle" : req.getJustificationIndemnites().trim())
                + "; rémunération de référence licenciement : " + req.getRemunerationReferenceLicenciement());

        // Résolution dynamique ref_mode_paiement
        String modeReg = simulation.getModeReglement() != null ? simulation.getModeReglement() : "VIREMENT";
        try (PreparedStatement psMp = c.prepareStatement("SELECT id_mode_paiement FROM ref_mode_paiement WHERE UPPER(code) = UPPER(?) LIMIT 1")) {
            psMp.setString(1, modeReg);
            try (ResultSet rsMp = psMp.executeQuery()) {
                if (rsMp.next()) {
                    entity.setIdModeReglement(rsMp.getInt(1));
                }
            }
        } catch (Exception ignored) {}

        // Résolution dynamique ref_motif_sortie
        if (entity.getMotifRupture() != null && !entity.getMotifRupture().isBlank()) {
            try (PreparedStatement psMot = c.prepareStatement("SELECT id_motif_sortie FROM ref_motif_sortie WHERE UPPER(code) = UPPER(?) AND actif = TRUE")) {
                psMot.setString(1, entity.getMotifRupture());
                try (ResultSet rsMot = psMot.executeQuery()) {
                    if (!rsMot.next()) throw new IllegalArgumentException("Motif de sortie absent ou inactif dans le référentiel RH.");
                    entity.setIdMotifSortie(rsMot.getInt(1));
                }
            }
        }

        entity.setCreePar(userId);
        entity.setValidePar(userId);
        entity.setDateValidation(new Timestamp(System.currentTimeMillis()));

        entity.save(c);
        simulation.setIdStc(entity.getIdStc());
        try (PreparedStatement psSnapshot = c.prepareStatement(
                "UPDATE paie_stc SET id_contrat = ?, source_pointage = ?, id_bulletin_mensuel = ?, "
                + "net_bulletin_mensuel = ?, montant_bulletin_deja_paye = ?, solde_bulletin_a_regler = ?, "
                + "date_paiement_bulletin = ?, reference_paiement_bulletin = ? WHERE id_stc = ?")) {
            psSnapshot.setInt(1, simulation.getIdContrat());
            psSnapshot.setString(2, simulation.getSourcePointage());
            if (simulation.getIdBulletinMensuel() == null) psSnapshot.setNull(3, java.sql.Types.INTEGER);
            else psSnapshot.setInt(3, simulation.getIdBulletinMensuel());
            psSnapshot.setBigDecimal(4, simulation.getNetBulletinMensuel());
            psSnapshot.setBigDecimal(5, simulation.getMontantBulletinDejaPaye());
            psSnapshot.setBigDecimal(6, simulation.getSoldeBulletinARegler());
            psSnapshot.setDate(7, req.getDatePaiementBulletin());
            psSnapshot.setString(8, req.getReferencePaiementBulletin());
            psSnapshot.setInt(9, entity.getIdStc());
            psSnapshot.executeUpdate();
        }

        // 2.1 Émission synchrone du Bulletin de Paie de sortie (STC) pour inclusion déclarative (DGI SAFI & CNaPS DNTS)
        BulletinPaie bpStc = new BulletinPaie();
        bpStc.setIdPeriodePaie(entity.getIdPeriodePaie());
        bpStc.setIdEmploye(entity.getIdEmploye());
        bpStc.setTypeBulletin("STC");

        // Résolution dynamique id_type_bulletin et id_mode_paiement
        try (PreparedStatement psTb = c.prepareStatement("SELECT id_type_bulletin FROM ref_type_bulletin WHERE code = 'STC' LIMIT 1");
             ResultSet rsTb = psTb.executeQuery()) {
            if (rsTb.next()) {
                bpStc.setIdTypeBulletin(rsTb.getInt(1));
            }
        } catch (Exception ignored) {}
        if (entity.getIdModeReglement() != null) {
            bpStc.setIdModePaiement(entity.getIdModeReglement());
        }

        bpStc.setReferenceBulletin("STC-" + entity.getNumeroRecu());
        bpStc.setMatricule(simulation.getMatricule() != null ? simulation.getMatricule() : "STC-" + req.getIdEmploye());
        bpStc.setNomPrenom(simulation.getNomComplet() != null ? simulation.getNomComplet() : "Salarié Sortant");
        StcPreparationDTO identiteValidee = preparation;
        bpStc.setCinNumero(identiteValidee.getCinNumero());
        bpStc.setCinDateDelivrance(identiteValidee.getCinDateDelivrance());
        bpStc.setCinLieuDelivrance(identiteValidee.getCinLieuDelivrance());
        bpStc.setNumeroCnaps(identiteValidee.getNumeroCnaps());
        bpStc.setIntitulePoste(simulation.getIntitulePoste() != null ? simulation.getIntitulePoste() : "Salarié");
        bpStc.setNomDepartement(simulation.getDepartement() != null ? simulation.getDepartement() : "Direction");
        bpStc.setNomSite(simulation.getSite() != null ? simulation.getSite() : "Antananarivo");
        bpStc.setRegimeIntervenant("PERMANENT");
        bpStc.setModeCalculPaie("STC");
        bpStc.setDateEmbauche(simulation.getDateEmbauche());
        bpStc.setAncienneteMois(simulation.getAncienneteMois());
        bpStc.setModePaiement(simulation.getModeReglement() != null ? simulation.getModeReglement() : "VIREMENT");

        bpStc.setSalaireBase(simulation.getSalaireBase());
        bpStc.setTauxHoraire(simulation.getTauxHoraire());
        bpStc.setTotalGainsBruts(simulation.getTotalBrutStc());
        bpStc.setTotalCotisationsSalariales(simulation.getTotalCotisationsSociales());
        bpStc.setTotalCotisationsPatronales(simulation.getTotalCotisationsPatronales());
        PaieConfigLegale configLegaleActive = chargerConfigLegale(req.getDateDepart(), c);
        if (configLegaleActive != null) {
            bpStc.setIdConfigLegale(configLegaleActive.getIdConfigLegale());
        }
        bpStc.setSalaireNetImposable(simulation.getNetImposable());
        bpStc.setIrsaNet(simulation.getIrsaNet());
        BigDecimal totalRetenuesStc = zeroIfNull(simulation.getSoldePretsDeduit())
                .add(zeroIfNull(simulation.getSoldeAvancesDeduit()))
                .add(zeroIfNull(simulation.getAutresDeductions()));
        bpStc.setTotalRetenuesAcomptePret(totalRetenuesStc);
        bpStc.setNetAvantArrondi(simulation.getNetAPayerStc());
        bpStc.setNetAPayer(simulation.getNetAPayerStc());
        bpStc.setStatut("VALIDE");
        bpStc.setDateCalcul(new Timestamp(System.currentTimeMillis()));
        bpStc.setCalculePar(userId);
        bpStc.setDateValidation(new Timestamp(System.currentTimeMillis()));
        bpStc.setValidePar(userId);
        bpStc.setCommentaire("Solde de Tout Compte : " + entity.getMotifRupture());

        bpStc.save(c);
        new PayrollEmployerSnapshotService().capture(bpStc.getIdBulletin(), c);
        entity.setIdBulletin(bpStc.getIdBulletin());
        simulation.setIdBulletin(bpStc.getIdBulletin());

        // Mise à jour de la clé étrangère dans paie_stc
        String sqlUpdStcBp = "UPDATE paie_stc SET id_bulletin = ? WHERE id_stc = ?";
        try (PreparedStatement psUpdBp = c.prepareStatement(sqlUpdStcBp)) {
            psUpdBp.setInt(1, bpStc.getIdBulletin());
            psUpdBp.setInt(2, entity.getIdStc());
            psUpdBp.executeUpdate();
        }

        // Cache des rubriques pour lier id_rubrique
        Map<String, Integer> cacheRubriques = new HashMap<>();
        try (PreparedStatement psRub = c.prepareStatement("SELECT id_rubrique, code FROM paie_rubrique");
             ResultSet rsRub = psRub.executeQuery()) {
            while (rsRub.next()) {
                cacheRubriques.put(rsRub.getString("code"), rsRub.getInt("id_rubrique"));
            }
        } catch (Exception ignored) {}

        // Lignes complètes du bulletin STC ventilées depuis la simulation
        int ordre = 10;
        for (StcCalculResultDTO.StcLigneDTO sl : simulation.getLignes()) {
            if (sl.getMontant() == null || sl.getMontant().compareTo(BigDecimal.ZERO) == 0) continue;
            BulletinPaieLigne l = new BulletinPaieLigne();
            l.setIdBulletin(bpStc.getIdBulletin());
            l.setCodeRubrique(sl.getCode());
            l.setIdRubrique(resoudreIdRubrique(sl.getCode(), cacheRubriques));
            l.setLibelleRubrique(sl.getLibelle());
            l.setSens(sl.getSens());
            l.setCategorie(determinerCategorieRubrique(sl.getCode()));
            l.setBaseCalcul(sl.getBase());
            if ("PATRONAL".equalsIgnoreCase(sl.getSens())) {
                l.setTauxPatronal(sl.getTaux());
                l.setMontantPatronal(sl.getMontant());
            } else {
                l.setTauxSalarial(sl.getTaux());
                l.setMontantSalarial(sl.getMontant());
            }
            l.setOrdre(ordre);
            ordre += 10;
            l.save(c);
        }

        // 4. Le STC n'impute que les acomptes versés et réellement retenus.
        BigDecimal resteAImputer = simulation.getSoldeAvancesDeduit();
        if (resteAImputer != null && resteAImputer.signum() > 0) {
            String sqlDossiers = "SELECT a.id_avance, a.solde_restant FROM paie_avance_acompte a " +
                    "JOIN ref_type_mouvement_paie t ON t.id_type_mouvement = a.id_type_mouvement " +
                    "WHERE a.id_employe = ? AND t.categorie = 'ACOMPTE' " +
                    "AND a.versement_confirme_at IS NOT NULL AND a.statut = 'APPROUVE' " +
                    "AND a.solde_restant > 0 ORDER BY a.date_paiement, a.id_avance FOR UPDATE OF a";
            try (PreparedStatement ps = c.prepareStatement(sqlDossiers)) {
                ps.setInt(1, req.getIdEmploye());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next() && resteAImputer.signum() > 0) {
                        int idAvance = rs.getInt("id_avance");
                        BigDecimal montant = rs.getBigDecimal("solde_restant").min(resteAImputer);
                        try (PreparedStatement update = c.prepareStatement(
                                "UPDATE paie_avance_acompte SET montant_impute = montant_impute + ?, " +
                                "solde_restant = solde_restant - ?, " +
                                "statut = CASE WHEN solde_restant - ? <= 0 THEN 'IMPUTE_PAIE' ELSE 'APPROUVE' END, " +
                                "id_bulletin = ?, updated_at = CURRENT_TIMESTAMP WHERE id_avance = ?")) {
                            update.setBigDecimal(1, montant);
                            update.setBigDecimal(2, montant);
                            update.setBigDecimal(3, montant);
                            update.setInt(4, bpStc.getIdBulletin());
                            update.setInt(5, idAvance);
                            update.executeUpdate();
                        }
                        try (PreparedStatement journal = c.prepareStatement(
                                "INSERT INTO paie_mouvement_imputation (id_avance, id_bulletin, montant) VALUES (?, ?, ?)")) {
                            journal.setInt(1, idAvance);
                            journal.setInt(2, bpStc.getIdBulletin());
                            journal.setBigDecimal(3, montant);
                            journal.executeUpdate();
                        }
                        resteAImputer = resteAImputer.subtract(montant);
                    }
                }
            }
            if (resteAImputer.signum() > 0) {
                throw new IllegalStateException("STC_AVANCES_INCOHERENTES: montant à imputer supérieur aux soldes versés.");
            }
        }

        // 4.1 Clôture du compteur de congés payés (ICCP liquidée au STC - remise à 0)
        BigDecimal soldeCongeSolde = simulation.getSoldeCongesRestant();
        if (soldeCongeSolde != null && soldeCongeSolde.signum() > 0) {
            MouvementSoldeConge msc = new MouvementSoldeConge();
            msc.setIdEmploye(entity.getIdEmploye());
            msc.setTypeMouvement("AJUSTEMENT");
            msc.setNbJours(soldeCongeSolde.negate());
            msc.setDateMouvement(entity.getDateDepart());
            msc.setCommentaire("Liquidation ICCP - STC " + entity.getNumeroRecu());
            msc.setCreatedBy(userId);
            msc.save(c);

            // Clôturer la période de référence active si elle existe
            try (PreparedStatement psCloseRef = c.prepareStatement(
                    "UPDATE conge_periode_reference SET statut = 'CLOTUREE' " +
                    "WHERE id_employe = ? AND statut = 'OUVERTE'")) {
                psCloseRef.setInt(1, entity.getIdEmploye());
                psCloseRef.executeUpdate();
            } catch (Exception eRef) {
                log.warn("[StcService] Clôture conge_periode_reference ignorée: {}", eRef.getMessage());
            }
        }

        // 4.2 Apurement des prêts d'entreprise déduits au STC
        BigDecimal pretDeduit = simulation.getSoldePretsDeduit();
        if (pretDeduit != null && pretDeduit.signum() > 0) {
            try {
                PaiePret.solderPretsPourStc(req.getIdEmploye(), pretDeduit, bpStc.getIdBulletin(), c);
            } catch (Exception ePret) {
                throw new IllegalStateException("L'apurement du prêt a échoué : validation du STC annulée.", ePret);
            }
        }

        // 5. Désactivation optionnelle de l'employé et clôture carrière
        // Note d'architecture (Règle Métier SIRH ACT-018 / ACT-025) :
        // La désactivation administrative standard de l'employé incombe au Workflow de Rupture RH (WorkflowRhService).
        // Si aucun workflow formel n'a été exécuté en amont et que le gestionnaire a requis `desactiverEmploye = true`,
        // le statut de l'employé, son contrat et son affectation carrière active sont clos pour éviter les salariés "fantômes".
        if (req.isDesactiverEmploye()) {
            try (PreparedStatement psEmp = c.prepareStatement(
                    "UPDATE employe SET est_actif = FALSE, date_desactivation = COALESCE(date_desactivation, CURRENT_TIMESTAMP), updated_at = CURRENT_TIMESTAMP WHERE id_employe = ?")) {
                psEmp.setInt(1, req.getIdEmploye());
                psEmp.executeUpdate();
            }
            // Clôture synchrone de l'affectation active pour cohérence stricte de la base
            String sqlClotureAff = "UPDATE employe_affectation_carriere SET " +
                    "id_statut_rh = COALESCE((SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'INACTIF' LIMIT 1), id_statut_rh), " +
                    "date_fin = COALESCE(date_fin, ?), updated_at = CURRENT_TIMESTAMP " +
                    "WHERE id_employe = ? AND id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF' LIMIT 1)";
            try (PreparedStatement psAff = c.prepareStatement(sqlClotureAff)) {
                psAff.setDate(1, req.getDateDepart());
                psAff.setInt(2, req.getIdEmploye());
                psAff.executeUpdate();
            }
            // Clôture synchrone du contrat de travail actif pour éliminer les contrats fantômes
            String sqlClotureContrat = "UPDATE contrat_travail SET " +
                    "id_statut_rh = COALESCE((SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'CLOTURE' OR code = 'ROMPU' LIMIT 1), id_statut_rh), " +
                    "date_fin = COALESCE(date_fin, ?), updated_at = CURRENT_TIMESTAMP " +
                    "WHERE id_employe = ? AND id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF' LIMIT 1)";
            try (PreparedStatement psCt = c.prepareStatement(sqlClotureContrat)) {
                psCt.setDate(1, req.getDateDepart());
                psCt.setInt(2, req.getIdEmploye());
                psCt.executeUpdate();
            }
        }

        // 6. Audit Log
        AuditLog.audit(c, "VALIDATION_STC", "paie_stc", String.valueOf(entity.getIdStc()), null, entity);

        return simulation;
    }

    private BigDecimal calculerAcomptesVersesNonImputes(Integer idEmploye, Connection c) throws Exception {
        String sql = "SELECT COALESCE(SUM(a.solde_restant), 0) FROM paie_avance_acompte a "
                + "JOIN ref_type_mouvement_paie t ON t.id_type_mouvement = a.id_type_mouvement "
                + "WHERE a.id_employe = ? AND t.categorie = 'ACOMPTE' "
                + "AND a.versement_confirme_at IS NOT NULL AND a.statut = 'APPROUVE'";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        }
    }

    private void verifierMontantsNonNegatifs(StcCalculRequest req) {
        for (BigDecimal montant : List.of(
                zeroIfNull(req.getJoursTravaillesDernierMois()),
                zeroIfNull(req.getMoisPreavis()),
                zeroIfNull(req.getIndemnitePreavis()),
                zeroIfNull(req.getIndemniteLicenciement()),
                zeroIfNull(req.getAutresIndemnites()),
                zeroIfNull(req.getAutresDeductions()))) {
            if (montant.signum() < 0) throw new IllegalArgumentException("Les montants du STC ne peuvent pas être négatifs.");
        }
        if (req.getAutresDeductions() != null && req.getAutresDeductions().signum() > 0) {
            throw new IllegalArgumentException("Les retenues diverses doivent être traitées dans leur dossier légal, hors STC.");
        }
        if (req.getJoursTravaillesDernierMois() != null
                && req.getJoursTravaillesDernierMois().compareTo(new BigDecimal("30")) > 0) {
            throw new IllegalArgumentException("Le prorata du dernier mois ne peut dépasser 30 jours.");
        }
    }

    private void verifierValidationJuridique(StcCalculRequest req, StcPreparationDTO prep, Connection c) throws Exception {
        verifierMontantsNonNegatifs(req);
        String motif = req.getMotifRupture() == null ? "" : req.getMotifRupture().trim().toUpperCase();
        if (!List.of("DEMISSION", "LICENCIEMENT", "LICENCIEMENT_ECONOMIQUE", "CESSATION_ACTIVITE",
                "FIN_CONTRAT", "RUPTURE_ESSAI", "RUPTURE_CONVENTIONNELLE", "RETRAITE",
                "DEPART_RETRAITE", "AUTRE").contains(motif)) {
            throw new IllegalArgumentException("Le motif de rupture doit être choisi dans le référentiel RH.");
        }
        boolean economique = "LICENCIEMENT_ECONOMIQUE".equals(motif) || "CESSATION_ACTIVITE".equals(motif);
        var emploi = new mg.galaxam.sirh.contrat.application.EmploymentContextResolver()
                .resolveContext(req.getIdEmploye(), req.getDateDepart().toLocalDate(), c);
        if (emploi == null || emploi.idContrat() == null) {
            throw new IllegalArgumentException("Aucun contrat applicable à la date du départ.");
        }
        if (emploi.dateFinContrat() == null
                || !emploi.dateFinContrat().equals(req.getDateDepart().toLocalDate())) {
            throw new IllegalArgumentException(
                    "La validation du STC exige une rupture appliquée avec une date de fin de contrat égale à la date de départ. Utilisez la simulation tant que la rupture est en brouillon.");
        }
        if ("FIN_CONTRAT".equals(motif) && "CDD".equalsIgnoreCase(emploi.typeContratCode())
                && (emploi.dateFinContrat() == null
                || !emploi.dateFinContrat().equals(req.getDateDepart().toLocalDate()))) {
            throw new IllegalArgumentException("La fin de CDD doit correspondre à son échéance contractuelle. Une rupture anticipée suit un autre régime.");
        }
        if ("AUTRE".equals(motif) && (req.getCommentaire() == null || req.getCommentaire().isBlank())) {
            throw new IllegalArgumentException("Un motif de sortie 'Autre' doit être décrit dans le commentaire RH.");
        }
        if (!"CDI".equalsIgnoreCase(emploi.typeContratCode())
                && (economique || "LICENCIEMENT".equals(motif)
                || zeroIfNull(req.getIndemnitePreavis()).signum() > 0)) {
            throw new IllegalArgumentException("Le préavis CDI et l'indemnité de licenciement économique ne s'appliquent pas à ce type de contrat.");
        }
        BigDecimal indemniteLicenciement = zeroIfNull(req.getIndemniteLicenciement());
        BigDecimal indemnitePreavis = zeroIfNull(req.getIndemnitePreavis());
        if (indemnitePreavis.signum() > 0 && zeroIfNull(req.getMoisPreavis()).signum() <= 0) {
            throw new IllegalArgumentException("La durée de préavis non exécutée doit être renseignée avec son indemnité.");
        }
        if (zeroIfNull(req.getAutresIndemnites()).signum() > 0
                && (req.getMotifAutresIndemnites() == null || req.getMotifAutresIndemnites().isBlank())) {
            throw new IllegalArgumentException("Le libellé des autres gains de départ est obligatoire.");
        }
        if ((indemniteLicenciement.signum() > 0 || indemnitePreavis.signum() > 0)
                && (req.getJustificationIndemnites() == null || req.getJustificationIndemnites().isBlank())) {
            throw new IllegalArgumentException("La référence de la décision et du calcul des indemnités est obligatoire.");
        }
        if (economique) {
            BigDecimal reference = req.getRemunerationReferenceLicenciement();
            if (reference == null || reference.signum() <= 0) {
                throw new IllegalArgumentException("La dernière rémunération avec accessoires, hors frais, est obligatoire pour le licenciement économique ou la cessation d'activité.");
            }
            if (reference.compareTo(prep.getSalaireBase()) < 0) {
                throw new IllegalArgumentException("La rémunération de référence ne peut être inférieure au salaire de base du contrat.");
            }
            BigDecimal jours = new BigDecimal(Math.min(prep.getAncienneteAnnees() * 10L, 180L));
            BigDecimal minimum = MoneyRoundingPolicy.amount(reference.multiply(jours)
                    .divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP));
            if (indemniteLicenciement.compareTo(minimum) < 0) {
                throw new IllegalArgumentException("L'indemnité de licenciement est inférieure au minimum légal calculé sur la rémunération de référence : " + minimum + " Ar.");
            }
        } else if (indemniteLicenciement.signum() > 0 && !"LICENCIEMENT".equals(motif)) {
            throw new IllegalArgumentException("Une indemnité de licenciement hors motif économique exige un motif de licenciement explicite.");
        }
    }

    // =========================================================================
    // 4. RÈGLEMENT ET DÉCOMPTE DE FIN DE CONTRAT
    // =========================================================================

    public StcSituationReglementDTO enregistrerReglement(Integer idStc, StcReglementRequest req, Integer userId, Connection c) throws Exception {
        if (idStc == null) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, "L'identifiant du STC est obligatoire.");
        }
        if (req == null || req.dateReglement() == null || req.referenceReglement() == null
                || req.referenceReglement().isBlank()) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, "La date et la référence du paiement sont obligatoires.");
        }
        if (req.dateReglement().toLocalDate().isAfter(mg.galaxam.sirh.paie.domain.PayrollBusinessTime.today())) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, "Le règlement ne peut pas être daté dans le futur.");
        }

        // 1. Verrouillage transactionnel pessimiste strict pour empêcher tout sur-règlement concurrent
        String sqlLock = "SELECT s.id_stc, s.id_employe, s.statut, s.net_a_payer_stc, s.date_depart, d.id_dossier_sortie "
                + "FROM paie_stc s "
                + "LEFT JOIN paie_dossier_sortie d ON d.id_stc = s.id_stc "
                + "WHERE s.id_stc = ? FOR UPDATE OF s";

        Integer idEmploye;
        String statutStc;
        BigDecimal netAPayerStc;
        Date dateDepart;
        Integer idDossierSortie = null;

        try (PreparedStatement psLock = c.prepareStatement(sqlLock)) {
            psLock.setInt(1, idStc);
            try (ResultSet rs = psLock.executeQuery()) {
                if (!rs.next()) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "STC introuvable : " + idStc);
                }
                idEmploye = rs.getInt("id_employe");
                statutStc = rs.getString("statut");
                netAPayerStc = rs.getBigDecimal("net_a_payer_stc") != null ? rs.getBigDecimal("net_a_payer_stc") : BigDecimal.ZERO;
                dateDepart = rs.getDate("date_depart");
                if (rs.getObject("id_dossier_sortie") != null) {
                    idDossierSortie = rs.getInt("id_dossier_sortie");
                }
            }
        }

        if (dateDepart != null && req.dateReglement().before(dateDepart)) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "La date de règlement (" + req.dateReglement() + ") ne peut pas être antérieure à la date de départ effective (" + dateDepart + ").");
        }

        if (!List.of("VALIDE", "REGLE").contains(statutStc)) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Le STC doit être scellé et validé avant de pouvoir enregistrer un règlement (statut actuel : " + statutStc + ").");
        }

        // 1.bis Vérification du statut du dossier de sortie associé et gestion des dérogations d'échéancier
        String sqlCheckDosCloture = "SELECT id_dossier_sortie, statut_dossier, derogation_reglement, justification_derogation "
                + "FROM paie_dossier_sortie WHERE id_stc = ? FOR UPDATE";
        try (PreparedStatement psDos = c.prepareStatement(sqlCheckDosCloture)) {
            psDos.setInt(1, idStc);
            try (ResultSet rsDos = psDos.executeQuery()) {
                if (rsDos.next()) {
                    String statutDossier = rsDos.getString("statut_dossier");
                    boolean derogation = rsDos.getBoolean("derogation_reglement");
                    if ("CLOTURE".equalsIgnoreCase(statutDossier)) {
                        if (!derogation) {
                            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                                    "Impossible d'enregistrer un règlement : le dossier de sortie associé est clôturé (statut CLOTURE) "
                                    + "et ne fait l'objet d'aucune dérogation formelle d'échéancier.");
                        }
                    }
                }
            }
        }

        // 1.ter Idempotence technique : si une clé d'idempotence technique est fournie
        String idemKey = req.idempotencyKey() != null && !req.idempotencyKey().isBlank()
                ? req.idempotencyKey().trim() : null;
        if (idemKey != null) {
            String sqlCheckIdem = "SELECT id_reglement, montant_regle, mode_reglement, reference_reglement, date_reglement, est_annule "
                    + "FROM paie_stc_reglement WHERE id_stc = ? AND cle_idempotence = ?";
            try (PreparedStatement psIdem = c.prepareStatement(sqlCheckIdem)) {
                psIdem.setInt(1, idStc);
                psIdem.setString(2, idemKey);
                try (ResultSet rsIdem = psIdem.executeQuery()) {
                    if (rsIdem.next()) {
                        boolean wasAnnule = rsIdem.getBoolean("est_annule");
                        if (wasAnnule) {
                            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                                    "Cette clé d'idempotence (" + idemKey + ") correspond à un règlement qui a été ultérieurement annulé. Nouvelle requête requise.");
                        }

                        // Contrôle strict de non-divergence du payload (détection de collision ou falsification d'idempotence)
                        BigDecimal montantExistant = rsIdem.getBigDecimal("montant_regle");
                        String modeExistant = rsIdem.getString("mode_reglement");
                        String refExistante = rsIdem.getString("reference_reglement");
                        Date dateExistante = rsIdem.getDate("date_reglement");

                        boolean memeRef = req.referenceReglement() != null && req.referenceReglement().trim().equalsIgnoreCase(refExistante);
                        boolean memeMode = (req.modeReglement() == null && "VIREMENT".equalsIgnoreCase(modeExistant))
                                || (req.modeReglement() != null && req.modeReglement().trim().equalsIgnoreCase(modeExistant));
                        boolean memeMontant = req.montantRegle() != null && req.montantRegle().compareTo(montantExistant) == 0;
                        boolean memeDate = req.dateReglement() != null && req.dateReglement().equals(dateExistante);

                        if (!memeRef || !memeMode || !memeMontant || !memeDate) {
                            throw new SirhBusinessException(SirhErrorCode.DATA_INTEGRITY_VIOLATION,
                                    "Conflit d'idempotence : la clé '" + idemKey + "' a déjà été enregistrée pour ce STC avec des paramètres distincts "
                                    + "(référence: '" + refExistante + "', montant: " + montantExistant + " Ar, mode: " + modeExistant + ", date: " + dateExistante + "). "
                                    + "Une répétition avec des données différentes est interdite.");
                        }

                        // Rejeu légitime : retourne de manière idempotente la situation déjà persistée
                        return obtenirSituationReglements(idStc, c);
                    }
                }
            }
        }

        // 2. Calcul du cumul déjà réglé actif depuis le journal paie_stc_reglement (source de vérité)
        BigDecimal totalDejaRegle = BigDecimal.ZERO;
        String sqlSum = "SELECT COALESCE(SUM(montant_regle), 0) FROM paie_stc_reglement WHERE id_stc = ? AND est_annule = FALSE";
        try (PreparedStatement psSum = c.prepareStatement(sqlSum)) {
            psSum.setInt(1, idStc);
            try (ResultSet rsSum = psSum.executeQuery()) {
                if (rsSum.next()) {
                    totalDejaRegle = rsSum.getBigDecimal(1);
                }
            }
        }

        BigDecimal resteAPayer = netAPayerStc.subtract(totalDejaRegle);
        if (resteAPayer.compareTo(BigDecimal.ZERO) < 0) {
            resteAPayer = BigDecimal.ZERO;
        }

        // Si déjà intégralement réglé et que rien ne reste à payer
        if ("REGLE".equals(statutStc) && resteAPayer.compareTo(BigDecimal.ZERO) <= 0) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Ce STC est déjà intégralement réglé (solde restant à payer : 0.00 Ar).");
        }

        // 3. Détermination du montant effectif du versement
        BigDecimal montantEffectif;
        if (netAPayerStc.compareTo(BigDecimal.ZERO) == 0) {
            // Gestion explicite du cas net nul (0 Ar)
            if (req.montantRegle() != null && req.montantRegle().compareTo(BigDecimal.ZERO) != 0) {
                throw new SirhBusinessException(SirhErrorCode.DATA_INTEGRITY_VIOLATION,
                        "Le montant net de ce STC est nul (0.00 Ar). Le règlement d'acquittement doit avoir un montant de 0.00 Ar.");
            }
            montantEffectif = BigDecimal.ZERO;
        } else {
            // Net positif
            if (req.montantRegle() == null) {
                montantEffectif = resteAPayer; // par défaut : solde l'intégralité
            } else {
                montantEffectif = req.montantRegle();
            }

            if (montantEffectif.compareTo(BigDecimal.ZERO) <= 0) {
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                        "Le montant du versement doit être strictement supérieur à 0 Ar pour un STC à net positif.");
            }

            if (montantEffectif.compareTo(resteAPayer) > 0) {
                throw new SirhBusinessException(SirhErrorCode.DATA_INTEGRITY_VIOLATION,
                        "Le montant du versement (" + montantEffectif + " Ar) dépasse le solde restant à payer ("
                                + resteAPayer + " Ar). Règlement refusé pour éviter tout sur-règlement.");
            }
        }

        // 4. Protection contre les doubles soumissions (référence métier active déjà enregistrée sur ce STC)
        String refClean = req.referenceReglement().trim();
        String sqlCheckRef = "SELECT 1 FROM paie_stc_reglement WHERE id_stc = ? AND reference_reglement = ? AND est_annule = FALSE";
        try (PreparedStatement psRef = c.prepareStatement(sqlCheckRef)) {
            psRef.setInt(1, idStc);
            psRef.setString(2, refClean);
            try (ResultSet rsRef = psRef.executeQuery()) {
                if (rsRef.next()) {
                    throw new SirhBusinessException(SirhErrorCode.DATA_INTEGRITY_VIOLATION,
                            "Un règlement actif porte déjà la référence métier '" + refClean + "' sur ce STC. Double soumission refusée.");
                }
            }
        }

        // 5. Insertion dans le journal officiel paie_stc_reglement
        String modeReg = req.modeReglement() != null && !req.modeReglement().isBlank()
                ? req.modeReglement().trim().toUpperCase() : "VIREMENT";
        int idReglementGenere = 0;
        String sqlInsert = "INSERT INTO paie_stc_reglement (id_stc, date_reglement, montant_regle, mode_reglement, reference_reglement, commentaire, cle_idempotence, enregistre_par, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) RETURNING id_reglement";
        try (PreparedStatement psIns = c.prepareStatement(sqlInsert)) {
            psIns.setInt(1, idStc);
            psIns.setDate(2, req.dateReglement());
            psIns.setBigDecimal(3, montantEffectif);
            psIns.setString(4, modeReg);
            psIns.setString(5, refClean);
            psIns.setString(6, req.commentaire());
            psIns.setString(7, idemKey);
            if (userId != null) {
                psIns.setInt(8, userId);
            } else {
                psIns.setNull(8, java.sql.Types.INTEGER);
            }
            try (ResultSet rsIns = psIns.executeQuery()) {
                if (rsIns.next()) {
                    idReglementGenere = rsIns.getInt(1);
                }
            }
        }

        // 6. Synchronisation atomique des statuts selon le nouveau reste à payer
        BigDecimal nouveauReste = resteAPayer.subtract(montantEffectif);
        boolean estSolde = nouveauReste.compareTo(BigDecimal.ZERO) == 0;

        if (estSolde) {
            // STC et Dossier basculent à 'REGLE'
            String sqlUpStc = "UPDATE paie_stc SET statut = 'REGLE', date_reglement = ?, reference_reglement = ?, "
                    + "mode_reglement = ?, regle_par = ?, updated_at = CURRENT_TIMESTAMP WHERE id_stc = ?";
            try (PreparedStatement psUpStc = c.prepareStatement(sqlUpStc)) {
                psUpStc.setDate(1, req.dateReglement());
                psUpStc.setString(2, refClean);
                psUpStc.setString(3, modeReg);
                if (userId != null) psUpStc.setInt(4, userId); else psUpStc.setNull(4, java.sql.Types.INTEGER);
                psUpStc.setInt(5, idStc);
                psUpStc.executeUpdate();
            }

            // Le dossier de sortie bascule à 'REGLE' uniquement s'il n'est pas déjà administrativement 'CLOTURE'
            String sqlUpDos = "UPDATE paie_dossier_sortie SET statut_dossier = 'REGLE', updated_at = CURRENT_TIMESTAMP "
                    + "WHERE id_stc = ? AND statut_dossier <> 'CLOTURE'";
            try (PreparedStatement psUpDos = c.prepareStatement(sqlUpDos)) {
                psUpDos.setInt(1, idStc);
                psUpDos.executeUpdate();
            }
        } else {
            // Règlement partiel : paie_stc et dossier restent en 'VALIDE', mais trace de la dernière référence/date
            String sqlUpStc = "UPDATE paie_stc SET date_reglement = ?, reference_reglement = ?, "
                    + "mode_reglement = ?, regle_par = ?, updated_at = CURRENT_TIMESTAMP WHERE id_stc = ?";
            try (PreparedStatement psUpStc = c.prepareStatement(sqlUpStc)) {
                psUpStc.setDate(1, req.dateReglement());
                psUpStc.setString(2, refClean);
                psUpStc.setString(3, modeReg);
                if (userId != null) psUpStc.setInt(4, userId); else psUpStc.setNull(4, java.sql.Types.INTEGER);
                psUpStc.setInt(5, idStc);
                psUpStc.executeUpdate();
            }

            String sqlUpDos = "UPDATE paie_dossier_sortie SET updated_at = CURRENT_TIMESTAMP WHERE id_stc = ?";
            try (PreparedStatement psUpDos = c.prepareStatement(sqlUpDos)) {
                psUpDos.setInt(1, idStc);
                psUpDos.executeUpdate();
            }
        }

        // 7. Audit Log
        AuditLog.audit(c, "REGLEMENT_STC", "paie_stc_reglement", String.valueOf(idReglementGenere), null,
                "Versement de " + montantEffectif + " Ar (Réf: " + refClean + ") sur STC #" + idStc + " - Nouveau reste: " + nouveauReste + " Ar");

        return obtenirSituationReglements(idStc, c);
    }

    public StcSituationReglementDTO obtenirSituationReglements(Integer idStc, Connection c) throws Exception {
        if (idStc == null) return null;
        String sql = "SELECT s.id_stc, s.id_employe, s.numero_recu, s.statut AS statut_stc, s.net_a_payer_stc, "
                + "e.matricule, CONCAT(e.nom, ' ', COALESCE(e.prenom, '')) AS nom_complet, "
                + "d.id_dossier_sortie, d.statut_dossier "
                + "FROM paie_stc s "
                + "JOIN employe e ON e.id_employe = s.id_employe "
                + "LEFT JOIN paie_dossier_sortie d ON d.id_stc = s.id_stc "
                + "WHERE s.id_stc = ?";

        StcSituationReglementDTO situation = new StcSituationReglementDTO();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idStc);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                situation.setIdStc(rs.getInt("id_stc"));
                situation.setIdEmploye(rs.getInt("id_employe"));
                situation.setNumeroRecu(rs.getString("numero_recu"));
                situation.setStatutStc(rs.getString("statut_stc"));
                situation.setNetAPayerStc(rs.getBigDecimal("net_a_payer_stc") != null ? rs.getBigDecimal("net_a_payer_stc") : BigDecimal.ZERO);
                situation.setMatricule(rs.getString("matricule"));
                situation.setNomComplet(rs.getString("nom_complet"));
                if (rs.getObject("id_dossier_sortie") != null) {
                    situation.setIdDossierSortie(rs.getInt("id_dossier_sortie"));
                    situation.setStatutDossier(rs.getString("statut_dossier"));
                }
            }
        }

        // Lecture de toutes les tranches de règlement (actives et annulées)
        String sqlRegs = "SELECT r.id_reglement, r.id_stc, r.date_reglement, r.montant_regle, r.mode_reglement, "
                + "r.reference_reglement, r.commentaire, r.cle_idempotence, r.enregistre_par, r.created_at, "
                + "r.est_annule, r.annule_par, r.date_annulation, r.motif_annulation, "
                + "ue.identifiant AS nom_enregistre_par, "
                + "ua.identifiant AS nom_annule_par "
                + "FROM paie_stc_reglement r "
                + "LEFT JOIN utilisateur ue ON ue.id_utilisateur = r.enregistre_par "
                + "LEFT JOIN utilisateur ua ON ua.id_utilisateur = r.annule_par "
                + "WHERE r.id_stc = ? ORDER BY r.created_at ASC, r.id_reglement ASC";

        BigDecimal totalRegleActif = BigDecimal.ZERO;
        List<StcReglementDTO> list = new ArrayList<>();
        try (PreparedStatement psReg = c.prepareStatement(sqlRegs)) {
            psReg.setInt(1, idStc);
            try (ResultSet rsReg = psReg.executeQuery()) {
                while (rsReg.next()) {
                    StcReglementDTO rDto = new StcReglementDTO();
                    rDto.setIdReglement(rsReg.getInt("id_reglement"));
                    rDto.setIdStc(rsReg.getInt("id_stc"));
                    rDto.setDateReglement(rsReg.getDate("date_reglement"));
                    rDto.setMontantRegle(rsReg.getBigDecimal("montant_regle"));
                    rDto.setModeReglement(rsReg.getString("mode_reglement"));
                    rDto.setReferenceReglement(rsReg.getString("reference_reglement"));
                    rDto.setCommentaire(rsReg.getString("commentaire"));
                    rDto.setCleIdempotence(rsReg.getString("cle_idempotence"));
                    if (rsReg.getObject("enregistre_par") != null) {
                        rDto.setEnregistrePar(rsReg.getInt("enregistre_par"));
                        rDto.setNomEnregistrePar(rsReg.getString("nom_enregistre_par"));
                    }
                    rDto.setCreatedAt(rsReg.getTimestamp("created_at"));
                    rDto.setEstAnnule(rsReg.getBoolean("est_annule"));
                    if (rsReg.getObject("annule_par") != null) {
                        rDto.setAnnulePar(rsReg.getInt("annule_par"));
                        rDto.setNomAnnulePar(rsReg.getString("nom_annule_par"));
                    }
                    rDto.setDateAnnulation(rsReg.getTimestamp("date_annulation"));
                    rDto.setMotifAnnulation(rsReg.getString("motif_annulation"));

                    if (!rDto.isEstAnnule() && rDto.getMontantRegle() != null) {
                        totalRegleActif = totalRegleActif.add(rDto.getMontantRegle());
                    }
                    list.add(rDto);
                }
            }
        }

        BigDecimal reste = situation.getNetAPayerStc().subtract(totalRegleActif);
        if (reste.compareTo(BigDecimal.ZERO) < 0) reste = BigDecimal.ZERO;

        situation.setTotalRegle(totalRegleActif);
        situation.setResteAPayer(reste);
        situation.setEstIntegralementRegle(reste.compareTo(BigDecimal.ZERO) == 0 && "REGLE".equals(situation.getStatutStc()));
        situation.setReglements(list);

        return situation;
    }

    public StcSituationReglementDTO annulerReglement(Integer idStc, Integer idReglement, AnnulationReglementRequest req, Integer userId, Connection c) throws Exception {
        if (idStc == null || idReglement == null) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, "L'identifiant du STC et du règlement sont obligatoires.");
        }
        if (req == null || req.motifAnnulation() == null || req.motifAnnulation().isBlank()) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, "Le motif de l'annulation est obligatoire pour traçabilité comptable.");
        }

        // 1. Verrou pessimiste sur le STC
        String sqlLock = "SELECT s.id_stc, s.statut, s.net_a_payer_stc FROM paie_stc s WHERE s.id_stc = ? FOR UPDATE";
        String statutStc;
        BigDecimal netAPayerStc;
        try (PreparedStatement psLock = c.prepareStatement(sqlLock)) {
            psLock.setInt(1, idStc);
            try (ResultSet rs = psLock.executeQuery()) {
                if (!rs.next()) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "STC introuvable : " + idStc);
                }
                statutStc = rs.getString("statut");
                netAPayerStc = rs.getBigDecimal("net_a_payer_stc") != null ? rs.getBigDecimal("net_a_payer_stc") : BigDecimal.ZERO;
            }
        }

        if ("ANNULE".equalsIgnoreCase(statutStc)) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Impossible d'annuler un règlement sur un STC déjà annulé.");
        }

        // 1.bis Interdiction formelle de l'annulation ordinaire si le dossier de sortie est clôturé
        String sqlCheckDosCloture = "SELECT id_dossier_sortie, statut_dossier FROM paie_dossier_sortie WHERE id_stc = ? FOR UPDATE";
        try (PreparedStatement psDos = c.prepareStatement(sqlCheckDosCloture)) {
            psDos.setInt(1, idStc);
            try (ResultSet rsDos = psDos.executeQuery()) {
                if (rsDos.next()) {
                    String statutDossier = rsDos.getString("statut_dossier");
                    if ("CLOTURE".equalsIgnoreCase(statutDossier)) {
                        throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                                "L'annulation ordinaire d'un règlement est strictement interdite lorsque le dossier de sortie est clôturé (statut CLOTURE). "
                                + "Toute correction ultérieure relève d'une procédure exceptionnelle de régularisation post-clôture.");
                    }
                }
            }
        }

        // 2. Verrou sur la ligne de règlement
        String sqlLockReg = "SELECT id_reglement, montant_regle, reference_reglement, est_annule "
                + "FROM paie_stc_reglement WHERE id_reglement = ? AND id_stc = ? FOR UPDATE";
        BigDecimal montantAnnule;
        String refAnnulee;
        try (PreparedStatement psReg = c.prepareStatement(sqlLockReg)) {
            psReg.setInt(1, idReglement);
            psReg.setInt(2, idStc);
            try (ResultSet rsReg = psReg.executeQuery()) {
                if (!rsReg.next()) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND,
                            "Règlement #" + idReglement + " introuvable pour le STC #" + idStc);
                }
                if (rsReg.getBoolean("est_annule")) {
                    throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                            "Ce versement a déjà été annulé précédemment.");
                }
                montantAnnule = rsReg.getBigDecimal("montant_regle");
                refAnnulee = rsReg.getString("reference_reglement");
            }
        }

        // 3. Marquage de l'annulation
        String sqlCancel = "UPDATE paie_stc_reglement SET est_annule = TRUE, annule_par = ?, "
                + "date_annulation = CURRENT_TIMESTAMP, motif_annulation = ? WHERE id_reglement = ?";
        try (PreparedStatement psCancel = c.prepareStatement(sqlCancel)) {
            if (userId != null) psCancel.setInt(1, userId); else psCancel.setNull(1, java.sql.Types.INTEGER);
            psCancel.setString(2, req.motifAnnulation().trim());
            psCancel.setInt(3, idReglement);
            psCancel.executeUpdate();
        }

        // 4. Recalcul de la somme active après annulation
        BigDecimal nouvelleSomme = BigDecimal.ZERO;
        Date dernierDate = null;
        String dernierRef = null;
        String sqlActive = "SELECT date_reglement, reference_reglement, montant_regle FROM paie_stc_reglement "
                + "WHERE id_stc = ? AND est_annule = FALSE ORDER BY created_at DESC, id_reglement DESC";
        try (PreparedStatement psAct = c.prepareStatement(sqlActive)) {
            psAct.setInt(1, idStc);
            try (ResultSet rsAct = psAct.executeQuery()) {
                while (rsAct.next()) {
                    nouvelleSomme = nouvelleSomme.add(rsAct.getBigDecimal("montant_regle"));
                    if (dernierDate == null) {
                        dernierDate = rsAct.getDate("date_reglement");
                        dernierRef = rsAct.getString("reference_reglement");
                    }
                }
            }
        }

        BigDecimal resteApresAnnulation = netAPayerStc.subtract(nouvelleSomme);
        if (resteApresAnnulation.compareTo(BigDecimal.ZERO) < 0) resteApresAnnulation = BigDecimal.ZERO;

        // 5. Si le STC était marqué 'REGLE' et que le reste est redevenu > 0, réouverture atomique en 'VALIDE'
        if ("REGLE".equals(statutStc) && resteApresAnnulation.compareTo(BigDecimal.ZERO) > 0) {
            String sqlReopenStc = "UPDATE paie_stc SET statut = 'VALIDE', date_reglement = ?, reference_reglement = ?, "
                    + "updated_at = CURRENT_TIMESTAMP WHERE id_stc = ?";
            try (PreparedStatement psReopen = c.prepareStatement(sqlReopenStc)) {
                psReopen.setDate(1, dernierDate);
                psReopen.setString(2, dernierRef);
                psReopen.setInt(3, idStc);
                psReopen.executeUpdate();
            }

            String sqlReopenDos = "UPDATE paie_dossier_sortie SET statut_dossier = 'VALIDE', "
                    + "updated_at = CURRENT_TIMESTAMP WHERE id_stc = ?";
            try (PreparedStatement psReopenDos = c.prepareStatement(sqlReopenDos)) {
                psReopenDos.setInt(1, idStc);
                psReopenDos.executeUpdate();
            }
        } else {
            // Mettre à jour la dernière référence active
            String sqlUpStc = "UPDATE paie_stc SET date_reglement = ?, reference_reglement = ?, "
                    + "updated_at = CURRENT_TIMESTAMP WHERE id_stc = ?";
            try (PreparedStatement psUp = c.prepareStatement(sqlUpStc)) {
                psUp.setDate(1, dernierDate);
                psUp.setString(2, dernierRef);
                psUp.setInt(3, idStc);
                psUp.executeUpdate();
            }
        }

        // 6. Audit Log
        AuditLog.audit(c, "ANNULATION_REGLEMENT_STC", "paie_stc_reglement", String.valueOf(idReglement),
                "Annulation versement " + montantAnnule + " Ar (Réf: " + refAnnulee + ") - Motif: " + req.motifAnnulation().trim(),
                "Nouveau total réglé: " + nouvelleSomme + " Ar");

        return obtenirSituationReglements(idStc, c);
    }

    public StcRecuDTO obtenirRecuStc(Integer idStc, Connection c) throws Exception {
        PaieStc s = PaieStc.trouverParId(idStc, c);
        if (s == null) return null;

        // Un reçu validé doit rester lisible même si le contrat, les congés ou
        // la configuration légale ont changé depuis son émission.
        StcRecuDTO r = new StcRecuDTO();
        r.setIdentiteHistoriqueDisponible(s.getIdBulletin() != null);
        r.setNumeroRecu(s.getNumeroRecu());
        r.setDateEmission(s.getDateValidation() != null ? s.getDateValidation() : new Timestamp(System.currentTimeMillis()));
        r.setStatut(s.getStatut());

        // Situation financière des règlements constatés
        StcSituationReglementDTO sit = obtenirSituationReglements(idStc, c);
        if (sit != null) {
            r.setMontantTotalRegle(sit.getTotalRegle());
            r.setSoldeRestantAPayer(sit.getResteAPayer());
            r.setEstIntegralementRegle(sit.isEstIntegralementRegle());
            r.setReglements(sit.getReglements());
        }

        try (PreparedStatement psReg = c.prepareStatement(
                "SELECT date_reglement, reference_reglement FROM paie_stc WHERE id_stc = ?")) {
            psReg.setInt(1, idStc);
            try (ResultSet rsReg = psReg.executeQuery()) {
                if (rsReg.next()) {
                    r.setDateReglement(rsReg.getDate("date_reglement"));
                    r.setReferenceReglement(rsReg.getString("reference_reglement"));
                }
            }
        }

        var employeurHistorique = s.getIdBulletin() == null ? null
                : new PayrollEmployerSnapshotService().find(s.getIdBulletin(), c);
        r.setEmployeurHistoriqueDisponible(employeurHistorique != null);
        if (employeurHistorique != null) {
            r.setEntrepriseNom(employeurHistorique.name());
            r.setEntrepriseNif(employeurHistorique.nif());
            r.setEntrepriseStat(employeurHistorique.stat());
            r.setEntrepriseAdresse(employeurHistorique.address());
        } else {
            r.setEntrepriseNom("Employeur historique non disponible");
        }

        r.setIdEmploye(s.getIdEmploye());
        try (PreparedStatement psSnapshot = c.prepareStatement(
                "SELECT id_contrat, source_pointage, id_bulletin_mensuel, net_bulletin_mensuel, "
                + "montant_bulletin_deja_paye, solde_bulletin_a_regler, date_paiement_bulletin, "
                + "reference_paiement_bulletin FROM paie_stc WHERE id_stc = ?")) {
            psSnapshot.setInt(1, idStc);
            try (ResultSet snapshot = psSnapshot.executeQuery()) {
                if (snapshot.next()) {
                    Integer contratFige = (Integer) snapshot.getObject("id_contrat");
                    r.setIdContrat(contratFige);
                    String sourceFigee = snapshot.getString("source_pointage");
                    r.setSourcePointage(sourceFigee);
                    r.setIdBulletinMensuel((Integer) snapshot.getObject("id_bulletin_mensuel"));
                    r.setNetBulletinMensuel(snapshot.getBigDecimal("net_bulletin_mensuel"));
                    r.setMontantBulletinDejaPaye(snapshot.getBigDecimal("montant_bulletin_deja_paye"));
                    r.setSoldeBulletinARegler(snapshot.getBigDecimal("solde_bulletin_a_regler"));
                    r.setDatePaiementBulletin(snapshot.getDate("date_paiement_bulletin"));
                    r.setReferencePaiementBulletin(snapshot.getString("reference_paiement_bulletin"));
                }
            }
        }
        r.setDateDepart(s.getDateDepart());
        r.setMotifRupture(s.getMotifRupture());
        // Le bulletin STC contient l'identité figée lors de la validation.
        if (s.getIdBulletin() != null) {
            try (PreparedStatement psIdentite = c.prepareStatement(
                    "SELECT matricule, nom_prenom, cin_numero, cin_date_delivrance, cin_lieu_delivrance, "
                    + "numero_cnaps, intitule_poste, nom_departement, nom_site, date_embauche, anciennete_mois "
                    + "FROM bulletin_paie WHERE id_bulletin = ?")) {
                psIdentite.setInt(1, s.getIdBulletin());
                try (ResultSet rsIdentite = psIdentite.executeQuery()) {
                    if (rsIdentite.next()) {
                        r.setMatricule(rsIdentite.getString("matricule"));
                        r.setNomComplet(rsIdentite.getString("nom_prenom"));
                        r.setCinNumero(rsIdentite.getString("cin_numero"));
                        r.setCinDateDelivrance(rsIdentite.getDate("cin_date_delivrance"));
                        r.setCinLieuDelivrance(rsIdentite.getString("cin_lieu_delivrance"));
                        r.setNumeroCnaps(rsIdentite.getString("numero_cnaps"));
                        r.setIntitulePoste(rsIdentite.getString("intitule_poste"));
                        r.setDepartement(rsIdentite.getString("nom_departement"));
                        r.setSite(rsIdentite.getString("nom_site"));
                        r.setDateEmbauche(rsIdentite.getDate("date_embauche"));
                        int mois = rsIdentite.getInt("anciennete_mois");
                        r.setAncienneteTexte((mois / 12) + " an(s) et " + (mois % 12) + " mois");
                    }
                }
            }
        }

        r.setSalaireProrata(s.getSalaireProrata());
        r.setSoldeCongesRestant(s.getSoldeCongesRestant());
        r.setTauxJournalierConge(s.getTauxJournalierConge());

        BigDecimal iccp = s.getIndemniteCongesPayes() != null ? s.getIndemniteCongesPayes() : BigDecimal.ZERO;
        r.setIndemniteCongesPayes(iccp.max(BigDecimal.ZERO));
        r.setAutresDeductions(s.getAutresDeductions() != null ? s.getAutresDeductions() : BigDecimal.ZERO);

        r.setMoisPreavis(s.getMoisPreavis());
        r.setIndemnitePreavis(s.getIndemnitePreavis());
        r.setIndemniteLicenciement(s.getIndemniteLicenciement());
        r.setAutresIndemnites(s.getAutresIndemnites());
        r.setTotalBrut(s.getTotalBrutStc());

        r.setCotisationsSociales(s.getTotalCotisationsSociales());
        r.setRetenueIrsa(s.getTotalIrsa());
        r.setNetImposable(s.getNetImposable());

        r.setSoldePretsApure(s.getSoldePretsDeduit());
        r.setSoldeAvancesApure(s.getSoldeAvancesDeduit());
        BigDecimal totalRetenues = s.getTotalCotisationsSociales().add(s.getTotalIrsa())
                .add(s.getSoldePretsDeduit()).add(s.getSoldeAvancesDeduit()).add(r.getAutresDeductions());

        // Réconciliation de l'arrondi à la dizaine d'Ariary pour le décompte imprimé.
        BigDecimal deltaArrondi = s.getNetAPayerStc().subtract(
                s.getTotalBrutStc().subtract(totalRetenues).add(zeroIfNull(r.getSoldeBulletinARegler())));
        if (deltaArrondi.compareTo(BigDecimal.ZERO) > 0) {
            r.setTotalBrut(s.getTotalBrutStc().add(deltaArrondi));
            r.setTotalRetenues(totalRetenues);
        } else if (deltaArrondi.compareTo(BigDecimal.ZERO) < 0) {
            r.setTotalBrut(s.getTotalBrutStc());
            r.setTotalRetenues(totalRetenues.add(deltaArrondi.abs()));
        } else {
            r.setTotalBrut(s.getTotalBrutStc());
            r.setTotalRetenues(totalRetenues);
        }

        r.setNetAPayer(s.getNetAPayerStc());
        r.setModeReglement(s.getModeReglement());
        r.setNetEnLettres(convertirEnLettres(s.getNetAPayerStc()));

        r.setMentionLiberatoire("Ce document détaille les sommes calculées à la cessation du contrat. "
                + "Le paiement doit être attesté séparément. En application du droit du travail malgache, "
                + "le reçu pour solde de tout compte ne peut être dénoncé ou contesté que dans un délai de deux (02) mois "
                + "suivant sa délivrance. La remise ou la signature de ce document "
                + "n'emporte aucune renonciation aux droits du travailleur.");

        return r;
    }

    public List<PaieStc> listerStc(Connection c) throws Exception {
        return PaieStc.lister(c);
    }

    public List<PaieStc> listerStcEmploye(Integer idEmploye, Connection c) throws Exception {
        if (idEmploye == null) return List.of();
        return PaieStc.listerParEmploye(idEmploye, c);
    }

    /**
     * Charge les tranches IRSA liées à la configLegale fournie.
     * Si configLegale est null, la date de référence est utilisée pour chercher
     * la LF active à cette date (et non CURRENT_DATE), garantissant la cohérence
     * lors des recalculs historiques (STC rétroactifs).
     */
    private List<Tranche> chargerTranchesIrsa(PaieConfigLegale configLegale, LocalDate dateRef, Connection c) throws Exception {
        if (dateRef == null) throw new IllegalArgumentException("La date métier IRSA est obligatoire.");
        return PayrollFiscalRulesResolver.resolveIrsaBrackets(configLegale != null ? configLegale.getIdConfigLegale() : null,
                dateRef, c);
    }

    private PaieConfigLegale chargerConfigLegale(Date dateDepart, Connection c) {
        if (dateDepart == null) throw new IllegalArgumentException("La date métier de la configuration légale est obligatoire.");
        try {
            PaieConfigLegale config = PaieConfigLegale.findActiveAt(c, dateDepart.toLocalDate());
            if (config == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.LEGAL_CONFIG_MISSING,
                        "Aucun barème réglementaire fiscal ou social (SME, CNaPS, IRSA) n'est configuré pour la date du " + dateDepart + ". "
                        + "Veuillez enregistrer la réglementation applicable dans les Paramètres de Paie > Réglementation Fiscale & Sociale (LEGAL_REVIEW_REQUIRED).");
            }
            return config;
        } catch (mg.galaxam.sirh.common.exception.SirhBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de résoudre la configuration légale au " + dateDepart + ".", e);
        }
    }

    private static final mg.galaxam.sirh.conge.application.CongeTauxApplicableService congeTauxApplicableService =
            new mg.galaxam.sirh.conge.application.CongeTauxApplicableService();

    public static BigDecimal getTauxJournalierApplicable(int idEmploye, LocalDate dateReference, BigDecimal salaireBaseFallback, Connection c) {
        if (dateReference == null) {
            throw new IllegalArgumentException("La date de référence temporelle est obligatoire pour résoudre TCONGE.");
        }
        return congeTauxApplicableService.resoudreTauxCongeApplicable(idEmploye, dateReference, salaireBaseFallback, c).getTauxJournalier();
    }

    public static BigDecimal getTauxJournalierDernier(int idEmploye, BigDecimal salaireBaseFallback, Connection c) {
        return getTauxJournalierApplicable(idEmploye, mg.galaxam.sirh.paie.domain.PayrollBusinessTime.today(), salaireBaseFallback, c);
    }

    private AncienneteEngine.AncienneteBilan calculerAncienneteEmploye(int idEmploye, Date dateDepart, Connection c) {
        if (dateDepart == null) throw new IllegalArgumentException("La date métier d'ancienneté est obligatoire.");
        LocalDate refDate = dateDepart.toLocalDate();
        return this.ancienneteEngine.calculerAncienneteEmploye(idEmploye, refDate, c);
    }

    record HealthContributionRate(String code, BigDecimal employeeRatePercent, BigDecimal employerRatePercent) {}

    HealthContributionRate resolveHealthContributionRate(String codeSite, String siteName,
            LocalDate effectiveDate, Connection connection) throws Exception {
        if (effectiveDate == null) throw new IllegalArgumentException("La date métier Santé du STC est obligatoire.");
        var rate = new SocialContributionContextResolver().healthRate(codeSite, effectiveDate, connection);
        return rate == null ? null : new HealthContributionRate(rate.code(),
                rate.employee().movePointRight(2), rate.employer().movePointRight(2));
    }

    private String convertirEnLettres(BigDecimal montant) {
        if (montant == null) return "Zéro Ariary";
        long val = montant.longValue();
        return val + " Ariary";
    }

    private String determinerCategorieRubrique(String code) {
        if (code == null) return "AUTRE";
        if (code.startsWith("SALAIRE_BASE") || code.contains("CONGE") || code.startsWith("RETENUE_ABSENCE")) return "SALAIRE_BASE";
        if (code.startsWith("HEURES_SUP") || code.startsWith("MAJORATION_")) return "HEURES_SUP";
        if (code.startsWith("INDEMNITE_RUPTURE") || code.equals("INDEMNITE_PREAVIS") || code.equals("INDEMNITE_LICENCIEMENT")) return "INDEMNITE_RUPTURE";
        if (code.startsWith("INDEMNITE_")) return "INDEMNITE";
        if (code.startsWith("COTISATION_") && code.endsWith("_PAT")) return "COTISATION_PATRONALE";
        if (code.startsWith("COTISATION_")) return "COTISATION_SALARIALE";
        if (code.startsWith("IMPOT_") || code.contains("IRSA")) return "IMPOT";
        if (code.startsWith("SOLDE_") || code.startsWith("AUTRES_RETENUES")) return "RETENUE_DIVERSE";
        if (code.contains("ARRONDI")) return "NET";
        return "AUTRE";
    }

    private Integer resoudreIdRubrique(String codeRubrique, Map<String, Integer> cache) {
        if (codeRubrique == null || cache == null) return null;
        Integer id = cache.get(codeRubrique);
        if (id != null) return id;

        if ("SALAIRE_BASE_PRORATA".equalsIgnoreCase(codeRubrique)) return cache.get("SALAIRE_BASE");
        if ("RETENUE_CONGE_ANTICIPE".equalsIgnoreCase(codeRubrique)) return cache.get("RETENUE_CONGE");
        if ("HEURES_SUP_30".equalsIgnoreCase(codeRubrique)) {
            Integer idHs = cache.get("HEURES_SUP_30");
            return idHs != null ? idHs : cache.get("HS30");
        }
        if ("HEURES_SUP_50".equalsIgnoreCase(codeRubrique)) {
            Integer idHs = cache.get("HEURES_SUP_50");
            return idHs != null ? idHs : cache.get("HS50");
        }
        if ("MAJORATION_NUIT".equalsIgnoreCase(codeRubrique)) {
            Integer idN = cache.get("MAJORATION_NUIT");
            return idN != null ? idN : cache.get("HS_NUIT");
        }
        if ("HEURES_DIMANCHE".equalsIgnoreCase(codeRubrique)) {
            Integer idD = cache.get("HEURES_DIMANCHE");
            return idD != null ? idD : cache.get("HS_DIM");
        }
        if ("MAJORATION_FERIE".equalsIgnoreCase(codeRubrique)) {
            Integer idF = cache.get("MAJORATION_FERIE");
            return idF != null ? idF : cache.get("HS_FERIE");
        }
        if ("INDEMNITE_REPAS".equalsIgnoreCase(codeRubrique)) return cache.get("REPAS");
        if ("INDEMNITE_DEPLACEMENT".equalsIgnoreCase(codeRubrique)) return cache.get("DPLCT");
        if ("COTISATION_SANTE_SAL".equalsIgnoreCase(codeRubrique)) {
            Integer idOstie = cache.get("COTISATION_OSTIE_SAL");
            return idOstie != null ? idOstie : cache.get("COTISATION_OMSI_SAL");
        }
        if ("COTISATION_SANTE_PAT".equalsIgnoreCase(codeRubrique)) {
            Integer idOstiePat = cache.get("COTISATION_OSTIE_PAT");
            return idOstiePat != null ? idOstiePat : cache.get("COTISATION_OMSI_PAT");
        }
        if ("SOLDE_PRET_STC".equalsIgnoreCase(codeRubrique)) return cache.get("RETENUE_ECHEANCE_PRET");
        if ("SOLDE_AVANCES_STC".equalsIgnoreCase(codeRubrique)) return cache.get("RETENUE_AVANCE_SPECIALE");
        if ("AUTRES_RETENUES_STC".equalsIgnoreCase(codeRubrique)) return cache.get("RETENUE_DIVERSE");
        if ("AUTRES_INDEMNITES_DEPART".equalsIgnoreCase(codeRubrique)) return cache.get("AUTIMP");

        return null;
    }

    private BigDecimal zeroIfNull(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }

    public record StcPointageVariables(
            BigDecimal heuresHs30,
            BigDecimal montantHs30,
            BigDecimal heuresHs50,
            BigDecimal montantHs50,
            BigDecimal heuresNuit,
            BigDecimal montantNuit,
            BigDecimal heuresDimanche,
            BigDecimal montantDimanche,
            BigDecimal heuresFerie,
            BigDecimal montantFerie,
            BigDecimal heuresAbsence,
            BigDecimal montantRetenueAbsence,
            BigDecimal montantRepas,
            BigDecimal montantDeplacement
    ) {
        public static StcPointageVariables zero() {
            return new StcPointageVariables(
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO
            );
        }

        public BigDecimal totalGainsVariables() {
            return montantHs30.add(montantHs50)
                    .add(montantNuit)
                    .add(montantDimanche)
                    .add(montantFerie)
                    .add(montantRepas)
                    .add(montantDeplacement);
        }
    }

    public StcPointageVariables calculerPointageVariables(
            int idEmploye,
            LocalDate dateDebut,
            LocalDate dateFin,
            BigDecimal tauxHoraire,
            Connection c
    ) throws SQLException {
        if (dateDebut == null || dateFin == null || dateFin.isBefore(dateDebut)
                || tauxHoraire == null || tauxHoraire.signum() <= 0) {
            return StcPointageVariables.zero();
        }

        // Le STC ne comptabilise les variables de pointage que si une saisie existe sur la période.
        try (PreparedStatement psCheck = c.prepareStatement(
                "SELECT 1 FROM pointage_jour WHERE id_employe = ? AND date_pointage BETWEEN ? AND ? LIMIT 1")) {
            psCheck.setInt(1, idEmploye);
            psCheck.setDate(2, Date.valueOf(dateDebut));
            psCheck.setDate(3, Date.valueOf(dateFin));
            try (ResultSet rsCheck = psCheck.executeQuery()) {
                if (!rsCheck.next()) return StcPointageVariables.zero();
            }
        }

        // Résolution de l'affectation et du statut cadre de l'employé
        String matricule = null;
        String nom = null;
        String prenom = null;
        String photoUrl = null;
        int idSite = 1;
        String siteCode = "SIEGE";
        String siteNom = "Siège";
        String codeCat = null;
        boolean isCadre = false;
        boolean estAutonome = false;
        LocalDate ldDebCar = null;
        LocalDate ldFinCar = null;

        String sqlCar = "SELECT a.id_site, s.code_site AS site_code, s.libelle AS site_nom, "
                + "e.matricule, e.nom, e.prenom, e.photo_url, "
                + "cc.code AS code_categorie, COALESCE(cc.est_cadre, FALSE) AS est_cadre, "
                + "FALSE AS est_autonome, "
                + "a.date_debut, a.date_fin "
                + "FROM employe e "
                + "LEFT JOIN employe_affectation_carriere a ON a.id_employe = e.id_employe "
                + "LEFT JOIN ref_site s ON a.id_site = s.id_site "
                + "LEFT JOIN ref_categorie_professionnelle cc ON a.id_categorie_professionnelle = cc.id_categorie_professionnelle "
                + "WHERE e.id_employe = ? "
                + "ORDER BY (a.date_fin IS NULL) DESC, a.date_debut DESC LIMIT 1";

        try (PreparedStatement psCar = c.prepareStatement(sqlCar)) {
            psCar.setInt(1, idEmploye);
            try (ResultSet rs = psCar.executeQuery()) {
                if (rs.next()) {
                    matricule = rs.getString("matricule");
                    nom = rs.getString("nom");
                    prenom = rs.getString("prenom");
                    photoUrl = rs.getString("photo_url");
                    if (rs.getObject("id_site") != null) idSite = rs.getInt("id_site");
                    if (rs.getString("site_code") != null) siteCode = rs.getString("site_code");
                    if (rs.getString("site_nom") != null) siteNom = rs.getString("site_nom");
                    codeCat = rs.getString("code_categorie");
                    isCadre = rs.getBoolean("est_cadre");
                    estAutonome = rs.getBoolean("est_autonome");
                    Date dbD = rs.getDate("date_debut");
                    Date dbF = rs.getDate("date_fin");
                    if (dbD != null) ldDebCar = dbD.toLocalDate();
                    if (dbF != null) ldFinCar = dbF.toLocalDate();
                }
            }
        }

        BigDecimal hHs30 = BigDecimal.ZERO;
        BigDecimal hHs50 = BigDecimal.ZERO;
        BigDecimal hNuit = BigDecimal.ZERO;
        BigDecimal hDimanche = BigDecimal.ZERO;
        BigDecimal hFerie = BigDecimal.ZERO;
        BigDecimal hAbsence = BigDecimal.ZERO;
        BigDecimal mRepas = BigDecimal.ZERO;
        BigDecimal mDeplacement = BigDecimal.ZERO;

        if (pointageSyntheseService != null) {
            // La synthèse est celle de la période de paie STC : résoudre à sa date
            // de début, comme le fait également PointageSyntheseService.
            Map<String, String> pMap = mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal
                    .loadActiveMapAtDate(Date.valueOf(dateDebut), c);

            try {
                PointageSyntheseMensuelleDTO synDto = pointageSyntheseService.calculerSyntheseEmploye(
                        idEmploye, matricule, nom, prenom, photoUrl,
                        idSite, siteCode, siteNom,
                        null, codeCat, isCadre, estAutonome,
                        dateDebut, dateFin, ldDebCar, ldFinCar,
                        null, null, "STC-PARTIEL", "CLOTURE",
                        0, null, pMap, c
                );
                if (synDto != null) {
                    if (synDto.getMinutesHs30Finales() != null && synDto.getMinutesHs30Finales() > 0) {
                        hHs30 = BigDecimal.valueOf(synDto.getMinutesHs30Finales())
                                .divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP);
                    } else if (synDto.getHeuresHs30() > 0) {
                        hHs30 = BigDecimal.valueOf(synDto.getHeuresHs30()).setScale(2, RoundingMode.HALF_UP);
                    }

                    if (synDto.getMinutesHs50Finales() != null && synDto.getMinutesHs50Finales() > 0) {
                        hHs50 = BigDecimal.valueOf(synDto.getMinutesHs50Finales())
                                .divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP);
                    } else if (synDto.getHeuresHs50() > 0) {
                        hHs50 = BigDecimal.valueOf(synDto.getHeuresHs50()).setScale(2, RoundingMode.HALF_UP);
                    }

                    if (synDto.getHeuresNuitTotal() > 0) {
                        hNuit = BigDecimal.valueOf(synDto.getHeuresNuitTotal()).setScale(2, RoundingMode.HALF_UP);
                    }
                    if (synDto.getHeuresDimancheTotal() > 0) {
                        hDimanche = BigDecimal.valueOf(synDto.getHeuresDimancheTotal()).setScale(2, RoundingMode.HALF_UP);
                    }
                    if (synDto.getHeuresFerieTotal() > 0) {
                        hFerie = BigDecimal.valueOf(synDto.getHeuresFerieTotal()).setScale(2, RoundingMode.HALF_UP);
                    }
                    if (synDto.getMinutesAbsenceFinale() != null && synDto.getMinutesAbsenceFinale() > 0) {
                        hAbsence = BigDecimal.valueOf(synDto.getMinutesAbsenceFinale())
                                .divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP);
                    } else if (synDto.getHeuresAbsenceRetenues() > 0) {
                        hAbsence = BigDecimal.valueOf(synDto.getHeuresAbsenceRetenues()).setScale(2, RoundingMode.HALF_UP);
                    }

                    mRepas = zeroIfNull(synDto.getMontantRepas());
                    mDeplacement = zeroIfNull(synDto.getMontantDeplacement());
                }
            } catch (Exception ex) {
                log.warn("[StcService] Erreur appel syntheseService, fallback direct pointage_jour : {}", ex.getMessage());
            }
        }

        // Si syntheseService n'a pas alimenté les volumes (ou en fallback direct)
        if (hHs30.signum() == 0 && hHs50.signum() == 0 && hNuit.signum() == 0 && hDimanche.signum() == 0
                && hFerie.signum() == 0 && hAbsence.signum() == 0 && mRepas.signum() == 0 && mDeplacement.signum() == 0) {
            String sqlPtgDirect = "SELECT p.heures_nuit, p.minutes_retard, p.total_heures, "
                    + "s.code AS statut_code, s.decompte_absence, "
                    + "EXTRACT(ISODOW FROM p.date_pointage) AS dow, "
                    + "pij_repas.montant_calcule AS repas_mnt, "
                    + "pij_dep.montant_calcule AS dep_mnt "
                    + "FROM pointage_jour p "
                    + "JOIN ref_statut_pointage_jour s ON p.id_statut_jour = s.id_statut_jour "
                    + "LEFT JOIN pointage_indemnite_jour pij_repas ON p.id_pointage = pij_repas.id_pointage "
                    + "    AND pij_repas.id_indemnite = (SELECT id_indemnite FROM ref_indemnite WHERE code = 'REPAS' LIMIT 1) "
                    + "LEFT JOIN pointage_indemnite_jour pij_dep ON p.id_pointage = pij_dep.id_pointage "
                    + "    AND pij_dep.id_indemnite IN (SELECT id_indemnite FROM ref_indemnite WHERE code IN ('DPLCT', 'DEPLACEMENT', 'TRANSPORT') LIMIT 1) "
                    + "WHERE p.id_employe = ? AND p.date_pointage BETWEEN ? AND ?";

            try (PreparedStatement psDirect = c.prepareStatement(sqlPtgDirect)) {
                psDirect.setInt(1, idEmploye);
                psDirect.setDate(2, Date.valueOf(dateDebut));
                psDirect.setDate(3, Date.valueOf(dateFin));
                try (ResultSet rs = psDirect.executeQuery()) {
                    while (rs.next()) {
                        BigDecimal hn = rs.getBigDecimal("heures_nuit");
                        if (hn != null && hn.signum() > 0) hNuit = hNuit.add(hn);

                        int dow = rs.getInt("dow");
                        if (dow == 7) {
                            BigDecimal th = rs.getBigDecimal("total_heures");
                            if (th != null && th.signum() > 0) hDimanche = hDimanche.add(th);
                        }

                        int ret = rs.getInt("minutes_retard");
                        boolean decompte = rs.getBoolean("decompte_absence");
                        String sc = rs.getString("statut_code");
                        int minsAbs = ret;
                        if (decompte || (sc != null && sc.contains("ABS"))) {
                            minsAbs += 480; // 8 heures = 480 minutes
                        }
                        if (minsAbs > 0) {
                            hAbsence = hAbsence.add(BigDecimal.valueOf(minsAbs).divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP));
                        }

                        BigDecimal mr = rs.getBigDecimal("repas_mnt");
                        if (mr != null) mRepas = mRepas.add(mr);
                        BigDecimal md = rs.getBigDecimal("dep_mnt");
                        if (md != null) mDeplacement = mDeplacement.add(md);
                    }
                }
            }
        }

        // Résolution des taux canoniques applicables (LF Madagascar / ref_taux_majoration)
        BigDecimal rateHs30 = new BigDecimal("0.30");
        BigDecimal rateHs50 = new BigDecimal("0.50");
        BigDecimal rateNuit = new BigDecimal("0.30");
        BigDecimal rateDimanche = new BigDecimal("0.40");
        BigDecimal rateFerie = new BigDecimal("1.00");

        try (PreparedStatement psTaux = c.prepareStatement(
                "SELECT type_majoration, taux FROM ref_taux_majoration "
                + "WHERE (date_debut IS NULL OR date_debut <= ?) "
                + "AND (date_fin IS NULL OR date_fin >= ?)")) {
            psTaux.setDate(1, Date.valueOf(dateFin));
            psTaux.setDate(2, Date.valueOf(dateFin));
            try (ResultSet rs = psTaux.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("type_majoration");
                    BigDecimal tx = rs.getBigDecimal("taux");
                    if (code != null && tx != null) {
                        switch (code.toUpperCase()) {
                            case "HS_30", "HS30" -> rateHs30 = tx;
                            case "HS_50", "HS50" -> rateHs50 = tx;
                            case "NUIT", "HS_NUIT" -> rateNuit = tx;
                            case "DIMANCHE", "DIM", "HS_DIM" -> rateDimanche = tx;
                            case "FERIE", "HS_FERIE" -> rateFerie = tx;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // Utilisation canonique de OvertimePayCalculator avec politique CUMUL_ADDITIF (règles Décret 72-226)
        PayrollCalculationInput input = new PayrollCalculationInput();
        input.setIdEmploye(idEmploye);
        input.setHeuresHs30(hHs30);
        input.setHeuresHs50(hHs50);
        input.setHeuresNuit(hNuit);
        input.setHeuresDimanche(hDimanche);
        input.setHeuresFeriees(hFerie);
        input.setMajorationHs30(rateHs30);
        input.setMajorationHs50(rateHs50);
        input.setMajorationNuit(rateNuit);
        input.setMajorationDimanche(rateDimanche);
        input.setMajorationFerie(rateFerie);
        input.setSpecialPremiumCollisionPolicy(SpecialPremiumCollisionPolicy.CUMUL_ADDITIF);

        OvertimePayCalculator.Result otRes = overtimePayCalculator.calculate(input, tauxHoraire);

        // Retenue absence selon le taux horaire contractuel
        BigDecimal montantRetenueAbsence = MoneyRoundingPolicy.amount(hAbsence.multiply(tauxHoraire));

        return new StcPointageVariables(
                hHs30, otRes.hs30(),
                hHs50, otRes.hs50(),
                hNuit, otRes.nuit(),
                hDimanche, otRes.dimanche(),
                hFerie, otRes.ferie(),
                hAbsence, montantRetenueAbsence,
                MoneyRoundingPolicy.amount(mRepas),
                MoneyRoundingPolicy.amount(mDeplacement)
        );
    }
}
