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
        long dailySurplus = safeDays.stream()
                .filter(this::isCompensableWorkingDay)
                .mapToLong(day -> Math.max(0, day.workedMinutes() - dailyTarget(day, context.standardDayMinutes())))
                .sum();

        // Les heures au-delà de la journée normale sont le seul crédit compensable.
        // La formule inclut également les jours sans pointage : ils sont présents dans
        // l'objectif hebdomadaire même s'ils n'ont aucun résultat journalier.
        long missingBeforeCompensation = Math.max(0, context.expectedMinutes() - worked + dailySurplus);
        long compensated = Math.min(dailySurplus, missingBeforeCompensation);
        long grossOvertime = dailySurplus - compensated;
        long eligibleOvertime = context.overtimeEligible() ? grossOvertime : 0;
        long overtime30 = Math.min(eligibleOvertime, context.firstOvertimeBandMinutes());
        long overtime50 = Math.max(0, eligibleOvertime - overtime30);
        long absence = missingBeforeCompensation - compensated;
        return new WeeklyAttendanceResult(context.weekStart(), context.expectedMinutes(), worked,
                dailySurplus, compensated, grossOvertime, overtime30, overtime50, absence, late, safeDays);
    }

    private boolean isCompensableWorkingDay(DailyAttendanceResult day) {
        return !day.sunday() && !day.publicHoliday()
                && day.status() != DailyAttendanceResult.Status.LEAVE
                && day.status() != DailyAttendanceResult.Status.REST
                && day.status() != DailyAttendanceResult.Status.PUBLIC_HOLIDAY_OFF;
    }

    private long dailyTarget(DailyAttendanceResult day, long standardDayMinutes) {
        return day.status() == DailyAttendanceResult.Status.HALF_DAY
                ? standardDayMinutes / 2
                : standardDayMinutes;
    }
}
