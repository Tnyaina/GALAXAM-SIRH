package mg.galaxam.sirh.paie.application;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.persistence.VSoldeCongeEmploye;
import mg.galaxam.sirh.contrat.domain.AncienneteEngine;
import mg.galaxam.sirh.contrat.persistence.EmployeEnfant;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import mg.galaxam.sirh.paie.persistence.PaiePret;
import mg.galaxam.sirh.paie.persistence.PaieStc;
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
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.stereotype.Service;

/**
 * Service Métier pour le traitement, la simulation, la validation et l'émission
 * du solde de tout compte et de son reçu détaillé.
 */
@Service
public class StcService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StcService.class);

    private final AncienneteEngine ancienneteEngine;
    private final mg.galaxam.sirh.conge.application.CongeLotValorisationService lotsConges =
            new mg.galaxam.sirh.conge.application.CongeLotValorisationService();

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
        prep.setRegimeFiscal(emploi.soumisIsi5() ? "ISI" : "IRSA");
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
        String sqlPer = "SELECT id_periode_paie, code FROM periode_paie " +
                "WHERE ? BETWEEN date_debut AND date_fin ORDER BY date_debut DESC, id_periode_paie DESC LIMIT 2";
        try (PreparedStatement psPer = c.prepareStatement(sqlPer)) {
            psPer.setDate(1, dateDepartEffective);
            try (ResultSet rsPer = psPer.executeQuery()) {
                if (rsPer.next()) {
                    prep.setIdPeriodePaie(rsPer.getInt("id_periode_paie"));
                    prep.setCodePeriodePaie(rsPer.getString("code"));
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

        // 4. Estimation du salaire au prorata du dernier mois
        LocalDate dDep = dateDepartEffective.toLocalDate();
        int jourDuMois = Math.min(dDep.getDayOfMonth(), 30);
        BigDecimal joursTravaillesEstimes = new BigDecimal(String.valueOf(jourDuMois));
        prep.setJoursTravaillesEstimes(joursTravaillesEstimes);
        BigDecimal prorataEstime = salaireBase.multiply(joursTravaillesEstimes)
                .divide(new BigDecimal("30.0"), 2, RoundingMode.HALF_UP);
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

        // Le référentiel actuel ne distingue pas les cinq groupes du décret 2007-009.
        // Aucune indemnité de préavis ou de licenciement ne peut être déduite de "CADRE/A".
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

        return prep;
    }

    // =========================================================================
    // 2. SIMULATION DU CALCUL DU STC
    // =========================================================================

    public StcCalculResultDTO simulerStc(StcCalculRequest req, Connection c) throws Exception {
        verifierMontantsNonNegatifs(req);
        StcPreparationDTO prep = preparerStc(req.getIdEmploye(), req.getDateDepart(), req.getMotifRupture(), c);

        StcCalculResultDTO res = new StcCalculResultDTO();
        res.setIdEmploye(prep.getIdEmploye());
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

        // 1. Salaire prorata dernier mois
        BigDecimal joursTravailles = req.getJoursTravaillesDernierMois() != null
                ? req.getJoursTravaillesDernierMois()
                : prep.getJoursTravaillesEstimes();
        res.setJoursTravaillesDernierMois(joursTravailles);

        BigDecimal salaireProrata = prep.getSalaireBase().multiply(joursTravailles)
                .divide(new BigDecimal("30.0"), 2, RoundingMode.HALF_UP);
        res.setSalaireProrata(salaireProrata);
        res.ajouterLigne("SALAIRE_BASE_PRORATA", "Salaire au prorata du dernier mois (" + joursTravailles + "j/30)",
                "GAIN", prep.getSalaireBase(), null, salaireProrata);

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
        BigDecimal totalBrut = salaireProrata.add(gainConge).add(indemnitePreavis).add(indemniteLicenciement).add(autresIndemnites);
        res.setTotalBrutStc(totalBrut);

        // 4. Cotisations sociales et assiette fiscale. Aucune exonération IRSA
        // générale de l'indemnité de licenciement n'est prévue par le CGI :
        // elle entre donc dans l'assiette fiscale. Son assiette sociale reste
        // distincte de celle des salaires et indemnités compensatrices.
        BigDecimal assietteBruteSoumise = salaireProrata.add(gainConge).add(autresIndemnites).max(BigDecimal.ZERO);
        BigDecimal assietteSociale = assietteBruteSoumise.add(indemnitePreavis);
        BigDecimal assietteFiscaleBrute = assietteSociale.add(indemniteLicenciement);

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
        SocialContributionCalculator.Result socialResult = new SocialContributionCalculator().calculate(
                assietteSociale, new SocialContributionCalculator.Rules(tauxCnapsSal, tauxCnapsPat,
                        BigDecimal.ZERO, BigDecimal.ZERO, tauxFmfp, plafondCotisation, true));
        var resCnaps = socialResult.cnaps();
        BigDecimal baseCnaps = resCnaps.assiette();
        BigDecimal cnapsSal = resCnaps.partSalariale();
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

        var resSante = new SocialContributionCalculator().calculate(assietteSociale,
                new SocialContributionCalculator.Rules(tauxCnapsSal, tauxCnapsPat,
                        tauxSanteSal, tauxSantePat, tauxFmfp, plafondCotisation, true)).health();
        BigDecimal santeSal = resSante.partSalariale();
        res.setCotisationSante(santeSal);
        if (santeSal.compareTo(BigDecimal.ZERO) > 0) {
            String libelleSante = "Cotisation " + codeSante + " Salariale (" + tauxSanteSal.movePointRight(2) + "%)";
            res.ajouterLigne("COTISATION_SANTE_SAL", libelleSante, "RETENUE", resSante.assiette(), tauxSanteSal.movePointRight(2), santeSal);
        }

        BigDecimal totalCotis = cnapsSal.add(santeSal);
        res.setTotalCotisationsSociales(totalCotis);

        // Cotisations patronales (CNaPS 13%, Santé patronale, FMFP 1%)
        BigDecimal cnapsPat = resCnaps.partPatronale();
        BigDecimal santePat = resSante.partPatronale();
        BigDecimal fmfpPat = MoneyRoundingPolicy.contribution(socialResult.fmfp());
        BigDecimal totalCotisPat = cnapsPat.add(santePat).add(fmfpPat);
        res.setTotalCotisationsPatronales(totalCotisPat);

        if (cnapsPat.compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("COTISATION_CNAPS_PAT", "Cotisation CNaPS Patronale (" + tauxCnapsPat.movePointRight(2).stripTrailingZeros().toPlainString() + "%)", "PATRONAL", baseCnaps, tauxCnapsPat.movePointRight(2), cnapsPat);
        }
        if (santePat.compareTo(BigDecimal.ZERO) > 0) {
            String libelleSantePat = "Cotisation " + codeSante + " Patronale (" + tauxSantePat.movePointRight(2) + "%)";
            res.ajouterLigne("COTISATION_SANTE_PAT", libelleSantePat, "PATRONAL", resSante.assiette(), tauxSantePat.movePointRight(2), santePat);
        }
        if (fmfpPat.compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("COTISATION_FMFP_PAT", "Cotisation FMFP Patronale (" + tauxFmfp.movePointRight(2).stripTrailingZeros().toPlainString() + "%)", "PATRONAL", assietteSociale, tauxFmfp.movePointRight(2), fmfpPat);
        }

        // Assiette IRSA
        BigDecimal netImposable = assietteFiscaleBrute.subtract(totalCotis).max(BigDecimal.ZERO);
        res.setNetImposable(netImposable);

        BigDecimal irsaNet = BigDecimal.ZERO;
        // Régime fiscal résolu une seule fois dans la préparation STC.
        if ("ISI".equalsIgnoreCase(prep.getRegimeFiscal())) {
            BigDecimal tauxIsi = PayrollFiscalRulesResolver.resolveIsiRate(req.getDateDepart().toLocalDate(), c);
            BigDecimal isi = new PayrollTaxCalculations().calculateIsi(assietteFiscaleBrute, tauxIsi);
            irsaNet = isi;
            res.setIrsaNet(BigDecimal.ZERO);
            res.ajouterLigne("RETENUE_ISI_5", "Retenue ISI", "RETENUE", assietteFiscaleBrute, tauxIsi, isi);
        }

        // Calcul IRSA selon barème progressif dynamique unifié
        if (!"ISI".equalsIgnoreCase(prep.getRegimeFiscal())) {
        LocalDate dateRefIrsa = req.getDateDepart().toLocalDate();
        List<Tranche> tranches = chargerTranchesIrsa(configLegale, dateRefIrsa, c);
        int nbEnfants = prep.getNbEnfantsACharge();
        if (configLegale == null || configLegale.getDeductionChargeFamille() == null
                || configLegale.getMinimumPerceptionIrsa() == null) {
            throw new IllegalStateException("IRSA_CONFIGURATION_MISSING");
        }
        BigDecimal deducParEnfant = configLegale.getDeductionChargeFamille();
        BigDecimal minPercep = configLegale.getMinimumPerceptionIrsa();

        var irsaRes = new PayrollTaxCalculations().calculateIrsa(
                netImposable, tranches, nbEnfants, deducParEnfant, minPercep
        );
        irsaNet = MoneyRoundingPolicy.tax(irsaRes.irsaNet().min(netImposable));
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

        // 5. Seuls les acomptes déjà versés sont imputés sur ce calcul.
        // Un prêt reste une créance distincte : aucune compensation automatique sur le salaire.
        res.setSoldePretsDeduit(BigDecimal.ZERO);

        // Imputation des acomptes déjà versés.
        BigDecimal soldeAvances = calculerAcomptesVersesNonImputes(req.getIdEmploye(), c);
        BigDecimal avancesADeduire = MoneyRoundingPolicy.amount(soldeAvances.min(netAvantEngagements));
        res.setSoldeAvancesDeduit(avancesADeduire);
        if (avancesADeduire.compareTo(BigDecimal.ZERO) > 0) {
            res.ajouterLigne("SOLDE_AVANCES_STC", "Imputation des acomptes déjà versés", "RETENUE", soldeAvances, null, avancesADeduire);
        }

        BigDecimal netApresAvances = netAvantEngagements.subtract(avancesADeduire).max(BigDecimal.ZERO);

        // Les retenues diverses ne sont plus acceptées par ce moteur.
        BigDecimal autresADeduire = BigDecimal.ZERO;
        res.setAutresDeductions(autresADeduire);
        res.setMotifAutresDeductions(null);

        // 6. Net Final à Payer (arrondi bancaire officiel Ariary à la dizaine la plus proche)
        BigDecimal netFinalNonArrondi = netApresAvances.subtract(autresADeduire).max(BigDecimal.ZERO);
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
        verifierValidationJuridique(req, c);
        if (req.getDateDepart() != null
                && req.getDateDepart().toLocalDate().isAfter(LocalDate.now(ZoneId.of("Indian/Antananarivo")))) {
            throw new IllegalArgumentException(
                    "La validation officielle du STC est possible à partir de la date de départ effective. " +
                    "La simulation reste disponible avant cette date.");
        }
        req.setSimulation(false);
        StcCalculResultDTO simulation = simulerStc(req, c);

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

        // 5. Désactivation optionnelle de l'employé et clôture carrière
        // Note d'architecture (Règle Métier SIRH ACT-018 / ACT-025) :
        // La désactivation administrative standard de l'employé incombe au Workflow de Rupture RH (WorkflowRhService).
        // Si aucun workflow formel n'a été exécuté en amont et que le gestionnaire a requis `desactiverEmploye = true`,
        // le statut de l'employé, son contrat et son affectation carrière active sont clos pour éviter les salariés "fantômes".
        if (req.isDesactiverEmploye()) {
            try (PreparedStatement psEmp = c.prepareStatement(
                    "UPDATE employe SET est_actif = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id_employe = ?")) {
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

    private void verifierValidationJuridique(StcCalculRequest req, Connection c) throws Exception {
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
            StcPreparationDTO prep = preparerStc(req.getIdEmploye(), req.getDateDepart(), motif, c);
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

    public void enregistrerReglement(Integer idStc, StcReglementRequest req, Integer userId, Connection c) throws Exception {
        if (req == null || req.dateReglement() == null || req.referenceReglement() == null
                || req.referenceReglement().isBlank()) {
            throw new IllegalArgumentException("La date et la référence du paiement sont obligatoires.");
        }
        if (req.dateReglement().toLocalDate().isAfter(LocalDate.now(ZoneId.of("Indian/Antananarivo")))) {
            throw new IllegalArgumentException("Le règlement ne peut pas être daté dans le futur.");
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE paie_stc SET statut = 'REGLE', date_reglement = ?, reference_reglement = ?, "
                + "regle_par = ?, updated_at = CURRENT_TIMESTAMP WHERE id_stc = ? AND statut = 'VALIDE' "
                + "AND date_depart <= ?")) {
            ps.setDate(1, req.dateReglement());
            ps.setString(2, req.referenceReglement().trim());
            ps.setInt(3, userId);
            ps.setInt(4, idStc);
            ps.setDate(5, req.dateReglement());
            if (ps.executeUpdate() != 1) {
                throw new IllegalStateException("STC absent, déjà réglé, annulé ou date de règlement antérieure au départ.");
            }
        }
        AuditLog.audit(c, "REGLEMENT_STC", "paie_stc", String.valueOf(idStc), null,
                req.referenceReglement().trim());
    }

    public StcRecuDTO obtenirRecuStc(Integer idStc, Connection c) throws Exception {
        PaieStc s = PaieStc.trouverParId(idStc, c);
        if (s == null) return null;

        StcPreparationDTO prep = preparerStc(s.getIdEmploye(), s.getDateDepart(), s.getMotifRupture(), c);

        StcRecuDTO r = new StcRecuDTO();
        r.setNumeroRecu(s.getNumeroRecu());
        r.setDateEmission(s.getDateValidation() != null ? s.getDateValidation() : new Timestamp(System.currentTimeMillis()));
        r.setStatut(s.getStatut());
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

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT raison_sociale, nif, stat FROM ref_filiale WHERE est_actif = TRUE ORDER BY id_filiale ASC LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    if (rs.getString("raison_sociale") != null) r.setEntrepriseNom(rs.getString("raison_sociale"));
                    if (rs.getString("nif") != null) r.setEntrepriseNif(rs.getString("nif"));
                    if (rs.getString("stat") != null) r.setEntrepriseStat(rs.getString("stat"));
                }
            }
        } catch (Exception ignored) {}

        r.setIdEmploye(s.getIdEmploye());
        r.setMatricule(prep.getMatricule());
        r.setNomComplet(prep.getNomComplet());
        r.setCinNumero(prep.getCinNumero());
        r.setCinDateDelivrance(prep.getCinDateDelivrance());
        r.setCinLieuDelivrance(prep.getCinLieuDelivrance());
        r.setNumeroCnaps(prep.getNumeroCnaps());
        r.setIntitulePoste(prep.getIntitulePoste());
        r.setDepartement(prep.getDepartement());
        r.setSite(prep.getSite());
        r.setDateEmbauche(prep.getDateEmbauche());
        r.setDateDepart(s.getDateDepart());
        r.setMotifRupture(s.getMotifRupture());
        r.setAncienneteTexte(prep.getAncienneteAnnees() + " an(s) et " + (prep.getAncienneteMois() % 12) + " mois");

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
        BigDecimal deltaArrondi = s.getNetAPayerStc().subtract(s.getTotalBrutStc().subtract(totalRetenues));
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
                + "Le paiement doit être attesté séparément. La remise ou la signature de ce document "
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
        return getTauxJournalierApplicable(idEmploye, LocalDate.now(), salaireBaseFallback, c);
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
        if (code.startsWith("SALAIRE_BASE") || code.contains("CONGE")) return "SALAIRE_BASE";
        if (code.startsWith("INDEMNITE_")) return "INDEMNITE_RUPTURE";
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
}
