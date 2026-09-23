package mg.galaxam.sirh.pointage.domain.calculation;

import java.time.LocalDate;

public record WeeklyAttendanceContext(
        LocalDate weekStart,
        long expectedMinutes,
        long firstOvertimeBandMinutes,
        boolean overtimeEligible,
        long standardDayMinutes,
        WeeklyPriorState priorState
) {
    public WeeklyAttendanceContext {
        if (weekStart == null || expectedMinutes < 0 || firstOvertimeBandMinutes < 0 || standardDayMinutes < 0) {
            throw new IllegalArgumentException("Contexte hebdomadaire invalide.");
        }
        if (priorState == null) {
            priorState = WeeklyPriorState.NONE;
        }
    }

    /** Constructeur standard pour une semaine sans tronçon antérieur. */
    public WeeklyAttendanceContext(LocalDate weekStart, long expectedMinutes,
                                   long firstOvertimeBandMinutes, boolean overtimeEligible,
                                   long standardDayMinutes) {
        this(weekStart, expectedMinutes, firstOvertimeBandMinutes, overtimeEligible,
                standardDayMinutes, WeeklyPriorState.NONE);
    }
}
