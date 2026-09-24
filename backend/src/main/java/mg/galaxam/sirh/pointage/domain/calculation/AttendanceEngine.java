package mg.galaxam.sirh.pointage.domain.calculation;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Calcul pur d'une journée : aucune requête, transaction, horloge ou écriture. */
@Component
public class AttendanceEngine {

    public record Context(
            Integer employeeId,
            LocalDate date,
            LocalTime morningEntry,
            LocalTime morningExit,
            LocalTime afternoonEntry,
            LocalTime afternoonExit,
            LocalTime scheduledMorningEntry,
            LocalTime scheduledMorningExit,
            LocalTime scheduledAfternoonEntry,
            LocalTime scheduledAfternoonExit,
            LocalTime nightStartsAt,
            LocalTime nightEndsAt,
            boolean continuousSchedule,
            boolean autonomous,
            boolean publicHoliday,
            LeavePart leavePart,
            boolean mealGranted,
            BigDecimal mealAmount,
            BigDecimal travelUnits,
            BigDecimal travelUnitAmount,
            BigDecimal mealTravelDeduction,
            boolean applyMealTravelDeduction
    ) {
        public enum LeavePart { NONE, MORNING, AFTERNOON, FULL_DAY }

        public Context {
            leavePart = leavePart == null ? LeavePart.NONE : leavePart;
            mealAmount = mealAmount == null ? BigDecimal.ZERO : mealAmount;
            travelUnits = travelUnits == null ? BigDecimal.ZERO : travelUnits;
            travelUnitAmount = travelUnitAmount == null ? BigDecimal.ZERO : travelUnitAmount;
            mealTravelDeduction = mealTravelDeduction == null ? BigDecimal.ZERO : mealTravelDeduction;
            if (date == null) throw new IllegalArgumentException("La date de pointage est obligatoire.");
            if (mealAmount.signum() < 0 || travelUnits.signum() < 0
                    || travelUnitAmount.signum() < 0 || mealTravelDeduction.signum() < 0) {
                throw new IllegalArgumentException("Les quantités et montants journaliers ne peuvent pas être négatifs.");
            }
        }
    }

    public record Result(
            Integer employeeId,
            LocalDate date,
            Status status,
            long morningMinutes,
            long afternoonMinutes,
            long workedMinutes,
            long normalMinutes,
            long lateMinutes,
            long absenceMinutes,
            long nightMinutes,
            boolean sunday,
            boolean publicHoliday,
            int mealDays,
            BigDecimal mealAmount,
            BigDecimal travelUnits,
            BigDecimal travelAmount,
            List<String> anomalies
    ) {
        public enum Status {
            PRESENT, HALF_DAY, ABSENT, LEAVE, REST, PUBLIC_HOLIDAY_OFF,
            PUBLIC_HOLIDAY_WORKED, SUNDAY_WORKED, AUTONOMOUS, ANOMALY
        }

        public Result {
            if (morningMinutes < 0 || afternoonMinutes < 0 || workedMinutes < 0 || normalMinutes < 0
                    || lateMinutes < 0 || absenceMinutes < 0 || nightMinutes < 0) {
                throw new IllegalArgumentException("Une durée journalière ne peut pas être négative.");
            }
            mealAmount = mealAmount == null ? BigDecimal.ZERO : mealAmount;
            travelUnits = travelUnits == null ? BigDecimal.ZERO : travelUnits;
            travelAmount = travelAmount == null ? BigDecimal.ZERO : travelAmount;
            anomalies = anomalies == null ? List.of() : List.copyOf(anomalies);
        }
    }

    /** Seule formule d'indemnité repas; les paramètres sont résolus par l'orchestrateur à la date. */
    public BigDecimal calculateMealAmount(boolean granted, BigDecimal baseAmount, LocalTime exit,
                                          LocalTime threshold1, BigDecimal amount1,
                                          LocalTime threshold2, BigDecimal amount2) {
        if (!granted) return BigDecimal.ZERO;
        BigDecimal total = baseAmount == null ? BigDecimal.ZERO : baseAmount;
        if (exit != null && threshold2 != null && !exit.isBefore(threshold2)) {
            return total.add(nonNegative(amount2));
        }
        if (exit != null && threshold1 != null && !exit.isBefore(threshold1)) {
            return total.add(nonNegative(amount1));
        }
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

    public Result calculateDay(Context context) {
        List<String> anomalies = validatePunches(context);
        boolean sunday = context.date().getDayOfWeek() == DayOfWeek.SUNDAY;
        boolean weekend = sunday || context.date().getDayOfWeek() == DayOfWeek.SATURDAY;

        long scheduledMinutes = scheduledMinutes(context);
        long morningMinutes = morningMinutes(context);
        long afternoonMinutes = afternoonMinutes(context);
        long workedMinutes = morningMinutes + afternoonMinutes;
        boolean hasPunch = hasAnyPunch(context);

        if (context.leavePart() == Context.LeavePart.FULL_DAY ||
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
        if (context.leavePart() == Context.LeavePart.FULL_DAY || (context.publicHoliday() && !hasPunch)) {
            absenceMinutes = 0;
        } else if (context.leavePart() == Context.LeavePart.MORNING) {
            absenceMinutes = Math.max(0, absenceMinutes - scheduledMorningMinutes(context));
        } else if (context.leavePart() == Context.LeavePart.AFTERNOON) {
            absenceMinutes = Math.max(0, absenceMinutes - (scheduledMinutes - scheduledMorningMinutes(context)));
        }
        long nightMinutes = nightMinutes(context);

        Result.Status status = determineStatus(
                context, anomalies, sunday, weekend, hasPunch, workedMinutes, scheduledMinutes);

        boolean eligibleForAllowance = workedMinutes > 0 &&
                context.leavePart() != Context.LeavePart.FULL_DAY && !context.publicHoliday();
        int mealDays = eligibleForAllowance && context.mealGranted() ? 1 : 0;
        BigDecimal mealAmount = mealDays == 1 ? context.mealAmount() : BigDecimal.ZERO;
        BigDecimal travelUnits = eligibleForAllowance ? context.travelUnits() : BigDecimal.ZERO;
        BigDecimal travelAmount = travelUnits.multiply(context.travelUnitAmount());
        if (context.applyMealTravelDeduction() && mealDays == 1) {
            travelAmount = travelAmount.subtract(travelUnits.multiply(context.mealTravelDeduction())).max(BigDecimal.ZERO);
        }

        return new Result(context.employeeId(), context.date(), status,
                morningMinutes, afternoonMinutes, workedMinutes, normalMinutes, lateMinutes, absenceMinutes, nightMinutes,
                sunday, context.publicHoliday(), mealDays, mealAmount, travelUnits,
                travelAmount, anomalies);
    }

    private List<String> validatePunches(Context c) {
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

    private long morningMinutes(Context c) {
        if (c.continuousSchedule()) return minutesBetween(c.morningEntry(), c.afternoonExit());
        if (isAutomaticBreak(c)) {
            return minutesBetween(c.morningEntry(), c.scheduledMorningExit());
        }
        return minutesBetween(c.morningEntry(), c.morningExit());
    }

    private long afternoonMinutes(Context c) {
        if (c.continuousSchedule()) return 0;
        if (isAutomaticBreak(c)) return minutesBetween(c.scheduledAfternoonEntry(), c.afternoonExit());
        return minutesBetween(c.afternoonEntry(), c.afternoonExit());
    }

    private long scheduledMinutes(Context c) {
        if (c.continuousSchedule()) {
            return minutesBetween(c.scheduledMorningEntry(), c.scheduledAfternoonExit());
        }
        return minutesBetween(c.scheduledMorningEntry(), c.scheduledMorningExit()) +
                minutesBetween(c.scheduledAfternoonEntry(), c.scheduledAfternoonExit());
    }

    private long scheduledMorningMinutes(Context c) {
        if (c.continuousSchedule()) return scheduledMinutes(c);
        return minutesBetween(c.scheduledMorningEntry(), c.scheduledMorningExit());
    }

    private long lateMinutes(Context c) {
        long morning = positiveMinutes(c.scheduledMorningEntry(), c.morningEntry());
        if (c.continuousSchedule() || isAutomaticBreak(c)) return morning;
        long afternoon = afternoonLateMinutes(c);
        return morning + afternoon;
    }

    /** La reprise tardive est mesurée sur la pause réellement prise lorsqu'elle débute tard. */
    private long afternoonLateMinutes(Context c) {
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

    private boolean isAutomaticBreak(Context c) {
        return !c.continuousSchedule() && c.morningEntry() != null && c.afternoonExit() != null
                && c.morningExit() == null && c.afternoonEntry() == null;
    }

    private long nightMinutes(Context c) {
        if (c.afternoonExit() == null || c.nightStartsAt() == null || c.nightEndsAt() == null) return 0;
        LocalTime exit = c.afternoonExit();
        if (exit.isAfter(c.nightStartsAt())) return Duration.between(c.nightStartsAt(), exit).toMinutes();
        if (!exit.isAfter(c.nightEndsAt())) return minutesBetween(c.nightStartsAt(), exit);
        return 0;
    }

    private Result.Status determineStatus(Context c, List<String> anomalies,
            boolean sunday, boolean weekend, boolean hasPunch, long worked, long scheduled) {
        if (!anomalies.isEmpty()) return Result.Status.ANOMALY;
        if (c.leavePart() == Context.LeavePart.FULL_DAY) return Result.Status.LEAVE;
        if (c.publicHoliday()) return hasPunch ? Result.Status.PUBLIC_HOLIDAY_WORKED
                : Result.Status.PUBLIC_HOLIDAY_OFF;
        if (sunday && worked > 0) return Result.Status.SUNDAY_WORKED;
        if (weekend && worked == 0) return Result.Status.REST;
        if (c.autonomous() && !hasPunch) return Result.Status.AUTONOMOUS;
        if (worked == 0) return Result.Status.ABSENT;
        if (c.leavePart() != Context.LeavePart.NONE || worked < scheduled) {
            return Result.Status.HALF_DAY;
        }
        return Result.Status.PRESENT;
    }

    private boolean hasAnyPunch(Context c) {
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

    // Calcul hebdomadaire : les surplus journaliers compensent les déficits
    // du même tronçon avant de devenir des heures supplémentaires.
    public record WeekContext(LocalDate weekStart, long expectedMinutes,
            long firstOvertimeBandMinutes, boolean overtimeEligible, long standardDayMinutes) {
        public WeekContext {
            if (weekStart == null || expectedMinutes < 0 || firstOvertimeBandMinutes < 0 || standardDayMinutes < 0) {
                throw new IllegalArgumentException("Contexte hebdomadaire invalide.");
            }
        }
    }

    public record WeekResult(LocalDate weekStart, long expectedMinutes, long workedMinutes,
            long dailySurplusMinutes, long compensatedMinutes, long grossOvertimeMinutes,
            long overtime30Minutes, long overtime50Minutes, long absenceMinutes,
            long lateMinutes, List<Result> days) {
        public WeekResult {
            days = days == null ? List.of() : List.copyOf(days);
        }
        public long totalWeekOvertimeMinutes() { return overtime30Minutes + overtime50Minutes; }
        public long totalWeekAbsenceMinutes() { return absenceMinutes; }
    }

    public WeekResult calculateWeek(WeekContext context, List<Result> days) {
        List<Result> safeDays = days == null ? List.of() : List.copyOf(days);
        long worked = safeDays.stream().mapToLong(Result::workedMinutes).sum();
        long late = safeDays.stream().mapToLong(Result::lateMinutes).sum();
        long dailySurplus = safeDays.stream().filter(this::isCompensableWorkingDay)
                .mapToLong(day -> Math.max(0, day.workedMinutes() - dailyTarget(day, context.standardDayMinutes())))
                .sum();
        long missingBeforeCompensation = Math.max(0, context.expectedMinutes() - worked + dailySurplus);
        long compensated = Math.min(dailySurplus, missingBeforeCompensation);
        long grossOvertime = dailySurplus - compensated;
        long eligibleOvertime = context.overtimeEligible() ? grossOvertime : 0;
        long overtime30 = Math.min(eligibleOvertime, context.firstOvertimeBandMinutes());
        long overtime50 = Math.max(0, eligibleOvertime - overtime30);
        long absence = missingBeforeCompensation - compensated;
        return new WeekResult(context.weekStart(), context.expectedMinutes(), worked,
                dailySurplus, compensated, grossOvertime, overtime30, overtime50, absence, late, safeDays);
    }

    private boolean isCompensableWorkingDay(Result day) {
        return !day.sunday() && !day.publicHoliday()
                && day.status() != Result.Status.LEAVE && day.status() != Result.Status.REST
                && day.status() != Result.Status.PUBLIC_HOLIDAY_OFF;
    }

    private long dailyTarget(Result day, long standardDayMinutes) {
        if (day.date() != null && day.date().getDayOfWeek() == DayOfWeek.SATURDAY) return 0;
        return day.status() == Result.Status.HALF_DAY ? standardDayMinutes / 2 : standardDayMinutes;
    }

    /** SEMAINE ∩ PÉRIODE : chaque tronçon est autonome pour la compensation. */
    public record WeekSegment(int index, LocalDate start, LocalDate end, LocalDate monday, LocalDate sunday) {
        public WeekSegment {
            if (start == null || end == null || monday == null || sunday == null || start.isAfter(end)) {
                throw new IllegalArgumentException("Bornes du tronçon hebdomadaire invalides.");
            }
        }
        public boolean isTruncatedStart() { return start.isAfter(monday); }
        public boolean isTruncatedEnd() { return end.isBefore(sunday); }
    }

    public static List<WeekSegment> segmentWeeks(LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut == null || dateFin == null || dateDebut.isAfter(dateFin)) return List.of();
        List<WeekSegment> segments = new ArrayList<>();
        LocalDate cur = dateDebut;
        int idx = 1;
        while (!cur.isAfter(dateFin)) {
            LocalDate monday = cur.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate sunday = cur.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            LocalDate end = sunday.isAfter(dateFin) ? dateFin : sunday;
            segments.add(new WeekSegment(idx++, cur, end, monday, sunday));
            cur = end.plusDays(1);
        }
        return segments;
    }
}
