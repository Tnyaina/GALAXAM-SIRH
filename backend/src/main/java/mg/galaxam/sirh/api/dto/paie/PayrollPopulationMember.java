package mg.galaxam.sirh.api.dto.paie;

public record PayrollPopulationMember(
        int employeeId,
        String matricule,
        String employeeName,
        String contractNumber,
        String regime,
        String decision,
        String reasonCode) {
}
