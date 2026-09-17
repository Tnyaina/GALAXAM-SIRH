package mg.galaxam.sirh.model.pointage.calculation;

import java.time.LocalDate;
import java.util.List;

public record WeeklyAttendanceResult(
        LocalDate weekStart,
        long expectedMinutes,
        long workedMinutes,
        long dailySurplusMinutes,
        long compensatedMinutes,
        long grossOvertimeMinutes,
        long overtime30Minutes,
        long overtime50Minutes,
        long absenceMinutes,
        long lateMinutes,
        List<DailyAttendanceResult> days,
        long cumulativeExpectedMinutes,
        long cumulativeWorkedMinutes,
        long cumulativeDailySurplusMinutes,
        long cumulativeCompensatedMinutes,
        long cumulativeGrossOvertimeMinutes,
        long cumulativeOvertime30Minutes,
        long cumulativeOvertime50Minutes,
        long cumulativeAbsenceMinutes
) {
    public WeeklyAttendanceResult {
        days = days == null ? List.of() : List.copyOf(days);
    }

    /** Compatibilité pour les appels existants à 11 paramètres (tronçon autonome ou semaine unique). */
    public WeeklyAttendanceResult(
            LocalDate weekStart, long expectedMinutes, long workedMinutes,
            long dailySurplusMinutes, long compensatedMinutes, long grossOvertimeMinutes,
            long overtime30Minutes, long overtime50Minutes, long absenceMinutes,
            long lateMinutes, List<DailyAttendanceResult> days
    ) {
        this(weekStart, expectedMinutes, workedMinutes, dailySurplusMinutes, compensatedMinutes,
                grossOvertimeMinutes, overtime30Minutes, overtime50Minutes, absenceMinutes, lateMinutes,
                days, expectedMinutes, workedMinutes, dailySurplusMinutes, compensatedMinutes,
                grossOvertimeMinutes, overtime30Minutes, overtime50Minutes, absenceMinutes);
    }

    /** Total des heures supplémentaires de la semaine civile complète. */
    public long totalWeekOvertimeMinutes() {
        return cumulativeOvertime30Minutes + cumulativeOvertime50Minutes;
    }

    /** Total de déficit/absence de la semaine civile complète. */
    public long totalWeekAbsenceMinutes() {
        return cumulativeAbsenceMinutes;
    }

    /** Exporte l'état cumulé pour transmission fluide au tronçon suivant de la même semaine. */
    public WeeklyPriorState toPriorState() {
        return new WeeklyPriorState(
                cumulativeExpectedMinutes,
                cumulativeWorkedMinutes,
                cumulativeDailySurplusMinutes,
                cumulativeCompensatedMinutes,
                cumulativeAbsenceMinutes,
                cumulativeGrossOvertimeMinutes,
                cumulativeOvertime30Minutes,
                cumulativeOvertime50Minutes
        );
    }
}
