package mg.galaxam.sirh.model.pointage.calculation;

import java.time.LocalDate;

public record WeeklyAttendanceContext(
        LocalDate weekStart,
        long expectedMinutes,
        long firstOvertimeBandMinutes,
        boolean overtimeEligible
) {
    public WeeklyAttendanceContext {
        if (weekStart == null || expectedMinutes < 0 || firstOvertimeBandMinutes < 0) {
            throw new IllegalArgumentException("Contexte hebdomadaire invalide.");
        }
    }
}
