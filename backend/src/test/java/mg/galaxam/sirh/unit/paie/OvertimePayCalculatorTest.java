package mg.galaxam.sirh.unit.paie;

import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;
import mg.galaxam.sirh.model.paie.engine.OvertimePayCalculator;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OvertimePayCalculatorTest {
    @Test void usesPremiumProvidedByContext() {
        PayrollCalculationInput input = new PayrollCalculationInput();
        input.setHeuresHs30(new BigDecimal("1.00"));
        input.setMajorationHs30(new BigDecimal("0.31"));
        OvertimePayCalculator.Result r = new OvertimePayCalculator().calculate(input, new BigDecimal("100.00"));
        assertEquals(new BigDecimal("131.00"), r.hs30());
    }
}
