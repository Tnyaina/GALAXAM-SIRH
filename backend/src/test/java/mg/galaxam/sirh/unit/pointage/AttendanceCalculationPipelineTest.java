package mg.galaxam.sirh.unit.pointage;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import mg.galaxam.sirh.model.pointage.calculation.*;
import org.junit.jupiter.api.Test;

class AttendanceCalculationPipelineTest {
    private final DailyAttendanceCalculator daily = new DailyAttendanceCalculator();
    private final WeeklyAttendanceCalculator weekly = new WeeklyAttendanceCalculator();
    private final AttendancePeriodCalculator period = new AttendancePeriodCalculator();

    @Test
    void dayKeepsExactMinutesAndExplainsAmounts() {
        DailyAttendanceResult result = daily.calculateDay(day(LocalDate.of(2026, 9, 14),
                LocalTime.of(8, 13), LocalTime.of(17, 22), true));

        assertThat(result.workedMinutes()).isEqualTo(549);
        assertThat(result.lateMinutes()).isEqualTo(13);
        assertThat(result.mealAmount()).isEqualByComparingTo("2000");
        assertThat(result.travelAmount()).isEqualByComparingTo("1400");
        assertThat(result.anomalies()).isEmpty();
    }

    @Test
    void incompleteDayIsAnomaly() {
        DailyAttendanceContext context = day(LocalDate.of(2026, 9, 14),
                LocalTime.of(8, 0), null, false);
        assertThat(daily.calculateDay(context).status()).isEqualTo(DailyAttendanceResult.Status.ANOMALY);
    }

    @Test
    void weeklyThenPeriodCompositionDoesNotRecalculatePunches() {
        List<DailyAttendanceResult> days = List.of(
                daily.calculateDay(day(LocalDate.of(2026, 9, 14), LocalTime.of(8, 0), LocalTime.of(17, 0), false)),
                daily.calculateDay(day(LocalDate.of(2026, 9, 15), LocalTime.of(8, 0), LocalTime.of(19, 0), false)));
        WeeklyAttendanceResult week = weekly.calculateWeek(
                new WeeklyAttendanceContext(LocalDate.of(2026, 9, 14), 960, 480, true), days);
        AttendancePeriodResult result = period.calculateEmployeePeriod(
                new AttendancePeriodContext(1, 9, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20)),
                List.of(week));

        assertThat(week.workedMinutes()).isEqualTo(1200);
        assertThat(week.overtime30Minutes()).isEqualTo(240);
        assertThat(result.overtime30Minutes()).isEqualTo(240);
        assertThat(result.mealDays()).isEqualTo(2);
        assertThat(week.days()).containsExactlyElementsOf(days);
        assertThat(result.weeks()).containsExactly(week);
    }

    @Test
    void overtimeBoundaryIsQualifiedOnceInExactMinutes() {
        LocalDate monday = LocalDate.of(2026, 9, 14);
        WeeklyAttendanceContext rule = new WeeklyAttendanceContext(monday, 2_400, 480, true);

        assertThat(weekly.calculateWeek(rule, List.of(resultWithMinutes(monday, 2_399)))
                .grossOvertimeMinutes()).isZero();
        assertThat(weekly.calculateWeek(rule, List.of(resultWithMinutes(monday, 2_400)))
                .grossOvertimeMinutes()).isZero();
        assertThat(weekly.calculateWeek(rule, List.of(resultWithMinutes(monday, 2_401)))
                .overtime30Minutes()).isEqualTo(1);
    }

    @Test
    void weeklyCalculatorPreservesLegacyCompensationAndBands() {
        LocalDate monday = LocalDate.of(2026, 9, 14);
        WeeklyAttendanceResult exact = week(monday, 2_400, 60, true);
        assertThat(exact.grossOvertimeMinutes()).isZero();
        assertThat(Math.max(0, exact.lateMinutes() - exact.absenceMinutes())).isEqualTo(60);

        WeeklyAttendanceResult above = week(monday, 2_520, 60, true);
        assertThat(above.overtime30Minutes()).isEqualTo(120);
        assertThat(above.overtime50Minutes()).isZero();

        WeeklyAttendanceResult deficit = week(monday, 2_280, 120, true);
        assertThat(deficit.grossOvertimeMinutes()).isZero();
        assertThat(Math.min(deficit.lateMinutes(), deficit.absenceMinutes())).isEqualTo(120);

        WeeklyAttendanceResult partialDeficit = week(monday, 2_340, 120, true);
        assertThat(Math.max(0, partialDeficit.lateMinutes() - partialDeficit.absenceMinutes())).isEqualTo(60);
        assertThat(Math.min(partialDeficit.lateMinutes(), partialDeficit.absenceMinutes())).isEqualTo(60);

        WeeklyAttendanceResult executive = week(monday, 3_000, 60, false);
        assertThat(executive.grossOvertimeMinutes()).isEqualTo(600);
        assertThat(executive.overtime30Minutes()).isZero();

        WeeklyAttendanceResult bands = week(monday, 3_060, 0, true);
        assertThat(bands.overtime30Minutes()).isEqualTo(480);
        assertThat(bands.overtime50Minutes()).isEqualTo(180);
    }

    @Test
    void weeklyCompensationMatchesTheBusinessExamples() {
        LocalDate monday = LocalDate.of(2026, 9, 14);
        WeeklyAttendanceContext rule = new WeeklyAttendanceContext(monday, 2_400, 480, true, 480);

        // 12h30 manquantes et 13h au-delà de 8h/j : il reste 30 minutes d'heures supplémentaires.
        WeeklyAttendanceResult compensated = weekly.calculateWeek(rule, List.of(
                resultWithMinutes(monday, 480), resultWithMinutes(monday.plusDays(1), 480),
                resultWithMinutes(monday.plusDays(2), 210), resultWithMinutes(monday.plusDays(3), 0),
                resultWithMinutes(monday.plusDays(4), 1_260)));
        assertThat(compensated.dailySurplusMinutes()).isEqualTo(780);
        assertThat(compensated.compensatedMinutes()).isEqualTo(750);
        assertThat(compensated.overtime30Minutes()).isEqualTo(30);
        assertThat(compensated.absenceMinutes()).isZero();

        // 14h manquantes et 13h au-delà de 8h/j : une heure reste à retenir.
        WeeklyAttendanceResult deficit = weekly.calculateWeek(rule, List.of(
                resultWithMinutes(monday, 480), resultWithMinutes(monday.plusDays(1), 480),
                resultWithMinutes(monday.plusDays(2), 120), resultWithMinutes(monday.plusDays(3), 0),
                resultWithMinutes(monday.plusDays(4), 1_260)));
        assertThat(deficit.compensatedMinutes()).isEqualTo(780);
        assertThat(deficit.overtime30Minutes()).isZero();
        assertThat(deficit.absenceMinutes()).isEqualTo(60);
    }

    private WeeklyAttendanceResult week(LocalDate monday, long worked, long late, boolean eligible) {
        DailyAttendanceResult day = new DailyAttendanceResult(1, monday, DailyAttendanceResult.Status.PRESENT,
                worked, 0, worked, worked, late, 0, 0, false, false,
                0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        return weekly.calculateWeek(new WeeklyAttendanceContext(monday, 2_400, 480, eligible), List.of(day));
    }

    private DailyAttendanceResult resultWithMinutes(LocalDate date, long minutes) {
        return new DailyAttendanceResult(1, date, DailyAttendanceResult.Status.PRESENT,
                minutes, 0, minutes, minutes, 0, 0, 0, false, false,
                0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }

    @Test
    void automaticBreakUsesConfiguredSegmentsWithoutInventingPunches() {
        DailyAttendanceResult result = daily.calculateDay(discontinuous(
                LocalTime.of(8, 5), null, null, LocalTime.of(17, 10)));

        assertThat(result.workedMinutes()).isEqualTo(485);
        assertThat(result.lateMinutes()).isEqualTo(5);
        assertThat(result.anomalies()).isEmpty();
    }

    @Test
    void lateReturnUsesActualBreakWhenMorningEndedLate() {
        DailyAttendanceResult result = daily.calculateDay(discontinuous(
                LocalTime.of(8, 0), LocalTime.of(12, 20), LocalTime.of(13, 25), LocalTime.of(17, 0)));

        assertThat(result.lateMinutes()).isEqualTo(5);
    }

    @Test
    void nightAcrossMidnightStaysInExactMinutes() {
        DailyAttendanceResult result = daily.calculateDay(discontinuous(
                LocalTime.of(18, 0), LocalTime.of(21, 0), LocalTime.of(22, 0), LocalTime.of(2, 15)));

        assertThat(result.nightMinutes()).isEqualTo(255);
    }

    @Test
    void periodContextAcceptsNullPeriodId() {
        AttendancePeriodContext context = new AttendancePeriodContext(1, null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        assertThat(context.employeeId()).isEqualTo(1);
        assertThat(context.periodId()).isNull();
        assertThat(context.startsOn()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(context.endsOn()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void periodContextRejectsNullEmployeeOrInvertedDates() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                new AttendancePeriodContext(null, 1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                new AttendancePeriodContext(1, 1, null, LocalDate.of(2026, 9, 30)));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                new AttendancePeriodContext(1, 1, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)));
    }

    private DailyAttendanceContext discontinuous(LocalTime em, LocalTime sm, LocalTime ea, LocalTime sa) {
        return new DailyAttendanceContext(1, LocalDate.of(2026, 9, 14), em, sm, ea, sa,
                LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(13, 0), LocalTime.of(17, 0),
                LocalTime.of(22, 0), LocalTime.of(5, 0), false, false, false,
                DailyAttendanceContext.LeavePart.NONE, false, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false);
    }

    private DailyAttendanceContext day(LocalDate date, LocalTime entry, LocalTime exit, boolean deduction) {
        return new DailyAttendanceContext(1, date, entry, null, null, exit,
                LocalTime.of(8, 0), null, null, LocalTime.of(17, 0),
                LocalTime.of(22, 0), LocalTime.of(5, 0), true, false, false,
                DailyAttendanceContext.LeavePart.NONE, true, new BigDecimal("2000"),
                BigDecimal.ONE, new BigDecimal("2400"), new BigDecimal("1000"), deduction);
    }
}
