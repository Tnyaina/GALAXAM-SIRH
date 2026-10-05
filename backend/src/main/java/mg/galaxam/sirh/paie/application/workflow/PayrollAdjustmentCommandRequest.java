package mg.galaxam.sirh.paie.application.workflow;

import mg.galaxam.sirh.paie.application.workflow.PayrollWorkflowCommands.PayrollAdjustmentCommand;


import jakarta.validation.constraints.NotNull;

/**
 * DTO de la commande métier d'un ajustement de paie.
 * Le client fournit une commande explicite — jamais un statut cible.
 */
public class PayrollAdjustmentCommandRequest {

    @NotNull(message = "La commande est obligatoire (APPROVE, REJECT, CANCEL).")
    private PayrollAdjustmentCommand command;

    private String reason;

    /** Version courante de l'ajustement (optimistic locking). */
    @NotNull(message = "La version courante de l'ajustement est obligatoire.")
    private Integer expectedVersion;

    public PayrollAdjustmentCommand getCommand() { return command; }
    public void setCommand(PayrollAdjustmentCommand command) { this.command = command; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Integer getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Integer expectedVersion) { this.expectedVersion = expectedVersion; }
}
