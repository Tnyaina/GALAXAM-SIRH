package mg.galaxam.sirh.pointage.domain.calculation;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/** Agrège les semaines sans relire ni recalculer les badges journaliers. */
@Component
public class AttendancePeriodCalculator {
    public AttendancePeriodResult calculateEmployeePeriod(AttendancePeriodContext context,
                                                           List<WeeklyAttendanceResult> weeks) {
        List<WeeklyAttendanceResult> safeWeeks = weeks == null ? List.of() : List.copyOf(weeks);
        List<DailyAttendanceResult> days = safeWeeks.stream().flatMap(w -> w.days().stream()).toList();
        long sunday = days.stream().filter(DailyAttendanceResult::sunday)
                .mapToLong(DailyAttendanceResult::workedMinutes).sum();
        long holiday = days.stream().filter(DailyAttendanceResult::publicHoliday)
                .mapToLong(DailyAttendanceResult::workedMinutes).sum();
        return new AttendancePeriodResult(context.employeeId(), context.periodId(),
                safeWeeks.stream().mapToLong(WeeklyAttendanceResult::workedMinutes).sum(),
                safeWeeks.stream().mapToLong(WeeklyAttendanceResult::overtime30Minutes).sum(),
                safeWeeks.stream().mapToLong(WeeklyAttendanceResult::overtime50Minutes).sum(),
                days.stream().mapToLong(DailyAttendanceResult::nightMinutes).sum(), sunday, holiday,
                safeWeeks.stream().mapToLong(WeeklyAttendanceResult::absenceMinutes).sum(),
                safeWeeks.stream().mapToLong(WeeklyAttendanceResult::lateMinutes).sum(),
                days.stream().mapToInt(DailyAttendanceResult::mealDays).sum(),
                days.stream().map(DailyAttendanceResult::mealAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                days.stream().map(DailyAttendanceResult::travelUnits).reduce(BigDecimal.ZERO, BigDecimal::add),
                days.stream().map(DailyAttendanceResult::travelAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                safeWeeks);
    }
}
