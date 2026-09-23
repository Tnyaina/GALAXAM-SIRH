package mg.galaxam.sirh.unit.paie;

import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.paie.application.workflow.PayrollAdjustmentCommand;
import mg.galaxam.sirh.paie.application.workflow.PayrollAdjustmentWorkflowService;
import mg.galaxam.sirh.paie.application.workflow.PayrollAdjustmentWorkflowService.AdjustmentState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires purs (sans base de données) de la matrice de transitions
 * des ajustements de paie.
 *
 * Ces tests DOIVENT ÉCHOUER avant création de PayrollAdjustmentWorkflowService
 * (contre-exemple prouvant le bug P1-01).
 */
@DisplayName("P1-01 — PayrollAdjustmentWorkflowService — Matrice de transitions")
class PayrollAdjustmentWorkflowTest {

    private final PayrollAdjustmentWorkflowService.TransitionMatrix matrix =
            new PayrollAdjustmentWorkflowService.TransitionMatrix();

    // =========================================================================
    // Transitions valides
    // =========================================================================

    @Test
    @DisplayName("EN_ATTENTE + APPROVE → APPROUVE")
    void approveFromPendingSucceeds() {
        AdjustmentState next = matrix.apply("EN_ATTENTE", PayrollAdjustmentCommand.APPROVE);
        assertThat(next.targetStatut()).isEqualTo("APPROUVE");
        assertThat(next.blocked()).isFalse();
    }

    @Test
    @DisplayName("EN_ATTENTE + REJECT → REJETE")
    void rejectFromPendingSucceeds() {
        AdjustmentState next = matrix.apply("EN_ATTENTE", PayrollAdjustmentCommand.REJECT);
        assertThat(next.targetStatut()).isEqualTo("REJETE");
        assertThat(next.blocked()).isFalse();
    }

    @Test
    @DisplayName("EN_ATTENTE + CANCEL → ANNULE")
    void cancelFromPendingSucceeds() {
        AdjustmentState next = matrix.apply("EN_ATTENTE", PayrollAdjustmentCommand.CANCEL);
        assertThat(next.targetStatut()).isEqualTo("ANNULE");
        assertThat(next.blocked()).isFalse();
    }

    @Test
    @DisplayName("APPROUVE + CANCEL → ANNULE")
    void cancelFromApprovedSucceeds() {
        AdjustmentState next = matrix.apply("APPROUVE", PayrollAdjustmentCommand.CANCEL);
        assertThat(next.targetStatut()).isEqualTo("ANNULE");
        assertThat(next.blocked()).isFalse();
    }

    // =========================================================================
    // Transitions bloquées
    // =========================================================================

    @Test
    @DisplayName("APPROUVE + APPROVE → BLOCKED (déjà approuvé)")
    void doubleApproveIsBlocked() {
        AdjustmentState next = matrix.apply("APPROUVE", PayrollAdjustmentCommand.APPROVE);
        assertThat(next.blocked()).isTrue();
        assertThat(next.errorCode()).isEqualTo(SirhErrorCode.PAYROLL_ADJUSTMENT_ALREADY_APPROVED);
    }

    @Test
    @DisplayName("IMPUTE_PAIE + APPROVE → BLOCKED (imputé sur bulletin)")
    void approveImputedIsBlocked() {
        AdjustmentState next = matrix.apply("IMPUTE_PAIE", PayrollAdjustmentCommand.APPROVE);
        assertThat(next.blocked()).isTrue();
        assertThat(next.errorCode()).isEqualTo(SirhErrorCode.PAYROLL_PERIOD_TRANSITION_BLOCKED);
    }

    @Test
    @DisplayName("IMPUTE_PAIE + CANCEL → BLOCKED (imputé sur bulletin)")
    void cancelImputedIsBlocked() {
        AdjustmentState next = matrix.apply("IMPUTE_PAIE", PayrollAdjustmentCommand.CANCEL);
        assertThat(next.blocked()).isTrue();
    }

    @Test
    @DisplayName("REJETE + APPROVE → BLOCKED (définitivement rejeté)")
    void approveRejectedIsBlocked() {
        AdjustmentState next = matrix.apply("REJETE", PayrollAdjustmentCommand.APPROVE);
        assertThat(next.blocked()).isTrue();
    }

    @Test
    @DisplayName("ANNULE + APPROVE → BLOCKED (définitivement annulé)")
    void approveAnnuledIsBlocked() {
        AdjustmentState next = matrix.apply("ANNULE", PayrollAdjustmentCommand.APPROVE);
        assertThat(next.blocked()).isTrue();
    }

    // =========================================================================
    // Invariant : le client ne peut jamais fournir un statut arbitraire
    // (test documentant le bug de l'ancienne implémentation PUT /statut)
    // =========================================================================

    @Test
    @DisplayName("La matrice ne connaît que APPROVE, REJECT, CANCEL — pas de statut arbitraire")
    void onlyExplicitCommandsAreSupported() {
        // Les seules commandes valides sont les 3 constantes de l'enum
        assertThat(PayrollAdjustmentCommand.values()).hasSize(3);
        assertThat(PayrollAdjustmentCommand.values())
                .containsExactlyInAnyOrder(
                        PayrollAdjustmentCommand.APPROVE,
                        PayrollAdjustmentCommand.REJECT,
                        PayrollAdjustmentCommand.CANCEL);
    }
}
