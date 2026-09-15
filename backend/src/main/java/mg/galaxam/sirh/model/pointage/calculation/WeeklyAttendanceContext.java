package mg.galaxam.sirh.model.pointage.calculation;

import java.time.LocalDate;

public record WeeklyAttendanceContext(
        LocalDate weekStart,
        long expectedMinutes,
        long firstOvertimeBandMinutes,
        boolean overtimeEligible,
        long standardDayMinutes
) {
    public WeeklyAttendanceContext {
        if (weekStart == null || expectedMinutes < 0 || firstOvertimeBandMinutes < 0 || standardDayMinutes < 0) {
            throw new IllegalArgumentException("Contexte hebdomadaire invalide.");
        }
    }

    /** Compatibilité des appels existants : une semaine complète compte cinq journées standard. */
    public WeeklyAttendanceContext(LocalDate weekStart, long expectedMinutes,
                                   long firstOvertimeBandMinutes, boolean overtimeEligible) {
        this(weekStart, expectedMinutes, firstOvertimeBandMinutes, overtimeEligible,
                expectedMinutes == 0 ? 0 : expectedMinutes / 5);
    }
}
