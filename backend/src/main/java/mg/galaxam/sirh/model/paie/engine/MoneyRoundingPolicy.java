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
}
