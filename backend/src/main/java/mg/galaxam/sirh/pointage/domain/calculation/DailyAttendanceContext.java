package mg.galaxam.sirh.pointage.domain.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** Entrées entièrement résolues, datées et sans dépendance de persistance d'une journée. */
public record DailyAttendanceContext(
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
    public enum LeavePart { NONE, FULL_DAY, MORNING, AFTERNOON }

    public DailyAttendanceContext {
        if (employeeId == null || date == null) {
            throw new IllegalArgumentException("L'employé et la date sont obligatoires pour calculer une journée.");
        }
        leavePart = leavePart == null ? LeavePart.NONE : leavePart;
        mealAmount = nonNegative(mealAmount, "mealAmount");
        travelUnits = nonNegative(travelUnits, "travelUnits");
        travelUnitAmount = nonNegative(travelUnitAmount, "travelUnitAmount");
        mealTravelDeduction = nonNegative(mealTravelDeduction, "mealTravelDeduction");
    }

    private static BigDecimal nonNegative(BigDecimal value, String name) {
        BigDecimal result = value == null ? BigDecimal.ZERO : value;
        if (result.signum() < 0) throw new IllegalArgumentException(name + " ne peut pas être négatif.");
        return result;
    }
}
