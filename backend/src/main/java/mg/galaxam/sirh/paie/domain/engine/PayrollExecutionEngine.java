package mg.galaxam.sirh.paie.domain.engine;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import mg.galaxam.sirh.paie.persistence.PaieAvanceAcompte;
import mg.galaxam.sirh.paie.domain.dto.BulletinPaieComplet;
import mg.galaxam.sirh.paie.domain.dto.BulletinPaieComplet.MouvementImputation;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;

/**
 * Moteur de calcul modulaire, pur et déterministe de paie pour GALAXAM-SIRH.
 * 
 * Décomposé en méthodes spécialisées pour chaque palier de calcul :
 * 1. Salaire brut & gains (Salaire de base, absences, congés, HS, primes)
 * 2. Cotisations sociales (plafond légal daté pour CNaPS et santé selon organisme, FMFP)
 * 3. Net imposable
 * 4. Fiscalité salariale IRSA ; l'ancien champ ISI reste nul en 2026.
 * 5. Remboursements et indemnités réellement non imposables
 * 6. Retenues de trésorerie (acomptes, avances et prêts selon les plafonds configurés)
 * 7. Net à payer bancaire & arrondi monétaire officiel
 */
@Component
public class PayrollExecutionEngine {

    public static final BigDecimal DIVISEUR_HEURES_MENSUELLES = new BigDecimal("173.33");
    public static final BigDecimal DIVISEUR_JOURS_TRENTIEME = new BigDecimal("30.0");

    public record CotisationsResult(BigDecimal totalCotisationsSalariales, BigDecimal totalCotisationsPatronales) {}
    public record FiscaliteResult(BigDecimal irsaNet, BigDecimal retenueIsi, PayrollTaxCalculations.Result detailIrsa) {}
    public record RetenuesResult(BigDecimal totalRetenues, List<MouvementImputation> imputations) {}

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

        // 3.1 Traçabilité des heures supplémentaires imposables.
        OvertimeExemptionDetail hsExo = calculerExonerationFiscaleHs(input, input.getTauxHoraireEffectif(), bp, lignes);

        // 4. Fiscalité salariale IRSA.
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
        BigDecimal tauxHoraire = input.getTauxHoraireEffectif();

        BigDecimal base = calculerSalaireBase(input, bp, lignes);
        BigDecimal retenueAbsence = calculerRetenueAbsence(input, base, tauxHoraire, bp, lignes);
        BigDecimal congesNet = calculerConges(input, bp, lignes);
        BigDecimal hsEtMaj = calculerMajorationsEtHeuresSup(input, tauxHoraire, bp, lignes);
        BigDecimal primes = calculerPrimes(input, input.getSalaireBaseCampagne(), bp, lignes);

        BigDecimal totalGainsBruts = base.subtract(retenueAbsence).add(congesNet).add(hsEtMaj).add(primes);
        totalGainsBruts = MoneyRoundingPolicy.amount(totalGainsBruts.max(BigDecimal.ZERO));
        bp.setTotalGainsBruts(totalGainsBruts);
        return totalGainsBruts;
    }

    public BigDecimal calculerSalaireBase(PayrollCalculationInput input, BulletinPaie bp, List<BulletinPaieLigne> lignes) {
        BigDecimal salaireBaseContractuel = zeroIfNull(input.getSalaireBaseContractuel());
        BigDecimal salaireBaseLigne = salaireBaseContractuel;
        if (!input.getSalaireParJour().isEmpty()) {
            salaireBaseLigne = input.getSalaireBaseCampagne();
        } else if (input.isEntreeSortieEnCoursDeMois()) {
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
            if (input.getSalaireParJour().values().stream().distinct().count() > 1)
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.BLOCKING_CONFIGURATION_ERROR,
                        "PAYROLL_ABSENCE_SALARY_SPLIT_UNRESOLVED: employé " + input.getIdEmploye()
                                + " campagne " + input.getDateDebutPeriode() + " au " + input.getDateFinPeriode()
                                + "; le déficit hebdomadaire figé n'est pas imputé à une date salariale. "
                                + "Arbitrage DAF requis avant calcul.");
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
        BigDecimal deductionCongeTrentieme;
        if (!input.getSalaireParJour().isEmpty()) {
            BigDecimal joursDates = input.getJoursCongeValorisesParDate().values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (joursDates.compareTo(nbJoursConges) != 0)
                throw new IllegalStateException("PAYROLL_LEAVE_DATES_MISSING: déduction congé sans dates de prise");
            BigDecimal somme = BigDecimal.ZERO;
            for (var prise : input.getJoursCongeValorisesParDate().entrySet()) {
                BigDecimal salaire = input.getSalaireParJour().get(prise.getKey());
                if (salaire == null) throw new IllegalStateException(
                        "MISSING_CONFIGURATION: SALAIRE_CONGE au " + prise.getKey());
                somme = somme.add(salaire.multiply(prise.getValue()));
            }
            deductionCongeTrentieme = MoneyRoundingPolicy.amount(
                    somme.divide(new BigDecimal("30"), 8, RoundingMode.HALF_UP));
        } else {
            deductionCongeTrentieme = MoneyRoundingPolicy.amount(nbJoursConges.multiply(tauxJournalierTrentieme));
        }

        String commDed = input.getSalaireParJour().isEmpty()
                ? String.format("Déduction base : %s j × (%s Ar / 30)", nbJoursConges, MoneyRoundingPolicy.amount(salaireBaseContractuel))
                : String.format("Déduction base : %s j aux salaires de leurs dates / 30", nbJoursConges);
        lignes.add(creerLigne(bp, "DEDUCTION_CONGE_BASE", "Déduction Salaire Jours de Congé (1/30ème)", "RETENUE", "SALAIRE_BASE",
                salaireBaseContractuel, null, nbJoursConges, deductionCongeTrentieme, null, commDed, 25));

        BigDecimal tauxJournalierConge = input.getTauxJournalierConge() != null && input.getTauxJournalierConge().compareTo(BigDecimal.ZERO) > 0
                ? input.getTauxJournalierConge()
                : tauxJournalierTrentieme;
        BigDecimal indemniteConges = input.getIndemniteCongeLots() != null
                ? MoneyRoundingPolicy.amount(input.getIndemniteCongeLots())
                : MoneyRoundingPolicy.amount(nbJoursConges.multiply(tauxJournalierConge));

        if (input.getIndemniteCongeLots() != null) {
            tauxJournalierConge = indemniteConges.divide(nbJoursConges, 8, RoundingMode.HALF_UP);
        }
        String commInd = input.getIndemniteCongeLots() != null
                ? String.format("Indemnité congés : %s j ventilés selon les TCONGE historiques des lots FIFO", nbJoursConges)
                : String.format("Indemnité congés : %s j × TCONGE %s Ar", nbJoursConges, MoneyRoundingPolicy.amount(tauxJournalierConge));
        lignes.add(creerLigne(bp, "INDEMNITE_CONGE_PAYE", "Indemnité de Congés Payés (Maintien de salaire)", "GAIN", "SALAIRE_BASE",
                tauxJournalierConge, null, nbJoursConges, indemniteConges, null, commInd, 26));

        return indemniteConges.subtract(deductionCongeTrentieme);
    }

    public BigDecimal calculerMajorationsEtHeuresSup(PayrollCalculationInput input, BigDecimal tauxHoraire,
                                                      BulletinPaie bp, List<BulletinPaieLigne> lignes) {
        BigDecimal total = BigDecimal.ZERO;
        OvertimePayCalculator overtimeCalculator = new OvertimePayCalculator();
        OvertimePayCalculator.Result overtime = overtimeCalculator.calculate(input, tauxHoraire);
        String commentaireTaux = input.getSalaireParJour().values().stream().distinct().count() > 1
                ? "Montant ventilé selon le salaire applicable à la date de chaque tranche horaire ; taux de clôture affiché à titre indicatif."
                : null;

        BigDecimal heuresHs30 = zeroIfNull(input.getHeuresHs30());
        if (heuresHs30.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal montantHs30 = overtime.hs30();
            BigDecimal tauxHs30 = BigDecimal.ONE.add(zeroIfNull(input.getMajorationHs30())).multiply(new BigDecimal("100"));
            lignes.add(creerLigne(bp, "HEURES_SUP_30", "Heures Supplémentaires (+" + tauxHs30.subtract(new BigDecimal("100")).stripTrailingZeros().toPlainString() + "%)" + dafOvertimeLabel(input, true), "GAIN", "HEURES_SUP",
                    tauxHoraire, tauxHs30, heuresHs30, montantHs30, null, commentaireTaux, 30));
            total = total.add(montantHs30);
        }

        BigDecimal heuresHs50 = zeroIfNull(input.getHeuresHs50());
        if (heuresHs50.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal montantHs50 = overtime.hs50();
            BigDecimal tauxHs50 = BigDecimal.ONE.add(zeroIfNull(input.getMajorationHs50())).multiply(new BigDecimal("100"));
            lignes.add(creerLigne(bp, "HEURES_SUP_50", "Heures Supplémentaires (+" + tauxHs50.subtract(new BigDecimal("100")).stripTrailingZeros().toPlainString() + "%)" + dafOvertimeLabel(input, false), "GAIN", "HEURES_SUP",
                    tauxHoraire, tauxHs50, heuresHs50, montantHs50, null, commentaireTaux, 40));
            total = total.add(montantHs50);
        }

        BigDecimal heuresNuit = zeroIfNull(input.getHeuresNuit());
        if (heuresNuit.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal montantNuit = overtime.nuit();
            BigDecimal tauxNuit = zeroIfNull(input.getMajorationNuit()).multiply(new BigDecimal("100"));
            lignes.add(creerLigne(bp, "MAJORATION_NUIT", "Majoration Travail de Nuit", "GAIN", "HEURES_SUP",
                    tauxHoraire, tauxNuit, heuresNuit, montantNuit, null, commentaireTaux, 50));
            total = total.add(montantNuit);
        }

        BigDecimal heuresDimanche = zeroIfNull(input.getHeuresDimanche());
        if (heuresDimanche.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal montantDimanche = overtime.dimanche();
            BigDecimal baseTauxDimanche = zeroIfNull(input.getMajorationDimanche())
                    .multiply(new BigDecimal("100"));
            lignes.add(creerLigne(bp, "HEURES_DIMANCHE", "Majoration Travail Dimanche", "GAIN", "HEURES_SUP",
                    tauxHoraire, baseTauxDimanche, heuresDimanche, montantDimanche, null, commentaireTaux, 60));
            total = total.add(montantDimanche);
        }

        BigDecimal heuresFerie = zeroIfNull(input.getHeuresFeriees());
        if (heuresFerie.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal montantFerie = overtime.ferie();
            BigDecimal tauxFerie = zeroIfNull(input.getMajorationFerie()).multiply(new BigDecimal("100"));
            lignes.add(creerLigne(bp, "MAJORATION_FERIE", "Majoration Travail Jour Férié", "GAIN", "HEURES_SUP",
                    tauxHoraire, tauxFerie, heuresFerie, montantFerie, null, commentaireTaux, 70));
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

    /** Applique le dispositif d'exonération seulement si la configuration datée l'autorise. */
    public OvertimeExemptionDetail calculerExonerationFiscaleHs(
            PayrollCalculationInput input, BigDecimal tauxHoraire, BulletinPaie bp, List<BulletinPaieLigne> lignes) {
        BigDecimal h30 = zeroIfNull(input.getHeuresHs30());
        BigDecimal h50 = zeroIfNull(input.getHeuresHs50());
        BigDecimal totalHeuresHs = h30.add(h50);
        OvertimePayCalculator.Result overtime = new OvertimePayCalculator().calculate(input, tauxHoraire);
        BigDecimal totalHsBrut = overtime.hs30().add(overtime.hs50());
        BigDecimal plafond = input.getPlafondMensuelHsExonerees();
        if (!input.isAppliquerExonerationHs() || plafond == null || plafond.signum() <= 0
                || totalHeuresHs.signum() <= 0) {
            if (totalHsBrut.signum() > 0) {
                String commentaire = String.format("HS imposables IRSA : totalité des %s h rémunérées", totalHeuresHs);
                lignes.add(creerLigne(bp, "HS_IMPOSABLE_IRSA", "Heures Supplémentaires Imposables IRSA", "INFO", "HEURES_SUP",
                        tauxHoraire, null, totalHeuresHs, totalHsBrut, null, commentaire, 76));
            }
            return new OvertimeExemptionDetail(totalHsBrut, BigDecimal.ZERO, totalHsBrut, BigDecimal.ZERO, totalHeuresHs);
        }

        // Le plafond est imputé chronologiquement : HS 30 %, puis HS 50 %.
        BigDecimal exo30Heures = h30.min(plafond);
        BigDecimal exo50Heures = h50.min(plafond.subtract(exo30Heures).max(BigDecimal.ZERO));
        BigDecimal heuresExonerees = exo30Heures.add(exo50Heures);
        BigDecimal heuresImposables = totalHeuresHs.subtract(heuresExonerees).max(BigDecimal.ZERO);
        BigDecimal coefficient30 = BigDecimal.ONE.add(zeroIfNull(input.getMajorationHs30()));
        BigDecimal coefficient50 = BigDecimal.ONE.add(zeroIfNull(input.getMajorationHs50()));
        BigDecimal montantExonere;
        if (input.getSalaireParJour().values().stream().distinct().count() > 1) {
            BigDecimal montant30 = montantHsExonereParDate(overtime.bucketDetails(),
                    mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.OvertimeTier.OVERTIME_30,
                    exo30Heures, tauxHoraire, coefficient30);
            BigDecimal montant50 = montantHsExonereParDate(overtime.bucketDetails(),
                    mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.OvertimeTier.OVERTIME_50,
                    exo50Heures, tauxHoraire, coefficient50);
            montantExonere = MoneyRoundingPolicy.amount(montant30.add(montant50)).min(totalHsBrut);
        } else {
            BigDecimal montant30 = MoneyRoundingPolicy.amount(exo30Heures.multiply(tauxHoraire).multiply(coefficient30));
            BigDecimal montant50 = MoneyRoundingPolicy.amount(exo50Heures.multiply(tauxHoraire).multiply(coefficient50));
            montantExonere = montant30.add(montant50).min(totalHsBrut);
        }
        BigDecimal montantImposable = totalHsBrut.subtract(montantExonere).max(BigDecimal.ZERO);
        if (montantExonere.signum() > 0) {
            String commentaire = String.format("Exonération HS configurée : %s h (plafond %s h/mois)",
                    heuresExonerees, plafond.stripTrailingZeros().toPlainString());
            lignes.add(creerLigne(bp, "HS_EXONEREE_IRSA", "Heures Supplémentaires Exonérées IRSA", "INFO", "HEURES_SUP",
                    tauxHoraire, null, heuresExonerees, montantExonere, null, commentaire, 75));
        }
        if (montantImposable.signum() > 0) {
            lignes.add(creerLigne(bp, "HS_IMPOSABLE_IRSA", "Heures Supplémentaires Imposables IRSA", "INFO", "HEURES_SUP",
                    tauxHoraire, null, heuresImposables, montantImposable, null,
                    String.format("HS imposables après plafond configuré de %s h/mois", plafond.stripTrailingZeros().toPlainString()), 76));
        }
        return new OvertimeExemptionDetail(totalHsBrut, montantExonere, montantImposable, heuresExonerees, heuresImposables);
    }

    /** Valorise les heures exonérées au taux du bucket de paie correspondant. */
    private static BigDecimal montantHsExonereParDate(List<BucketValuationResult> details,
            mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.OvertimeTier tier,
            BigDecimal heuresExonerees, BigDecimal tauxHoraire, BigDecimal coefficient) {
        BigDecimal restantes = heuresExonerees;
        BigDecimal montant = BigDecimal.ZERO;
        for (var detail : details) {
            if (restantes.signum() <= 0 || detail.sourceBucket().overtimeTier() != tier) continue;
            BigDecimal heures = BigDecimal.valueOf(detail.sourceBucket().minutes()).divide(new BigDecimal("60"), 12, RoundingMode.HALF_UP);
            BigDecimal prises = heures.min(restantes);
            BigDecimal montantBucket = detail.overtimeBaseAmount().add(detail.overtimePremiumAmount());
            montant = montant.add(montantBucket.multiply(prises).divide(heures, 12, RoundingMode.HALF_UP));
            restantes = restantes.subtract(prises);
        }
        // Les heures ajoutées par arbitrage sans bucket de pointage utilisent le taux de clôture.
        return restantes.signum() > 0 ? montant.add(restantes.multiply(tauxHoraire).multiply(coefficient)) : montant;
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

            boolean penaliteApplicable = input.isRegleMalusPerformanceActive()
                    && (input.getMinutesRetard() > input.getSeuilRetardMalusPerformanceMinutes())
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
        int seuilAssiduite = input.getSeuilRetardAssiduiteMinutes();
        if (input.isReglePrimeAssiduiteActive() && primeAssiduite.compareTo(BigDecimal.ZERO) > 0
                && input.getMinutesRetard() < seuilAssiduite) {
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
                if (ManualPayrollRules.Contributions.resolve(gv).brut().signum() > 0) {
                    String cat = gv.getCategorie() != null ? gv.getCategorie() : "PRIME";
                    String lib = gv.getLibelle() != null && !gv.getLibelle().isBlank() ? gv.getLibelle() : gv.getCode();
                    BulletinPaieLigne ligne = creerLigne(bp, gv.getCodeRubrique(), lib, "GAIN", cat,
                            gv.getBaseCalcul(), gv.getTaux(), gv.getQuantite(), gv.getMontant(), null, 120);
                    ligne.setImposable(gv.isImposable());
                    ligne.setSoumisCotisation(gv.isSoumisCotisation());
                    ligne.setEtapeCalcul(gv.getEtapeCalcul());
                    ligne.setCommentaire(gv.getExplicationCalcul());
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
        BigDecimal assietteSanteVariables = totalGainsBruts;
        BigDecimal assietteFmfp=totalGainsBruts;
        if (input.getGainsVariablesDynamiques() != null) {
            for (PayrollCalculationInput.GainVariable gain : input.getGainsVariablesDynamiques()) {
                assietteSociale = assietteSociale.add(
                        ManualPayrollRules.Contributions.resolve(gain).correctionSociale());
                assietteSanteVariables = assietteSanteVariables.add(
                        ManualPayrollRules.Contributions.resolve(gain).correctionSante());
                assietteFmfp=assietteFmfp.add(ManualPayrollRules.Contributions.resolve(gain).correctionFmfp());
            }
        }
        assietteSociale = assietteSociale.max(BigDecimal.ZERO);
        assietteSanteVariables = assietteSanteVariables.max(BigDecimal.ZERO);

        BigDecimal plafondCotisation = input.getPlafondCotisationMensuel();
        SocialContributionCalculator socialCalculator = new SocialContributionCalculator();
        SocialContributionCalculator.Result social = socialCalculator.calculate(assietteSociale, assietteSanteVariables, assietteFmfp,
                new SocialContributionCalculator.Rules(input.getTauxCnapsSalarialDecimal(), input.getTauxCnapsPatronalDecimal(),
                        input.getTauxSanteSalarialDecimal(), input.getTauxSantePatronalDecimal(), input.getTauxFmfpPatronalDecimal(), plafondCotisation, input.isEstPlafonneSante()));

        // 2.1 CNaPS et FMFP : taux et plafond résolus à la date de la période.
        if (input.isSoumisCnaps()) {
            SocialContributionCalculator.Contribution resCnaps = social.cnaps();
            BigDecimal assietteCnaps = resCnaps.assiette();
            BigDecimal cnapsSal = resCnaps.partSalariale();
            BigDecimal cnapsPat = resCnaps.partPatronale();

            BulletinPaieLigne lCnapsSal = creerLigne(bp, "COTISATION_CNAPS_SAL", "Cotisation CNaPS Salariale (" + percent(input.getTauxCnapsSalarialDecimal()) + "%)", "RETENUE", "COTISATION_SALARIALE",
                    assietteCnaps, input.getTauxCnapsSalarialDecimal().movePointRight(2), cnapsSal, null, 200);
            lCnapsSal.setIdOrganisme(input.getIdCnapsOrganisme());
            lCnapsSal.setCodeOrganisme(input.getCodeCnapsOrganisme() != null ? input.getCodeCnapsOrganisme() : "CNAPS");
            lCnapsSal.setIdTypeCotisation(input.getIdCnapsTypeCotisation());
            lignes.add(lCnapsSal);

            BulletinPaieLigne lCnapsPat = creerLigne(bp, "COTISATION_CNAPS_PAT", "Cotisation CNaPS Patronale (" + percent(input.getTauxCnapsPatronalDecimal()) + "%)", "PATRONAL", "COTISATION_PATRONALE",
                    assietteCnaps, input.getTauxCnapsPatronalDecimal().movePointRight(2), null, cnapsPat, 300);
            lCnapsPat.setIdOrganisme(input.getIdCnapsOrganisme());
            lCnapsPat.setCodeOrganisme(input.getCodeCnapsOrganisme() != null ? input.getCodeCnapsOrganisme() : "CNAPS");
            lCnapsPat.setIdTypeCotisation(input.getIdCnapsTypeCotisation());
            lignes.add(lCnapsPat);

            totalCotisSalariales = totalCotisSalariales.add(cnapsSal);
            totalCotisPatronales = totalCotisPatronales.add(cnapsPat);

            // 2.3 FMFP patronale sur sa propre assiette.
            BigDecimal fmfpPat = MoneyRoundingPolicy.contribution(social.fmfp());
            BulletinPaieLigne lFmfpPat = creerLigne(bp, "COTISATION_FMFP_PAT", "Cotisation FMFP Patronale (" + percent(input.getTauxFmfpPatronalDecimal()) + "%)", "PATRONAL", "COTISATION_PATRONALE",
                    assietteFmfp.max(BigDecimal.ZERO).min(plafondCotisation), input.getTauxFmfpPatronalDecimal().movePointRight(2), null, fmfpPat, 330);
            lFmfpPat.setIdOrganisme(input.getIdFmfpOrganisme());
            lFmfpPat.setCodeOrganisme(input.getCodeFmfpOrganisme() != null ? input.getCodeFmfpOrganisme() : "FMFP");
            lFmfpPat.setIdTypeCotisation(input.getIdFmfpTypeCotisation());
            lignes.add(lFmfpPat);
            totalCotisPatronales = totalCotisPatronales.add(fmfpPat);
        }

        // 2.2 Santé (OSTIE, OMSI, AMIT ou autre organisme configuré).
        if (input.isSoumisSante()) {
            String organe = input.getCodeSanteOrgane() != null ? input.getCodeSanteOrgane().toUpperCase() : "OSTIE";
            boolean estOmsi = "OMSI".equals(organe);
            boolean estOstie = "OSTIE".equals(organe);

            String codeSanteSal = estOmsi ? "COTISATION_OMSI_SAL" : (estOstie ? "COTISATION_OSTIE_SAL" : "COTISATION_" + organe + "_SAL");
            String salRateStr = input.getTauxSanteSalarialDecimal() != null ? input.getTauxSanteSalarialDecimal().movePointRight(2).stripTrailingZeros().toPlainString() : "0";
            String libelleSanteSal = "Cotisation " + organe + " Salariale (" + salRateStr + "%)";

            String codeSantePat = estOmsi ? "COTISATION_OMSI_PAT" : (estOstie ? "COTISATION_OSTIE_PAT" : "COTISATION_" + organe + "_PAT");
            String patRateStr = input.getTauxSantePatronalDecimal() != null ? input.getTauxSantePatronalDecimal().movePointRight(2).stripTrailingZeros().toPlainString() : "0";
            String libelleSantePat = "Cotisation " + organe + " Patronale (" + patRateStr + "%)";

            BigDecimal assietteSante = social.health().assiette();

            SocialContributionCalculator.Contribution resSante = social.health();
            BigDecimal santeSal = resSante.partSalariale();
            BigDecimal santePat = resSante.partPatronale();

            String codeOrgSante = input.getCodeSanteOrganisme() != null ? input.getCodeSanteOrganisme() : organe;

            BulletinPaieLigne lSanteSal = creerLigne(bp, codeSanteSal, libelleSanteSal, "RETENUE", "COTISATION_SALARIALE",
                    assietteSante, input.getTauxSanteSalarialDecimal().movePointRight(2), santeSal, null, 210);
            lSanteSal.setIdOrganisme(input.getIdSanteOrganisme());
            lSanteSal.setCodeOrganisme(codeOrgSante);
            lSanteSal.setIdTypeCotisation(input.getIdSanteTypeCotisation());
            lignes.add(lSanteSal);

            BulletinPaieLigne lSantePat = creerLigne(bp, codeSantePat, libelleSantePat, "PATRONAL", "COTISATION_PATRONALE",
                    assietteSante, input.getTauxSantePatronalDecimal().movePointRight(2), null, santePat, 310);
            lSantePat.setIdOrganisme(input.getIdSanteOrganisme());
            lSantePat.setCodeOrganisme(codeOrgSante);
            lSantePat.setIdTypeCotisation(input.getIdSanteTypeCotisation());
            lignes.add(lSantePat);

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
    // 5. FISCALITÉ SALARIALE — IRSA (Palier 4)
    // =========================================================================

    public FiscaliteResult calculerFiscalite(BigDecimal salaireNetImposable, BigDecimal totalGainsBruts,
                                            PayrollCalculationInput input, BulletinPaie bp, List<BulletinPaieLigne> lignes) {
        return calculerFiscalite(salaireNetImposable, BigDecimal.ZERO, totalGainsBruts, input, bp, lignes);
    }

    public FiscaliteResult calculerFiscalite(BigDecimal salaireNetImposable, BigDecimal hsExonereeIrsa, BigDecimal totalGainsBruts,
                                            PayrollCalculationInput input, BulletinPaie bp, List<BulletinPaieLigne> lignes) {
        BigDecimal irsaNet = MoneyRoundingPolicy.tax(BigDecimal.ZERO);
        BigDecimal retenueIsi = MoneyRoundingPolicy.tax(BigDecimal.ZERO);
        PayrollTaxCalculations.Result detailIrsa = null;

        if (input.isSoumisIsi5()) {
            throw new IllegalArgumentException("La retenue ISI n'est plus applicable aux bulletins de paie 2026.");
        }
        bp.setRetenueIsi5(BigDecimal.ZERO);
        if (input.isSoumisIrsa()) {
            // Assiette IRSA : toutes les HS rémunérées sont déjà dans le net imposable social.
            BigDecimal gainsBrutsExoneres = (input.getGainsVariablesDynamiques() != null
                    ? input.getGainsVariablesDynamiques() : List.<PayrollCalculationInput.GainVariable>of()).stream()
                    .map(gain -> ManualPayrollRules.Contributions.resolve(gain).exclusionIrsa())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal assietteFiscale = salaireNetImposable.subtract(zeroIfNull(hsExonereeIrsa))
                    .subtract(gainsBrutsExoneres).max(BigDecimal.ZERO);
            bp.setRevenuNetImposable(assietteFiscale);

            // Régime Salarié : Barème progressif IRSA unifié (6 tranches, min 3 000 Ar)
            detailIrsa = new PayrollTaxCalculations().calculateIrsa(
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
            BulletinPaieLigne ligne = creerLigne(bp, "EXTRA_DEPLACEMENT", "Indemnité Extra Déplacement", "GAIN", "INDEMNITE_NON_IMPOSABLE",
                    extraDepl, null, extraDepl, null, 505);
            ligne.setEtapeCalcul("GAIN_NET");
            lignes.add(ligne);
            totalIndemnitesNet = totalIndemnitesNet.add(extraDepl);
        }

        // 6.4 Notes de frais remboursées
        BigDecimal notesFrais = zeroIfNull(input.getRemboursementNotesFrais());
        if (notesFrais.compareTo(BigDecimal.ZERO) > 0) {
            BulletinPaieLigne ligne = creerLigne(bp, "REMBOURSEMENT_NOTE_FRAIS", "Remboursement Notes de Frais Validées", "GAIN", "INDEMNITE_NON_IMPOSABLE",
                    notesFrais, null, notesFrais, null, 520);
            ligne.setEtapeCalcul("GAIN_NET");
            lignes.add(ligne);
            totalIndemnitesNet = totalIndemnitesNet.add(notesFrais);
        }

        // 6.5 Indemnités et gains dynamiques non imposables
        if (input.getGainsVariablesDynamiques() != null) {
            for (PayrollCalculationInput.GainVariable gv : input.getGainsVariablesDynamiques()) {
                if (ManualPayrollRules.Contributions.resolve(gv).ajoutNet().signum() > 0) {
                    String cat = gv.getCategorie() != null ? gv.getCategorie() : "INDEMNITE_NON_IMPOSABLE";
                    String lib = gv.getLibelle() != null && !gv.getLibelle().isBlank() ? gv.getLibelle() : gv.getCode();
                    BulletinPaieLigne ligne = creerLigne(bp, gv.getCodeRubrique(), lib, "GAIN", cat,
                            gv.getBaseCalcul(), gv.getTaux(), gv.getQuantite(), gv.getMontant(), null, 530);
                    ligne.setImposable(gv.isImposable());
                    ligne.setSoumisCotisation(gv.isSoumisCotisation());
                    ligne.setEtapeCalcul(gv.getEtapeCalcul());
                    ligne.setCommentaire(gv.getExplicationCalcul());
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
                    String codeRubrique = a.getCodeRubriqueCalcul() != null
                            ? a.getCodeRubriqueCalcul() : mapRubriqueAvance(type);
                    String libelle = a.getLibelleRubriqueCalcul() != null && !a.getLibelleRubriqueCalcul().isBlank()
                            ? a.getLibelleRubriqueCalcul() : "Acompte sur salaire";

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

        // Plafond des avances employeur résolu dans la configuration légale de la période.
        BigDecimal plafondDixiemeAvances = MoneyRoundingPolicy.amount(netApresAcompte.multiply(input.getPlafondAvanceEmployeur()));

        if (input.getAvancesAcomptes() != null) {
            for (PaieAvanceAcompte a : input.getAvancesAcomptes()) {
                if (a.getMontant() == null || a.getMontant().compareTo(BigDecimal.ZERO) <= 0) continue;

                String type = a.getTypeMouvement() != null ? a.getTypeMouvement() : "";
                boolean isAvanceSpeciale = "AVANCE_SPECIALE".equalsIgnoreCase(type);

                if (isAvanceSpeciale) {
                    String codeRubrique = a.getCodeRubriqueCalcul() != null
                            ? a.getCodeRubriqueCalcul() : mapRubriqueAvance(type);
                    String libelle = a.getLibelleRubriqueCalcul() != null && !a.getLibelleRubriqueCalcul().isBlank()
                            ? a.getLibelleRubriqueCalcul() : "Avance exceptionnelle sur salaire";

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

        // Autres retenues directes sur salaire (CALL PERSO, RETENUE DIVERSE)
        BigDecimal totalAutresRetenues = BigDecimal.ZERO;
        BigDecimal netApresAvance = netApresAcompte.subtract(totalAvancesSpeciales).max(BigDecimal.ZERO);

        if (input.getAvancesAcomptes() != null) {
            for (PaieAvanceAcompte a : input.getAvancesAcomptes()) {
                if (a.getMontant() == null || a.getMontant().compareTo(BigDecimal.ZERO) <= 0) continue;

                String type = a.getTypeMouvement() != null ? a.getTypeMouvement() : "";
                boolean isAcompte = "ACOMPTE_15".equalsIgnoreCase(type) || "AVANCE_QUINZAINE".equalsIgnoreCase(type);
                boolean isAvanceSpeciale = "AVANCE_SPECIALE".equalsIgnoreCase(type);

                if (!isAcompte && !isAvanceSpeciale) {
                    String codeRubrique = a.getCodeRubriqueCalcul() != null
                            ? a.getCodeRubriqueCalcul() : mapRubriqueAvance(type);
                    String libelleDefaut = "CALL_PERSO".equalsIgnoreCase(type)
                            ? "Retenue Téléphonie Privée (Call Perso)"
                            : "Retenue Diverse sur Salaire";
                    String libelle = a.getLibelleRubriqueCalcul() != null && !a.getLibelleRubriqueCalcul().isBlank()
                            ? a.getLibelleRubriqueCalcul() : libelleDefaut;

                    BigDecimal soldeDisponible = netApresAvance.subtract(totalAutresRetenues).max(BigDecimal.ZERO);
                    BigDecimal mntRetenu = MoneyRoundingPolicy.amount(a.getMontant().min(soldeDisponible));

                    if (mntRetenu.compareTo(BigDecimal.ZERO) > 0) {
                        int ordre = "CALL_PERSO".equalsIgnoreCase(type) ? 630 : 640;
                        BulletinPaieLigne ligne = creerLigne(bp, codeRubrique, libelle, "RETENUE", "RETENUE_DIVERSE",
                                a.getMontant(), null, mntRetenu, null, ordre);
                        ligne.setEtapeCalcul("RETENUE_NET");
                        ligne.setCommentaire(a.getExplicationCalcul());
                        lignes.add(ligne);
                        totalAutresRetenues = totalAutresRetenues.add(mntRetenu);
                    }
                    if (a.getIdAvance() != null && mntRetenu.compareTo(BigDecimal.ZERO) > 0) {
                        imputations.add(new MouvementImputation(a.getIdAvance(), mntRetenu));
                    }
                }
            }
        }

        BigDecimal totalRetenuesTresorerie = totalAcomptesQuinzaine.add(totalAvancesSpeciales).add(totalAutresRetenues);
        bp.setTotalRetenuesAvancesAcomptes(totalRetenuesTresorerie);

        return new RetenuesResult(totalRetenuesTresorerie, imputations);
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
        BigDecimal tauxLigne = taux != null ? MoneyRoundingPolicy.amount(taux) : null;
        if ("PATRONAL".equals(sens)) {
            l.setTauxPatronal(tauxLigne);
        } else {
            l.setTauxSalarial(tauxLigne);
        }
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
    private static String percent(BigDecimal decimalRate) {
        return decimalRate.movePointRight(2).stripTrailingZeros().toPlainString();
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
