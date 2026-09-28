package mg.galaxam.sirh.paie.domain.engine;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import mg.galaxam.sirh.paie.persistence.PaieAvanceAcompte;
import mg.galaxam.sirh.paie.persistence.PaiePretEcheance;
import mg.galaxam.sirh.paie.domain.dto.BulletinPaieComplet;
import mg.galaxam.sirh.paie.domain.dto.BulletinPaieComplet.ImpactPret;
import mg.galaxam.sirh.paie.domain.dto.BulletinPaieComplet.MouvementImputation;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;

/**
 * Moteur de calcul modulaire, pur et déterministe de paie pour GALAXAM-SIRH.
 * 
 * Décomposé en méthodes spécialisées pour chaque palier de calcul :
 * 1. Salaire brut & gains (Salaire de base, absences, congés, HS, primes)
 * 2. Cotisations sociales (CNaPS plafonnée à 8x SME, Santé OSTIE/OMSI, FMFP)
 * 3. Net imposable
 * 4. Fiscalité (Barème progressif IRSA 6 tranches, déductions familiales, minimum 3000 Ar, ou ISI 5%)
 * 5. Remboursements et indemnités réellement non imposables
 * 6. Retenues de trésorerie (Acomptes, Avances plafonnées 1/10 Art. 68, Prêts plafonnés 1/3 Art. 72)
 * 7. Net à payer bancaire & arrondi monétaire officiel
 */
@Component
public class PayrollExecutionEngine {

    public static final BigDecimal PLAFOND_QUOTITE_CESSIBLE = new BigDecimal("0.33333333"); // 1/3 légal (Saisies-arrêts et prêts Art. 72)
    public static final BigDecimal PLAFOND_AVANCE_EMPLOYEUR_DIXIEME = new BigDecimal("0.10"); // 1/10 légal (Avances employeur Art. 68)
    public static final BigDecimal DIVISEUR_HEURES_MENSUELLES = new BigDecimal("173.33");
    public static final BigDecimal DIVISEUR_JOURS_TRENTIEME = new BigDecimal("30.0");
    public static final BigDecimal MULTIPLICATEUR_PLAFOND_CNAPS = new BigDecimal("8.0");

    public record CotisationsResult(BigDecimal totalCotisationsSalariales, BigDecimal totalCotisationsPatronales) {}
    public record FiscaliteResult(BigDecimal irsaNet, BigDecimal retenueIsi, IrsaCalculator.Result detailIrsa) {}
    public record RetenuesResult(BigDecimal totalRetenues, ImpactPret impactPret, List<MouvementImputation> imputations) {}

    public PayrollExecutionEngine() { }

    /** Constructeur de compatibilité de tests; aucune dépendance métier n'est conservée. */
    public PayrollExecutionEngine(Object ignoredLegacyCalculator) { }

    /**
     * Point d'entrée principal pour le calcul d'un bulletin individuel.
     * Orchestre séquentiellement chaque palier de calcul.
     */
    public BulletinPaieComplet calculer(PayrollCalculationInput input) {
        return calculerBulletin(input);
    }

    public BulletinPaieComplet calculerBulletin(PayrollCalculationInput input) {
        if (input == null) {
            throw new IllegalArgumentException("Les données d'entrée de calcul (PayrollCalculationInput) sont obligatoires.");
        }

        BulletinPaie bp = initialiserEnTete(input);
        List<BulletinPaieLigne> lignes = new ArrayList<>();

        // 1. Salaire brut et gains
        BigDecimal totalGainsBruts = calculerSalaireBrut(input, bp, lignes);

        // 2. Cotisations sociales (CNaPS, Santé, FMFP)
        CotisationsResult cotis = calculerCotisationsSociales(totalGainsBruts, input, bp, lignes);

        // 3. Net imposable
        BigDecimal revenuNetAvantImpot = calculerNetImposable(totalGainsBruts, cotis.totalCotisationsSalariales(), bp);

        // 3.1 Exonération fiscale des Heures Supplémentaires (CGI Art. 01.03.03-9°)
        OvertimeExemptionDetail hsExo = calculerExonerationFiscaleHs(input, input.getTauxHoraireEffectif(), bp, lignes);

        // 4. Fiscalité (IRSA progressif ou ISI 5%)
        FiscaliteResult fisc = calculerFiscalite(revenuNetAvantImpot, hsExo.hsExonereeIrsa(), totalGainsBruts, input, bp, lignes);

        // 5. Remboursements et indemnités réellement non imposables.
        BigDecimal totalIndemnitesNet = calculerIndemnitesNonImposables(input, bp, lignes);

        // Net disponible avant les déductions de trésorerie personnelles
        BigDecimal netAvantTresorerie = revenuNetAvantImpot
                .subtract(fisc.retenueIsi())
                .subtract(fisc.irsaNet())
                .add(totalIndemnitesNet)
                .max(BigDecimal.ZERO);

        // 6. Retenues de trésorerie (Acomptes, Avances, Prêts, Saisies)
        RetenuesResult retenues = calculerRetenuesEtAvances(netAvantTresorerie, input, bp, lignes);

        // 7. Net à payer bancaire & arrondi monétaire
        calculerNetAPayer(totalGainsBruts, cotis.totalCotisationsPatronales(), totalIndemnitesNet,
                netAvantTresorerie, retenues.totalRetenues(), bp, lignes);

        List<Integer> idsAvancesImputees = retenues.imputations().stream()
                .map(MouvementImputation::idMouvement).toList();
        idsAvancesImputees = new ArrayList<>(idsAvancesImputees);
        if (input.getIdsMouvementsGains() != null && !input.getIdsMouvementsGains().isEmpty()) {
            idsAvancesImputees.addAll(input.getIdsMouvementsGains());
        }

        BulletinPaieComplet complet = new BulletinPaieComplet(bp, lignes);
        complet.setImpactPret(retenues.impactPret());
        complet.setIdsAvancesImputees(idsAvancesImputees);
        complet.setImputationsMouvements(retenues.imputations());

        // Construction du DTO d'explication canonique backend-driven
        BigDecimal diviseur = input.getDiviseurMensuelHeuresEffectif();
        BigDecimal base = bp.getSalaireBase();
        BigDecimal txHor = bp.getTauxHoraire();
        long minAbs = input.getAttendanceInput() != null ? input.getAttendanceInput().unpaidAbsenceMinutes()
                : (input.getNbHeuresAbsence() != null ? input.getNbHeuresAbsence().multiply(BigDecimal.valueOf(60)).longValue() : 0L);
        BigDecimal hAbs = zeroIfNull(input.getNbHeuresAbsence());
        int minRetard = input.getMinutesRetard();
        BigDecimal mntAbs = complet.getMontantLigneSalariale("RETENUE_ABSENCE");

        BigDecimal nbConge = zeroIfNull(input.getNbJoursConges());
        BigDecimal tx30 = input.getTauxJournalierTrentieme();
        BigDecimal dedConge = complet.getMontantLigneSalariale("DEDUCTION_CONGE_BASE");
        BigDecimal txConge = input.getTauxJournalierCongeEffectif();
        BigDecimal indConge = complet.getMontantLigneSalariale("INDEMNITE_CONGE_PAYE");
        BigDecimal netConge = indConge.subtract(dedConge);

        BigDecimal h30 = zeroIfNull(input.getHeuresHs30());
        BigDecimal mnt30 = complet.getMontantLigneSalariale("HEURES_SUP_30");
        BigDecimal h50 = zeroIfNull(input.getHeuresHs50());
        BigDecimal mnt50 = complet.getMontantLigneSalariale("HEURES_SUP_50");
        BigDecimal hNuit = zeroIfNull(input.getHeuresNuit());
        BigDecimal mntNuit = complet.getMontantLigneSalariale("MAJORATION_NUIT");
        BigDecimal hDim = zeroIfNull(input.getHeuresDimanche());
        BigDecimal mntDim = complet.getMontantLigneSalariale("HEURES_DIMANCHE");
        BigDecimal hFer = zeroIfNull(input.getHeuresFeriees());
        BigDecimal mntFer = complet.getMontantLigneSalariale("MAJORATION_FERIE");

        complet.setExplication(new BulletinPaieComplet.ExplicationPaieDTO(
                minAbs, hAbs, minRetard, diviseur, txHor, mntAbs,
                nbConge, base, tx30, dedConge, txConge, indConge, netConge,
                h30, mnt30, h50, mnt50, hNuit, mntNuit, hDim, mntDim, hFer, mntFer
        ));
        complet.setExplicationFiscalite(new BulletinPaieComplet.ExplicationFiscaliteDTO(
                revenuNetAvantImpot,
                hsExo.hsExonereeIrsa(),
                bp.getRevenuNetImposable(),
                bp.getAssietteIrsa(),
                bp.getIrsaCalcule(),
                MoneyRoundingPolicy.irsaCalculatedUpperTen(bp.getIrsaCalcule()),
                bp.getReductionEnfants(),
                zeroIfNull(input.getMinimumPerceptionIrsa()),
                bp.getIrsaDu(),
                fisc.detailIrsa() == null ? List.of() : fisc.detailIrsa().detailsTranches()
        ));

        return complet;
    }

    // =========================================================================
    // 1. INITIALISATION DE L'EN-TÊTE DU BULLETIN
    // =========================================================================

    public BulletinPaie initialiserEnTete(PayrollCalculationInput input) {
        BulletinPaie bp = new BulletinPaie();
        bp.setIdPeriodePaie(input.getIdPeriodePaie());
        bp.setIdEmploye(input.getIdEmploye());
        bp.setIdConfigLegale(input.getIdConfigLegale());
        bp.setTypeBulletin(input.getTypeBulletin() != null ? input.getTypeBulletin() : "MENSUEL");
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

        bp.setHeuresNormales(zeroIfNull(input.getHeuresNormales()));
        bp.setHeuresHs30(zeroIfNull(input.getHeuresHs30()));
        bp.setHeuresHs50(zeroIfNull(input.getHeuresHs50()));
        bp.setHeuresNuit(zeroIfNull(input.getHeuresNuit()));
        bp.setJoursOuvrables(zeroIfNull(input.getNbJoursOuvrables()));
        bp.setJoursTravailles(zeroIfNull(input.getNbJoursPresents()));
        bp.setJoursAbsence(zeroIfNull(input.getNbJoursAbsence()));
        bp.setJoursConges(zeroIfNull(input.getNbJoursConges()));
        bp.setMinutesRetard(input.getMinutesRetard());

        BigDecimal salaireBaseContractuel = zeroIfNull(input.getSalaireBaseContractuel());
        BigDecimal tauxHoraire = input.getTauxHoraireEffectif();
        bp.setSalaireBase(salaireBaseContractuel);
        bp.setTauxHoraire(tauxHoraire);

        return bp;
    }

    // =========================================================================
    // 2. SALAIRE BRUT & GAINS (Palier 1)
    // =========================================================================
    public BigDecimal calculerSalaireBrut(PayrollCalculationInput input, BulletinPaie bp, List<BulletinPaieLigne> lignes) {
        BigDecimal salaireBaseContractuel = zeroIfNull(input.getSalaireBaseContractuel());
        BigDecimal tauxHoraire = input.getTauxHoraireEffectif();

        BigDecimal base = calculerSalaireBase(input, bp, lignes);
        BigDecimal retenueAbsence = calculerRetenueAbsence(input, base, tauxHoraire, bp, lignes);
        BigDecimal congesNet = calculerConges(input, bp, lignes);
        BigDecimal hsEtMaj = calculerMajorationsEtHeuresSup(input, tauxHoraire, bp, lignes);
        BigDecimal primes = calculerPrimes(input, salaireBaseContractuel, bp, lignes);

        BigDecimal totalGainsBruts = base.subtract(retenueAbsence).add(congesNet).add(hsEtMaj).add(primes);
        totalGainsBruts = MoneyRoundingPolicy.amount(totalGainsBruts.max(BigDecimal.ZERO));
        bp.setTotalGainsBruts(totalGainsBruts);
        return totalGainsBruts;
    }

    public BigDecimal calculerSalaireBase(PayrollCalculationInput input, BulletinPaie bp, List<BulletinPaieLigne> lignes) {
        BigDecimal salaireBaseContractuel = zeroIfNull(input.getSalaireBaseContractuel());
        BigDecimal salaireBaseLigne = salaireBaseContractuel;

        if (input.isEntreeSortieEnCoursDeMois()) {
            if (input.getJoursSousContrat() == null || input.getJoursSousContrat().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("PAYROLL_CONTRACT_PERIOD_DATA_MISSING: prorata requis mais joursSousContrat absent ou invalide");
            }
            // Règle canonique GALAXAM : BASE PRORATISÉE = BASE MENSUELLE * JOURS CALENDAIRES SOUS CONTRAT / 30
            salaireBaseLigne = MoneyRoundingPolicy.amount(
                    salaireBaseContractuel.multiply(input.getJoursSousContrat())
                            .divide(new BigDecimal("30"), 4, RoundingMode.HALF_UP)
            );
        }

        lignes.add(creerLigne(bp, "SALAIRE_BASE", "Salaire de Base", "GAIN", "SALAIRE_BASE",
                salaireBaseContractuel, null, salaireBaseLigne, null, 10));
        return salaireBaseLigne;
    }

    public BigDecimal calculerRetenueAbsence(PayrollCalculationInput input, BigDecimal salaireBaseLigne, BigDecimal tauxHoraire,
                                              BulletinPaie bp, List<BulletinPaieLigne> lignes) {
        BigDecimal nbHeuresAbsence = zeroIfNull(input.getNbHeuresAbsence());
        BigDecimal montantAbsence = BigDecimal.ZERO;

        if (nbHeuresAbsence.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diviseur = input.getDiviseurMensuelHeuresEffectif();
            BigDecimal baseCalcul = zeroIfNull(input.getSalaireBasePlein());

            // Précision exacte sans setScale(2) prématuré :
            // SalaireBase / DiviseurMensuel * Minutes / 60
            if (input.getAttendanceInput() != null && input.getAttendanceInput().unpaidAbsenceMinutes() > 0) {
                long minutesAbsence = input.getAttendanceInput().unpaidAbsenceMinutes();
                BigDecimal montantRaw = baseCalcul
                        .multiply(BigDecimal.valueOf(minutesAbsence))
                        .divide(diviseur.multiply(new BigDecimal("60")), 8, RoundingMode.HALF_UP);
                montantAbsence = MoneyRoundingPolicy.amount(montantRaw);
            } else {
                BigDecimal montantRaw = baseCalcul
                        .multiply(nbHeuresAbsence)
                        .divide(diviseur, 8, RoundingMode.HALF_UP);
                montantAbsence = MoneyRoundingPolicy.amount(montantRaw);
            }

            montantAbsence = montantAbsence.min(salaireBaseLigne);
            if (montantAbsence.compareTo(BigDecimal.ZERO) > 0) {
                String comm = String.format("Absence retenue : %s h (Diviseur : %s h, Taux : %s Ar/h)",
                        nbHeuresAbsence, diviseur, MoneyRoundingPolicy.amount(tauxHoraire));
                lignes.add(creerLigne(bp, "RETENUE_ABSENCE", "Retenue pour Absences Décomptées (Heures réelles)", "RETENUE", "SALAIRE_BASE",
                        tauxHoraire, null, nbHeuresAbsence, montantAbsence, null, comm, 20));
            }
        }
        return montantAbsence;
    }

    public BigDecimal calculerConges(PayrollCalculationInput input, BulletinPaie bp, List<BulletinPaieLigne> lignes) {
        BigDecimal nbJoursConges = zeroIfNull(input.getNbJoursConges());
        if (nbJoursConges.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal salaireBaseContractuel = zeroIfNull(input.getSalaireBaseContractuel());
        BigDecimal tauxJournalierTrentieme = input.getTauxJournalierTrentieme();
        BigDecimal deductionCongeTrentieme = MoneyRoundingPolicy.amount(nbJoursConges.multiply(tauxJournalierTrentieme));

        String commDed = String.format("Déduction base : %s j × (%s Ar / 30)", nbJoursConges, MoneyRoundingPolicy.amount(salaireBaseContractuel));
        lignes.add(creerLigne(bp, "DEDUCTION_CONGE_BASE", "Déduction Salaire Jours de Congé (1/30ème)", "RETENUE", "SALAIRE_BASE",
                salaireBaseContractuel, null, nbJoursConges, deductionCongeTrentieme, null, commDed, 25));

        BigDecimal tauxJournalierConge = input.getTauxJournalierConge() != null && input.getTauxJournalierConge().compareTo(BigDecimal.ZERO) > 0
                ? input.getTauxJournalierConge()
                : tauxJournalierTrentieme;
        BigDecimal indemniteConges = MoneyRoundingPolicy.amount(nbJoursConges.multiply(tauxJournalierConge));

        String commInd = String.format("Indemnité congés : %s j × TCONGE %s Ar", nbJoursConges, MoneyRoundingPolicy.amount(tauxJournalierConge));
        lignes.add(creerLigne(bp, "INDEMNITE_CONGE_PAYE", "Indemnité de Congés Payés (Maintien de salaire)", "GAIN", "SALAIRE_BASE",
                tauxJournalierConge, null, nbJoursConges, indemniteConges, null, commInd, 26));

        return indemniteConges.subtract(deductionCongeTrentieme);
    }

    public BigDecimal calculerMajorationsEtHeuresSup(PayrollCalculationInput input, BigDecimal tauxHoraire,
                                                      BulletinPaie bp, List<BulletinPaieLigne> lignes) {
        BigDecimal total = BigDecimal.ZERO;
        OvertimePayCalculator overtimeCalculator = new OvertimePayCalculator();
        OvertimePayCalculator.Result overtime = overtimeCalculator.calculate(input, tauxHoraire);

        BigDecimal heuresHs30 = zeroIfNull(input.getHeuresHs30());
        if (heuresHs30.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal montantHs30 = overtime.hs30();
            BigDecimal tauxHs30 = BigDecimal.ONE.add(zeroIfNull(input.getMajorationHs30())).multiply(new BigDecimal("100"));
            lignes.add(creerLigne(bp, "HEURES_SUP_30", "Heures Supplémentaires (+" + tauxHs30.subtract(new BigDecimal("100")).stripTrailingZeros().toPlainString() + "%)" + dafOvertimeLabel(input, true), "GAIN", "HEURES_SUP",
                    tauxHoraire, tauxHs30, heuresHs30, montantHs30, null, 30));
            total = total.add(montantHs30);
        }

        BigDecimal heuresHs50 = zeroIfNull(input.getHeuresHs50());
        if (heuresHs50.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal montantHs50 = overtime.hs50();
            BigDecimal tauxHs50 = BigDecimal.ONE.add(zeroIfNull(input.getMajorationHs50())).multiply(new BigDecimal("100"));
            lignes.add(creerLigne(bp, "HEURES_SUP_50", "Heures Supplémentaires (+" + tauxHs50.subtract(new BigDecimal("100")).stripTrailingZeros().toPlainString() + "%)" + dafOvertimeLabel(input, false), "GAIN", "HEURES_SUP",
                    tauxHoraire, tauxHs50, heuresHs50, montantHs50, null, 40));
            total = total.add(montantHs50);
        }

        BigDecimal heuresNuit = zeroIfNull(input.getHeuresNuit());
        if (heuresNuit.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal montantNuit = overtime.nuit();
            BigDecimal tauxNuit = zeroIfNull(input.getMajorationNuit()).multiply(new BigDecimal("100"));
            lignes.add(creerLigne(bp, "MAJORATION_NUIT", "Majoration Travail de Nuit", "GAIN", "HEURES_SUP",
                    tauxHoraire, tauxNuit, heuresNuit, montantNuit, null, 50));
            total = total.add(montantNuit);
        }

        BigDecimal heuresDimanche = zeroIfNull(input.getHeuresDimanche());
        if (heuresDimanche.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal montantDimanche = overtime.dimanche();
            BigDecimal baseTauxDimanche = zeroIfNull(input.getMajorationDimanche())
                    .multiply(new BigDecimal("100"));
            lignes.add(creerLigne(bp, "HEURES_DIMANCHE", "Majoration Travail Dimanche", "GAIN", "HEURES_SUP",
                    tauxHoraire, baseTauxDimanche, heuresDimanche, montantDimanche, null, 60));
            total = total.add(montantDimanche);
        }

        BigDecimal heuresFerie = zeroIfNull(input.getHeuresFeriees());
        if (heuresFerie.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal montantFerie = overtime.ferie();
            BigDecimal tauxFerie = zeroIfNull(input.getMajorationFerie()).multiply(new BigDecimal("100"));
            lignes.add(creerLigne(bp, "MAJORATION_FERIE", "Majoration Travail Jour Férié", "GAIN", "HEURES_SUP",
                    tauxHoraire, tauxFerie, heuresFerie, montantFerie, null, 70));
            total = total.add(montantFerie);
        }

        return total;
    }

    public record OvertimeExemptionDetail(
            BigDecimal totalHsBrut,
            BigDecimal hsExonereeIrsa,
            BigDecimal hsImposableIrsa,
            BigDecimal heuresExonerees,
            BigDecimal heuresImposables
    ) {}

    /**
     * Calcule l'exonération fiscale des heures supplémentaires selon l'Article 01.03.03-9° du CGI :
     * "Les rémunérations perçues au titre des heures supplémentaires effectuées dans la limite de
     * 20 heures par mois, à l'exclusion de celles perçues par le personnel cadre."
     */
    public OvertimeExemptionDetail calculerExonerationFiscaleHs(
            PayrollCalculationInput input, BigDecimal tauxHoraire, BulletinPaie bp, List<BulletinPaieLigne> lignes) {
        BigDecimal h30 = zeroIfNull(input.getHeuresHs30());
        BigDecimal h50 = zeroIfNull(input.getHeuresHs50());
        BigDecimal totalHeuresHs = h30.add(h50);

        OvertimePayCalculator overtimeCalculator = new OvertimePayCalculator();
        OvertimePayCalculator.Result overtime = overtimeCalculator.calculate(input, tauxHoraire);
        BigDecimal totalHsBrut = overtime.hs30().add(overtime.hs50());

        BigDecimal plafond = input.getPlafondMensuelHsExonerees();
        if (!input.isAppliquerExonerationHs()
                || plafond == null
                || plafond.compareTo(BigDecimal.ZERO) <= 0
                || totalHeuresHs.compareTo(BigDecimal.ZERO) <= 0) {
            return new OvertimeExemptionDetail(totalHsBrut, BigDecimal.ZERO, totalHsBrut, BigDecimal.ZERO, totalHeuresHs);
        }

        // Imputation chronologique légale : tranche 30% d'abord, puis tranche 50%
        BigDecimal exo30Heures = h30.min(plafond);
        BigDecimal restePlafond = plafond.subtract(exo30Heures).max(BigDecimal.ZERO);
        BigDecimal exo50Heures = h50.min(restePlafond);

        BigDecimal totalHeuresExo = exo30Heures.add(exo50Heures);
        BigDecimal totalHeuresImp = totalHeuresHs.subtract(totalHeuresExo).max(BigDecimal.ZERO);

        BigDecimal txHs30 = BigDecimal.ONE.add(zeroIfNull(input.getMajorationHs30()));
        BigDecimal txHs50 = BigDecimal.ONE.add(zeroIfNull(input.getMajorationHs50()));

        BigDecimal remunExo30 = MoneyRoundingPolicy.amount(exo30Heures.multiply(tauxHoraire).multiply(txHs30));
        BigDecimal remunExo50 = MoneyRoundingPolicy.amount(exo50Heures.multiply(tauxHoraire).multiply(txHs50));
        BigDecimal totalExonereIrsa = remunExo30.add(remunExo50).min(totalHsBrut);
        BigDecimal totalImposableIrsa = totalHsBrut.subtract(totalExonereIrsa).max(BigDecimal.ZERO);

        if (totalExonereIrsa.compareTo(BigDecimal.ZERO) > 0) {
            String comm = String.format("Exonération IRSA Art. 01.03.03-9° CGI : %s h (Plafond %sh/mois)", totalHeuresExo, plafond.stripTrailingZeros().toPlainString());
            lignes.add(creerLigne(bp, "HS_EXONEREE_IRSA", "Heures Supplémentaires Exonérées IRSA (CGI 01.03.03-9°)", "INFO", "HEURES_SUP",
                    tauxHoraire, null, totalHeuresExo, totalExonereIrsa, null, comm, 75));
        }
        if (totalImposableIrsa.compareTo(BigDecimal.ZERO) > 0) {
            String comm = String.format("HS Imposables IRSA : %s h au-delà du plafond mensuel de %sh", totalHeuresImp, plafond.stripTrailingZeros().toPlainString());
            lignes.add(creerLigne(bp, "HS_IMPOSABLE_IRSA", "Heures Supplémentaires Imposables IRSA", "INFO", "HEURES_SUP",
                    tauxHoraire, null, totalHeuresImp, totalImposableIrsa, null, comm, 76));
        }

        return new OvertimeExemptionDetail(totalHsBrut, totalExonereIrsa, totalImposableIrsa, totalHeuresExo, totalHeuresImp);
    }

    public BigDecimal calculerPrimes(PayrollCalculationInput input, BigDecimal salaireBaseContractuel,
                                      BulletinPaie bp, List<BulletinPaieLigne> lignes) {
        BigDecimal totalPrimes = BigDecimal.ZERO;

        BigDecimal primePerfBase = zeroIfNull(input.getPrimePerformanceBase());
        if (primePerfBase.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal nbJoursOuvrables = zeroIfNull(input.getNbJoursOuvrables());
            BigDecimal nbPresents = zeroIfNull(input.getNbJoursPresents());
            BigDecimal ratioPresence = nbJoursOuvrables.compareTo(BigDecimal.ZERO) > 0
                    ? nbPresents.divide(nbJoursOuvrables, 4, RoundingMode.HALF_UP).min(BigDecimal.ONE)
                    : BigDecimal.ONE;

            BigDecimal mntPrimePerf = MoneyRoundingPolicy.amount(primePerfBase.multiply(ratioPresence));

            boolean penaliteApplicable = (input.getMinutesRetard() > input.getSeuilRetardMalusPerformanceMinutes())
                    && (zeroIfNull(input.getNbJoursConges()).compareTo(zeroIfNull(input.getSeuilCongesMalusPerformanceJours())) > 0);

            BigDecimal malusPp = BigDecimal.ZERO;
            if (penaliteApplicable && input.getTauxMalusPerformance() != null && input.getTauxMalusPerformance().compareTo(BigDecimal.ZERO) > 0) {
                malusPp = MoneyRoundingPolicy.amount(mntPrimePerf.multiply(input.getTauxMalusPerformance()));
            }

            BigDecimal mntPrimePerfFinal = mntPrimePerf.subtract(malusPp);

            String libellePp = malusPp.compareTo(BigDecimal.ZERO) > 0
                    ? "Prime de Performance (Proratisée - Pénalité " + input.getTauxMalusPerformance().multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString() + "% Retard/Congé)"
                    : "Prime de Performance (Proratisée)";

            lignes.add(creerLigne(bp, "PRIME_PERFORMANCE", libellePp, "GAIN", "PRIME",
                    primePerfBase, ratioPresence.multiply(new BigDecimal("100")), mntPrimePerfFinal, null, 90));
            totalPrimes = totalPrimes.add(mntPrimePerfFinal);
        }

        BigDecimal primeAssiduite = zeroIfNull(input.getPrimeAssiduiteForfait());
        int seuilAssiduite = input.getSeuilRetardAssiduiteMinutes() > 0 ? input.getSeuilRetardAssiduiteMinutes() : 45;
        if (primeAssiduite.compareTo(BigDecimal.ZERO) > 0 && input.getMinutesRetard() < seuilAssiduite) {
            lignes.add(creerLigne(bp, "PRIME_ASSIDUITE", "Prime d'Assiduité", "GAIN", "PRIME",
                    primeAssiduite, null, primeAssiduite, null, 100));
            totalPrimes = totalPrimes.add(primeAssiduite);
        }

        if (input.isCalculerPrimeAncienneteAuto() && input.getAncienneteMois() >= 36) {
            int annees = input.getAncienneteMois() / 12;
            BigDecimal tauxAnciennete = BigDecimal.valueOf(Math.min(annees, 20) * 0.01);
            BigDecimal primeAnc = MoneyRoundingPolicy.amount(salaireBaseContractuel.multiply(tauxAnciennete));
            if (primeAnc.compareTo(BigDecimal.ZERO) > 0) {
                lignes.add(creerLigne(bp, "PRIME_ANCIENNETE", "Prime d'Ancienneté (" + annees + " ans)", "GAIN", "PRIME",
                        salaireBaseContractuel, tauxAnciennete.multiply(new BigDecimal("100")), primeAnc, null, 95));
                totalPrimes = totalPrimes.add(primeAnc);
            }
        }

        BigDecimal primeExceptionnelle = zeroIfNull(input.getPrimeExceptionnelle());
        if (primeExceptionnelle.compareTo(BigDecimal.ZERO) > 0) {
            String libelle = input.getMotifPrimeExceptionnelle() != null && !input.getMotifPrimeExceptionnelle().isBlank()
                    ? "Prime Exceptionnelle : " + input.getMotifPrimeExceptionnelle()
                    : "Prime Exceptionnelle";
            lignes.add(creerLigne(bp, "PRIME_EXCEPTIONNELLE", libelle, "GAIN", "PRIME",
                    primeExceptionnelle, null, primeExceptionnelle, null, 110));
            totalPrimes = totalPrimes.add(primeExceptionnelle);
        }

        BigDecimal regularisationPaie = zeroIfNull(input.getRegularisationPaie());
        if (regularisationPaie.compareTo(BigDecimal.ZERO) > 0) {
            String libelle = input.getMotifRegularisationPaie() != null && !input.getMotifRegularisationPaie().isBlank()
                    ? "Régularisation : " + input.getMotifRegularisationPaie()
                    : "Régularisation Congés (Restitution Absence)";
            lignes.add(creerLigne(bp, "REGULARISATION_PAIE", libelle, "GAIN", "SALAIRE_BASE",
                    regularisationPaie, null, regularisationPaie, null, 112));
            totalPrimes = totalPrimes.add(regularisationPaie);
        }

        BigDecimal autimp = zeroIfNull(input.getAutimp());
        if (autimp.compareTo(BigDecimal.ZERO) > 0) {
            lignes.add(creerLigne(bp, "AUTIMP", "Autres Gains Imposables (AUTIMP)", "GAIN", "PRIME",
                    autimp, null, autimp, null, 115));
            totalPrimes = totalPrimes.add(autimp);
        }

        // Repas et déplacement payés en fin de mois : gains bruts imposables
        // et cotisables, séparés des primes et exclus de la valorisation congés.
        BigDecimal repas = zeroIfNull(input.getIndemniteRepas());
        if (repas.signum() > 0) {
            BulletinPaieLigne ligne = creerLigne(bp, "INDEMNITE_REPAS", "Indemnité de repas", "GAIN", "INDEMNITE_IMPOSABLE",
                    repas, null, repas, null, 510);
            ligne.setImposable(true);
            ligne.setSoumisCotisation(true);
            lignes.add(ligne);
            totalPrimes = totalPrimes.add(repas);
        }
        BigDecimal transport = zeroIfNull(input.getIndemniteTransport());
        if (transport.signum() > 0) {
            BulletinPaieLigne ligne = creerLigne(bp, "INDEMNITE_TRANSPORT", "Indemnité de déplacement", "GAIN", "INDEMNITE_IMPOSABLE",
                    transport, null, transport, null, 500);
            ligne.setImposable(true);
            ligne.setSoumisCotisation(true);
            lignes.add(ligne);
            totalPrimes = totalPrimes.add(transport);
        }

        // Primes et gains dynamiques imposables (configurés en BDD dans ref_type_mouvement_paie)
        if (input.getGainsVariablesDynamiques() != null) {
            for (PayrollCalculationInput.GainVariable gv : input.getGainsVariablesDynamiques()) {
                if (gv.getMontant() != null && gv.getMontant().compareTo(BigDecimal.ZERO) > 0 && gv.isImposable()) {
                    String cat = gv.getCategorie() != null ? gv.getCategorie() : "PRIME";
                    String lib = gv.getLibelle() != null && !gv.getLibelle().isBlank() ? gv.getLibelle() : gv.getCode();
                    BulletinPaieLigne ligne = creerLigne(bp, gv.getCode(), lib, "GAIN", cat,
                            gv.getMontant(), null, gv.getMontant(), null, 120);
                    ligne.setImposable(gv.isImposable());
                    ligne.setSoumisCotisation(gv.isSoumisCotisation());
                    lignes.add(ligne);
                    totalPrimes = totalPrimes.add(gv.getMontant());
                }
            }
        }

        return totalPrimes;
    }

    // =========================================================================
    // 3. COTISATIONS SOCIALES (Palier 2)
    // =========================================================================

    public CotisationsResult calculerCotisationsSociales(BigDecimal totalGainsBruts, PayrollCalculationInput input,
                                                         BulletinPaie bp, List<BulletinPaieLigne> lignes) {
        BigDecimal totalCotisSalariales = MoneyRoundingPolicy.amount(BigDecimal.ZERO);
        BigDecimal totalCotisPatronales = MoneyRoundingPolicy.amount(BigDecimal.ZERO);

        // L'assujettissement social est indépendant de l'imposition IRSA.
        BigDecimal assietteSociale = totalGainsBruts;
        if (input.getGainsVariablesDynamiques() != null) {
            for (PayrollCalculationInput.GainVariable gain : input.getGainsVariablesDynamiques()) {
                if (gain.getMontant() == null || gain.getMontant().signum() <= 0) continue;
                if (gain.isImposable() && !gain.isSoumisCotisation()) {
                    assietteSociale = assietteSociale.subtract(gain.getMontant());
                } else if (!gain.isImposable() && gain.isSoumisCotisation()) {
                    assietteSociale = assietteSociale.add(gain.getMontant());
                }
            }
        }
        assietteSociale = assietteSociale.max(BigDecimal.ZERO);

        BigDecimal sme = zeroIfNull(input.getSmeNational());
        BigDecimal plafondCnaps = sme.multiply(MULTIPLICATEUR_PLAFOND_CNAPS);
        SocialContributionCalculator socialCalculator = new SocialContributionCalculator();
        SocialContributionCalculator.Result social = socialCalculator.calculate(assietteSociale,
                new SocialContributionCalculator.Rules(input.getTauxCnapsSalarialDecimal(), input.getTauxCnapsPatronalDecimal(),
                        input.getTauxSanteSalarialDecimal(), input.getTauxSantePatronalDecimal(), input.getTauxFmfpPatronalDecimal(), sme, input.isEstPlafonneSante()));

        // 2.1 CNaPS (plafonnée à 8x SME) & FMFP Patronale (1% sur brut total)
        if (input.isSoumisCnaps()) {
            SocialContributionCalculator.Contribution resCnaps = social.cnaps();
            BigDecimal assietteCnaps = resCnaps.assiette();
            BigDecimal cnapsSal = resCnaps.partSalariale();
            BigDecimal cnapsPat = resCnaps.partPatronale();

            lignes.add(creerLigne(bp, "COTISATION_CNAPS_SAL", "Cotisation CNaPS Salariale (1%)", "RETENUE", "COTISATION_SALARIALE",
                    assietteCnaps, input.getTauxCnapsSalarialDecimal().movePointRight(2), cnapsSal, null, 200));
            lignes.add(creerLigne(bp, "COTISATION_CNAPS_PAT", "Cotisation CNaPS Patronale (13%)", "PATRONAL", "COTISATION_PATRONALE",
                    assietteCnaps, input.getTauxCnapsPatronalDecimal().movePointRight(2), null, cnapsPat, 300));

            totalCotisSalariales = totalCotisSalariales.add(cnapsSal);
            totalCotisPatronales = totalCotisPatronales.add(cnapsPat);

            // 2.3 FMFP Patronale (1% sur brut total)
            BigDecimal fmfpPat = MoneyRoundingPolicy.contribution(social.fmfp());
            lignes.add(creerLigne(bp, "COTISATION_FMFP_PAT", "Cotisation FMFP Patronale (1%)", "PATRONAL", "COTISATION_PATRONALE",
                    assietteSociale, input.getTauxFmfpPatronalDecimal().movePointRight(2), null, fmfpPat, 330));
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
                    ? assietteSociale.min(plafondCnaps)
                    : assietteSociale;

            SocialContributionCalculator.Contribution resSante = social.health();
            BigDecimal santeSal = resSante.partSalariale();
            BigDecimal santePat = resSante.partPatronale();

            lignes.add(creerLigne(bp, codeSanteSal, libelleSanteSal, "RETENUE", "COTISATION_SALARIALE",
                    assietteSante, input.getTauxSanteSalarialDecimal().movePointRight(2), santeSal, null, 210));
            lignes.add(creerLigne(bp, codeSantePat, libelleSantePat, "PATRONAL", "COTISATION_PATRONALE",
                    assietteSante, input.getTauxSantePatronalDecimal().movePointRight(2), null, santePat, 310));

            totalCotisSalariales = totalCotisSalariales.add(santeSal);
            totalCotisPatronales = totalCotisPatronales.add(santePat);
        }

        bp.setTotalCotisationsSalariales(totalCotisSalariales);
        bp.setTotalCotisationsPatronales(totalCotisPatronales);

        return new CotisationsResult(totalCotisSalariales, totalCotisPatronales);
    }

    // =========================================================================
    // 4. NET IMPOSABLE (Palier 3)
    // =========================================================================

    public BigDecimal calculerNetImposable(BigDecimal totalGainsBruts, BigDecimal totalCotisSalariales, BulletinPaie bp) {
        BigDecimal revenuNetAvantImpot = totalGainsBruts.subtract(totalCotisSalariales).max(BigDecimal.ZERO);
        bp.setSalaireNetImposable(revenuNetAvantImpot);
        bp.setRevenuNetImposable(revenuNetAvantImpot);
        return revenuNetAvantImpot;
    }

    // =========================================================================
    // 5. FISCALITÉ — IRSA OU ISI 5% (Palier 4)
    // =========================================================================

    public FiscaliteResult calculerFiscalite(BigDecimal salaireNetImposable, BigDecimal totalGainsBruts,
                                            PayrollCalculationInput input, BulletinPaie bp, List<BulletinPaieLigne> lignes) {
        return calculerFiscalite(salaireNetImposable, BigDecimal.ZERO, totalGainsBruts, input, bp, lignes);
    }

    public FiscaliteResult calculerFiscalite(BigDecimal salaireNetImposable, BigDecimal hsExonereeIrsa, BigDecimal totalGainsBruts,
                                            PayrollCalculationInput input, BulletinPaie bp, List<BulletinPaieLigne> lignes) {
        BigDecimal irsaNet = MoneyRoundingPolicy.tax(BigDecimal.ZERO);
        BigDecimal retenueIsi = MoneyRoundingPolicy.tax(BigDecimal.ZERO);
        IrsaCalculator.Result detailIrsa = null;

        if (input.isSoumisIsi5()) {
            // Régime Prestataire / Consultant : Retenue libératoire ISI directe de 5%
            BigDecimal tauxIsi = input.getTauxIsi();
            if (tauxIsi == null) {
                throw new IllegalStateException("TAX_CONFIGURATION_MISSING: ISI");
            }
            retenueIsi = new IsiCalculator().calculate(totalGainsBruts, tauxIsi);
            lignes.add(creerLigne(bp, "RETENUE_ISI_5", "Retenue Libératoire ISI (" + tauxIsi.stripTrailingZeros().toPlainString() + "%)", "RETENUE", "IMPOT",
                    totalGainsBruts, tauxIsi, retenueIsi, null, 430));
            bp.setRetenueIsi5(retenueIsi);
            bp.setIrsaNet(MoneyRoundingPolicy.tax(BigDecimal.ZERO));
        } else if (input.isSoumisIrsa()) {
            // Assiette IRSA Fiscale : Net Imposable Social - Rémunération des HS exonérées (CGI Art. 01.03.03-9°)
            BigDecimal assietteFiscale = salaireNetImposable.subtract(zeroIfNull(hsExonereeIrsa)).max(BigDecimal.ZERO);
            bp.setRevenuNetImposable(assietteFiscale);

            // Régime Salarié : Barème progressif IRSA unifié (6 tranches, min 3 000 Ar)
            detailIrsa = new IrsaCalculator().calculate(
                    assietteFiscale,
                    input.getTranchesIrsa(),
                    input.getNbEnfantsEligibles(),
                    input.getDeductionParEnfant(),
                    input.getMinimumPerceptionIrsa()
            );
            var irsaRes = detailIrsa;

            bp.setBaseIrsaArrondie(irsaRes.baseIrsaArrondie());
            bp.setIrsaBrut(irsaRes.irsaBrut());
            bp.setReductionEnfants(irsaRes.reductionEnfants());

            irsaNet = MoneyRoundingPolicy.tax(irsaRes.irsaNet().min(salaireNetImposable));

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
            bp.setRetenueIsi5(MoneyRoundingPolicy.tax(BigDecimal.ZERO));
        } else {
            bp.setIrsaNet(MoneyRoundingPolicy.tax(BigDecimal.ZERO));
            bp.setRetenueIsi5(MoneyRoundingPolicy.tax(BigDecimal.ZERO));
        }

        return new FiscaliteResult(irsaNet, retenueIsi, detailIrsa);
    }

    // =========================================================================
    // 6. INDEMNITÉS NON IMPOSABLES & FRAIS (Palier 5)
    // =========================================================================

    public BigDecimal calculerIndemnitesNonImposables(PayrollCalculationInput input, BulletinPaie bp, List<BulletinPaieLigne> lignes) {
        BigDecimal totalIndemnitesNet = BigDecimal.ZERO;

        // 6.1 Extra déplacement
        BigDecimal extraDepl = zeroIfNull(input.getExtraDeplacement());
        if (extraDepl.compareTo(BigDecimal.ZERO) > 0) {
            lignes.add(creerLigne(bp, "EXTRA_DEPLACEMENT", "Indemnité Extra Déplacement", "GAIN", "INDEMNITE_NON_IMPOSABLE",
                    extraDepl, null, extraDepl, null, 505));
            totalIndemnitesNet = totalIndemnitesNet.add(extraDepl);
        }

        // 6.4 Notes de frais remboursées
        BigDecimal notesFrais = zeroIfNull(input.getRemboursementNotesFrais());
        if (notesFrais.compareTo(BigDecimal.ZERO) > 0) {
            lignes.add(creerLigne(bp, "REMBOURSEMENT_NOTE_FRAIS", "Remboursement Notes de Frais Validées", "GAIN", "INDEMNITE_NON_IMPOSABLE",
                    notesFrais, null, notesFrais, null, 520));
            totalIndemnitesNet = totalIndemnitesNet.add(notesFrais);
        }

        // 6.5 Indemnités et gains dynamiques non imposables
        if (input.getGainsVariablesDynamiques() != null) {
            for (PayrollCalculationInput.GainVariable gv : input.getGainsVariablesDynamiques()) {
                if (gv.getMontant() != null && gv.getMontant().compareTo(BigDecimal.ZERO) > 0 && !gv.isImposable()) {
                    String cat = gv.getCategorie() != null ? gv.getCategorie() : "INDEMNITE_NON_IMPOSABLE";
                    String lib = gv.getLibelle() != null && !gv.getLibelle().isBlank() ? gv.getLibelle() : gv.getCode();
                    BulletinPaieLigne ligne = creerLigne(bp, gv.getCode(), lib, "GAIN", cat,
                            gv.getMontant(), null, gv.getMontant(), null, 530);
                    ligne.setImposable(gv.isImposable());
                    ligne.setSoumisCotisation(gv.isSoumisCotisation());
                    lignes.add(ligne);
                    totalIndemnitesNet = totalIndemnitesNet.add(gv.getMontant());
                }
            }
        }

        bp.setTotalIndemnitesNonImposables(totalIndemnitesNet);
        return totalIndemnitesNet;
    }

    // =========================================================================
    // 7. RETENUES DE TRÉSORERIE & QUOTITÉ CESSIBLE (Palier 6)
    // =========================================================================

    public RetenuesResult calculerRetenuesEtAvances(BigDecimal netAvantTresorerie, PayrollCalculationInput input,
                                                   BulletinPaie bp, List<BulletinPaieLigne> lignes) {
        BigDecimal totalAcomptesQuinzaine = BigDecimal.ZERO;
        BigDecimal totalAvancesSpeciales = BigDecimal.ZERO;
        List<MouvementImputation> imputations = new ArrayList<>();

        // Traitement prioritaire des acomptes de quinzaine (salaire échu)
        BigDecimal soldeDisponibleAcompte = netAvantTresorerie;
        if (input.getAvancesAcomptes() != null) {
            for (PaieAvanceAcompte a : input.getAvancesAcomptes()) {
                if (a.getMontant() == null || a.getMontant().compareTo(BigDecimal.ZERO) <= 0) continue;

                String type = a.getTypeMouvement() != null ? a.getTypeMouvement() : "ACOMPTE_15";
                boolean isAcompteQuinzaine = "ACOMPTE_15".equalsIgnoreCase(type) || "AVANCE_QUINZAINE".equalsIgnoreCase(type);

                if (isAcompteQuinzaine) {
                    String codeRubrique = mapRubriqueAvance(type);
                    String libelle = a.getMotif() != null && !a.getMotif().isBlank() ? a.getMotif() : "Acompte sur salaire";

                    BigDecimal mntAcompte = MoneyRoundingPolicy.amount(a.getMontant().min(soldeDisponibleAcompte));
                    if (mntAcompte.compareTo(BigDecimal.ZERO) > 0) {
                        lignes.add(creerLigne(bp, codeRubrique, libelle, "RETENUE", "ACOMPTE_AVANCE",
                                a.getMontant(), null, mntAcompte, null, 600));
                        totalAcomptesQuinzaine = totalAcomptesQuinzaine.add(mntAcompte);
                        soldeDisponibleAcompte = soldeDisponibleAcompte.subtract(mntAcompte).max(BigDecimal.ZERO);
                    }
                    if (a.getIdAvance() != null && mntAcompte.compareTo(BigDecimal.ZERO) > 0) {
                        imputations.add(new MouvementImputation(a.getIdAvance(), mntAcompte));
                    }
                }
            }
        }

        BigDecimal netApresAcompte = netAvantTresorerie.subtract(totalAcomptesQuinzaine).max(BigDecimal.ZERO);

        // Plafond Art. 68 : Remboursement d'avances consenti par l'employeur max 1/10e (10%)
        BigDecimal plafondDixiemeAvances = MoneyRoundingPolicy.amount(netApresAcompte.multiply(PLAFOND_AVANCE_EMPLOYEUR_DIXIEME));

        if (input.getAvancesAcomptes() != null) {
            for (PaieAvanceAcompte a : input.getAvancesAcomptes()) {
                if (a.getMontant() == null || a.getMontant().compareTo(BigDecimal.ZERO) <= 0) continue;

                String type = a.getTypeMouvement() != null ? a.getTypeMouvement() : "";
                boolean isAvanceSpeciale = "AVANCE_SPECIALE".equalsIgnoreCase(type);

                if (isAvanceSpeciale) {
                    String codeRubrique = mapRubriqueAvance(type);
                    String libelle = a.getMotif() != null && !a.getMotif().isBlank() ? a.getMotif() : "Avance sur salaire (Art. 68)";

                    BigDecimal capaciteRestanteAvance = plafondDixiemeAvances.subtract(totalAvancesSpeciales).max(BigDecimal.ZERO);
                    BigDecimal mntAvanceRetenu = MoneyRoundingPolicy.amount(a.getMontant().min(capaciteRestanteAvance));

                    if (mntAvanceRetenu.compareTo(BigDecimal.ZERO) > 0) {
                        lignes.add(creerLigne(bp, codeRubrique, libelle, "RETENUE", "ACOMPTE_AVANCE",
                                a.getMontant(), null, mntAvanceRetenu, null, 610));
                        totalAvancesSpeciales = totalAvancesSpeciales.add(mntAvanceRetenu);
                    }
                    if (a.getIdAvance() != null && mntAvanceRetenu.compareTo(BigDecimal.ZERO) > 0) {
                        imputations.add(new MouvementImputation(a.getIdAvance(), mntAvanceRetenu));
                    }
                }
            }
        }

        // Plafond global de quotité cessible (1/3 du net après acompte Art. 72)
        BigDecimal plafondTiers = MoneyRoundingPolicy.amount(netApresAcompte.multiply(PLAFOND_QUOTITE_CESSIBLE));
        BigDecimal capaciteRestantePret = plafondTiers.subtract(totalAvancesSpeciales).max(BigDecimal.ZERO);

        ImpactPret impactPret = null;
        PaiePretEcheance echeanceActive = input.getEcheancePretActive();
        BigDecimal montantPrelevePret = BigDecimal.ZERO;

        if (echeanceActive != null) {
            BigDecimal mensualiteTheorique = echeanceActive.getMontantRestantEcheance() != null
                    ? echeanceActive.getMontantRestantEcheance()
                    : echeanceActive.getMontantEcheance();

            montantPrelevePret = MoneyRoundingPolicy.amount(mensualiteTheorique.min(capaciteRestantePret));
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

        // Autres retenues directes sur salaire (CALL PERSO, RETENUE DIVERSE)
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
                    BigDecimal mntRetenu = MoneyRoundingPolicy.amount(a.getMontant().min(soldeDisponible));

                    if (mntRetenu.compareTo(BigDecimal.ZERO) > 0) {
                        int ordre = "CALL_PERSO".equalsIgnoreCase(type) ? 630 : 640;
                        lignes.add(creerLigne(bp, codeRubrique, libelle, "RETENUE", "RETENUE_DIVERSE",
                                a.getMontant(), null, mntRetenu, null, ordre));
                        totalAutresRetenues = totalAutresRetenues.add(mntRetenu);
                    }
                    if (a.getIdAvance() != null && mntRetenu.compareTo(BigDecimal.ZERO) > 0) {
                        imputations.add(new MouvementImputation(a.getIdAvance(), mntRetenu));
                    }
                }
            }
        }

        BigDecimal totalRetenuesTresorerie = totalAcomptesQuinzaine.add(totalAvancesSpeciales).add(montantPrelevePret).add(totalAutresRetenues);
        bp.setTotalRetenuesAcomptePret(totalRetenuesTresorerie);

        return new RetenuesResult(totalRetenuesTresorerie, impactPret, imputations);
    }

    // =========================================================================
    // 8. NET À PAYER BANCAIRE & ARRONDI MONÉTAIRE (Palier 7)
    // =========================================================================

    public void calculerNetAPayer(BigDecimal totalGainsBruts, BigDecimal totalCotisPatronales,
                                 BigDecimal totalIndemnitesNet, BigDecimal netAvantTresorerie,
                                 BigDecimal totalRetenues, BulletinPaie bp, List<BulletinPaieLigne> lignes) {
        BigDecimal netAvantArrondi = netAvantTresorerie.subtract(totalRetenues);
        bp.setNetAvantArrondi(netAvantArrondi);

        // Arrondi officiel bancaire à la dizaine d'Ariary la plus proche
        BigDecimal netAPayer = BulletinPaie.arrondirDizaineAriary(netAvantArrondi);
        bp.setNetAPayer(netAPayer);

        // Écart d'arrondi monétaire
        BigDecimal ecartArrondi = MoneyRoundingPolicy.netPay(netAPayer.subtract(netAvantArrondi));

        if (ecartArrondi.compareTo(BigDecimal.ZERO) != 0) {
            String sensArrondi = ecartArrondi.compareTo(BigDecimal.ZERO) > 0 ? "GAIN" : "RETENUE";
            BigDecimal montantLigneArrondi = ecartArrondi.abs();

            lignes.add(creerLigne(bp, "AJUSTEMENT_ARRONDI", "Ajustement Arrondi Monétaire (Écart de Règlement)",
                    sensArrondi, "NET", ecartArrondi, null, montantLigneArrondi, null, 999));
        }

        // Coût global employeur = Total Brut + Cotisations Patronales + Indemnités
        BigDecimal coutGlobalEmployeur = MoneyRoundingPolicy.amount(totalGainsBruts.add(totalCotisPatronales).add(totalIndemnitesNet));
        bp.setCoutGlobalEmployeur(coutGlobalEmployeur);
    }

    // =========================================================================
    // UTILITAIRES PRIVÉS
    // =========================================================================

    private BulletinPaieLigne creerLigne(BulletinPaie bp, String codeRubrique, String libelle, String sens,
                                        String categorie, BigDecimal baseCalcul, BigDecimal taux,
                                        BigDecimal mntSal, BigDecimal mntPat, int ordre) {
        return creerLigne(bp, codeRubrique, libelle, sens, categorie, baseCalcul, taux, null, mntSal, mntPat, null, ordre);
    }

    private BulletinPaieLigne creerLigne(BulletinPaie bp, String codeRubrique, String libelle, String sens,
                                        String categorie, BigDecimal baseCalcul, BigDecimal taux,
                                        BigDecimal quantite, BigDecimal mntSal, BigDecimal mntPat, int ordre) {
        return creerLigne(bp, codeRubrique, libelle, sens, categorie, baseCalcul, taux, quantite, mntSal, mntPat, null, ordre);
    }

    private BulletinPaieLigne creerLigne(BulletinPaie bp, String codeRubrique, String libelle, String sens,
                                        String categorie, BigDecimal baseCalcul, BigDecimal taux,
                                        BigDecimal quantite, BigDecimal mntSal, BigDecimal mntPat,
                                        String commentaire, int ordre) {
        BulletinPaieLigne l = new BulletinPaieLigne();
        l.setIdBulletin(bp.getIdBulletin());
        l.setCodeRubrique(codeRubrique);
        l.setLibelleRubrique(libelle);
        l.setSens(sens);
        l.setCategorie(categorie);
        boolean gainBrut = "GAIN".equals(sens) && !"INDEMNITE_NON_IMPOSABLE".equals(categorie);
        l.setImposable(gainBrut);
        l.setSoumisCotisation(gainBrut);
        l.setBaseCalcul(baseCalcul != null ? MoneyRoundingPolicy.amount(baseCalcul) : null);
        l.setTauxSalarial(taux != null ? MoneyRoundingPolicy.amount(taux) : null);
        l.setQuantite(quantite);
        l.setMontantSalarial(mntSal != null ? MoneyRoundingPolicy.amount(mntSal) : null);
        l.setMontantPatronal(mntPat != null ? MoneyRoundingPolicy.amount(mntPat) : null);
        l.setCommentaire(commentaire);
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
        String type = in.getTypeBulletin() != null && "STC".equalsIgnoreCase(in.getTypeBulletin()) ? "STC" : "BP";
        return type + "-" + codePer + "-" + matr;
    }

    private BigDecimal zeroIfNull(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }
    private static String dafOvertimeLabel(PayrollCalculationInput input, boolean firstTier) {
        var attendance = input.getAttendanceInput();
        if (attendance == null) return "";
        long minutes = firstTier ? attendance.dafOvertime30Minutes() : attendance.dafOvertime50Minutes();
        if (minutes == 0) return "";
        return " — dont complément DAF : "
                + mg.galaxam.sirh.paie.domain.dto.PayrollAttendanceInput.minutesToHours(minutes)
                        .stripTrailingZeros().toPlainString() + " h";
    }

}
