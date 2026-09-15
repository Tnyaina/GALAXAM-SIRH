package mg.galaxam.sirh.model.paie.workflow;

/** Actions de l'assistant Paie qui ne sont pas des transitions d'état de période. */
public enum PayrollAction {
    SIMULATE,
    CALCULATE,
    VALIDATE,
    ADD_VARIABLE
}
