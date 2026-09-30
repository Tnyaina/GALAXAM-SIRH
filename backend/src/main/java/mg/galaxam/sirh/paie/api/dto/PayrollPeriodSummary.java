package mg.galaxam.sirh.paie.api.dto;

import java.time.LocalDate;

public record PayrollPeriodSummary(
        int id,
        String code,
        String label,
        LocalDate startDate,
        LocalDate endDate,
        String state,
        int version,
        PayrollWorkflowStatuses workflowStatuses) {
}
