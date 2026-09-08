package mg.galaxam.sirh.model.paie.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.BulletinPaieLigne;
import mg.galaxam.sirh.model.paie.PaieAvanceAcompte;
import mg.galaxam.sirh.model.paie.PaiePretEcheance;
import mg.galaxam.sirh.model.paie.dto.BulletinPaieComplet;
import mg.galaxam.sirh.model.paie.dto.BulletinPaieComplet.ImpactPret;
import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine.CotisationResult;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine.TrancheIrsa;

/**
 * Moteur de calcul pur et déterministe de paie pour GALAXAM-SIRH.
 * 
 * Conforme au Code du Travail malgache, au Code Général des Impôts (LF 2026),
 * et aux résolutions du contre-audit.
 */
@Component
public class PayrollExecutionEngine {

    public static final BigDecimal PLAFOND_QUOTITE_CESSIBLE = new BigDecimal("0.33333333"); // 1/3 légal (Saisies-arrêts et prêts Art. 72)
    public static final BigDecimal PLAFOND_AVANCE_EMPLOYEUR_DIXIEME = new BigDecimal("0.10"); // 1/10 légal (Avances consenties par l'employeur Art. 68)
    public static final BigDecimal DIVISEUR_HEURES_MENSUELLES = new BigDecimal("173.33");
    public static final BigDecimal DIVISEUR_JOURS_TRENTIEME = new BigDecimal("30.0");
    public static final BigDecimal MULTIPLICATEUR_PLAFOND_CNAPS = new BigDecimal("8.0");

    private final CalculateurPaieEngine calculateurPaieEngine;

    public PayrollExecutionEngine() {
        this(new CalculateurPaieEngine());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PayrollExecutionEngine(CalculateurPaieEngine calculateurPaieEngine) {
        this.calculateurPaieEngine = calculateurPaieEngine != null ? calculateurPaieEngine : new CalculateurPaieEngine();
    }

    /**
     * Calcule un bulletin de paie complet à partir des données d'entrée fournies.
     */
    public BulletinPaieComplet calculer(PayrollCalculationInput input) {
        if (input == null) {
            throw new IllegalArgumentException("Les données d'entrée de calcul (PayrollCalculationInput) sont obligatoires.");
        }

        BulletinPaie bp = new BulletinPaie();
        List<BulletinPaieLigne> lignes = new ArrayList<>();

        // 1. Initialisation de l'en-tête à partir du snapshot employé / contrat
        bp.setIdPeriodePaie(input.getIdPeriodePaie());
        bp.setIdEmploye(input.getIdEmploye());
        bp.setReferenceBulletin(genererReferenceBulletin(input));
        bp.setMatricule(input.getMatricule());
        bp.setNomPrenom(input.getNomPrenom());
        bp.setCinNumero(input.getCinNumero());
        bp.setCinDateDelivrance(input.getCinDateDelivrance());
        bp.setCinLieuDelivrance(input.getCinLieuDelivrance());
        bp.setNumeroCnaps(input.getNumeroCnaps());
        bp.setIntitulePoste(input.getIntitulePoste());
        bp.setNomDepartement(input.getNomDepartement());
        bp.setNomSite(input.getNomSite());
        bp.setRegimeIntervenant(input.getRegimeIntervenant());
        bp.setCategorieProfessionnelle(input.getCategorieProfessionnelle());
        bp.setModeCalculPaie(input.getModeCalculPaie());
        bp.setDateEmbauche(input.getDateEmbauche());
        bp.setAncienneteMois(input.getAncienneteMois());
        bp.setNbEnfantsCharge(input.getNbEnfantsEligibles());
        bp.setModePaiement(input.getModePaiement());
        bp.setBanqueNom(input.getBanqueNom());
        bp.setBanqueRib(input.getBanqueRib());

        // Compteurs de temps
        bp.setHeuresNormales(zeroIfNull(input.getHeuresNormales()));
        bp.setHeuresHs30(zeroIfNull(input.getHeuresHs30()));
        bp.setHeuresHs50(zeroIfNull(input.getHeuresHs50()));
        bp.setHeuresNuit(zeroIfNull(input.getHeuresNuit()));
        bp.setJoursOuvrables(zeroIfNull(input.getNbJoursOuvrables()));
        bp.setJoursTravailles(zeroIfNull(input.getNbJoursPresents()));
        bp.setJoursAbsence(zeroIfNull(input.getNbJoursAbsence()));
        bp.setJoursConges(zeroIfNull(input.getNbJoursConges()));
        bp.setMinutesRetard(input.getMinutesRetard());

        // 2. Salaire de base et taux horaire
        BigDecimal salaireBaseContractuel = zeroIfNull(input.getSalaireBaseContractuel());
        BigDecimal tauxHoraire = input.getTauxHoraireEffectif();
        bp.setSalaireBase(salaireBaseContractuel);
        bp.setTauxHoraire(tauxHoraire);

        // ----------------------------------------------------------------------
        // ÉTAPE 1 : RÉMUNÉRATION BRUTE & GAINS
        // ----------------------------------------------------------------------
        BigDecimal totalGainsBruts = BigDecimal.ZERO;

        // 1.1 Salaire de base (avec prorata si entrée/sortie en cours de mois)
        BigDecimal salaireBaseLigne = salaireBaseContractuel;
        if (input.isEntreeSortieEnCoursDeMois()) {
            BigDecimal heuresReelles = zeroIfNull(input.getHeuresNormales());
            salaireBaseLigne = tauxHoraire.multiply(heuresReelles).min(salaireBaseContractuel).setScale(2, RoundingMode.HALF_UP);
        }

        lignes.add(creerLigne(bp, "SALAIRE_BASE", "Salaire de Base", "GAIN", "SALAIRE_BASE",
                salaireBaseContractuel, null, salaireBaseLigne, null, 10));
        totalGainsBruts = totalGainsBruts.add(salaireBaseLigne);

        // 1.2 Retenue sur salaire pour absences (Méthode légale des heures réelles vs trentième)
        BigDecimal nbJoursAbsence = zeroIfNull(input.getNbJoursAbsence());
        BigDecimal nbHeuresAbsence = zeroIfNull(input.getNbHeuresAbsence());
        BigDecimal nbJoursOuvrables = zeroIfNull(input.getNbJoursOuvrables());
        BigDecimal montantAbsence = BigDecimal.ZERO;

        boolean aAbsence = nbHeuresAbsence.compareTo(BigDecimal.ZERO) > 0 || nbJoursAbsence.compareTo(BigDecimal.ZERO) > 0;
        if (aAbsence) {
            String modeAbsence = input.getModeCalculAbsence() != null ? input.getModeCalculAbsence() : "HEURES_REELLES";
            BigDecimal baseCalculLigne;
            String libelleAbsence;

            if ("TRENTIEME".equalsIgnoreCase(modeAbsence)) {
                BigDecimal tauxJournalierTrentieme = input.getTauxJournalierTrentieme();
                montantAbsence = nbJoursAbsence.multiply(tauxJournalierTrentieme).setScale(2, RoundingMode.HALF_UP);
                baseCalculLigne = tauxJournalierTrentieme;
                libelleAbsence = "Retenue pour Absences Décomptées (1/30ème)";
            } else {
                // Méthode légale des heures réelles (opposable Inspection du Travail)
                BigDecimal heuresJourRef = input.getHeuresJournalieresLegales() != null && input.getHeuresJournalieresLegales().compareTo(BigDecimal.ZERO) > 0
                        ? input.getHeuresJournalieresLegales()
                        : new BigDecimal("8.00");
                BigDecimal heuresACompter = nbHeuresAbsence.compareTo(BigDecimal.ZERO) > 0
                        ? nbHeuresAbsence
                        : nbJoursAbsence.multiply(heuresJourRef);
                montantAbsence = heuresACompter.multiply(tauxHoraire).setScale(2, RoundingMode.HALF_UP);
                baseCalculLigne = tauxHoraire;
                libelleAbsence = "Retenue pour Absences (" + heuresACompter + "h réelles)";
            }

            // Garde-fou 1 : Si absence totale sur le mois, la retenue absorbe tout le salaire de base
            if (nbJoursOuvrables.compareTo(BigDecimal.ZERO) > 0 && nbJoursAbsence.compareTo(nbJoursOuvrables) >= 0) {
                montantAbsence = salaireBaseLigne;
            } else {
                // Garde-fou 2 : Ne peut jamais dépasser le salaire de base
                montantAbsence = montantAbsence.min(salaireBaseLigne);
            }

            lignes.add(creerLigne(bp, "RETENUE_ABSENCE", libelleAbsence, "RETENUE", "SALAIRE_BASE",
                    baseCalculLigne, null, montantAbsence, null, 20));

            totalGainsBruts = totalGainsBruts.subtract(montantAbsence);
        }

        // 1.3 Gestion transparente et rigoureuse des congés payés (Art. 89 & Modèle GALAXAM)
        // En salaire mensualisé (30 jours), les jours pris en congé sont déduits de la base
        // (RETENUE_CONGE au trentième) et réintégrés via l'indemnité légale (INDEMNITE_CONGE à TCONGE).
        // Si TCONGE == Base/30, l'opération est neutre (maintien pur à 100%).
        // Si TCONGE != Base/30, la valorisation légale moyenne est rigoureusement appliquée sans double paiement.
        BigDecimal nbJoursConges = zeroIfNull(input.getNbJoursConges());
        if (nbJoursConges.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal tauxTrentieme = input.getTauxJournalierTrentieme();
            BigDecimal montantDeductionConge = nbJoursConges.multiply(tauxTrentieme).setScale(2, RoundingMode.HALF_UP);
            lignes.add(creerLigne(bp, "RETENUE_CONGE", "Retenue sur Salaire pour Congés Pris (1/30ème)", "RETENUE", "SALAIRE_BASE",
                    tauxTrentieme, null, montantDeductionConge, null, 25));
            totalGainsBruts = totalGainsBruts.subtract(montantDeductionConge);

            BigDecimal tauxEffectifConge = input.getTauxJournalierCongeEffectif();
            BigDecimal montantIndemniteConge = nbJoursConges.multiply(tauxEffectifConge).setScale(2, RoundingMode.HALF_UP);
            lignes.add(creerLigne(bp, "INDEMNITE_CONGE", "Indemnité de Congés Payés (Valorisation)", "GAIN", "SALAIRE_BASE",
                    tauxEffectifConge, null, montantIndemniteConge, null, 30));
            totalGainsBruts = totalGainsBruts.add(montantIndemniteConge);
        }

        // 1.4 Heures supplémentaires et majorations
        // Seuls les cadres dirigeants ou collaborateurs sous convention forfait-jours sont dispensés d'heures supplémentaires
        boolean estCadreExempteHs = "CADRE_DIRIGEANT".equalsIgnoreCase(input.getCategorieProfessionnelle())
                || "FORFAIT_JOUR".equalsIgnoreCase(input.getCategorieProfessionnelle());

        if (!estCadreExempteHs) {
            BigDecimal hs30 = zeroIfNull(input.getHeuresHs30());
            if (hs30.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal mntHs30 = hs30.multiply(tauxHoraire).multiply(new BigDecimal("1.30")).setScale(2, RoundingMode.HALF_UP);
                lignes.add(creerLigne(bp, "HEURES_SUP_30", "Heures Supplémentaires (+30%)", "GAIN", "HEURES_SUP",
                        tauxHoraire, new BigDecimal("130.00"), mntHs30, null, 40));
                totalGainsBruts = totalGainsBruts.add(mntHs30);
            }

            BigDecimal hs50 = zeroIfNull(input.getHeuresHs50());
            if (hs50.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal mntHs50 = hs50.multiply(tauxHoraire).multiply(new BigDecimal("1.50")).setScale(2, RoundingMode.HALF_UP);
                lignes.add(creerLigne(bp, "HEURES_SUP_50", "Heures Supplémentaires (+50%)", "GAIN", "HEURES_SUP",
                        tauxHoraire, new BigDecimal("150.00"), mntHs50, null, 50));
                totalGainsBruts = totalGainsBruts.add(mntHs50);
            }
        }

        // Le travail de nuit et les jours fériés sont des compensations de sujétion légale dues à tous les collaborateurs
        BigDecimal hNuit = zeroIfNull(input.getHeuresNuit());
        if (hNuit.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal mntNuit = hNuit.multiply(tauxHoraire).multiply(new BigDecimal("0.30")).setScale(2, RoundingMode.HALF_UP);
            lignes.add(creerLigne(bp, "HEURES_NUIT", "Majoration Travail de Nuit (+30%)", "GAIN", "HEURES_SUP",
                    tauxHoraire, new BigDecimal("30.00"), mntNuit, null, 60));
            totalGainsBruts = totalGainsBruts.add(mntNuit);
        }

        BigDecimal hFerie = zeroIfNull(input.getHeuresFeriees());
        if (hFerie.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal mntFerie = hFerie.multiply(tauxHoraire).multiply(BigDecimal.ONE).setScale(2, RoundingMode.HALF_UP);
            lignes.add(creerLigne(bp, "HEURES_FERIE", "Majoration Travail Jour Férié (+100%)", "GAIN", "HEURES_SUP",
                    tauxHoraire, new BigDecimal("100.00"), mntFerie, null, 80));
            totalGainsBruts = totalGainsBruts.add(mntFerie);
        }

        // 1.5 Primes & Ajustements opérationnels
        BigDecimal primePerfBase = zeroIfNull(input.getPrimePerformanceBase());
        if (primePerfBase.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal nbPresents = zeroIfNull(input.getNbJoursPresents());
            BigDecimal ratioPresence = nbJoursOuvrables.compareTo(BigDecimal.ZERO) > 0
                    ? nbPresents.divide(nbJoursOuvrables, 4, RoundingMode.HALF_UP).min(BigDecimal.ONE)
                    : BigDecimal.ONE;

            BigDecimal mntPrimePerf = primePerfBase.multiply(ratioPresence).setScale(2, RoundingMode.HALF_UP);

            // Malus performance si retard > seuil et congés > 1 jour
            int seuilMalus = input.getSeuilRetardMalusPerformanceMinutes() > 0 ? input.getSeuilRetardMalusPerformanceMinutes() : 70;
            if (input.getMinutesRetard() > seuilMalus && nbJoursConges.compareTo(BigDecimal.ONE) > 0) {
                BigDecimal tauxMalus = input.getTauxMalusPerformance() != null ? input.getTauxMalusPerformance() : new BigDecimal("0.25");
                BigDecimal coefMalus = BigDecimal.ONE.subtract(tauxMalus).max(BigDecimal.ZERO);
                mntPrimePerf = mntPrimePerf.multiply(coefMalus).setScale(2, RoundingMode.HALF_UP);
            }

            lignes.add(creerLigne(bp, "PRIME_PERFORMANCE", "Prime de Performance (Proratisée)", "GAIN", "PRIME",
                    primePerfBase, ratioPresence.multiply(new BigDecimal("100")), mntPrimePerf, null, 90));
            totalGainsBruts = totalGainsBruts.add(mntPrimePerf);
        }

        BigDecimal primeAssiduite = zeroIfNull(input.getPrimeAssiduiteForfait());
        int seuilAssiduite = input.getSeuilRetardAssiduiteMinutes() > 0 ? input.getSeuilRetardAssiduiteMinutes() : 45;
        if (primeAssiduite.compareTo(BigDecimal.ZERO) > 0 && input.getMinutesRetard() < seuilAssiduite) {
            lignes.add(creerLigne(bp, "PRIME_ASSIDUITE", "Prime d'Assiduité", "GAIN", "PRIME",
                    primeAssiduite, null, primeAssiduite, null, 100));
            totalGainsBruts = totalGainsBruts.add(primeAssiduite);
        }

        BigDecimal primeExceptionnelle = zeroIfNull(input.getPrimeExceptionnelle());
        if (primeExceptionnelle.compareTo(BigDecimal.ZERO) > 0) {
            String libelle = input.getMotifPrimeExceptionnelle() != null && !input.getMotifPrimeExceptionnelle().isBlank()
                    ? "Prime Exceptionnelle : " + input.getMotifPrimeExceptionnelle()
                    : "Prime Exceptionnelle";
            lignes.add(creerLigne(bp, "PRIME_EXCEPTIONNELLE", libelle, "GAIN", "PRIME",
                    primeExceptionnelle, null, primeExceptionnelle, null, 110));
            totalGainsBruts = totalGainsBruts.add(primeExceptionnelle);
        }

        BigDecimal regularisationPaie = zeroIfNull(input.getRegularisationPaie());
        if (regularisationPaie.compareTo(BigDecimal.ZERO) > 0) {
            String libelle = input.getMotifRegularisationPaie() != null && !input.getMotifRegularisationPaie().isBlank()
                    ? "Régularisation : " + input.getMotifRegularisationPaie()
                    : "Régularisation Congés (Restitution Absence)";
            lignes.add(creerLigne(bp, "REGULARISATION_PAIE", libelle, "GAIN", "SALAIRE_BASE",
                    regularisationPaie, null, regularisationPaie, null, 112));
            totalGainsBruts = totalGainsBruts.add(regularisationPaie);
        }

        BigDecimal autimp = zeroIfNull(input.getAutimp());
        if (autimp.compareTo(BigDecimal.ZERO) > 0) {
            lignes.add(creerLigne(bp, "AUTIMP", "Autres Gains Imposables (AUTIMP)", "GAIN", "PRIME",
                    autimp, null, autimp, null, 115));
            totalGainsBruts = totalGainsBruts.add(autimp);
        }

        totalGainsBruts = totalGainsBruts.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        bp.setTotalGainsBruts(totalGainsBruts);

        // ----------------------------------------------------------------------
        // ----------------------------------------------------------------------
        // ÉTAPE 2 : COTISATIONS SOCIALES (CNaPS, Santé, FMFP)
        // ----------------------------------------------------------------------
        BigDecimal totalCotisSalariales = BigDecimal.ZERO.setScale(2);
        BigDecimal totalCotisPatronales = BigDecimal.ZERO.setScale(2);

        BigDecimal sme = zeroIfNull(input.getSmeNational());
        BigDecimal plafondCnaps = sme.multiply(MULTIPLICATEUR_PLAFOND_CNAPS);

        // 2.1 CNaPS (plafonnée à 8x SME) & FMFP Patronale (1% sur brut total)
        if (input.isSoumisCnaps()) {
            CotisationResult resCnaps = calculateurPaieEngine.calculerCnaps(
                    totalGainsBruts, input.getSmeNational(), input.getTauxCnapsSalarial(), input.getTauxCnapsPatronal());
            BigDecimal assietteCnaps = resCnaps.assiette();
            BigDecimal cnapsSal = resCnaps.partSalariale();
            BigDecimal cnapsPat = resCnaps.partPatronale();

            lignes.add(creerLigne(bp, "COTISATION_CNAPS_SAL", "Cotisation CNaPS Salariale (1%)", "RETENUE", "COTISATION_SALARIALE",
                    assietteCnaps, BigDecimal.valueOf(input.getTauxCnapsSalarial() * 100), cnapsSal, null, 200));
            lignes.add(creerLigne(bp, "COTISATION_CNAPS_PAT", "Cotisation CNaPS Patronale (13%)", "PATRONAL", "COTISATION_PATRONALE",
                    assietteCnaps, BigDecimal.valueOf(input.getTauxCnapsPatronal() * 100), null, cnapsPat, 300));

            totalCotisSalariales = totalCotisSalariales.add(cnapsSal);
            totalCotisPatronales = totalCotisPatronales.add(cnapsPat);

            // 2.3 FMFP Patronale (1% sur brut total)
            BigDecimal fmfpPat = totalGainsBruts.multiply(BigDecimal.valueOf(input.getTauxFmfpPatronal())).setScale(2, RoundingMode.HALF_UP);
            lignes.add(creerLigne(bp, "COTISATION_FMFP_PAT", "Cotisation FMFP Patronale (1%)", "PATRONAL", "COTISATION_PATRONALE",
                    totalGainsBruts, BigDecimal.valueOf(input.getTauxFmfpPatronal() * 100), null, fmfpPat, 330));
            totalCotisPatronales = totalCotisPatronales.add(fmfpPat);
        }

        // 2.2 Santé (OSTIE ou OMSI - Plafonnement à 8x SME si activé)
        if (input.isSoumisSante()) {
            String organe = input.getCodeSanteOrgane() != null ? input.getCodeSanteOrgane().toUpperCase() : "OSTIE";
            boolean estOmsi = "OMSI".equals(organe);

            String codeSanteSal = estOmsi ? "COTISATION_OMSI_SAL" : "COTISATION_OSTIE_SAL";
            String libelleSanteSal = estOmsi ? "Cotisation OMSI Salariale (1.5%)" : "Cotisation OSTIE Salariale (1%)";
            String codeSantePat = estOmsi ? "COTISATION_OMSI_PAT" : "COTISATION_OSTIE_PAT";
            String libelleSantePat = estOmsi ? "Cotisation OMSI Patronale (5.5%)" : "Cotisation OSTIE Patronale (5%)";

            BigDecimal assietteSante = input.isEstPlafonneSante()
                    ? totalGainsBruts.min(plafondCnaps)
                    : totalGainsBruts;

            CotisationResult resSante = calculateurPaieEngine.calculerSante(
                    assietteSante, input.getTauxSanteSalarial(), input.getTauxSantePatronal());
            BigDecimal santeSal = resSante.partSalariale();
            BigDecimal santePat = resSante.partPatronale();

            lignes.add(creerLigne(bp, codeSanteSal, libelleSanteSal, "RETENUE", "COTISATION_SALARIALE",
                    assietteSante, BigDecimal.valueOf(input.getTauxSanteSalarial() * 100), santeSal, null, 210));
            lignes.add(creerLigne(bp, codeSantePat, libelleSantePat, "PATRONAL", "COTISATION_PATRONALE",
                    assietteSante, BigDecimal.valueOf(input.getTauxSantePatronal() * 100), null, santePat, 310));

            totalCotisSalariales = totalCotisSalariales.add(santeSal);
            totalCotisPatronales = totalCotisPatronales.add(santePat);
        }

        bp.setTotalCotisationsSalariales(totalCotisSalariales);
        bp.setTotalCotisationsPatronales(totalCotisPatronales);

        // ----------------------------------------------------------------------
        // ÉTAPE 3 : FISCALITÉ (IRSA ou ISI 5%)
        // ----------------------------------------------------------------------
        BigDecimal salaireNetImposable = totalGainsBruts.subtract(totalCotisSalariales).max(BigDecimal.ZERO);
        bp.setSalaireNetImposable(salaireNetImposable);

        BigDecimal irsaNet = BigDecimal.ZERO;
        BigDecimal retenueIsi = BigDecimal.ZERO;

        if (input.isSoumisIsi5()) {
            // Régime Prestataire / Consultant / Temporaire : Retenue libératoire ISI directe de 5%
            BigDecimal tauxIsi = input.getTauxIsi() != null ? input.getTauxIsi() : new BigDecimal("5.00");
            retenueIsi = totalGainsBruts.multiply(tauxIsi.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)).setScale(2, RoundingMode.HALF_UP);
            lignes.add(creerLigne(bp, "RETENUE_ISI_5", "Retenue Libératoire ISI (" + tauxIsi.stripTrailingZeros().toPlainString() + "%)", "RETENUE", "IMPOT",
                    totalGainsBruts, tauxIsi, retenueIsi, null, 430));
            bp.setRetenueIsi5(retenueIsi);
            bp.setIrsaNet(BigDecimal.ZERO.setScale(2));
        } else if (input.isSoumisIrsa()) {
            // Régime Salarié : Barème progressif IRSA unifié via CalculateurPaieEngine
            var irsaRes = calculateurPaieEngine.calculerIrsaDetaille(
                    salaireNetImposable,
                    input.getTranchesIrsa(),
                    input.getNbEnfantsEligibles(),
                    input.getDeductionParEnfant(),
                    input.getMinimumPerceptionIrsa(),
                    input.getSeuilExonerationIrsa()
            );

            bp.setBaseIrsaArrondie(irsaRes.baseIrsaArrondie());
            bp.setIrsaBrut(irsaRes.irsaBrut());
            bp.setReductionEnfants(irsaRes.reductionEnfants());

            irsaNet = irsaRes.irsaNet().min(salaireNetImposable).setScale(2, RoundingMode.HALF_UP);

            lignes.add(creerLigne(bp, "IMPOT_IRSA_BRUT", "IRSA Brut (Barème Progressif 6 Tranches)", "INFO", "IMPOT",
                    irsaRes.baseIrsaArrondie(), null, irsaRes.irsaArrondiDizaine(), null, 400));

            int nbEnfants = input.getNbEnfantsEligibles();
            if (irsaRes.reductionEnfants().compareTo(BigDecimal.ZERO) > 0) {
                lignes.add(creerLigne(bp, "REDUCTION_CHARGE_FAMILLE", "Réduction IRSA pour Charges de Famille (" + nbEnfants + " enf.)",
                        "INFO", "IMPOT", irsaRes.reductionEnfants(), null, irsaRes.reductionEnfants(), null, 410));
            }

            if (irsaNet.compareTo(BigDecimal.ZERO) > 0) {
                lignes.add(creerLigne(bp, "IMPOT_IRSA_NET", "IRSA Net à Payer (Retenu à la Source)", "RETENUE", "IMPOT",
                        irsaRes.baseIrsaArrondie(), null, irsaNet, null, 420));
            }
            bp.setIrsaNet(irsaNet);
            bp.setRetenueIsi5(BigDecimal.ZERO.setScale(2));
        } else {
            bp.setIrsaNet(BigDecimal.ZERO.setScale(2));
            bp.setRetenueIsi5(BigDecimal.ZERO.setScale(2));
        }

        // ----------------------------------------------------------------------
        // ÉTAPE 4 : INDEMNITÉS NON IMPOSABLES & FRAIS (Ajouts au Net)
        // ----------------------------------------------------------------------
        BigDecimal totalIndemnitesNet = BigDecimal.ZERO;

        // 4.1 Indemnité repas
        BigDecimal montantRepas = input.getIndemniteRepas();
        if (montantRepas == null) {
            BigDecimal forfaitRepas = input.getForfaitRepasSite() != null ? input.getForfaitRepasSite() : BigDecimal.ZERO;
            if (forfaitRepas.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal joursPresents = zeroIfNull(input.getNbJoursPresents());
                montantRepas = joursPresents.setScale(0, RoundingMode.DOWN).multiply(forfaitRepas);
            } else {
                montantRepas = BigDecimal.ZERO;
            }
        }
        if (montantRepas.compareTo(BigDecimal.ZERO) > 0) {
            lignes.add(creerLigne(bp, "INDEMNITE_REPAS", "Indemnité de Panier (Accord Établissement GALAXAM)", "GAIN", "INDEMNITE_NON_IMPOSABLE",
                    montantRepas, null, montantRepas, null, 510));
            totalIndemnitesNet = totalIndemnitesNet.add(montantRepas);
        }

        // 4.2 Indemnité de transport / déplacement
        BigDecimal montantTransport = input.getIndemniteTransport();
        if (montantTransport == null) {
            BigDecimal forfaitSite = input.getForfaitTransportSite() != null ? input.getForfaitTransportSite() : new BigDecimal("31200.00");
            BigDecimal nbPresents = zeroIfNull(input.getNbJoursPresents());
            BigDecimal ratio = nbJoursOuvrables.compareTo(BigDecimal.ZERO) > 0
                    ? nbPresents.divide(nbJoursOuvrables, 4, RoundingMode.HALF_UP).min(BigDecimal.ONE)
                    : BigDecimal.ONE;
            montantTransport = forfaitSite.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
        }
        if (montantTransport.compareTo(BigDecimal.ZERO) > 0) {
            lignes.add(creerLigne(bp, "INDEMNITE_TRANSPORT", "Indemnité Forfaitaire de Déplacement", "GAIN", "INDEMNITE_NON_IMPOSABLE",
                    montantTransport, null, montantTransport, null, 500));
            totalIndemnitesNet = totalIndemnitesNet.add(montantTransport);
        }

        // 4.3 Extra déplacement (missions / dépassement 19h30 non imposable)
        BigDecimal extraDepl = zeroIfNull(input.getExtraDeplacement());
        if (extraDepl.compareTo(BigDecimal.ZERO) > 0) {
            lignes.add(creerLigne(bp, "EXTRA_DEPLACEMENT", "Indemnité Extra Déplacement", "GAIN", "INDEMNITE_NON_IMPOSABLE",
                    extraDepl, null, extraDepl, null, 505));
            totalIndemnitesNet = totalIndemnitesNet.add(extraDepl);
        }

        // 4.4 Notes de frais remboursées
        BigDecimal notesFrais = zeroIfNull(input.getRemboursementNotesFrais());
        if (notesFrais.compareTo(BigDecimal.ZERO) > 0) {
            lignes.add(creerLigne(bp, "REMBOURSEMENT_NOTE_FRAIS", "Remboursement Notes de Frais Validées", "GAIN", "INDEMNITE_NON_IMPOSABLE",
                    notesFrais, null, notesFrais, null, 520));
            totalIndemnitesNet = totalIndemnitesNet.add(notesFrais);
        }

        bp.setTotalIndemnitesNonImposables(totalIndemnitesNet);

        // ----------------------------------------------------------------------
        // ÉTAPE 5 : RETENUES DE TRÉSORERIE & QUOTITÉ CESSIBLE (Art. 68)
        // ----------------------------------------------------------------------
        // Net disponible avant les déductions de trésorerie personnelles
        BigDecimal netAvantTresorerie = salaireNetImposable
                .subtract(retenueIsi)
                .subtract(irsaNet)
                .add(totalIndemnitesNet)
                .max(BigDecimal.ZERO);

        // 5.1 Séparation des Acomptes sur salaire échu (Art. 64) et des dettes/avances spéciales (Art. 68)
        BigDecimal totalAcomptesQuinzaine = BigDecimal.ZERO;
        BigDecimal totalAvancesSpeciales = BigDecimal.ZERO;
        List<Integer> idsAvancesImputees = new ArrayList<>();

        // Traitement prioritaire des acomptes de quinzaine (salaire déjà échu)
        BigDecimal soldeDisponibleAcompte = netAvantTresorerie;
        if (input.getAvancesAcomptes() != null) {
            for (PaieAvanceAcompte a : input.getAvancesAcomptes()) {
                if (a.getMontant() == null || a.getMontant().compareTo(BigDecimal.ZERO) <= 0) continue;

                String type = a.getTypeMouvement() != null ? a.getTypeMouvement() : "ACOMPTE_15";
                boolean isAcompteQuinzaine = "ACOMPTE_15".equalsIgnoreCase(type) || "AVANCE_QUINZAINE".equalsIgnoreCase(type);

                if (isAcompteQuinzaine) {
                    String codeRubrique = mapRubriqueAvance(type);
                    String libelle = a.getMotif() != null && !a.getMotif().isBlank() ? a.getMotif() : "Acompte sur salaire";

                    BigDecimal mntAcompte = a.getMontant().min(soldeDisponibleAcompte).setScale(2, RoundingMode.HALF_UP);
                    if (mntAcompte.compareTo(BigDecimal.ZERO) > 0) {
                        lignes.add(creerLigne(bp, codeRubrique, libelle, "RETENUE", "ACOMPTE_AVANCE",
                                a.getMontant(), null, mntAcompte, null, 600));
                        totalAcomptesQuinzaine = totalAcomptesQuinzaine.add(mntAcompte);
                        soldeDisponibleAcompte = soldeDisponibleAcompte.subtract(mntAcompte).max(BigDecimal.ZERO);
                    }
                    if (a.getIdAvance() != null) {
                        idsAvancesImputees.add(a.getIdAvance());
                    }
                }
            }
        }

        // Net disponible après paiement de l'acompte de quinzaine déjà échu
        BigDecimal netApresAcompte = netAvantTresorerie.subtract(totalAcomptesQuinzaine).max(BigDecimal.ZERO);

        // Plafond Art. 68 du Code du Travail : Le remboursement d'avances en espèces consenties par l'employeur
        // ne peut excéder le dixième (10%) du montant des salaires échus
        BigDecimal plafondDixiemeAvances = netApresAcompte.multiply(PLAFOND_AVANCE_EMPLOYEUR_DIXIEME).setScale(2, RoundingMode.HALF_UP);

        // Traitement des avances exceptionnelles / spéciales (plafonnées au 1/10ème légal)
        if (input.getAvancesAcomptes() != null) {
            for (PaieAvanceAcompte a : input.getAvancesAcomptes()) {
                if (a.getMontant() == null || a.getMontant().compareTo(BigDecimal.ZERO) <= 0) continue;

                String type = a.getTypeMouvement() != null ? a.getTypeMouvement() : "";
                boolean isAvanceSpeciale = "AVANCE_SPECIALE".equalsIgnoreCase(type);

                if (isAvanceSpeciale) {
                    String codeRubrique = mapRubriqueAvance(type);
                    String libelle = a.getMotif() != null && !a.getMotif().isBlank() ? a.getMotif() : "Avance sur salaire (Art. 68)";

                    BigDecimal capaciteRestanteAvance = plafondDixiemeAvances.subtract(totalAvancesSpeciales).max(BigDecimal.ZERO);
                    BigDecimal mntAvanceRetenu = a.getMontant().min(capaciteRestanteAvance).setScale(2, RoundingMode.HALF_UP);

                    if (mntAvanceRetenu.compareTo(BigDecimal.ZERO) > 0) {
                        lignes.add(creerLigne(bp, codeRubrique, libelle, "RETENUE", "ACOMPTE_AVANCE",
                                a.getMontant(), null, mntAvanceRetenu, null, 610));
                        totalAvancesSpeciales = totalAvancesSpeciales.add(mntAvanceRetenu);
                    }
                    if (a.getIdAvance() != null) {
                        idsAvancesImputees.add(a.getIdAvance());
                    }
                }
            }
        }

        // Plafond global de la quotité cessible pour prêts et saisies (1/3 du net disponible après acompte Art. 72)
        BigDecimal plafondTiers = netApresAcompte.multiply(PLAFOND_QUOTITE_CESSIBLE).setScale(2, RoundingMode.HALF_UP);

        // Capacité d'endettement résiduelle pour l'échéance de prêt (après imputation des avances)
        BigDecimal capaciteRestantePret = plafondTiers.subtract(totalAvancesSpeciales).max(BigDecimal.ZERO);

        // 5.2 Échéance de prêt (plafonnement au tiers légal Art. 68)
        ImpactPret impactPret = null;
        PaiePretEcheance echeanceActive = input.getEcheancePretActive();
        BigDecimal montantPrelevePret = BigDecimal.ZERO;

        if (echeanceActive != null) {
            BigDecimal mensualiteTheorique = echeanceActive.getMontantRestantEcheance() != null
                    ? echeanceActive.getMontantRestantEcheance()
                    : echeanceActive.getMontantEcheance();

            montantPrelevePret = mensualiteTheorique.min(capaciteRestantePret).setScale(2, RoundingMode.HALF_UP);
            BigDecimal reliquatEcheance = mensualiteTheorique.subtract(montantPrelevePret).max(BigDecimal.ZERO);

            String nouveauStatut = reliquatEcheance.compareTo(BigDecimal.ZERO) == 0
                    ? "PRELEVEE_EN_PAIE"
                    : "PRELEVEE_PARTIELLEMENT";

            if (montantPrelevePret.compareTo(BigDecimal.ZERO) > 0) {
                lignes.add(creerLigne(bp, "RETENUE_ECHEANCE_PRET", "Échéance Prêt N° " + echeanceActive.getNumeroEcheance(),
                        "RETENUE", "RETENUE_DIVERSE", mensualiteTheorique, null, montantPrelevePret, null, 620));
            }

            BigDecimal capitalPretRestantApres = echeanceActive.getCapitalRestantApres() != null
                    ? echeanceActive.getCapitalRestantApres().add(reliquatEcheance)
                    : BigDecimal.ZERO;

            impactPret = new ImpactPret(
                    echeanceActive.getIdEcheance(),
                    echeanceActive.getIdPret(),
                    echeanceActive.getMontantEcheance(),
                    montantPrelevePret,
                    reliquatEcheance,
                    nouveauStatut,
                    capitalPretRestantApres
            );
        }

        // 5.3 Autres retenues directes sur salaire (CALL PERSO, RETENUE DIVERSE, pénalités)
        BigDecimal totalAutresRetenues = BigDecimal.ZERO;
        BigDecimal netApresPret = netApresAcompte.subtract(totalAvancesSpeciales).subtract(montantPrelevePret).max(BigDecimal.ZERO);

        if (input.getAvancesAcomptes() != null) {
            for (PaieAvanceAcompte a : input.getAvancesAcomptes()) {
                if (a.getMontant() == null || a.getMontant().compareTo(BigDecimal.ZERO) <= 0) continue;

                String type = a.getTypeMouvement() != null ? a.getTypeMouvement() : "";
                boolean isAcompte = "ACOMPTE_15".equalsIgnoreCase(type) || "AVANCE_QUINZAINE".equalsIgnoreCase(type);
                boolean isAvanceSpeciale = "AVANCE_SPECIALE".equalsIgnoreCase(type);

                if (!isAcompte && !isAvanceSpeciale) {
                    String codeRubrique = mapRubriqueAvance(type);
                    String libelleDefaut = "CALL_PERSO".equalsIgnoreCase(type)
                            ? "Retenue Téléphonie Privée (Call Perso)"
                            : "Retenue Diverse sur Salaire";
                    String libelle = a.getMotif() != null && !a.getMotif().isBlank() ? a.getMotif() : libelleDefaut;

                    BigDecimal soldeDisponible = netApresPret.subtract(totalAutresRetenues).max(BigDecimal.ZERO);
                    BigDecimal mntRetenu = a.getMontant().min(soldeDisponible).setScale(2, RoundingMode.HALF_UP);

                    if (mntRetenu.compareTo(BigDecimal.ZERO) > 0) {
                        int ordre = "CALL_PERSO".equalsIgnoreCase(type) ? 630 : 640;
                        lignes.add(creerLigne(bp, codeRubrique, libelle, "RETENUE", "RETENUE_DIVERSE",
                                a.getMontant(), null, mntRetenu, null, ordre));
                        totalAutresRetenues = totalAutresRetenues.add(mntRetenu);
                    }
                    if (a.getIdAvance() != null) {
                        idsAvancesImputees.add(a.getIdAvance());
                    }
                }
            }
        }

        BigDecimal totalRetenuesTresorerie = totalAcomptesQuinzaine.add(totalAvancesSpeciales).add(montantPrelevePret).add(totalAutresRetenues);
        bp.setTotalRetenuesAcomptePret(totalRetenuesTresorerie);

        // ----------------------------------------------------------------------
        // ÉTAPE 6 : NET À PAYER BANCAIRE & AJUSTEMENT D'ARRONDI COMPTABLE
        // ----------------------------------------------------------------------
        BigDecimal netAvantArrondi = netAvantTresorerie.subtract(totalRetenuesTresorerie);
        bp.setNetAvantArrondi(netAvantArrondi);

        // Arrondi officiel bancaire à la dizaine d'Ariary la plus proche
        BigDecimal netAPayer = BulletinPaie.arrondirDizaineAriary(netAvantArrondi);
        bp.setNetAPayer(netAPayer);

        // Écart d'arrondi monétaire
        BigDecimal ecartArrondi = netAPayer.subtract(netAvantArrondi).setScale(2, RoundingMode.HALF_UP);

        // Ligne d'ajustement pour équilibre monétaire du net à payer :
        if (ecartArrondi.compareTo(BigDecimal.ZERO) != 0) {
            String sensArrondi = ecartArrondi.compareTo(BigDecimal.ZERO) > 0 ? "GAIN" : "RETENUE";
            BigDecimal montantLigneArrondi = ecartArrondi.abs();

            lignes.add(creerLigne(bp, "AJUSTEMENT_ARRONDI", "Ajustement Arrondi Monétaire (Écart de Règlement)",
                    sensArrondi, "NET", ecartArrondi, null, montantLigneArrondi, null, 999));
        }

        // Coût global employeur = Total Brut + Cotisations Patronales + Indemnités
        BigDecimal coutGlobalEmployeur = totalGainsBruts.add(totalCotisPatronales).add(totalIndemnitesNet).setScale(2, RoundingMode.HALF_UP);
        bp.setCoutGlobalEmployeur(coutGlobalEmployeur);

        // Statut
        bp.setStatut(input.isSimulation() ? "CALCULE" : "VALIDE");
        bp.setDateCalcul(new Timestamp(System.currentTimeMillis()));

        // Assemblage final du conteneur
        if (input.getIdsMouvementsGains() != null && !input.getIdsMouvementsGains().isEmpty()) {
            idsAvancesImputees.addAll(input.getIdsMouvementsGains());
        }

        BulletinPaieComplet complet = new BulletinPaieComplet(bp, lignes);
        complet.setImpactPret(impactPret);
        complet.setIdsAvancesImputees(idsAvancesImputees);

        return complet;
    }



    /**
     * Arrondit à la dizaine d'Ariary supérieure.
     */
    private BigDecimal arrondirDizaineSuperieure(BigDecimal montant) {
        if (montant == null || montant.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return montant.divide(BigDecimal.TEN, 0, RoundingMode.CEILING).multiply(BigDecimal.TEN).setScale(2, RoundingMode.HALF_UP);
    }

    private BulletinPaieLigne creerLigne(BulletinPaie bp, String codeRubrique, String libelle, String sens,
                                        String categorie, BigDecimal baseCalcul, BigDecimal taux,
                                        BigDecimal mntSal, BigDecimal mntPat, int ordre) {
        BulletinPaieLigne l = new BulletinPaieLigne();
        l.setIdBulletin(bp.getIdBulletin());
        l.setCodeRubrique(codeRubrique);
        l.setLibelleRubrique(libelle);
        l.setSens(sens);
        l.setCategorie(categorie);
        l.setBaseCalcul(baseCalcul != null ? baseCalcul.setScale(2, RoundingMode.HALF_UP) : null);
        l.setTauxSalarial(taux != null ? taux.setScale(2, RoundingMode.HALF_UP) : null);
        l.setMontantSalarial(mntSal != null ? mntSal.setScale(2, RoundingMode.HALF_UP) : null);
        l.setMontantPatronal(mntPat != null ? mntPat.setScale(2, RoundingMode.HALF_UP) : null);
        l.setOrdre(ordre);
        return l;
    }

    private String mapRubriqueAvance(String type) {
        return switch (type) {
            case "AVANCE_SPECIALE" -> "RETENUE_AVANCE_SPECIALE";
            case "CALL_PERSO" -> "RETENUE_CALL_PERSO";
            case "ECHEANCE_PRET" -> "RETENUE_ECHEANCE_PRET";
            case "RETENUE_DIVERSE" -> "RETENUE_DIVERSE";
            default -> "RETENUE_ACOMPTE_15";
        };
    }

    private String genererReferenceBulletin(PayrollCalculationInput in) {
        String codePer = in.getCodePeriode() != null ? in.getCodePeriode() : "PER";
        String matr = in.getMatricule() != null ? in.getMatricule() : "EMP";
        return "BP-" + codePer + "-" + matr;
    }

    private BigDecimal zeroIfNull(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }
}
