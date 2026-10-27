package mg.galaxam.sirh.paie.domain.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput.GainVariable;

/** Règles pures des rubriques manuelles : validité, montant et assiettes. */
public final class ManualPayrollRules {
    private ManualPayrollRules() {
    }

    public record AmountResult(BigDecimal base, BigDecimal quantite, BigDecimal taux,
                               BigDecimal avantArrondi, BigDecimal montant, String explication) {
    }

    // Montant saisi ou dérivé ; l'arrondi de rubrique précède l'exécution du bulletin.
    public static AmountResult calculateAmount(String method, String rounding, BigDecimal amount,
            BigDecimal quantity, BigDecimal rate, BigDecimal salaryBase) {
        BigDecimal base, raw;
        switch (method) {
            case "MONTANT" -> {
                base = positive(amount, "Montant");
                raw = base;
            }
            case "QUANTITE_TAUX" -> {
                quantity = positive(quantity, "Quantité");
                rate = positive(rate, "Taux unitaire");
                precision(quantity);
                precision(rate);
                base = rate;
                raw = quantity.multiply(rate);
            }
            case "POURCENTAGE_SALAIRE_BASE" -> {
                base = positive(salaryBase, "Salaire de base");
                rate = positive(rate, "Pourcentage");
                precision(rate);
                if (rate.compareTo(new BigDecimal("100")) > 0) {
                    throw new IllegalArgumentException("Le pourcentage doit être compris entre 0 et 100.");
                }
                raw = base.multiply(rate).movePointLeft(2);
            }
            default -> throw new IllegalArgumentException("Méthode de calcul inconnue : " + method);
        }
        BigDecimal result = switch (rounding) {
            case "CENTIME" -> raw.setScale(2, RoundingMode.HALF_UP);
            case "ARIARY" -> raw.setScale(0, RoundingMode.HALF_UP).setScale(2);
            case "DIZAINE" -> raw.divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP).multiply(BigDecimal.TEN).setScale(2);
            default -> throw new IllegalArgumentException("Politique d’arrondi inconnue : " + rounding);
        };
        if (result.signum() <= 0 || result.precision() - result.scale() > 18) {
            throw new IllegalArgumentException("Le montant calculé est nul ou dépasse la capacité monétaire.");
        }
        String details = switch (method) {
            case "MONTANT" -> "Montant saisi : " + base.toPlainString();
            case "QUANTITE_TAUX" -> quantity.toPlainString() + " × " + rate.toPlainString() + " Ar";
            default -> "Salaire de base " + base.toPlainString() + " Ar × " + rate.toPlainString() + " %";
        };
        return new AmountResult(base, quantity, rate, raw, result,
                details + " ; avant arrondi : " + raw.toPlainString() + " ; " + rounding + " : " + result.toPlainString() + " Ar");
    }

    private static BigDecimal positive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(label + " doit être positif.");
        return value;
    }

    private static void precision(BigDecimal value) {
        if (value.stripTrailingZeros().scale() > 6 || value.precision() - value.scale() > 14) {
            throw new IllegalArgumentException("Quantité et taux : six décimales et quatorze chiffres entiers maximum.");
        }
    }

    /** Contributions indépendantes de la catégorie d'affichage. */
    public record Contributions(BigDecimal brut, BigDecimal social, BigDecimal sante,
                                BigDecimal fmfp, BigDecimal irsa, BigDecimal ajoutNet) {
        public static Contributions resolve(GainVariable gain) {
            BigDecimal montant = gain.getMontant() == null ? BigDecimal.ZERO
                    : gain.getMontant().max(BigDecimal.ZERO);
            boolean brut = gain.isDansBrut();
            return new Contributions(brut ? montant : BigDecimal.ZERO,
                    gain.isSoumisCotisation() ? montant : BigDecimal.ZERO,
                    gain.isSoumisSante() ? montant : BigDecimal.ZERO,
                    gain.isSoumisFmfp() ? montant : BigDecimal.ZERO,
                    brut && gain.isImposable() ? montant : BigDecimal.ZERO,
                    brut ? BigDecimal.ZERO : montant);
        }

        /** Le brut est déjà inclus dans l'assiette sociale initiale. */
        public BigDecimal correctionSociale() { return social.subtract(brut); }
        public BigDecimal correctionSante() { return sante.subtract(brut); }
        public BigDecimal correctionFmfp() { return fmfp.subtract(brut); }
        public BigDecimal exclusionIrsa() { return brut.subtract(irsa); }
    }

    // Une version doit couvrir toute la campagne ; aucun prorata implicite.
    public static boolean covers(LocalDate start, LocalDate end, LocalDate periodStart, LocalDate periodEnd) {
        return (start == null || !start.isAfter(periodStart)) && (end == null || !end.isBefore(periodEnd));
    }

    public static boolean applies(String condition, boolean irsa, boolean cnaps, boolean health) {
        return switch (condition) {
            case "TOUS" -> true;
            case "ASSUJETTI_IRSA" -> irsa;
            case "ASSUJETTI_CNAPS" -> cnaps;
            case "ASSUJETTI_SANTE" -> health;
            default -> throw new IllegalArgumentException("Condition d’application inconnue : " + condition);
        };
    }
}
