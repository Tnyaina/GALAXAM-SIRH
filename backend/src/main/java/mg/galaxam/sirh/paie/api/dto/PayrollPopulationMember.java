package mg.galaxam.sirh.paie.api.dto;

public record PayrollPopulationMember(
        int employeeId,
        String matricule,
        String employeeName,
        String typeContrat,
        String contractNumber,
        String nomSite,
        String statutEmploye,
        String regime,
        String decision,
        String reasonCode) {
}
