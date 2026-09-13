package mg.galaxam.sirh.unit.paie;

import mg.galaxam.sirh.model.paie.engine.MoneyRoundingPolicy;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyRoundingPolicyTest {
    @Test void amountUsesHalfUpAtCentBoundary() {
        assertEquals(new BigDecimal("1.00"), MoneyRoundingPolicy.amount(new BigDecimal("1.004")));
        assertEquals(new BigDecimal("1.01"), MoneyRoundingPolicy.amount(new BigDecimal("1.005")));
        assertEquals(new BigDecimal("1.01"), MoneyRoundingPolicy.amount(new BigDecimal("1.006")));
    }

    @Test void payrollLinesAreRoundedBeforeTheyAreSummed() {
        BigDecimal firstLine = MoneyRoundingPolicy.amount(new BigDecimal("1.005"));
        BigDecimal secondLine = MoneyRoundingPolicy.amount(new BigDecimal("1.005"));

        assertEquals(new BigDecimal("2.02"), firstLine.add(secondLine));
        assertEquals(new BigDecimal("2.01"),
                MoneyRoundingPolicy.amount(new BigDecimal("1.005").add(new BigDecimal("1.005"))));
    }
}
