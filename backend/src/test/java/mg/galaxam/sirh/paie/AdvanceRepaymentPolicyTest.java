package mg.galaxam.sirh.paie;

import java.math.BigDecimal;
import mg.galaxam.sirh.paie.domain.engine.AdvanceRepaymentPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AdvanceRepaymentPolicyTest {
    @Test
    void scheduledAndEarlyRepaymentKeepTheRemainingBalanceAsLimit() {
        BigDecimal initial = new BigDecimal("1000000.00");
        BigDecimal remaining = new BigDecimal("1000000.00");
        assertEquals(new BigDecimal("500000.00"), AdvanceRepaymentPolicy.retenueDemandee(initial, 2, remaining, null));
        assertEquals(new BigDecimal("1000000.00"), AdvanceRepaymentPolicy.retenueDemandee(initial, 2, remaining, initial));
        assertEquals(new BigDecimal("200000.00"), AdvanceRepaymentPolicy.retenueDemandee(initial, 2,
                new BigDecimal("200000.00"), null));
        assertEquals(BigDecimal.ZERO, AdvanceRepaymentPolicy.retenueDemandee(initial, 2, remaining, BigDecimal.ZERO));
    }
}
