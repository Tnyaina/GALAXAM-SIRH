package mg.galaxam.sirh.oracle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * ORACLE FINANCIER DE PAIE — 100% INDÉPENDANT DU MOTEUR DE PRODUCTION.
 * 
 * N'importe AUCUN calculator, engine, resolver, service ou repository de production.
 * Utilise exclusivement du pur BigDecimal et des formules arithmétiques autonomes.
 */
public class PayrollIndependentOracle {

    public record IrsaTrancheExpected(
            BigDecimal trancheMin,
            BigDecimal trancheMax,
            BigDecimal taux
    ) {}

    public record LegalConfigExpected(
            BigDecimal smeNational,
            BigDecimal tauxCnapsSalarial,
            BigDecimal tauxCnapsPatronal,
            BigDecimal plafondCnapsNbSme,
            BigDecimal tauxSanteSalarial,
            BigDecimal tauxSantePatronal,
            boolean estSantePlafonnee,
            BigDecimal plafondSanteMensuel,
            BigDecimal tauxFmfpPatronal,
            boolean exonerationHsActive,
            BigDecimal plafondHsExonerees,
            BigDecimal seuilExonerationIrsa,
            BigDecimal minimumPerceptionIrsa,
            BigDecimal deductionParEnfant,
            List<IrsaTrancheExpected> tranchesIrsa
    ) {}

    public record PayrollExpectedResult(
            BigDecimal salaireBaseContractuel,
            BigDecimal salaireBaseLigne,
            BigDecimal retenueAbsence,
            BigDecimal deductionCongeBase,
            BigDecimal indemniteConge,
            BigDecimal montantHs30,
            BigDecimal montantHs50,
            BigDecimal totalHsBrut,
            BigDecimal montantNuit,
            BigDecimal montantDimanche,
            BigDecimal montantFerie,
            BigDecimal primesImposables,
            BigDecimal totalGainsBruts,
            BigDecimal cnapsSalarial,
            BigDecimal cnapsPatronal,
            BigDecimal santeSalarial,
            BigDecimal santePatronal,
            BigDecimal fmfpPatronal,
            BigDecimal salaireNetImposable,
            BigDecimal hsExonereeIrsa,
            BigDecimal hsImposableIrsa,
            BigDecimal baseIrsaBrute,
            BigDecimal baseIrsaArrondie,
            BigDecimal irsaBrut,
            BigDecimal reductionEnfants,
            BigDecimal irsaNet,
            BigDecimal indemnitesNonImposables,
            BigDecimal acomptesEtAvances,
            BigDecimal netAvantArrondi,
            BigDecimal netAPayer
    ) {}

    public static PayrollExpectedResult calculate(
            BigDecimal salaireBaseContractuel,
            BigDecimal joursSousContrat, // null ou 30 si mois plein, < 30 si entrée/sortie
            BigDecimal diviseurMensuelHeures,
            long minutesAbsence,
            BigDecimal joursCongePris,
            BigDecimal tcongeJournalier,
            BigDecimal heuresHs30,
            BigDecimal heuresHs50,
            BigDecimal heuresNuit,
            BigDecimal heuresDimanche,
            BigDecimal heuresFerie,
            BigDecimal primesImposables,
            BigDecimal indemnitesNonImposables,
            BigDecimal acomptesEtAvances,
            int nbEnfantsACharge,
            boolean estCadre,
            LegalConfigExpected config
    ) {
        BigDecimal diviseurHeures = diviseurMensuelHeures != null ? diviseurMensuelHeures : new BigDecimal("173.33");
        BigDecimal tauxHoraire = salaireBaseContractuel.divide(diviseurHeures, 8, RoundingMode.HALF_UP);

        // 1. Salaire de base (prorata éventuel au trentième)
        BigDecimal salaireBaseLigne = salaireBaseContractuel;
        if (joursSousContrat != null && joursSousContrat.compareTo(new BigDecimal("30")) < 0) {
            salaireBaseLigne = salaireBaseContractuel
                    .multiply(joursSousContrat)
                    .divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);
        }

        // 2. Retenue pour absence injustifiée : Base * minutesAbsence / (diviseur * 60)
        BigDecimal retenueAbsence = BigDecimal.ZERO;
        if (minutesAbsence > 0) {
            retenueAbsence = salaireBaseContractuel
                    .multiply(BigDecimal.valueOf(minutesAbsence))
                    .divide(diviseurHeures.multiply(new BigDecimal("60")), 2, RoundingMode.HALF_UP);
        }

        // 3. Congés payés
        BigDecimal deductionCongeBase = BigDecimal.ZERO;
        BigDecimal indemniteConge = BigDecimal.ZERO;
        if (joursCongePris != null && joursCongePris.compareTo(BigDecimal.ZERO) > 0) {
            deductionCongeBase = salaireBaseContractuel
                    .multiply(joursCongePris)
                    .divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);
            BigDecimal tconge = tcongeJournalier != null 
                    ? tcongeJournalier 
                    : salaireBaseContractuel.divide(new BigDecimal("30"), 4, RoundingMode.HALF_UP);
            indemniteConge = joursCongePris.multiply(tconge).setScale(2, RoundingMode.HALF_UP);
        }

        // 4. Heures supplémentaires & Majorations
        BigDecimal h30 = heuresHs30 != null ? heuresHs30 : BigDecimal.ZERO;
        BigDecimal h50 = heuresHs50 != null ? heuresHs50 : BigDecimal.ZERO;
        BigDecimal hNuit = heuresNuit != null ? heuresNuit : BigDecimal.ZERO;
        BigDecimal hDim = heuresDimanche != null ? heuresDimanche : BigDecimal.ZERO;
        BigDecimal hFer = heuresFerie != null ? heuresFerie : BigDecimal.ZERO;

        BigDecimal montantHs30 = h30.multiply(tauxHoraire).multiply(new BigDecimal("1.30")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal montantHs50 = h50.multiply(tauxHoraire).multiply(new BigDecimal("1.50")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalHsBrut = montantHs30.add(montantHs50);

        BigDecimal montantNuit = hNuit.multiply(tauxHoraire).multiply(new BigDecimal("0.30")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal montantDimanche = hDim.multiply(tauxHoraire).multiply(new BigDecimal("0.40")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal montantFerie = hFer.multiply(tauxHoraire).multiply(new BigDecimal("1.00")).setScale(2, RoundingMode.HALF_UP);

        BigDecimal primes = primesImposables != null ? primesImposables : BigDecimal.ZERO;

        // 5. Total Gains Bruts (Brut Social)
        BigDecimal totalGainsBruts = salaireBaseLigne
                .subtract(retenueAbsence)
                .subtract(deductionCongeBase)
                .add(indemniteConge)
                .add(montantHs30)
                .add(montantHs50)
                .add(montantNuit)
                .add(montantDimanche)
                .add(montantFerie)
                .add(primes)
                .max(BigDecimal.ZERO);

        // 6. Cotisations CNaPS (plafonné à 8 * SME)
        BigDecimal plafondCnaps = config.smeNational().multiply(config.plafondCnapsNbSme());
        BigDecimal assietteCnaps = totalGainsBruts.min(plafondCnaps);
        BigDecimal cnapsSal = assietteCnaps.multiply(config.tauxCnapsSalarial()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cnapsPat = assietteCnaps.multiply(config.tauxCnapsPatronal()).setScale(2, RoundingMode.HALF_UP);

        // 7. Cotisations Santé (OMSI / OSTIE)
        BigDecimal assietteSante = totalGainsBruts;
        if (config.estSantePlafonnee() && config.plafondSanteMensuel() != null && config.plafondSanteMensuel().compareTo(BigDecimal.ZERO) > 0) {
            assietteSante = assietteSante.min(config.plafondSanteMensuel());
        }
        BigDecimal santeSal = assietteSante.multiply(config.tauxSanteSalarial()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal santePat = assietteSante.multiply(config.tauxSantePatronal()).setScale(2, RoundingMode.HALF_UP);

        // 8. FMFP (1% employeur sur Brut total non plafonné)
        BigDecimal fmfpPat = totalGainsBruts.multiply(config.tauxFmfpPatronal()).setScale(2, RoundingMode.HALF_UP);

        // 9. Salaire Net Imposable Social
        BigDecimal netImposable = totalGainsBruts.subtract(cnapsSal).subtract(santeSal);

        // 10. Exonération Fiscale des Heures Supplémentaires (CGI Art. 01.03.03-9°)
        // "Les rémunérations perçues au titre des heures supplémentaires effectuées dans la limite de 20h par mois, à l'exclusion des cadres."
        BigDecimal hsExoneree = BigDecimal.ZERO;
        BigDecimal hsImposable = totalHsBrut;

        if (config.exonerationHsActive() && !estCadre && config.plafondHsExonerees() != null && config.plafondHsExonerees().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal plafond = config.plafondHsExonerees();
            BigDecimal exo30H = h30.min(plafond);
            BigDecimal restePlafond = plafond.subtract(exo30H).max(BigDecimal.ZERO);
            BigDecimal exo50H = h50.min(restePlafond);

            BigDecimal remunExo30 = exo30H.multiply(tauxHoraire).multiply(new BigDecimal("1.30")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal remunExo50 = exo50H.multiply(tauxHoraire).multiply(new BigDecimal("1.50")).setScale(2, RoundingMode.HALF_UP);
            hsExoneree = remunExo30.add(remunExo50).min(totalHsBrut);
            hsImposable = totalHsBrut.subtract(hsExoneree).max(BigDecimal.ZERO);
        }

        // 11. Assiette IRSA Fiscale
        BigDecimal baseIrsaBrute = netImposable.subtract(hsExoneree).max(BigDecimal.ZERO);

        // Arrondi légal centaine inférieure : ex. 438 725.57 -> 438 700.00 Ar
        BigDecimal baseIrsaArrondie = baseIrsaBrute
                .divide(new BigDecimal("100"), 0, RoundingMode.FLOOR)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.UNNECESSARY);

        // 12. Barème progressif IRSA par tranches
        BigDecimal irsaCalcule = BigDecimal.ZERO;
        if (baseIrsaArrondie.compareTo(config.seuilExonerationIrsa()) > 0) {
            for (IrsaTrancheExpected t : config.tranchesIrsa()) {
                if (baseIrsaArrondie.compareTo(t.trancheMin()) >= 0) {
                    BigDecimal maxT = t.trancheMax() != null ? t.trancheMax() : baseIrsaArrondie;
                    BigDecimal taxableInTranche = baseIrsaArrondie.min(maxT).subtract(t.trancheMin()).max(BigDecimal.ZERO);
                    BigDecimal impotTranche = taxableInTranche.multiply(t.taux());
                    irsaCalcule = irsaCalcule.add(impotTranche);
                }
            }
        }
        BigDecimal irsaBrut = irsaCalcule.setScale(2, RoundingMode.HALF_UP);
        // CGI Madagascar Art. 01.03.14 : Arrondi à la dizaine supérieure
        BigDecimal irsaDizaine = irsaBrut.divide(BigDecimal.TEN, 0, RoundingMode.CEILING).multiply(BigDecimal.TEN).setScale(2);

        // Réduction pour charges de famille
        BigDecimal reductionEnfants = config.deductionParEnfant()
                .multiply(BigDecimal.valueOf(nbEnfantsACharge))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal irsaApresCharges = irsaDizaine.subtract(reductionEnfants).max(BigDecimal.ZERO);

        // Application du minimum de perception (2 000 Ar) si base > seuil d'exonération
        BigDecimal irsaNet = BigDecimal.ZERO;
        if (baseIrsaArrondie.compareTo(config.seuilExonerationIrsa()) > 0) {
            irsaNet = irsaApresCharges.max(config.minimumPerceptionIrsa());
        }

        // 13. Net à payer
        BigDecimal indemNonImp = indemnitesNonImposables != null ? indemnitesNonImposables : BigDecimal.ZERO;
        BigDecimal retenuesNet = acomptesEtAvances != null ? acomptesEtAvances : BigDecimal.ZERO;

        BigDecimal netAvantArrondi = netImposable
                .subtract(irsaNet)
                .subtract(retenuesNet)
                .add(indemNonImp);

        // Arrondi bancaire officiel MROUND(Net, 10)
        BigDecimal netAPayer = roundToTen(netAvantArrondi);

        return new PayrollExpectedResult(
                salaireBaseContractuel,
                salaireBaseLigne,
                retenueAbsence,
                deductionCongeBase,
                indemniteConge,
                montantHs30,
                montantHs50,
                totalHsBrut,
                montantNuit,
                montantDimanche,
                montantFerie,
                primes,
                totalGainsBruts,
                cnapsSal,
                cnapsPat,
                santeSal,
                santePat,
                fmfpPat,
                netImposable,
                hsExoneree,
                hsImposable,
                baseIrsaBrute,
                baseIrsaArrondie,
                irsaBrut,
                reductionEnfants,
                irsaNet,
                indemNonImp,
                retenuesNet,
                netAvantArrondi,
                netAPayer
        );
    }

    public static BigDecimal roundToTen(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO.setScale(2);
        return value.divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP)
                .multiply(BigDecimal.TEN)
                .setScale(2, RoundingMode.UNNECESSARY);
    }

    public static LegalConfigExpected configSeptembre2026() {
        return new LegalConfigExpected(
                new BigDecimal("300000.00"),
                new BigDecimal("0.01"),
                new BigDecimal("0.13"),
                new BigDecimal("8.00"),
                new BigDecimal("0.01"),
                new BigDecimal("0.05"),
                true,
                new BigDecimal("2400000.00"),
                new BigDecimal("0.01"),
                true,
                new BigDecimal("20.00"),
                new BigDecimal("350000.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("2000.00"),
                List.of(
                        new IrsaTrancheExpected(BigDecimal.ZERO, new BigDecimal("350000.00"), BigDecimal.ZERO),
                        new IrsaTrancheExpected(new BigDecimal("350000.00"), new BigDecimal("400000.00"), new BigDecimal("0.05")),
                        new IrsaTrancheExpected(new BigDecimal("400000.00"), new BigDecimal("500000.00"), new BigDecimal("0.10")),
                        new IrsaTrancheExpected(new BigDecimal("500000.00"), new BigDecimal("600000.00"), new BigDecimal("0.15")),
                        new IrsaTrancheExpected(new BigDecimal("600000.00"), new BigDecimal("4000000.00"), new BigDecimal("0.20")),
                        new IrsaTrancheExpected(new BigDecimal("4000000.00"), null, new BigDecimal("0.25"))
                )
        );
    }

    public static LegalConfigExpected configDecembre2025() {
        return new LegalConfigExpected(
                new BigDecimal("262680.00"),
                new BigDecimal("0.01"),
                new BigDecimal("0.13"),
                new BigDecimal("8.00"),
                new BigDecimal("0.01"),
                new BigDecimal("0.05"),
                true,
                new BigDecimal("2101440.00"),
                new BigDecimal("0.01"),
                true,
                new BigDecimal("20.00"),
                new BigDecimal("350000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("2000.00"),
                List.of(
                        new IrsaTrancheExpected(BigDecimal.ZERO, new BigDecimal("350000.00"), BigDecimal.ZERO),
                        new IrsaTrancheExpected(new BigDecimal("350000.00"), new BigDecimal("400000.00"), new BigDecimal("0.05")),
                        new IrsaTrancheExpected(new BigDecimal("400000.00"), new BigDecimal("500000.00"), new BigDecimal("0.10")),
                        new IrsaTrancheExpected(new BigDecimal("500000.00"), new BigDecimal("600000.00"), new BigDecimal("0.15")),
                        new IrsaTrancheExpected(new BigDecimal("600000.00"), null, new BigDecimal("0.20"))
                )
        );
    }
}
