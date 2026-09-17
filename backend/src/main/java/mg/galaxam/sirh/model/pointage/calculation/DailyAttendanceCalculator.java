package mg.galaxam.sirh.model.pointage.calculation;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Calcul pur d'une journée : aucune requête, transaction, horloge ou écriture. */
@Component
public class DailyAttendanceCalculator {

    /** Seule formule d'indemnité repas; les paramètres sont résolus par l'orchestrateur à la date. */
    public BigDecimal calculateMealAmount(boolean granted, BigDecimal baseAmount, LocalTime exit,
                                          LocalTime threshold1, BigDecimal amount1,
                                          LocalTime threshold2, BigDecimal amount2) {
        if (!granted) return BigDecimal.ZERO;
        BigDecimal total = baseAmount == null ? BigDecimal.ZERO : baseAmount;
        if (exit != null && threshold2 != null && !exit.isBefore(threshold2)) return total.add(nonNegative(amount2));
        if (exit != null && threshold1 != null && !exit.isBefore(threshold1)) return total.add(nonNegative(amount1));
        return total;
    }

    /** Seule formule d'indemnité déplacement; le résultat est en BigDecimal. */
    public BigDecimal calculateTravelAmount(BigDecimal fraction, BigDecimal dailyRate,
                                            boolean reduce, BigDecimal reduction) {
        BigDecimal amount = nonNegative(fraction).multiply(nonNegative(dailyRate));
        if (reduce) amount = amount.subtract(nonNegative(fraction).multiply(nonNegative(reduction))).max(BigDecimal.ZERO);
        // La politique d'arrondi est appliquée une seule fois à la frontière de persistance/export.
        return amount;
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        BigDecimal result = value == null ? BigDecimal.ZERO : value;
        if (result.signum() < 0) throw new IllegalArgumentException("Un montant d'indemnité ne peut pas être négatif.");
        return result;
    }

    public DailyAttendanceResult calculateDay(DailyAttendanceContext context) {
        List<String> anomalies = validatePunches(context);
        boolean sunday = context.date().getDayOfWeek() == DayOfWeek.SUNDAY;
        boolean weekend = sunday || context.date().getDayOfWeek() == DayOfWeek.SATURDAY;

        long scheduledMinutes = scheduledMinutes(context);
        long morningMinutes = morningMinutes(context);
        long afternoonMinutes = afternoonMinutes(context);
        long workedMinutes = morningMinutes + afternoonMinutes;
        boolean hasPunch = hasAnyPunch(context);

        if (context.leavePart() == DailyAttendanceContext.LeavePart.FULL_DAY ||
                (context.publicHoliday() && !hasPunch)) {
            workedMinutes = 0;
            morningMinutes = 0;
            afternoonMinutes = 0;
        } else if (context.autonomous() && !hasPunch && !weekend) {
            workedMinutes = scheduledMinutes;
            morningMinutes = scheduledMorningMinutes(context);
            afternoonMinutes = scheduledMinutes - morningMinutes;
        }

        long lateMinutes = context.autonomous() ? 0 : lateMinutes(context);
        long normalMinutes = Math.min(workedMinutes, scheduledMinutes);
        long absenceMinutes = Math.max(0, scheduledMinutes - workedMinutes);
        long nightMinutes = nightMinutes(context);

        DailyAttendanceResult.Status status = determineStatus(
                context, anomalies, sunday, weekend, hasPunch, workedMinutes, scheduledMinutes);

        boolean eligibleForAllowance = workedMinutes > 0 &&
                context.leavePart() != DailyAttendanceContext.LeavePart.FULL_DAY && !context.publicHoliday();
        int mealDays = eligibleForAllowance && context.mealGranted() ? 1 : 0;
        BigDecimal mealAmount = mealDays == 1 ? context.mealAmount() : BigDecimal.ZERO;
        BigDecimal travelUnits = eligibleForAllowance ? context.travelUnits() : BigDecimal.ZERO;
        BigDecimal travelAmount = travelUnits.multiply(context.travelUnitAmount());
        if (context.applyMealTravelDeduction() && mealDays == 1) {
            travelAmount = travelAmount.subtract(travelUnits.multiply(context.mealTravelDeduction())).max(BigDecimal.ZERO);
        }

        return new DailyAttendanceResult(context.employeeId(), context.date(), status,
                morningMinutes, afternoonMinutes, workedMinutes, normalMinutes, lateMinutes, absenceMinutes, nightMinutes,
                sunday, context.publicHoliday(), mealDays, mealAmount, travelUnits,
                travelAmount, anomalies);
    }

    private List<String> validatePunches(DailyAttendanceContext c) {
        List<String> anomalies = new ArrayList<>();
        if (c.continuousSchedule()) {
            if ((c.morningEntry() == null) != (c.afternoonExit() == null)) {
                anomalies.add("Pointage de journée continue incomplet.");
            }
            return anomalies;
        }
        if (isAutomaticBreak(c)) return anomalies;
        validatePair(c.morningEntry(), c.morningExit(), "matin", anomalies);
        validatePair(c.afternoonEntry(), c.afternoonExit(), "après-midi", anomalies);
        if (!c.continuousSchedule() && c.morningExit() != null && c.afternoonEntry() != null
                && c.afternoonEntry().isBefore(c.morningExit())) {
            anomalies.add("L'entrée d'après-midi précède la sortie du matin.");
        }
        return anomalies;
    }

    private void validatePair(LocalTime start, LocalTime end, String segment, List<String> anomalies) {
        if ((start == null) != (end == null)) anomalies.add("Pointage " + segment + " incomplet.");
    }

    private long morningMinutes(DailyAttendanceContext c) {
        if (c.continuousSchedule()) return minutesBetween(c.morningEntry(), c.afternoonExit());
        if (isAutomaticBreak(c)) {
            return minutesBetween(c.morningEntry(), c.scheduledMorningExit());
        }
        return minutesBetween(c.morningEntry(), c.morningExit());
    }

    private long afternoonMinutes(DailyAttendanceContext c) {
        if (c.continuousSchedule()) return 0;
        if (isAutomaticBreak(c)) return minutesBetween(c.scheduledAfternoonEntry(), c.afternoonExit());
        return minutesBetween(c.afternoonEntry(), c.afternoonExit());
    }

    private long scheduledMinutes(DailyAttendanceContext c) {
        if (c.continuousSchedule()) {
            return minutesBetween(c.scheduledMorningEntry(), c.scheduledAfternoonExit());
        }
        return minutesBetween(c.scheduledMorningEntry(), c.scheduledMorningExit()) +
                minutesBetween(c.scheduledAfternoonEntry(), c.scheduledAfternoonExit());
    }

    private long scheduledMorningMinutes(DailyAttendanceContext c) {
        if (c.continuousSchedule()) return scheduledMinutes(c);
        return minutesBetween(c.scheduledMorningEntry(), c.scheduledMorningExit());
    }

    private long lateMinutes(DailyAttendanceContext c) {
        long morning = positiveMinutes(c.scheduledMorningEntry(), c.morningEntry());
        if (c.continuousSchedule() || isAutomaticBreak(c)) return morning;
        long afternoon = afternoonLateMinutes(c);
        return morning + afternoon;
    }

    /** La reprise tardive est mesurée sur la pause réellement prise lorsqu'elle débute tard. */
    private long afternoonLateMinutes(DailyAttendanceContext c) {
        if (c.afternoonEntry() == null) return 0;
        if (c.morningExit() != null && c.scheduledMorningExit() != null && c.scheduledAfternoonEntry() != null
                && c.scheduledAfternoonEntry().isAfter(c.scheduledMorningExit())
                && c.morningExit().isAfter(c.scheduledMorningExit())
                && c.afternoonEntry().isAfter(c.morningExit())) {
            long scheduledBreak = minutesBetween(c.scheduledMorningExit(), c.scheduledAfternoonEntry());
            long actualBreak = minutesBetween(c.morningExit(), c.afternoonEntry());
            return Math.max(0, actualBreak - scheduledBreak);
        }
        return positiveMinutes(c.scheduledAfternoonEntry(), c.afternoonEntry());
    }

    private boolean isAutomaticBreak(DailyAttendanceContext c) {
        return !c.continuousSchedule() && c.morningEntry() != null && c.afternoonExit() != null
                && c.morningExit() == null && c.afternoonEntry() == null;
    }

    private long nightMinutes(DailyAttendanceContext c) {
        if (c.afternoonExit() == null || c.nightStartsAt() == null || c.nightEndsAt() == null) return 0;
        LocalTime exit = c.afternoonExit();
        if (exit.isAfter(c.nightStartsAt())) return Duration.between(c.nightStartsAt(), exit).toMinutes();
        if (!exit.isAfter(c.nightEndsAt())) return minutesBetween(c.nightStartsAt(), exit);
        return 0;
    }

    private DailyAttendanceResult.Status determineStatus(DailyAttendanceContext c, List<String> anomalies,
            boolean sunday, boolean weekend, boolean hasPunch, long worked, long scheduled) {
        if (!anomalies.isEmpty()) return DailyAttendanceResult.Status.ANOMALY;
        if (c.leavePart() == DailyAttendanceContext.LeavePart.FULL_DAY) return DailyAttendanceResult.Status.LEAVE;
        if (c.publicHoliday()) return hasPunch ? DailyAttendanceResult.Status.PUBLIC_HOLIDAY_WORKED
                : DailyAttendanceResult.Status.PUBLIC_HOLIDAY_OFF;
        if (sunday && worked > 0) return DailyAttendanceResult.Status.SUNDAY_WORKED;
        if (weekend && worked == 0) return DailyAttendanceResult.Status.REST;
        if (c.autonomous() && !hasPunch) return DailyAttendanceResult.Status.AUTONOMOUS;
        if (worked == 0) return DailyAttendanceResult.Status.ABSENT;
        if (c.leavePart() != DailyAttendanceContext.LeavePart.NONE || worked < scheduled) {
            return DailyAttendanceResult.Status.HALF_DAY;
        }
        return DailyAttendanceResult.Status.PRESENT;
    }

    private boolean hasAnyPunch(DailyAttendanceContext c) {
        return c.morningEntry() != null || c.morningExit() != null ||
                c.afternoonEntry() != null || c.afternoonExit() != null;
    }

    private long positiveMinutes(LocalTime expected, LocalTime actual) {
        if (expected == null || actual == null || !actual.isAfter(expected)) return 0;
        return Duration.between(expected, actual).toMinutes();
    }

    private long minutesBetween(LocalTime start, LocalTime end) {
        if (start == null || end == null) return 0;
        long minutes = Duration.between(start, end).toMinutes();
        return minutes >= 0 ? minutes : minutes + 24L * 60L;
    }
}
