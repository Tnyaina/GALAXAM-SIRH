package mg.galaxam.sirh.pointage.domain.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Résultat journalier explicable. Le temps est conservé exclusivement en minutes. */
public record DailyAttendanceResult(
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

    public DailyAttendanceResult {
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
