package mg.galaxam.sirh.model.pointage.calculation;

import java.util.List;
import org.springframework.stereotype.Component;

/** Applique uniquement les règles hebdomadaires à des résultats journaliers déjà calculés. */
@Component
public class WeeklyAttendanceCalculator {
    public WeeklyAttendanceResult calculateWeek(WeeklyAttendanceContext context,
                                                 List<DailyAttendanceResult> days) {
        List<DailyAttendanceResult> safeDays = days == null ? List.of() : List.copyOf(days);
        long worked = safeDays.stream().mapToLong(DailyAttendanceResult::workedMinutes).sum();
        long late = safeDays.stream().mapToLong(DailyAttendanceResult::lateMinutes).sum();
        long grossOvertime = Math.max(0, worked - context.expectedMinutes());
        long eligibleOvertime = context.overtimeEligible() ? grossOvertime : 0;
        long overtime30 = Math.min(eligibleOvertime, context.firstOvertimeBandMinutes());
        long overtime50 = Math.max(0, eligibleOvertime - overtime30);
        long absence = Math.max(0, context.expectedMinutes() - worked);
        return new WeeklyAttendanceResult(context.weekStart(), context.expectedMinutes(), worked,
                grossOvertime, overtime30, overtime50, absence, late, safeDays);
    }
}
