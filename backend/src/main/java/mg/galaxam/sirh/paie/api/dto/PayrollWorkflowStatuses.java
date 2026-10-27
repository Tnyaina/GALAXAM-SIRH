package mg.galaxam.sirh.paie.api.dto;

/** Les deux statuts à afficher séparément pour une même période. */
public record PayrollWorkflowStatuses(PayrollPhaseStatus pointage, PayrollPhaseStatus paie) {
    public record PayrollPhaseStatus(String code, String label, String tone) {}
}
