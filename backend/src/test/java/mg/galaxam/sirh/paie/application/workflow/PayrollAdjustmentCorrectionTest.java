package mg.galaxam.sirh.paie.application.workflow;

import mg.galaxam.sirh.paie.application.workflow.PayrollWorkflowCommands.PayrollAdjustmentCommand;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PayrollAdjustmentCorrectionTest {
    private final PayrollAdjustmentWorkflowService.TransitionMatrix matrix =
            new PayrollAdjustmentWorkflowService.TransitionMatrix();

    @Test
    void variableImputeePeutEtreAnnuleeAvantRecalcul() {
        var decision = matrix.apply("IMPUTE_PAIE", PayrollAdjustmentCommand.CANCEL);
        assertFalse(decision.blocked());
        assertEquals("ANNULE", decision.targetStatut());
    }

    @Test
    void variableAnnuleeNePeutPasEtreApprouveeDeNouveau() {
        var decision = matrix.apply("ANNULE", PayrollAdjustmentCommand.APPROVE);
        assertTrue(decision.blocked());
    }
}
