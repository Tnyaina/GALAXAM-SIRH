package mg.galaxam.sirh.paie.application.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PayrollPeriodCapabilityTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);

    @Test
    void closeRequiresValidatedPayrollAndAnEndedPeriod() {
        var pointageOnly = PayrollPeriodWorkflowService.closeStateCapability(
                "VALIDEE", TODAY.minusDays(1), TODAY);
        assertFalse(pointageOnly.allowed());
        assertEquals("PAYROLL_NOT_VALIDATED", pointageOnly.reasonCodes().get(0));

        var futurePeriod = PayrollPeriodWorkflowService.closeStateCapability(
                "PAIE_VALIDEE", TODAY.plusDays(1), TODAY);
        assertFalse(futurePeriod.allowed());
        assertEquals("PERIOD_NOT_ENDED", futurePeriod.reasonCodes().get(0));

        assertTrue(PayrollPeriodWorkflowService.closeStateCapability(
                "PAIE_VALIDEE", TODAY, TODAY).allowed());
    }

    @Test
    void outputsRequireThePayrollPhaseRatherThanThePointagePhase() {
        assertEquals("PAYROLL_NOT_VALIDATED", PayrollPeriodWorkflowService
                .outputStateCapability("VALIDEE").reasonCodes().get(0));
        assertTrue(PayrollPeriodWorkflowService.outputStateCapability("PAIE_VALIDEE").allowed());
        assertTrue(PayrollPeriodWorkflowService.outputStateCapability("CLOTUREE").allowed());
    }
}
