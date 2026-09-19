package mg.galaxam.sirh.unit.paie.certification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Oracle financier indépendant pour la contre-certification temporelle.
 * Ne fait appel à AUCUN calculateur du moteur de production (PayrollExecutionEngine, IrsaCalculator, etc.).
 * Implémente strictement les règles de droit positif malgache (CGI, CNaPS, Décrets).
 */
public final class TemporalTaxAndSocialCertificationOracle {

    private TemporalTaxAndSocialCertificationOracle() {}

    // --- SEUILS LÉGAUX ET PARAMÈTRES TEMPORELS ---
    public static final LocalDate DATE_PIVOT_CNAPS = LocalDate.of(2026, 3, 1);
    public static final LocalDate DATE_PIVOT_IRSA = LocalDate.of(2026, 1, 1);

    public static final BigDecimal SME_2024_2025 = new BigDecimal("262680.00");
    public static final BigDecimal PLAFOND_CNAPS_2024_2025 = new BigDecimal("2101440.00"); // 8 * 262 680

    public static final BigDecimal SME_2026_MARS = new BigDecimal("300000.00");
    public static final BigDecimal PLAFOND_CNAPS_2026_MARS = new BigDecimal("2400000.00"); // 8 * 300 000

    public static final BigDecimal MIN_PERCEPTION_IRSA_2025 = new BigDecimal("2000.00");
    public static final BigDecimal MIN_PERCEPTION_IRSA_2026 = new BigDecimal("3000.00");
    public static final BigDecimal REDUCTION_PAR_ENFANT = new BigDecimal("2000.00");
    public static final BigDecimal SEUIL_EXONERATION_IRSA = new BigDecimal("350000.00");

    // --- ENREGISTREMENTS RÉSULTATS ---
    public record IrsaResult(
            BigDecimal assietteInitiale,
            BigDecimal assietteTronquee,
            BigDecimal irsaBrut,
            BigDecimal irsaArrondiDizaine,
            BigDecimal reductionEnfants,
            BigDecimal irsaTheoriqueApresReduction,
            BigDecimal irsaNet
    ) {}

    public record CnapsResult(
            BigDecimal brut,
            BigDecimal assiettePlafonnee,
            BigDecimal cotisSalariale,
            BigDecimal cotisPatronale,
            BigDecimal smeApplicable,
            BigDecimal plafondApplicable
    ) {}

    public record OvertimeExemptionResult(
            BigDecimal totalHeuresHs,
            BigDecimal heuresExonerees,
            BigDecimal heuresImposables,
            BigDecimal remunHsTotale,
            BigDecimal remunHsExoneree,
            BigDecimal remunHsImposable
    ) {}

    // =========================================================================
    // 1. ORACLES IRSA TEMPORELS (2025 vs 2026)
    // =========================================================================

    public static BigDecimal tronquerAssiette(BigDecimal neti) {
        if (neti == null || neti.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return neti.divide(new BigDecimal("100"), 0, RoundingMode.FLOOR).multiply(new BigDecimal("100"));
    }

    public static BigDecimal arrondirDizaineSup(BigDecimal montant) {
        if (montant == null || montant.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return montant.divide(BigDecimal.TEN, 0, RoundingMode.CEILING).multiply(BigDecimal.TEN);
    }

    /**
     * IRSA 2025 : LF 2024/2025 (CGI en vigueur jusqu'au 31/12/2025)
     * 5 Tranches :
     * T1 : 0 - 350 000 Ar @ 0%
     * T2 : 350 001 - 400 000 Ar @ 5%
     * T3 : 400 001 - 500 000 Ar @ 10%
     * T4 : 500 001 - 600 000 Ar @ 15%
     * T5 : > 600 000 Ar @ 20%
     * Minimum de perception : 2 000 Ar
     */
    public static IrsaResult calculateIrsa2025(BigDecimal netImposable, int nbEnfants) {
        BigDecimal assiette = tronquerAssiette(netImposable);
        BigDecimal irsaBrut = BigDecimal.ZERO;

        if (assiette.compareTo(new BigDecimal("350000")) > 0) {
            BigDecimal t2 = assiette.min(new BigDecimal("400000")).subtract(new BigDecimal("350000"));
            irsaBrut = irsaBrut.add(t2.multiply(new BigDecimal("0.05")));
        }
        if (assiette.compareTo(new BigDecimal("400000")) > 0) {
            BigDecimal t3 = assiette.min(new BigDecimal("500000")).subtract(new BigDecimal("400000"));
            irsaBrut = irsaBrut.add(t3.multiply(new BigDecimal("0.10")));
        }
        if (assiette.compareTo(new BigDecimal("500000")) > 0) {
            BigDecimal t4 = assiette.min(new BigDecimal("600000")).subtract(new BigDecimal("500000"));
            irsaBrut = irsaBrut.add(t4.multiply(new BigDecimal("0.15")));
        }
        if (assiette.compareTo(new BigDecimal("600000")) > 0) {
            BigDecimal t5 = assiette.subtract(new BigDecimal("600000"));
            irsaBrut = irsaBrut.add(t5.multiply(new BigDecimal("0.20")));
        }

        BigDecimal irsaArrondi = arrondirDizaineSup(irsaBrut);
        BigDecimal reduction = REDUCTION_PAR_ENFANT.multiply(BigDecimal.valueOf(Math.max(0, nbEnfants)));
        BigDecimal netApresReduction = irsaArrondi.subtract(reduction).max(BigDecimal.ZERO);

        BigDecimal irsaNet;
        if (netImposable.compareTo(SEUIL_EXONERATION_IRSA) <= 0) {
            irsaNet = MIN_PERCEPTION_IRSA_2025;
        } else {
            irsaNet = netApresReduction.max(MIN_PERCEPTION_IRSA_2025);
        }

        return new IrsaResult(
                netImposable, assiette, irsaBrut.setScale(2, RoundingMode.HALF_UP),
                irsaArrondi, reduction, netApresReduction, irsaNet
        );
    }

    /**
     * IRSA 2026 : LF 2026 (CGI en vigueur à compter du 01/01/2026)
     * 6 Tranches :
     * T1 : 0 - 350 000 Ar @ 0%
     * T2 : 350 001 - 400 000 Ar @ 5%
     * T3 : 400 001 - 500 000 Ar @ 10%
     * T4 : 500 001 - 600 000 Ar @ 15%
     * T5 : 600 001 - 4 000 000 Ar @ 20%
     * T6 : > 4 000 000 Ar @ 25% (Tranche marginale hauts revenus)
     * Minimum de perception : 3 000 Ar
     */
    public static IrsaResult calculateIrsa2026(BigDecimal netImposable, int nbEnfants) {
        BigDecimal assiette = tronquerAssiette(netImposable);
        BigDecimal irsaBrut = BigDecimal.ZERO;

        if (assiette.compareTo(new BigDecimal("350000")) > 0) {
            BigDecimal t2 = assiette.min(new BigDecimal("400000")).subtract(new BigDecimal("350000"));
            irsaBrut = irsaBrut.add(t2.multiply(new BigDecimal("0.05")));
        }
        if (assiette.compareTo(new BigDecimal("400000")) > 0) {
            BigDecimal t3 = assiette.min(new BigDecimal("500000")).subtract(new BigDecimal("400000"));
            irsaBrut = irsaBrut.add(t3.multiply(new BigDecimal("0.10")));
        }
        if (assiette.compareTo(new BigDecimal("500000")) > 0) {
            BigDecimal t4 = assiette.min(new BigDecimal("600000")).subtract(new BigDecimal("500000"));
            irsaBrut = irsaBrut.add(t4.multiply(new BigDecimal("0.15")));
        }
        if (assiette.compareTo(new BigDecimal("600000")) > 0) {
            BigDecimal t5 = assiette.min(new BigDecimal("4000000")).subtract(new BigDecimal("600000"));
            irsaBrut = irsaBrut.add(t5.multiply(new BigDecimal("0.20")));
        }
        if (assiette.compareTo(new BigDecimal("4000000")) > 0) {
            BigDecimal t6 = assiette.subtract(new BigDecimal("4000000"));
            irsaBrut = irsaBrut.add(t6.multiply(new BigDecimal("0.25")));
        }

        BigDecimal irsaArrondi = arrondirDizaineSup(irsaBrut);
        BigDecimal reduction = REDUCTION_PAR_ENFANT.multiply(BigDecimal.valueOf(Math.max(0, nbEnfants)));
        BigDecimal netApresReduction = irsaArrondi.subtract(reduction).max(BigDecimal.ZERO);

        BigDecimal irsaNet;
        if (netImposable.compareTo(SEUIL_EXONERATION_IRSA) <= 0) {
            irsaNet = MIN_PERCEPTION_IRSA_2026;
        } else {
            irsaNet = netApresReduction.max(MIN_PERCEPTION_IRSA_2026);
        }

        return new IrsaResult(
                netImposable, assiette, irsaBrut.setScale(2, RoundingMode.HALF_UP),
                irsaArrondi, reduction, netApresReduction, irsaNet
        );
    }

    public static IrsaResult calculateIrsaByDate(LocalDate date, BigDecimal netImposable, int nbEnfants) {
        if (date.isBefore(DATE_PIVOT_IRSA)) {
            return calculateIrsa2025(netImposable, nbEnfants);
        } else {
            return calculateIrsa2026(netImposable, nbEnfants);
        }
    }

    // =========================================================================
    // 2. ORACLES CNaPS TEMPORELS (Décret SME vs Revalorisation)
    // =========================================================================

    public static BigDecimal resolveSmeAt(LocalDate date) {
        if (date.isBefore(DATE_PIVOT_CNAPS)) {
            return SME_2024_2025;
        } else {
            return SME_2026_MARS;
        }
    }

    public static BigDecimal resolvePlafondCnapsAt(LocalDate date) {
        return resolveSmeAt(date).multiply(new BigDecimal("8")).setScale(2, RoundingMode.HALF_UP);
    }

    public static CnapsResult calculateCnaps(LocalDate date, BigDecimal totalGainsBruts) {
        BigDecimal sme = resolveSmeAt(date);
        BigDecimal plafond = resolvePlafondCnapsAt(date);
        BigDecimal baseCotisable = totalGainsBruts.max(BigDecimal.ZERO).min(plafond);

        BigDecimal cotisSalariale = baseCotisable.multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cotisPatronale = baseCotisable.multiply(new BigDecimal("0.13")).setScale(2, RoundingMode.HALF_UP);

        return new CnapsResult(totalGainsBruts, baseCotisable, cotisSalariale, cotisPatronale, sme, plafond);
    }

    // =========================================================================
    // 3. ORACLE EXONÉRATION HEURES SUPPLÉMENTAIRES (CGI Art. 01.03.03-9°)
    // =========================================================================

    /**
     * Calcule l'exonération des 20 premières heures supplémentaires du mois.
     * Les heures supplémentaires sont imputées chronologiquement : d'abord la tranche 30%, puis la tranche 50%.
     * L'exonération porte sur (salaire horaire de base + majoration HS).
     */
    public static OvertimeExemptionResult calculateOvertimeExemption(
            BigDecimal tauxHoraire, BigDecimal heuresHs30, BigDecimal heuresHs50) {

        BigDecimal h30 = heuresHs30 != null ? heuresHs30 : BigDecimal.ZERO;
        BigDecimal h50 = heuresHs50 != null ? heuresHs50 : BigDecimal.ZERO;
        BigDecimal totalHs = h30.add(h50);

        BigDecimal tauxHs30 = new BigDecimal("1.30");
        BigDecimal tauxHs50 = new BigDecimal("1.50");

        BigDecimal remunHs30 = h30.multiply(tauxHoraire).multiply(tauxHs30).setScale(2, RoundingMode.HALF_UP);
        BigDecimal remunHs50 = h50.multiply(tauxHoraire).multiply(tauxHs50).setScale(2, RoundingMode.HALF_UP);
        BigDecimal remunTotale = remunHs30.add(remunHs50);

        BigDecimal plafondHeures = new BigDecimal("20.00");
        BigDecimal exo30Heures = h30.min(plafondHeures);
        BigDecimal heuresRestantesExo = plafondHeures.subtract(exo30Heures).max(BigDecimal.ZERO);
        BigDecimal exo50Heures = h50.min(heuresRestantesExo);

        BigDecimal totalHeuresExo = exo30Heures.add(exo50Heures);
        BigDecimal totalHeuresImposables = totalHs.subtract(totalHeuresExo).max(BigDecimal.ZERO);

        BigDecimal remunExo30 = exo30Heures.multiply(tauxHoraire).multiply(tauxHs30).setScale(2, RoundingMode.HALF_UP);
        BigDecimal remunExo50 = exo50Heures.multiply(tauxHoraire).multiply(tauxHs50).setScale(2, RoundingMode.HALF_UP);
        BigDecimal remunExoneree = remunExo30.add(remunExo50);

        BigDecimal remunImposable = remunTotale.subtract(remunExoneree).max(BigDecimal.ZERO);

        return new OvertimeExemptionResult(
                totalHs, totalHeuresExo, totalHeuresImposables,
                remunTotale, remunExoneree, remunImposable
        );
    }
}
