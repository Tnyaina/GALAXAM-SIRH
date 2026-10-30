package mg.galaxam.sirh.pointage.domain.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import mg.galaxam.sirh.paie.domain.engine.OvertimePayCalculator;
import mg.galaxam.sirh.paie.domain.engine.SpecialPremiumCollisionPolicy;
import mg.galaxam.sirh.pointage.domain.calculation.AttendanceEngine.Context;
import mg.galaxam.sirh.pointage.domain.calculation.AttendanceEngine.Result;
import mg.galaxam.sirh.pointage.domain.calculation.AttendanceEngine.WeekContext;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.DaySpecialType;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.NightWorkType;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.OvertimeTier;
import org.junit.jupiter.api.Test;

class AttendanceOvertimeWorkflowTest {
    private static final BigDecimal RATE = new BigDecimal("1000");
    private static final BigDecimal FIRST_BAND = new BigDecimal("240");
    private static final OvertimePayCalculator.Rates RATES = new OvertimePayCalculator.Rates(
            new BigDecimal("0.30"), new BigDecimal("0.50"), BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, SpecialPremiumCollisionPolicy.CUMUL_ADDITIF);

    @Test
    void minutesReellesDePointageProduisentDuDeficitOuDesHsSansForcerHuitHeures() {
        AttendanceEngine attendance = new AttendanceEngine();
        LocalDate monday = LocalDate.of(2026, 8, 3);

        var shortDays = java.util.stream.IntStream.range(0, 5)
                .mapToObj(i -> attendance.calculateDay(workday(monday.plusDays(i), LocalTime.of(16, 59))))
                .toList();
        var shortWeek = attendance.calculateWeek(new WeekContext(monday, 5 * 480L, 240, true, 480), shortDays);
        assertEquals(5 * 479L, shortWeek.workedMinutes());
        assertEquals(5, shortWeek.absenceMinutes());
        assertEquals(0, shortWeek.totalWeekOvertimeMinutes());

        var longDays = java.util.stream.IntStream.range(0, 5)
                .mapToObj(i -> attendance.calculateDay(workday(monday.plusDays(i), LocalTime.of(18, 0))))
                .toList();
        var longWeek = attendance.calculateWeek(new WeekContext(monday, 5 * 480L, 240, true, 480), longDays);
        assertEquals(5 * 540L, longWeek.workedMinutes());
        assertEquals(0, longWeek.absenceMinutes());
        assertEquals(240, longWeek.overtime30Minutes());
        assertEquals(60, longWeek.overtime50Minutes());

        var ineligible = attendance.calculateWeek(new WeekContext(monday, 5 * 480L, 240, false, 480), longDays);
        assertEquals(300, ineligible.grossOvertimeMinutes());
        assertEquals(0, ineligible.totalWeekOvertimeMinutes());
    }

    @Test
    void leSeuilHsEstProrataSurUneSemaineEcourteeEtLaCompensationMangeLeSurplus() {
        assertEquals(192, AttendanceEngine.prorateFirstOvertimeBand(240, 4 * 480, 5 * 480));
        assertEquals(240, AttendanceEngine.prorateFirstOvertimeBand(240, 5 * 480, 5 * 480));

        var balanced = AttendanceEngine.PeriodBalance.calculate(180, 60, 120, 0);
        assertEquals(120, balanced.balanceMinutes());
        assertEquals(60, balanced.overtime30Minutes());
        assertEquals(60, balanced.overtime50Minutes());

        var invalid = assertThrows(IllegalArgumentException.class,
                () -> AttendanceEngine.PeriodBalance.calculate(0, 0, -1, 0));
        assertEquals("Volumes de rapprochement invalides.", invalid.getMessage());
    }

    @Test
    void projectionDesPaliersDansLesBucketsEtMontantDePaieConserventLesMinutesEtLaBase() {
        var start = LocalDate.of(2026, 8, 8).atTime(17, 0);
        var timeline = new TemporalAttendanceEngine.TemporalBucket(start, start.plusMinutes(300), 300,
                DaySpecialType.STANDARD, NightWorkType.NONE, 8, 42);
        var qualified = new TemporalAttendanceEngine.OvertimeProjectionEngine()
                .project(List.of(timeline), 0, 240, 60);

        assertEquals(List.of(OvertimeTier.OVERTIME_30, OvertimeTier.OVERTIME_50),
                qualified.stream().map(QualifiedTimeBucket::overtimeTier).toList());
        assertEquals(240, qualified.get(0).minutes());
        assertEquals(60, qualified.get(1).minutes());

        var pay = new OvertimePayCalculator().calculate(qualified, RATE, ignored -> RATES, 42);
        assertMoney("5200.00", pay.hs30());
        assertMoney("1500.00", pay.hs50());
        assertMoney("6700.00", pay.total());
        assertThrows(IllegalStateException.class,
                () -> new TemporalAttendanceEngine.OvertimeProjectionEngine().project(List.of(timeline), 0, 239, 60));
    }

    private static Context workday(LocalDate date, LocalTime afternoonExit) {
        return new Context(42, date, LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(13, 0), afternoonExit,
                LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(13, 0), LocalTime.of(17, 0),
                null, null, false, false, false, Context.LeavePart.NONE, false,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "Montant inattendu");
    }
}
