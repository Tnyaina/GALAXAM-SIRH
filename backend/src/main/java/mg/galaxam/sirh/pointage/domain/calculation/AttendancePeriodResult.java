package mg.galaxam.sirh.pointage.domain.calculation;

import java.math.BigDecimal;
import java.util.List;

public record AttendancePeriodResult(
        Integer employeeId,
        Integer periodId,
        long workedMinutes,
        long overtime30Minutes,
        long overtime50Minutes,
        long nightMinutes,
        long sundayMinutes,
        long publicHolidayMinutes,
        long unpaidAbsenceMinutes,
        long lateMinutes,
        int mealDays,
        BigDecimal mealAmount,
        BigDecimal travelUnits,
        BigDecimal travelAmount,
        List<WeeklyAttendanceResult> weeks
) {
    public AttendancePeriodResult { weeks = List.copyOf(weeks); }
}
