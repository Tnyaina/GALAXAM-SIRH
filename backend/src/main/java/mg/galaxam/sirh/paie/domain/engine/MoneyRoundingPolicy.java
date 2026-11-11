package mg.galaxam.sirh.paie.domain.engine;


import java.math.BigDecimal;
import java.math.RoundingMode;

/** Politique unique des arrondis monétaires de Paie. */
public final class MoneyRoundingPolicy {
    private MoneyRoundingPolicy() { }

    public static BigDecimal intermediate(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal contribution(BigDecimal value) { return amount(value); }
    public static BigDecimal tax(BigDecimal value) { return amount(value); }
    public static BigDecimal netPay(BigDecimal value) { return amount(value); }

    /** Ariary entier le plus proche ; une demi-unité s'éloigne de zéro. */
    public static BigDecimal roundToNearestAriary(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(0, RoundingMode.HALF_UP);
    }

    /** Ariary entier immédiatement inférieur, y compris pour les montants négatifs. */
    public static BigDecimal roundDownToAriary(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(0, RoundingMode.FLOOR);
    }

    /** Ariary entier immédiatement supérieur, y compris pour les montants négatifs. */
    public static BigDecimal roundUpToAriary(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(0, RoundingMode.CEILING);
    }

    public static BigDecimal roundDownToHundredAriary(BigDecimal value) {
        return roundToMultiple(value, 100, RoundingMode.FLOOR);
    }

    public static BigDecimal roundUpToHundredAriary(BigDecimal value) {
        return roundToMultiple(value, 100, RoundingMode.CEILING);
    }

    public static BigDecimal roundDownToThousandAriary(BigDecimal value) {
        return roundToMultiple(value, 1000, RoundingMode.FLOOR);
    }

    public static BigDecimal roundUpToThousandAriary(BigDecimal value) {
        return roundToMultiple(value, 1000, RoundingMode.CEILING);
    }

    private static BigDecimal roundToMultiple(BigDecimal value, int step, RoundingMode mode) {
        if (value == null) return BigDecimal.ZERO;
        BigDecimal unit = BigDecimal.valueOf(step);
        return value.divide(unit, 0, mode).multiply(unit);
    }

    public static BigDecimal irsaTaxBaseLowerHundred(BigDecimal value) {
        if (value == null || value.signum() <= 0) return BigDecimal.ZERO.setScale(2);
        return value.divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                .multiply(BigDecimal.valueOf(100)).setScale(2);
    }

    public static BigDecimal irsaCalculatedUpperTen(BigDecimal value) {
        if (value == null || value.signum() <= 0) return BigDecimal.ZERO.setScale(2);
        return value.divide(BigDecimal.TEN, 0, RoundingMode.CEILING)
                .multiply(BigDecimal.TEN).setScale(2);
    }
}
