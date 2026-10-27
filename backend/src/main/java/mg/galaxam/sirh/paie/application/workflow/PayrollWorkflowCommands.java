package mg.galaxam.sirh.paie.application.workflow;

/** Commandes typées du workflow Paie ; les noms JSON des valeurs restent stables. */
public final class PayrollWorkflowCommands {
    private PayrollWorkflowCommands() {}

    public enum PayrollAction {
        SIMULATE,
        CALCULATE,
        VALIDATE,
        ADD_VARIABLE,
        GENERATE_OUTPUT
    }

    public enum PayrollPeriodCommand {
        OPEN,
        SUBMIT,
        APPROVE,
        REJECT,
        CLOSE,
        REOPEN
    }

    public enum PayrollAdjustmentCommand {
        /** Approuver l'ajustement : EN_ATTENTE → APPROUVE */
        APPROVE,
        /** Rejeter l'ajustement : EN_ATTENTE → REJETE */
        REJECT,
        /** Annuler l'ajustement : EN_ATTENTE ou APPROUVE → ANNULE */
        CANCEL
    }

    public enum PointagePeriodAction {
        EDIT_POINTAGE,
        REGULARIZE_ANOMALY
    }
}
