package mg.galaxam.sirh.model.paie.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Contrat immutable entre le snapshot Pointage et la Paie.
 * Les durées sont exprimées en minutes; les montants ne passent jamais par double.
 */
public record PayrollAttendanceInput(
        int snapshotId,
        int snapshotVersion,
        int employeeId,
        int payrollPeriodId,
        Instant frozenAt,
        long workedMinutes,
        long overtime30Minutes,
        long overtime50Minutes,
        long nightMinutes,
        long unpaidAbsenceMinutes,
        int lateMinutes,
        BigDecimal workingDays,
        BigDecimal presentDays,
        BigDecimal absenceDays,
        BigDecimal leaveDays,
        BigDecimal mealDays,
        BigDecimal mealAmount,
        BigDecimal travelUnits,
        BigDecimal travelAmount
) {
    public PayrollAttendanceInput {
        if (snapshotId <= 0 || snapshotVersion <= 0 || employeeId <= 0 || payrollPeriodId <= 0 || frozenAt == null) {
            throw new IllegalArgumentException("La provenance du snapshot Pointage est incomplète.");
        }
        if (workedMinutes < 0 || overtime30Minutes < 0 || overtime50Minutes < 0 || nightMinutes < 0
                || unpaidAbsenceMinutes < 0 || lateMinutes < 0) {
            throw new IllegalArgumentException("Une quantité Pointage ne peut pas être négative.");
        }
        workingDays = zeroIfNull(workingDays);
        presentDays = zeroIfNull(presentDays);
        absenceDays = zeroIfNull(absenceDays);
        leaveDays = zeroIfNull(leaveDays);
        mealDays = zeroIfNull(mealDays);
        mealAmount = zeroIfNull(mealAmount);
        travelUnits = zeroIfNull(travelUnits);
        travelAmount = zeroIfNull(travelAmount);
    }

    public static long hoursToMinutes(BigDecimal hours) {
        if (hours == null) return 0;
        return hours.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static BigDecimal minutesToHours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
