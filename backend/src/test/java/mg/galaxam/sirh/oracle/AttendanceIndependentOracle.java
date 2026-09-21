package mg.galaxam.sirh.oracle;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ORACLE DE TEST POINTAGE — 100% INDÉPENDANT DU MOTEUR DE PRODUCTION.
 * 
 * N'importe AUCUN calculator, service ou engine de production.
 * Calcule de manière déterministe les minutes travaillées, absences,
 * retards, heures supplémentaires (tranche 30% / 50%) et majorations
 * directement à partir des événements bruts.
 */
public class AttendanceIndependentOracle {

    public record DailyShift(
            LocalDate date,
            LocalTime schedMorningIn,
            LocalTime schedMorningOut,
            LocalTime schedAfternoonIn,
            LocalTime schedAfternoonOut,
            LocalTime actualMorningIn,
            LocalTime actualMorningOut,
            LocalTime actualAfternoonIn,
            LocalTime actualAfternoonOut,
            boolean continuous,
            boolean isHoliday,
            boolean isPaidLeave,
            boolean isOffDay
    ) {}

    public record AttendanceExpectedResult(
            long expectedWorkMinutes,
            long actualWorkedMinutes,
            long absenceMinutes,
            long lateMinutes,
            long overtimeMinutes,
            long hs30Minutes,
            long hs50Minutes,
            long nightMinutes,
            long sundayMinutes,
            long holidayMinutes,
            int paidLeaveDays
    ) {}

    public static AttendanceExpectedResult calculatePeriod(
            List<DailyShift> shifts,
            long weeklyThresholdMinutes,
            long weeklyTier1LimitMinutes
    ) {
        long totalExpectedWorkMinutes = 0;
        long totalActualWorkedMinutes = 0;
        long totalAbsenceMinutes = 0;
        long totalLateMinutes = 0;
        long totalNightMinutes = 0;
        long totalSundayMinutes = 0;
        long totalHolidayMinutes = 0;
        int paidLeaveDays = 0;

        for (DailyShift s : shifts) {
            if (s.isPaidLeave()) {
                paidLeaveDays++;
                continue;
            }

            long schedM = 0;
            if (s.continuous()) {
                schedM = minutesBetween(s.schedMorningIn(), s.schedAfternoonOut());
            } else {
                schedM = minutesBetween(s.schedMorningIn(), s.schedMorningOut()) +
                         minutesBetween(s.schedAfternoonIn(), s.schedAfternoonOut());
            }

            if (!s.isOffDay() && !s.isHoliday()) {
                totalExpectedWorkMinutes += schedM;
            }

            long workedM = 0;
            if (s.continuous()) {
                workedM = minutesBetween(s.actualMorningIn(), s.actualAfternoonOut());
            } else {
                LocalTime outM = s.actualMorningOut();
                LocalTime inA = s.actualAfternoonIn();
                if (outM == null && inA == null && s.actualMorningIn() != null && s.actualAfternoonOut() != null) {
                    outM = s.schedMorningOut();
                    inA = s.schedAfternoonIn();
                }
                workedM = minutesBetween(s.actualMorningIn(), outM) +
                          minutesBetween(inA, s.actualAfternoonOut());
            }

            totalActualWorkedMinutes += workedM;

            if (s.isHoliday()) {
                totalHolidayMinutes += workedM;
            }
            if (s.date().getDayOfWeek() == DayOfWeek.SUNDAY) {
                totalSundayMinutes += workedM;
            }

            if (!s.isOffDay() && !s.isHoliday()) {
                if (workedM < schedM) {
                    totalAbsenceMinutes += (schedM - workedM);
                }
                // Retard
                if (s.schedMorningIn() != null && s.actualMorningIn() != null && s.actualMorningIn().isAfter(s.schedMorningIn())) {
                    totalLateMinutes += minutesBetween(s.schedMorningIn(), s.actualMorningIn());
                }
                if (!s.continuous() && s.schedAfternoonIn() != null && s.actualAfternoonIn() != null && s.actualAfternoonIn().isAfter(s.schedAfternoonIn())) {
                    totalLateMinutes += minutesBetween(s.schedAfternoonIn(), s.actualAfternoonIn());
                }
            }

            // Nuit
            if (s.continuous()) {
                totalNightMinutes += calculateNightMinutes(s.actualMorningIn(), s.actualAfternoonOut());
            } else {
                totalNightMinutes += calculateNightMinutes(s.actualMorningIn(), s.actualMorningOut()) +
                                     calculateNightMinutes(s.actualAfternoonIn(), s.actualAfternoonOut());
            }
        }

        long totalOvertimeMinutes = Math.max(0, totalActualWorkedMinutes - totalExpectedWorkMinutes);
        long hs30Min = Math.min(totalOvertimeMinutes, weeklyTier1LimitMinutes);
        long hs50Min = Math.max(0, totalOvertimeMinutes - hs30Min);

        return new AttendanceExpectedResult(
                totalExpectedWorkMinutes,
                totalActualWorkedMinutes,
                totalAbsenceMinutes,
                totalLateMinutes,
                totalOvertimeMinutes,
                hs30Min,
                hs50Min,
                totalNightMinutes,
                totalSundayMinutes,
                totalHolidayMinutes,
                paidLeaveDays
        );
    }

    private static long minutesBetween(LocalTime start, LocalTime end) {
        if (start == null || end == null) return 0;
        long startM = start.getHour() * 60L + start.getMinute();
        long endM = end.getHour() * 60L + end.getMinute();
        if (endM < startM) {
            // Traverse minuit
            endM += 24 * 60;
        }
        return Math.max(0, endM - startM);
    }

    private static long calculateNightMinutes(LocalTime in, LocalTime out) {
        if (in == null || out == null) return 0;
        // Plage de nuit légale : 22h00 (1320 min) à 05h00 (300 min)
        long inMin = in.getHour() * 60L + in.getMinute();
        long outMin = out.getHour() * 60L + out.getMinute();
        if (outMin < inMin) outMin += 24 * 60;

        long nightStart = 22 * 60;     // 1320
        long nightEnd = 24 * 60 + 5 * 60; // 1740 (05h00 le lendemain)

        long overlapStart = Math.max(inMin, nightStart);
        long overlapEnd = Math.min(outMin, nightEnd);

        if (overlapEnd > overlapStart) {
            return overlapEnd - overlapStart;
        }

        // Également plage 00h00 à 05h00 le matin même si la vacation commence avant 05h00
        if (inMin < 5 * 60) {
            long morningOverlap = Math.min(outMin, 5 * 60) - inMin;
            if (morningOverlap > 0) return morningOverlap;
        }

        return 0;
    }
}
