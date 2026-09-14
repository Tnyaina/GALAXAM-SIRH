package mg.galaxam.sirh.model.pointage.calculation;

import java.time.LocalDate;

public record AttendancePeriodContext(Integer employeeId, Integer periodId,
                                      LocalDate startsOn, LocalDate endsOn) {
    public AttendancePeriodContext {
        if (employeeId == null || startsOn == null || endsOn == null || endsOn.isBefore(startsOn)) {
            throw new IllegalArgumentException("Contexte de période de pointage invalide.");
        }
    }
}
