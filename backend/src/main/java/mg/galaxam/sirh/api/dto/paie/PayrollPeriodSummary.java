package mg.galaxam.sirh.api.dto.paie;

import java.time.LocalDate;

public record PayrollPeriodSummary(
        int id,
        String code,
        String label,
        LocalDate startDate,
        LocalDate endDate,
        String state,
        int version) {
}
