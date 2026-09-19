package mg.galaxam.sirh.unit.paie.certification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * ORACLE FINANCIER INDÉPENDANT POUR LA CERTIFICATION FINANCIÈRE PRÉ-PRODUCTION.
 *
 * RÈGLES CRITIQUES D'INDÉPENDANCE :
 * 1. N'importe et n'appelle AUCUNE classe du moteur de production :
 *    - Strictement aucun appel à PayrollExecutionEngine
 *    - Strictement aucun appel à IrsaCalculator
 *    - Strictement aucun appel à SocialContributionCalculator
 *    - Strictement aucun appel à OvertimePayCalculator
 *    - Strictement aucun appel à MoneyRoundingPolicy
 * 2. Implémente des calculs purs en BigDecimal selon les formules mathématiques et légales officielles :
 *    - Retenue absence : BASE * minutes / (diviseur * 60)
 *    - Déduction congé base : NBCONGE * BASE / 30
 *    - Indemnité congé : NBCONGE * TCONGE
 *    - Cotisations CNaPS : 1% salarial / 13% patronal sur min(BRUT, 8 * SME)
 *    - Cotisations Santé : taux salarial/patronal sur min(BRUT, 8*SME) si plafonné, ou BRUT si non plafonné
 *    - FMFP : 1% patronal sur BRUT total
 *    - NETI = BRUT - Cotisations salariales
 *    - Assiette IRSA = 100 * floor(NETI / 100)
 *    - IRSA brut = somme par tranches
 *    - IRSA arrondi = 10 * ceil(IRSA brut / 10)
 *    - IRSA net = max(minimumPerception, IRSA arrondi - nbEnfants * deductionEnfant)
 *    - Net à payer = 10 * round(NetAvantArrondi / 10, HALF_UP) (MROUND 10)
 */
public final class FinancialCertificationIndependentOracle {

    public record IrsaTrancheDef(BigDecimal seuilMin, BigDecimal seuilMax, BigDecimal tauxPourcent) {}

    public record OracleInput(
            BigDecimal salaireBaseContractuel,
            boolean isEntreeSortieEnCoursDeMois,
            BigDecimal joursSousContrat,
            long minutesAbsence,
            BigDecimal heuresAbsenceFallback,
            BigDecimal diviseurMensuelHeures,
            BigDecimal nbJoursConges,
            BigDecimal tauxJournalierConge,
            BigDecimal heuresHs30,
            BigDecimal heuresHs50,
            BigDecimal heuresNuit,
            BigDecimal heuresDimanche,
            BigDecimal heuresFerie,
            BigDecimal primesImposablesAutimp,
            BigDecimal sme,
            BigDecimal tauxCnapsSal,
            BigDecimal tauxCnapsPat,
            BigDecimal tauxSanteSal,
            BigDecimal tauxSantePat,
            boolean estPlafonneSante,
            BigDecimal tauxFmfpPat,
            List<IrsaTrancheDef> tranchesIrsa,
            int nbEnfants,
            BigDecimal deductionParEnfant,
            BigDecimal minPerceptionIrsa,
            boolean isSoumisIsi,
            BigDecimal tauxIsiPourcent,
            BigDecimal indemnitesNonImposables,
            BigDecimal retenuesTresorerie
    ) {}

    public record OracleResult(
            BigDecimal salaireBaseLigne,
            BigDecimal tauxHoraire,
            BigDecimal retenueAbsence,
            BigDecimal deductionCongeTrentieme,
            BigDecimal indemniteConge,
            BigDecimal netConge,
            BigDecimal montantHs30,
            BigDecimal montantHs50,
            BigDecimal montantNuit,
            BigDecimal montantDimanche,
            BigDecimal montantFerie,
            BigDecimal totalHs,
            BigDecimal totalGainsBruts,
            BigDecimal cnapsSalariale,
            BigDecimal cnapsPatronale,
            BigDecimal santeSalariale,
            BigDecimal santePatronale,
            BigDecimal fmfpPatronale,
            BigDecimal totalCotisationsSalariales,
            BigDecimal totalCotisationsPatronales,
            BigDecimal salaireNetImposable,
            BigDecimal baseIrsaArrondie,
            BigDecimal irsaBrut,
            BigDecimal irsaArrondiDizaine,
            BigDecimal reductionEnfants,
            BigDecimal irsaNet,
            BigDecimal retenueIsi,
            BigDecimal totalIndemnitesNonImposables,
            BigDecimal totalRetenuesTresorerie,
            BigDecimal netAvantArrondi,
            BigDecimal netAPayer,
            BigDecimal ajustementArrondi,
            BigDecimal coutGlobalEmployeur
    ) {}

    private static final BigDecimal SIXTY = new BigDecimal("60");
    private static final BigDecimal THIRTY = new BigDecimal("30");
    private static final BigDecimal EIGHT = new BigDecimal("8");
    private static final BigDecimal TEN = BigDecimal.TEN;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private static BigDecimal zeroIfNull(BigDecimal val) {
        return val == null ? BigDecimal.ZERO : val;
    }

    private static BigDecimal round2(BigDecimal val) {
        return val == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : val.setScale(2, RoundingMode.HALF_UP);
    }

    public static OracleResult calculate(OracleInput in) {
        BigDecimal baseContractuelle = zeroIfNull(in.salaireBaseContractuel());
        BigDecimal diviseurHeures = in.diviseurMensuelHeures() != null && in.diviseurMensuelHeures().compareTo(BigDecimal.ZERO) > 0
                ? in.diviseurMensuelHeures()
                : new BigDecimal("173.33");

        // 1. Base salariale & Taux horaire
        BigDecimal baseLigne = baseContractuelle;
        if (in.isEntreeSortieEnCoursDeMois()) {
            if (in.joursSousContrat() == null || in.joursSousContrat().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("ORACLE: joursSousContrat manquant pour prorata entrée/sortie");
            }
            baseLigne = round2(baseContractuelle.multiply(in.joursSousContrat()).divide(THIRTY, 4, RoundingMode.HALF_UP));
        }

        BigDecimal tauxHoraire = baseContractuelle.divide(diviseurHeures, 4, RoundingMode.HALF_UP);

        // 2. Retenue Absence
        BigDecimal retenueAbsence = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (in.minutesAbsence() > 0) {
            BigDecimal raw = baseContractuelle
                    .multiply(BigDecimal.valueOf(in.minutesAbsence()))
                    .divide(diviseurHeures.multiply(SIXTY), 8, RoundingMode.HALF_UP);
            retenueAbsence = round2(raw).min(baseLigne);
        } else if (in.heuresAbsenceFallback() != null && in.heuresAbsenceFallback().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal raw = baseContractuelle
                    .multiply(in.heuresAbsenceFallback())
                    .divide(diviseurHeures, 8, RoundingMode.HALF_UP);
            retenueAbsence = round2(raw).min(baseLigne);
        }

        // 3. Congés payés
        BigDecimal nbConges = zeroIfNull(in.nbJoursConges());
        BigDecimal deductionCongeTrentieme = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal indemniteConge = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal netConge = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (nbConges.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal txTrentieme = baseContractuelle.divide(THIRTY, 4, RoundingMode.HALF_UP);
            deductionCongeTrentieme = round2(nbConges.multiply(txTrentieme));

            BigDecimal txConge = in.tauxJournalierConge() != null && in.tauxJournalierConge().compareTo(BigDecimal.ZERO) > 0
                    ? in.tauxJournalierConge()
                    : txTrentieme;
            indemniteConge = round2(nbConges.multiply(txConge));
            netConge = indemniteConge.subtract(deductionCongeTrentieme);
        }

        // 4. Heures supplémentaires & majorations
        BigDecimal h30 = zeroIfNull(in.heuresHs30());
        BigDecimal h50 = zeroIfNull(in.heuresHs50());
        BigDecimal hNuit = zeroIfNull(in.heuresNuit());
        BigDecimal hDim = zeroIfNull(in.heuresDimanche());
        BigDecimal hFer = zeroIfNull(in.heuresFerie());

        BigDecimal mntHs30 = h30.compareTo(BigDecimal.ZERO) > 0 ? round2(h30.multiply(tauxHoraire).multiply(new BigDecimal("1.30"))) : BigDecimal.ZERO.setScale(2);
        BigDecimal mntHs50 = h50.compareTo(BigDecimal.ZERO) > 0 ? round2(h50.multiply(tauxHoraire).multiply(new BigDecimal("1.50"))) : BigDecimal.ZERO.setScale(2);
        BigDecimal mntNuit = hNuit.compareTo(BigDecimal.ZERO) > 0 ? round2(hNuit.multiply(tauxHoraire).multiply(new BigDecimal("0.30"))) : BigDecimal.ZERO.setScale(2);
        BigDecimal mntDim = hDim.compareTo(BigDecimal.ZERO) > 0 ? round2(hDim.multiply(tauxHoraire).multiply(new BigDecimal("1.40"))) : BigDecimal.ZERO.setScale(2);
        BigDecimal mntFer = hFer.compareTo(BigDecimal.ZERO) > 0 ? round2(hFer.multiply(tauxHoraire).multiply(new BigDecimal("1.00"))) : BigDecimal.ZERO.setScale(2);
        BigDecimal totalHs = mntHs30.add(mntHs50).add(mntNuit).add(mntDim).add(mntFer);

        // 5. Primes imposables / AUTIMP
        BigDecimal autimp = zeroIfNull(in.primesImposablesAutimp());

        // 6. Brut canonique
        BigDecimal brut = baseLigne
                .subtract(retenueAbsence)
                .subtract(deductionCongeTrentieme)
                .add(indemniteConge)
                .add(totalHs)
                .add(autimp);
        brut = round2(brut.max(BigDecimal.ZERO));

        // 7. Cotisations sociales (CNaPS, Santé, FMFP)
        BigDecimal sme = zeroIfNull(in.sme());
        BigDecimal plafondCnaps = sme.multiply(EIGHT);

        BigDecimal assietteCnaps = brut.min(plafondCnaps);
        BigDecimal txCnapsSal = zeroIfNull(in.tauxCnapsSal());
        BigDecimal txCnapsPat = zeroIfNull(in.tauxCnapsPat());
        BigDecimal cnapsSal = round2(assietteCnaps.multiply(txCnapsSal));
        BigDecimal cnapsPat = round2(assietteCnaps.multiply(txCnapsPat));

        BigDecimal assietteSante = in.estPlafonneSante() ? brut.min(plafondCnaps) : brut;
        BigDecimal txSanteSal = zeroIfNull(in.tauxSanteSal());
        BigDecimal txSantePat = zeroIfNull(in.tauxSantePat());
        BigDecimal santeSal = round2(assietteSante.multiply(txSanteSal));
        BigDecimal santePat = round2(assietteSante.multiply(txSantePat));

        BigDecimal txFmfpPat = zeroIfNull(in.tauxFmfpPat());
        BigDecimal fmfpPat = round2(brut.multiply(txFmfpPat));

        BigDecimal totalCotisSal = round2(cnapsSal.add(santeSal));
        BigDecimal totalCotisPat = round2(cnapsPat.add(santePat).add(fmfpPat));

        // 8. Net Imposable (NETI)
        BigDecimal neti = round2(brut.subtract(totalCotisSal).max(BigDecimal.ZERO));

        // 9. Fiscalité (IRSA ou ISI 5%)
        BigDecimal baseIrsaArrondie = BigDecimal.ZERO.setScale(2);
        BigDecimal irsaBrut = BigDecimal.ZERO.setScale(2);
        BigDecimal irsaArrondiDizaine = BigDecimal.ZERO.setScale(2);
        BigDecimal reductionEnfants = BigDecimal.ZERO.setScale(2);
        BigDecimal irsaNet = BigDecimal.ZERO.setScale(2);
        BigDecimal retenueIsi = BigDecimal.ZERO.setScale(2);

        if (in.isSoumisIsi()) {
            BigDecimal txIsi = in.tauxIsiPourcent() != null ? in.tauxIsiPourcent() : new BigDecimal("5.00");
            retenueIsi = round2(brut.multiply(txIsi).divide(HUNDRED, 2, RoundingMode.HALF_UP));
        } else {
            // Arrondi base IRSA centaine inférieure : 100 * floor(neti / 100)
            baseIrsaArrondie = neti.setScale(0, RoundingMode.DOWN)
                    .divide(HUNDRED, 0, RoundingMode.DOWN)
                    .multiply(HUNDRED)
                    .setScale(2, RoundingMode.HALF_UP);

            if (in.tranchesIrsa() != null && !in.tranchesIrsa().isEmpty() && baseIrsaArrondie.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal sumBrut = BigDecimal.ZERO;
                for (IrsaTrancheDef t : in.tranchesIrsa()) {
                    BigDecimal min = zeroIfNull(t.seuilMin());
                    BigDecimal max = t.seuilMax() == null ? baseIrsaArrondie : t.seuilMax();
                    BigDecimal taxable = baseIrsaArrondie.min(max).subtract(min).max(BigDecimal.ZERO);
                    BigDecimal rate = t.tauxPourcent() == null ? BigDecimal.ZERO : (t.tauxPourcent().compareTo(BigDecimal.ONE) >= 0 ? t.tauxPourcent().movePointLeft(2) : t.tauxPourcent());
                    sumBrut = sumBrut.add(taxable.multiply(rate));
                }
                irsaBrut = round2(sumBrut);
                // Arrondi à la dizaine d'Ariary supérieure : 10 * ceil(irsaBrut / 10)
                irsaArrondiDizaine = irsaBrut.divide(TEN, 0, RoundingMode.CEILING).multiply(TEN).setScale(2, RoundingMode.HALF_UP);

                BigDecimal dedEnfant = in.deductionParEnfant() != null ? in.deductionParEnfant() : new BigDecimal("2000.00");
                reductionEnfants = round2(dedEnfant.multiply(BigDecimal.valueOf(Math.max(0, in.nbEnfants()))));

                BigDecimal minPerception = in.minPerceptionIrsa() != null ? in.minPerceptionIrsa() : new BigDecimal("2000.00");
                BigDecimal computedNet = irsaArrondiDizaine.subtract(reductionEnfants).max(minPerception);
                irsaNet = round2(computedNet.min(neti));
            }
        }

        // 10. Indemnités non imposables & Retenues personnelles
        BigDecimal totalIndemnitesNonImposables = round2(zeroIfNull(in.indemnitesNonImposables()));
        BigDecimal totalRetenuesTresorerie = round2(zeroIfNull(in.retenuesTresorerie()));

        BigDecimal netAvantTresorerie = neti.subtract(retenueIsi).subtract(irsaNet).add(totalIndemnitesNonImposables).max(BigDecimal.ZERO);
        BigDecimal netAvantArrondi = round2(netAvantTresorerie.subtract(totalRetenuesTresorerie));

        // 11. Net à payer final : arrondi bancaire MROUND 10
        BigDecimal netAPayer = netAvantArrondi.divide(TEN, 0, RoundingMode.HALF_UP).multiply(TEN).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ajustementArrondi = round2(netAPayer.subtract(netAvantArrondi));

        // 12. Coût global employeur
        BigDecimal coutGlobalEmployeur = round2(brut.add(totalCotisPat).add(totalIndemnitesNonImposables));

        return new OracleResult(
                baseLigne,
                tauxHoraire,
                retenueAbsence,
                deductionCongeTrentieme,
                indemniteConge,
                netConge,
                mntHs30,
                mntHs50,
                mntNuit,
                mntDim,
                mntFer,
                totalHs,
                brut,
                cnapsSal,
                cnapsPat,
                santeSal,
                santePat,
                fmfpPat,
                totalCotisSal,
                totalCotisPat,
                neti,
                baseIrsaArrondie,
                irsaBrut,
                irsaArrondiDizaine,
                reductionEnfants,
                irsaNet,
                retenueIsi,
                totalIndemnitesNonImposables,
                totalRetenuesTresorerie,
                netAvantArrondi,
                netAPayer,
                ajustementArrondi,
                coutGlobalEmployeur
        );
    }
}
