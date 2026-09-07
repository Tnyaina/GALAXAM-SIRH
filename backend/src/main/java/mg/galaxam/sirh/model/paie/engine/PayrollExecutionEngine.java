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
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine.TrancheIrsa;

/**
 * Moteur de calcul pur et déterministe de paie pour GALAXAM-SIRH.
 * 
 * Conforme au Code du Travail malgache, au Code Général des Impôts (LF 2026),
 * et aux résolutions du contre-audit.
 */
@Component
public class PayrollExecutionEngine {

    public static final BigDecimal PLAFOND_QUOTITE_CESSIBLE = new BigDecimal("0.33333333"); // 1/3 légal (Art. 68)
    public static final BigDecimal DIVISEUR_HEURES_MENSUELLES = new BigDecimal("173.3333");
    public static final BigDecimal DIVISEUR_JOURS_TRENTIEME = new BigDecimal("30.0");
    public static final BigDecimal MULTIPLICATEUR_PLAFOND_CNAPS = new BigDecimal("8.0");

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
                BigDecimal heuresACompter = nbHeuresAbsence.compareTo(BigDecimal.ZERO) > 0
                        ? nbHeuresAbsence
                        : nbJoursAbsence.multiply(new BigDecimal("8.00")); // 8h par jour ouvré légal (40h/5j)
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

        // 1.3 Maintien de salaire pour congés payés
        BigDecimal nbJoursConges = zeroIfNull(input.getNbJoursConges());
        if (nbJoursConges.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal tauxJournalierConge = input.getTauxJournalierTrentieme();
            BigDecimal montantConge = nbJoursConges.multiply(tauxJournalierConge).setScale(2, RoundingMode.HALF_UP);
            lignes.add(creerLigne(bp, "INDEMNITE_CONGE", "Indemnité de Congés Payés (Maintien)", "GAIN", "SALAIRE_BASE",
                    tauxJournalierConge, null, montantConge, null, 30));
            totalGainsBruts = totalGainsBruts.add(montantConge);
        }

        // 1.4 Heures supplémentaires et majorations (Réservées aux non-cadres / Agents)
        boolean estCadre = "CADRE".equalsIgnoreCase(input.getCategorieProfessionnelle());
        if (!estCadre) {
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
        }

        // 1.5 Primes & Ajustements opérationnels
        BigDecimal primePerfBase = zeroIfNull(input.getPrimePerformanceBase());
        if (primePerfBase.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal nbPresents = zeroIfNull(input.getNbJoursPresents());
            BigDecimal ratioPresence = nbJoursOuvrables.compareTo(BigDecimal.ZERO) > 0
                    ? nbPresents.divide(nbJoursOuvrables, 4, RoundingMode.HALF_UP).min(BigDecimal.ONE)
                    : BigDecimal.ONE;

            BigDecimal mntPrimePerf = primePerfBase.multiply(ratioPresence).setScale(2, RoundingMode.HALF_UP);

            // Malus -25% si retard > 70 min et conge > 1 jour
            if (input.getMinutesRetard() > 70 && nbJoursConges.compareTo(BigDecimal.ONE) > 0) {
                mntPrimePerf = mntPrimePerf.multiply(new BigDecimal("0.75")).setScale(2, RoundingMode.HALF_UP);
            }

            lignes.add(creerLigne(bp, "PRIME_PERFORMANCE", "Prime de Performance (Proratisée)", "GAIN", "PRIME",
                    primePerfBase, ratioPresence.multiply(new BigDecimal("100")), mntPrimePerf, null, 90));
            totalGainsBruts = totalGainsBruts.add(mntPrimePerf);
        }

        BigDecimal primeAssiduite = zeroIfNull(input.getPrimeAssiduiteForfait());
        if (primeAssiduite.compareTo(BigDecimal.ZERO) > 0 && input.getMinutesRetard() < 45) {
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

        totalGainsBruts = totalGainsBruts.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        bp.setTotalGainsBruts(totalGainsBruts);

        // ----------------------------------------------------------------------
        // ÉTAPE 2 : COTISATIONS SOCIALES (CNaPS, Santé, FMFP)
        // ----------------------------------------------------------------------
        BigDecimal totalCotisSalariales = BigDecimal.ZERO.setScale(2);
        BigDecimal totalCotisPatronales = BigDecimal.ZERO.setScale(2);

        boolean estConsultant = "CONSULTANT".equalsIgnoreCase(input.getRegimeIntervenant())
                || "TEMPORAIRE".equalsIgnoreCase(input.getRegimeIntervenant());

        if (!estConsultant) {
            // 2.1 CNaPS (plafonnée à 8x SME)
            BigDecimal sme = zeroIfNull(input.getSmeNational());
            BigDecimal plafondCnaps = sme.multiply(MULTIPLICATEUR_PLAFOND_CNAPS);
            BigDecimal assietteCnaps = totalGainsBruts.min(plafondCnaps);

            BigDecimal cnapsSal = assietteCnaps.multiply(BigDecimal.valueOf(input.getTauxCnapsSalarial())).setScale(2, RoundingMode.HALF_UP);
            BigDecimal cnapsPat = assietteCnaps.multiply(BigDecimal.valueOf(input.getTauxCnapsPatronal())).setScale(2, RoundingMode.HALF_UP);

            lignes.add(creerLigne(bp, "COTISATION_CNAPS_SAL", "Cotisation CNaPS Salariale (1%)", "RETENUE", "COTISATION_SALARIALE",
                    assietteCnaps, BigDecimal.valueOf(input.getTauxCnapsSalarial() * 100), cnapsSal, null, 200));
            lignes.add(creerLigne(bp, "COTISATION_CNAPS_PAT", "Cotisation CNaPS Patronale (13%)", "PATRONAL", "COTISATION_PATRONALE",
                    assietteCnaps, BigDecimal.valueOf(input.getTauxCnapsPatronal() * 100), null, cnapsPat, 300));

            totalCotisSalariales = totalCotisSalariales.add(cnapsSal);
            totalCotisPatronales = totalCotisPatronales.add(cnapsPat);

            // 2.2 Santé (OSTIE ou OMSI - Plafonnement à 8x SME si activé)
            boolean estToamasina = "TMM".equalsIgnoreCase(input.getCodeSite())
                    || (input.getNomSite() != null && input.getNomSite().toUpperCase().contains("TOAMASINA"));

            String codeSanteSal = estToamasina ? "COTISATION_OMSI_SAL" : "COTISATION_OSTIE_SAL";
            String libelleSanteSal = estToamasina ? "Cotisation OMSI Salariale (1.5%)" : "Cotisation OSTIE Salariale (1%)";
            String codeSantePat = estToamasina ? "COTISATION_OMSI_PAT" : "COTISATION_OSTIE_PAT";
            String libelleSantePat = estToamasina ? "Cotisation OMSI Patronale (5.5%)" : "Cotisation OSTIE Patronale (5%)";

            BigDecimal assietteSante = input.isEstPlafonneSante()
                    ? totalGainsBruts.min(plafondCnaps)
                    : totalGainsBruts;

            BigDecimal santeSal = assietteSante.multiply(BigDecimal.valueOf(input.getTauxSanteSalarial())).setScale(2, RoundingMode.HALF_UP);
            BigDecimal santePat = assietteSante.multiply(BigDecimal.valueOf(input.getTauxSantePatronal())).setScale(2, RoundingMode.HALF_UP);

            lignes.add(creerLigne(bp, codeSanteSal, libelleSanteSal, "RETENUE", "COTISATION_SALARIALE",
                    assietteSante, BigDecimal.valueOf(input.getTauxSanteSalarial() * 100), santeSal, null, 210));
            lignes.add(creerLigne(bp, codeSantePat, libelleSantePat, "PATRONAL", "COTISATION_PATRONALE",
                    assietteSante, BigDecimal.valueOf(input.getTauxSantePatronal() * 100), null, santePat, 310));

            totalCotisSalariales = totalCotisSalariales.add(santeSal);
            totalCotisPatronales = totalCotisPatronales.add(santePat);

            // 2.3 FMFP Patronale (1% sur brut total)
            BigDecimal fmfpPat = totalGainsBruts.multiply(BigDecimal.valueOf(input.getTauxFmfpPatronal())).setScale(2, RoundingMode.HALF_UP);
            lignes.add(creerLigne(bp, "COTISATION_FMFP_PAT", "Cotisation FMFP Patronale (1%)", "PATRONAL", "COTISATION_PATRONALE",
                    totalGainsBruts, BigDecimal.valueOf(input.getTauxFmfpPatronal() * 100), null, fmfpPat, 330));
            totalCotisPatronales = totalCotisPatronales.add(fmfpPat);
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

        if (estConsultant) {
            // Régime Prestataire / Consultant : Retenue libératoire ISI directe de 5%
            retenueIsi = totalGainsBruts.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
            lignes.add(creerLigne(bp, "RETENUE_ISI_5", "Retenue Libératoire ISI (5%)", "RETENUE", "IMPOT",
                    totalGainsBruts, new BigDecimal("5.00"), retenueIsi, null, 430));
            bp.setRetenueIsi5(retenueIsi);
            bp.setIrsaNet(BigDecimal.ZERO.setScale(2));
        } else {
            // Régime Salarié : Barème progressif IRSA
            // 3.1 Arrondi fiscal à la centaine inférieure
            long netImposableLong = salaireNetImposable.setScale(0, RoundingMode.DOWN).longValue();
            long baseArrondieCentaine = (netImposableLong / 100L) * 100L;
            BigDecimal baseIrsaArrondie = BigDecimal.valueOf(baseArrondieCentaine).setScale(2, RoundingMode.HALF_UP);
            bp.setBaseIrsaArrondie(baseIrsaArrondie);

            // 3.2 Calcul progressif de l'impôt brut par tranches
            BigDecimal irsaBrut = calculerIrsaBrut(baseIrsaArrondie, input.getTranchesIrsa());
            bp.setIrsaBrut(irsaBrut);

            // Arrondi supérieur à la dizaine d'Ariary
            BigDecimal irsaArrondiDizaine = arrondirDizaineSuperieure(irsaBrut);

            lignes.add(creerLigne(bp, "IMPOT_IRSA_BRUT", "IRSA Brut (Barème Progressif 6 Tranches)", "INFO", "IMPOT",
                    baseIrsaArrondie, null, irsaArrondiDizaine, null, 400));

            // 3.3 Réduction pour charges de famille (2 000 Ar par enfant éligible)
            int nbEnfants = input.getNbEnfantsEligibles();
            BigDecimal reductionEnfants = input.getDeductionParEnfant().multiply(BigDecimal.valueOf(nbEnfants));
            bp.setReductionEnfants(reductionEnfants);

            if (reductionEnfants.compareTo(BigDecimal.ZERO) > 0) {
                lignes.add(creerLigne(bp, "REDUCTION_CHARGE_FAMILLE", "Réduction IRSA pour Charges de Famille (" + nbEnfants + " enf.)",
                        "INFO", "IMPOT", reductionEnfants, null, reductionEnfants, null, 410));
            }

            // 3.4 Impôt net dû avec application du plancher légal (3 000 Ar si revenu imposable)
            if (salaireNetImposable.compareTo(BigDecimal.ZERO) <= 0) {
                irsaNet = BigDecimal.ZERO.setScale(2);
            } else {
                BigDecimal minimumPerception = input.getMinimumPerceptionIrsa();
                irsaNet = irsaArrondiDizaine.subtract(reductionEnfants).max(BigDecimal.ZERO);
                irsaNet = irsaNet.max(minimumPerception).setScale(2, RoundingMode.HALF_UP);
            }

            if (irsaNet.compareTo(BigDecimal.ZERO) > 0) {
                lignes.add(creerLigne(bp, "IMPOT_IRSA_NET", "IRSA Net à Payer (Retenu à la Source)", "RETENUE", "IMPOT",
                        baseIrsaArrondie, null, irsaNet, null, 420));
            }
            bp.setIrsaNet(irsaNet);
        }

        // ----------------------------------------------------------------------
        // ÉTAPE 4 : INDEMNITÉS NON IMPOSABLES & FRAIS (Ajouts au Net)
        // ----------------------------------------------------------------------
        BigDecimal totalIndemnitesNet = BigDecimal.ZERO;

        // 4.1 Indemnité repas (2 000 Ar par jour complet de travail à TNR)
        BigDecimal montantRepas = input.getIndemniteRepas();
        if (montantRepas == null) {
            // Calcul automatique standard : 2 000 Ar par jour de présence validé
            boolean estTnr = !"TMM".equalsIgnoreCase(input.getCodeSite());
            if (estTnr) {
                BigDecimal joursPresents = zeroIfNull(input.getNbJoursPresents());
                montantRepas = joursPresents.setScale(0, RoundingMode.DOWN).multiply(new BigDecimal("2000.00"));
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
            // Forfait mensuel proratisé aux présences : TNR 31 200 Ar / TMM 53 200 Ar
            boolean estToamasina = "TMM".equalsIgnoreCase(input.getCodeSite());
            BigDecimal forfaitSite = estToamasina ? new BigDecimal("53200.00") : new BigDecimal("31200.00");
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

        // 4.3 Notes de frais remboursées
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
                .subtract(estConsultant ? retenueIsi : irsaNet)
                .add(totalIndemnitesNet)
                .max(BigDecimal.ZERO);

        // 5.1 Séparation des Acomptes sur salaire échu (Art. 64) et des dettes/avances spéciales (Art. 68)
        BigDecimal totalAcomptesQuinzaine = BigDecimal.ZERO;
        BigDecimal totalAvancesSpeciales = BigDecimal.ZERO;
        List<Integer> idsAvancesImputees = new ArrayList<>();

        if (input.getAvancesAcomptes() != null) {
            for (PaieAvanceAcompte a : input.getAvancesAcomptes()) {
                if (a.getMontant() == null || a.getMontant().compareTo(BigDecimal.ZERO) <= 0) continue;

                String type = a.getTypeMouvement() != null ? a.getTypeMouvement() : "ACOMPTE_15";
                String codeRubrique = mapRubriqueAvance(type);
                String libelle = a.getMotif() != null && !a.getMotif().isBlank() ? a.getMotif() : "Acompte sur salaire";

                lignes.add(creerLigne(bp, codeRubrique, libelle, "RETENUE", "ACOMPTE_AVANCE",
                        a.getMontant(), null, a.getMontant(), null, 600));

                if ("ACOMPTE_15".equalsIgnoreCase(type) || "AVANCE_QUINZAINE".equalsIgnoreCase(type)) {
                    totalAcomptesQuinzaine = totalAcomptesQuinzaine.add(a.getMontant());
                } else {
                    totalAvancesSpeciales = totalAvancesSpeciales.add(a.getMontant());
                }

                if (a.getIdAvance() != null) {
                    idsAvancesImputees.add(a.getIdAvance());
                }
            }
        }

        // Net disponible après paiement de l'acompte de quinzaine déjà échu
        BigDecimal netApresAcompte = netAvantTresorerie.subtract(totalAcomptesQuinzaine).max(BigDecimal.ZERO);

        // Plafond de la quotité cessible (Art. 68 : maximum un tiers du net disponible après acompte)
        BigDecimal plafondTiers = netApresAcompte.multiply(PLAFOND_QUOTITE_CESSIBLE).setScale(2, RoundingMode.HALF_UP);

        // Capacité d'endettement résiduelle pour l'échéance de prêt (après avances exceptionnelles)
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

        BigDecimal totalRetenuesTresorerie = totalAcomptesQuinzaine.add(totalAvancesSpeciales).add(montantPrelevePret);
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
        BulletinPaieComplet complet = new BulletinPaieComplet(bp, lignes);
        complet.setImpactPret(impactPret);
        complet.setIdsAvancesImputees(idsAvancesImputees);

        return complet;
    }

    /**
     * Calcule l'IRSA progressif par tranches (LF 2026).
     */
    private BigDecimal calculerIrsaBrut(BigDecimal baseImposable, List<TrancheIrsa> tranches) {
        if (baseImposable == null || baseImposable.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        if (tranches != null && !tranches.isEmpty()) {
            BigDecimal totalImpot = BigDecimal.ZERO;
            for (TrancheIrsa t : tranches) {
                if (baseImposable.compareTo(t.seuilMin()) > 0) {
                    BigDecimal maxTranche = t.seuilMax() != null ? t.seuilMax() : baseImposable;
                    BigDecimal taxable = baseImposable.min(maxTranche).subtract(t.seuilMin());
                    if (taxable.compareTo(BigDecimal.ZERO) > 0) {
                        double taux = t.taux() >= 1.0 ? t.taux() / 100.0 : t.taux();
                        totalImpot = totalImpot.add(taxable.multiply(BigDecimal.valueOf(taux)));
                    }
                }
            }
            return totalImpot.setScale(2, RoundingMode.HALF_UP);
        }

        // Barème par défaut standard LF 2026
        BigDecimal impot = BigDecimal.ZERO;
        // Tranche 1 : 0 à 350 000 (0%)
        // Tranche 2 : 350 001 à 400 000 (5%)
        impot = impot.add(calculerTranche(baseImposable, new BigDecimal("350000.00"), new BigDecimal("400000.00"), new BigDecimal("0.05")));
        // Tranche 3 : 400 001 à 500 000 (10%)
        impot = impot.add(calculerTranche(baseImposable, new BigDecimal("400000.00"), new BigDecimal("500000.00"), new BigDecimal("0.10")));
        // Tranche 4 : 500 001 à 600 000 (15%)
        impot = impot.add(calculerTranche(baseImposable, new BigDecimal("500000.00"), new BigDecimal("600000.00"), new BigDecimal("0.15")));
        // Tranche 5 : 600 001 à 4 000 000 (20%)
        impot = impot.add(calculerTranche(baseImposable, new BigDecimal("600000.00"), new BigDecimal("4000000.00"), new BigDecimal("0.20")));
        // Tranche 6 : > 4 000 000 (25%)
        if (baseImposable.compareTo(new BigDecimal("4000000.00")) > 0) {
            BigDecimal taxable = baseImposable.subtract(new BigDecimal("4000000.00"));
            impot = impot.add(taxable.multiply(new BigDecimal("0.25")));
        }

        return impot.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculerTranche(BigDecimal base, BigDecimal min, BigDecimal max, BigDecimal taux) {
        if (base.compareTo(min) <= 0) return BigDecimal.ZERO;
        BigDecimal taxable = base.min(max).subtract(min);
        return taxable.multiply(taux);
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
