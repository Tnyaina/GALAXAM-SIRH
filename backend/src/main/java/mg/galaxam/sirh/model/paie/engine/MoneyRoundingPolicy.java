package mg.galaxam.sirh.model.paie.engine;

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
