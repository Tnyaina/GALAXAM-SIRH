package mg.galaxam.sirh.paie.api.dto;

public record PayrollPopulationMember(
        int employeeId,
        String matricule,
        String employeeName,
        String contractNumber,
        String regime,
        String decision,
        String reasonCode) {
}
