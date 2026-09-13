package mg.galaxam.sirh.unit.paie;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import mg.galaxam.sirh.model.paie.dto.PayrollAttendanceInput;
import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;
import org.junit.jupiter.api.Test;

class PayrollAttendanceInputTest {
    @Test
    void snapshotProvenanceAndExactMinutesFeedPayrollInput() {
        PayrollAttendanceInput snapshot = new PayrollAttendanceInput(
                42, 3, 7, 9, Instant.parse("2026-09-30T20:00:00Z"),
                10_400, 420, 75, 135, 480, 12,
                new BigDecimal("22"), new BigDecimal("20"), BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal("20"), new BigDecimal("40000"), new BigDecimal("19.5"),
                new BigDecimal("47154.51"));
        PayrollCalculationInput input = new PayrollCalculationInput();
        input.setIdEmploye(7);
        input.setIdPeriodePaie(9);

        input.setAttendanceInput(snapshot);

        assertThat(input.getAttendanceInput().snapshotId()).isEqualTo(42);
        assertThat(input.getHeuresHs30()).isEqualByComparingTo("7.00");
        assertThat(input.getHeuresNuit()).isEqualByComparingTo("2.25");
        assertThat(input.getIndemniteRepas()).isEqualByComparingTo("40000");
    }
}
