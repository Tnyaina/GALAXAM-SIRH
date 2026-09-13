package mg.galaxam.sirh.model.paie.engine;

import java.math.BigDecimal;
import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;

/** Valorisation pure des quantités Pointage déjà qualifiées. */
public final class OvertimePayCalculator {
    public record Result(BigDecimal hs30, BigDecimal hs50, BigDecimal nuit, BigDecimal ferie) {
        public BigDecimal total() { return hs30.add(hs50).add(nuit).add(ferie); }
    }
    public Result calculate(PayrollCalculationInput input, BigDecimal hourlyRate) {
        BigDecimal h30 = nz(input.getHeuresHs30());
        BigDecimal h50 = nz(input.getHeuresHs50());
        BigDecimal night = nz(input.getHeuresNuit());
        BigDecimal holiday = nz(input.getHeuresFeriees());
        return new Result(
            MoneyRoundingPolicy.amount(h30.multiply(hourlyRate).multiply(BigDecimal.ONE.add(input.getMajorationHs30()))),
            MoneyRoundingPolicy.amount(h50.multiply(hourlyRate).multiply(BigDecimal.ONE.add(input.getMajorationHs50()))),
            MoneyRoundingPolicy.amount(night.multiply(hourlyRate).multiply(input.getMajorationNuit())),
            MoneyRoundingPolicy.amount(holiday.multiply(hourlyRate).multiply(BigDecimal.ONE.add(input.getMajorationFerie()))));
    }
    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
