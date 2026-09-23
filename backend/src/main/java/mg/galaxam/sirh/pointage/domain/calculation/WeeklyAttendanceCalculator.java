package mg.galaxam.sirh.pointage.domain.calculation;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Applique les règles hebdomadaires à des résultats journaliers déjà calculés.
 * Assure la continuité exacte des calculs pour les semaines civiles chevauchant
 * deux périodes de paie (semaines tronquées) via l'état WeeklyPriorState.
 */
@Component
public class WeeklyAttendanceCalculator {

    public WeeklyAttendanceResult calculateWeek(WeeklyAttendanceContext context,
                                                 List<DailyAttendanceResult> days) {
        List<DailyAttendanceResult> safeDays = days == null ? List.of() : List.copyOf(days);
        WeeklyPriorState prior = context.priorState() == null ? WeeklyPriorState.NONE : context.priorState();

        long worked = safeDays.stream().mapToLong(DailyAttendanceResult::workedMinutes).sum();
        long late = safeDays.stream().mapToLong(DailyAttendanceResult::lateMinutes).sum();
        long dailySurplus = safeDays.stream()
                .filter(this::isCompensableWorkingDay)
                .mapToLong(day -> Math.max(0, day.workedMinutes() - dailyTarget(day, context.standardDayMinutes())))
                .sum();

        // 1. Calcul cumulé à l'échelle de la semaine civile complète (prior + actuel)
        long totalExpected = prior.expectedMinutes() + context.expectedMinutes();
        long totalWorked = prior.workedMinutes() + worked;
        long totalSurplus = prior.dailySurplusMinutes() + dailySurplus;

        // Les heures au-delà de la journée normale sont le seul crédit compensable.
        // La formule inclut également les jours sans pointage : ils sont présents dans
        // l'objectif hebdomadaire même s'ils n'ont aucun résultat journalier.
        long missingBeforeCompensation = Math.max(0, totalExpected - totalWorked + totalSurplus);
        long totalCompensated = Math.min(totalSurplus, missingBeforeCompensation);
        long totalGrossOvertime = totalSurplus - totalCompensated;
        long totalEligibleOvertime = context.overtimeEligible() ? totalGrossOvertime : 0;
        long totalOvertime30 = Math.min(totalEligibleOvertime, context.firstOvertimeBandMinutes());
        long totalOvertime50 = Math.max(0, totalEligibleOvertime - totalOvertime30);
        long totalAbsence = missingBeforeCompensation - totalCompensated;

        // 2. Quote-part incrémentale imputable au tronçon actuel (après déduction du prior)
        long incrementalCompensated = Math.max(0, totalCompensated - prior.compensatedMinutes());
        long incrementalGrossOvertime = Math.max(0, totalGrossOvertime - prior.grossOvertimeMinutes());
        long incrementalOvertime30 = Math.max(0, totalOvertime30 - prior.overtime30Minutes());
        long incrementalOvertime50 = Math.max(0, totalOvertime50 - prior.overtime50Minutes());
        long incrementalAbsence = Math.max(0, totalAbsence - prior.deficitMinutes());

        return new WeeklyAttendanceResult(
                context.weekStart(), context.expectedMinutes(), worked,
                dailySurplus, incrementalCompensated, incrementalGrossOvertime,
                incrementalOvertime30, incrementalOvertime50, incrementalAbsence, late, safeDays,
                totalExpected, totalWorked, totalSurplus, totalCompensated,
                totalGrossOvertime, totalOvertime30, totalOvertime50, totalAbsence
        );
    }

    private boolean isCompensableWorkingDay(DailyAttendanceResult day) {
        return !day.sunday() && !day.publicHoliday()
                && day.status() != DailyAttendanceResult.Status.LEAVE
                && day.status() != DailyAttendanceResult.Status.REST
                && day.status() != DailyAttendanceResult.Status.PUBLIC_HOLIDAY_OFF;
    }

    private long dailyTarget(DailyAttendanceResult day, long standardDayMinutes) {
        if (day.date() != null && day.date().getDayOfWeek() == java.time.DayOfWeek.SATURDAY) {
            return 0;
        }
        return day.status() == DailyAttendanceResult.Status.HALF_DAY
                ? standardDayMinutes / 2
                : standardDayMinutes;
    }
}
