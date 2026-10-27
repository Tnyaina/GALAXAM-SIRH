package mg.galaxam.sirh.paie.application.workflow;

import mg.galaxam.sirh.common.dto.ActionCapability;
import mg.galaxam.sirh.paie.api.dto.PayrollPeriodContextView;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayrollPeriodNavigationTest {
    @Test
    void pointageLinksRetainSelectedPayrollPeriod() throws Exception {
        Method next = PayrollPeriodWorkflowService.class.getDeclaredMethod(
                "computeNextStep", int.class, String.class, Map.class, String.class);
        next.setAccessible(true);
        var service = new PayrollPeriodWorkflowService(null, null, null, null);
        for (String action : new String[]{"EDIT_POINTAGE", "SUBMIT", "APPROVE"}) {
            var result = (PayrollPeriodContextView.NextStep) next.invoke(service, 15,
                    "EN_COURS", Map.of(action, ActionCapability.permit()), "POINTAGE");
            assertEquals("15", result.destination().split("periode=")[1], action);
        }
    }
}
