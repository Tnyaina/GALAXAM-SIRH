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
        List<DailyAttendanceResult> days
) {
    public WeeklyAttendanceResult {
        days = List.copyOf(days);
    }
}
