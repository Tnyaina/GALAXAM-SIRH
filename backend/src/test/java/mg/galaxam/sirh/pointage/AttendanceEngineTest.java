package mg.galaxam.sirh.pointage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import mg.galaxam.sirh.pointage.domain.calculation.AttendanceEngine;
import org.junit.jupiter.api.Test;

class AttendanceEngineTest {

    private static final long STANDARD_DAY_MINUTES = 8 * 60;
    private static final long OVERTIME_BAND_MINUTES = 8 * 60;
    private final AttendanceEngine calculator = new AttendanceEngine();

    @Test
    void surplusUnJourCompenseDeficitUnAutreJour() {
        var result = calculate(5 * STANDARD_DAY_MINUTES, days(600, 360, 480, 480, 480));
        assertEquals(120, result.dailySurplusMinutes());
        assertEquals(120, result.compensatedMinutes());
        assertEquals(0, result.grossOvertimeMinutes());
        assertEquals(0, result.overtime30Minutes());
        assertEquals(0, result.overtime50Minutes());
    }

    @Test
    void surplusAuDessusDeLObjectifDevientHs30() {
        var result = calculate(5 * STANDARD_DAY_MINUTES, days(600, 480, 480, 480, 480));
        assertEquals(120, result.grossOvertimeMinutes());
        assertEquals(120, result.overtime30Minutes());
        assertEquals(0, result.overtime50Minutes());
    }

    @Test
    void jourFerieChomeAbaisseLobjectifFourniParLeCalendrier() {
        var result = calculate(4 * STANDARD_DAY_MINUTES,
                List.of(day(480, AttendanceEngine.Result.Status.PRESENT, false, false),
                        day(480, AttendanceEngine.Result.Status.PRESENT, false, false),
                        day(0, AttendanceEngine.Result.Status.PUBLIC_HOLIDAY_OFF, false, true),
                        day(480, AttendanceEngine.Result.Status.PRESENT, false, false),
                        day(480, AttendanceEngine.Result.Status.PRESENT, false, false)));
        assertEquals(0, result.absenceMinutes());
        assertEquals(0, result.grossOvertimeMinutes());
        assertEquals(4 * STANDARD_DAY_MINUTES, result.expectedMinutes());
    }

    @Test
    void premierPalierHsEstProratePourUnTronconLundiMardi() {
        long configuredBand = 8 * 60;
        long expected = 2 * STANDARD_DAY_MINUTES;
        long band = AttendanceEngine.prorateFirstOvertimeBand(configuredBand, expected,
                5 * STANDARD_DAY_MINUTES);
        var result = calculator.calculateWeek(new AttendanceEngine.WeekContext(
                        LocalDate.of(2026, 9, 7), expected, band, true, STANDARD_DAY_MINUTES),
                days(600, 600));

        assertEquals(192, band); // 8 h × 16/40
        assertEquals(240, result.grossOvertimeMinutes());
        assertEquals(192, result.overtime30Minutes());
        assertEquals(48, result.overtime50Minutes());
    }

    @Test
    void premierPalierHsEstProratePourUnTronconDeTroisJours() {
        assertEquals(288, AttendanceEngine.prorateFirstOvertimeBand(8 * 60,
                3 * STANDARD_DAY_MINUTES, 5 * STANDARD_DAY_MINUTES)); // 8 h × 24/40
    }

    @Test
    void premierPalierHsEstProrateQuandUnJourDuTronconEstFerie() {
        long expected = STANDARD_DAY_MINUTES;
        long band = AttendanceEngine.prorateFirstOvertimeBand(8 * 60, expected, 5 * STANDARD_DAY_MINUTES);
        var result = calculator.calculateWeek(new AttendanceEngine.WeekContext(
                        LocalDate.of(2026, 9, 7), expected, band, true, STANDARD_DAY_MINUTES),
                List.of(day(600, AttendanceEngine.Result.Status.PRESENT, false, false),
                        day(0, AttendanceEngine.Result.Status.PUBLIC_HOLIDAY_OFF, false, true)));
        assertEquals(96, band);
        assertEquals(120, result.grossOvertimeMinutes());
        assertEquals(96, result.overtime30Minutes());
        assertEquals(24, result.overtime50Minutes());
    }

    @Test
    void premierPalierHsEstProrateQuandUnJourDuTronconEstConge() {
        long expected = STANDARD_DAY_MINUTES;
        long band = AttendanceEngine.prorateFirstOvertimeBand(8 * 60, expected, 5 * STANDARD_DAY_MINUTES);
        var result = calculator.calculateWeek(new AttendanceEngine.WeekContext(
                        LocalDate.of(2026, 9, 7), expected, band, true, STANDARD_DAY_MINUTES),
                List.of(day(600, AttendanceEngine.Result.Status.PRESENT, false, false),
                        day(0, AttendanceEngine.Result.Status.LEAVE, false, false)));
        assertEquals(96, band);
        assertEquals(96, result.overtime30Minutes());
        assertEquals(24, result.overtime50Minutes());
    }

    @Test
    void absenceNeReduitPasLObjectifNiLePremierPalierEtCompenseLeSurplus() {
        long expected = 2 * STANDARD_DAY_MINUTES;
        long band = AttendanceEngine.prorateFirstOvertimeBand(8 * 60, expected, 5 * STANDARD_DAY_MINUTES);
        var result = calculator.calculateWeek(new AttendanceEngine.WeekContext(
                        LocalDate.of(2026, 9, 7), expected, band, true, STANDARD_DAY_MINUTES),
                List.of(day(600, AttendanceEngine.Result.Status.PRESENT, false, false),
                        day(0, AttendanceEngine.Result.Status.ABSENT, false, false)));
        assertEquals(360, result.absenceMinutes());
        assertEquals(120, result.compensatedMinutes());
        assertEquals(0, result.grossOvertimeMinutes());
    }

    @Test
    void deplacementConserveLeProrataEtLaReductionConfigures() {
        assertEquals(0, new BigDecimal("25000.00").compareTo(calculator.calculateTravelAmount(
                BigDecimal.ONE, new BigDecimal("25000"), false, new BigDecimal("5000"))));
        assertEquals(0, new BigDecimal("10000.00").compareTo(calculator.calculateTravelAmount(
                new BigDecimal("0.5"), new BigDecimal("25000"), true, new BigDecimal("5000"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(calculator.calculateTravelAmount(
                BigDecimal.ONE, new BigDecimal("25000"), true, new BigDecimal("30000"))));
    }

    @Test
    void congeCompletAbaisseLobjectifSansCreerDeficit() {
        var result = calculate(4 * STANDARD_DAY_MINUTES,
                List.of(day(480, AttendanceEngine.Result.Status.PRESENT, false, false),
                        day(0, AttendanceEngine.Result.Status.LEAVE, false, false),
                        day(480, AttendanceEngine.Result.Status.PRESENT, false, false),
                        day(480, AttendanceEngine.Result.Status.PRESENT, false, false),
                        day(480, AttendanceEngine.Result.Status.PRESENT, false, false)));
        assertEquals(0, result.absenceMinutes());
        assertEquals(0, result.grossOvertimeMinutes());
    }

    @Test
    void compensationNeTraversePasUneFrontiereDePeriode() {
        LocalDate start = LocalDate.of(2026, 9, 7); // lundi
        assertEquals(1, AttendanceEngine.segmentWeeks(start, start).size());
        assertEquals(1, AttendanceEngine.segmentWeeks(start.plusDays(1), start.plusDays(1)).size());

        var first = calculator.calculateWeek(context(start, STANDARD_DAY_MINUTES),
                List.of(day(600, AttendanceEngine.Result.Status.PRESENT, false, false)));
        var second = calculator.calculateWeek(context(start, STANDARD_DAY_MINUTES),
                List.of(day(360, AttendanceEngine.Result.Status.PRESENT, false, false)));
        assertEquals(120, first.grossOvertimeMinutes());
        assertEquals(120, second.absenceMinutes());
        assertEquals(0, second.compensatedMinutes());
    }

    @Test
    void periodeCoupeeProduitDesTronconsSemaineAncrésAuxLimitesDePeriode() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        var startCut = AttendanceEngine.segmentWeeks(monday.plusDays(2), monday.plusDays(11));
        assertEquals(2, startCut.size());
        assertEquals(monday.plusDays(2), startCut.get(0).start());
        assertEquals(monday.plusDays(6), startCut.get(0).end());
        assertEquals(monday.plusDays(7), startCut.get(1).start());
        assertEquals(monday.plusDays(11), startCut.get(1).end());
    }

    @Test
    void deficitSuperieurAuSurplusNeProduitAucuneHs() {
        var result = calculate(5 * STANDARD_DAY_MINUTES, days(600, 240, 480, 480, 480));
        assertEquals(120, result.dailySurplusMinutes());
        assertEquals(120, result.compensatedMinutes());
        assertEquals(120, result.absenceMinutes());
        assertEquals(0, result.grossOvertimeMinutes());
    }

    @Test
    void surplusEstRepartiSelonLaTrancheHsConfiguree() {
        var result = calculator.calculateWeek(new AttendanceEngine.WeekContext(
                        LocalDate.of(2026, 9, 7), 5 * STANDARD_DAY_MINUTES, 60, true, STANDARD_DAY_MINUTES),
                days(600, 600, 480, 480, 480));
        assertEquals(240, result.grossOvertimeMinutes());
        assertEquals(60, result.overtime30Minutes());
        assertEquals(180, result.overtime50Minutes());
    }

    @Test
    void semainesCadresRestentNonEligiblesAuxHs() {
        var result = calculator.calculateWeek(new AttendanceEngine.WeekContext(
                        LocalDate.of(2026, 9, 7), 5 * STANDARD_DAY_MINUTES,
                        OVERTIME_BAND_MINUTES, false, STANDARD_DAY_MINUTES),
                days(600, 480, 480, 480, 480));
        assertEquals(120, result.dailySurplusMinutes());
        assertEquals(0, result.overtime30Minutes());
        assertEquals(0, result.overtime50Minutes());
    }

    @Test
    void ineligibiliteHsNeSupprimePasLaCompensationDuDeficit() {
        var result = calculator.calculateWeek(new AttendanceEngine.WeekContext(
                        LocalDate.of(2026, 9, 7), 5 * STANDARD_DAY_MINUTES,
                        OVERTIME_BAND_MINUTES, false, STANDARD_DAY_MINUTES),
                days(600, 360, 480, 480, 480));
        assertEquals(120, result.compensatedMinutes());
        assertEquals(0, result.grossOvertimeMinutes());
        assertEquals(0, result.overtime30Minutes());
        assertEquals(0, result.overtime50Minutes());
    }

    @Test
    void eligibilityHistoriqueEstUnParametreDuSegmentEtNonUneRegleCadreDuMoteur() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        var days = days(600, 480, 480, 480, 480);
        var eligiblePeriod = calculator.calculateWeek(
                new AttendanceEngine.WeekContext(monday, 5 * STANDARD_DAY_MINUTES,
                        OVERTIME_BAND_MINUTES, true, STANDARD_DAY_MINUTES), days);
        var ineligiblePeriod = calculator.calculateWeek(
                new AttendanceEngine.WeekContext(monday, 5 * STANDARD_DAY_MINUTES,
                        OVERTIME_BAND_MINUTES, false, STANDARD_DAY_MINUTES), days);
        assertEquals(120, eligiblePeriod.overtime30Minutes());
        assertEquals(0, ineligiblePeriod.overtime30Minutes());
    }

    private AttendanceEngine.WeekResult calculate(long expectedMinutes,
            List<AttendanceEngine.Result> days) {
        return calculator.calculateWeek(context(LocalDate.of(2026, 9, 7), expectedMinutes), days);
    }

    private AttendanceEngine.WeekContext context(LocalDate monday, long expectedMinutes) {
        return new AttendanceEngine.WeekContext(monday, expectedMinutes, OVERTIME_BAND_MINUTES,
                true, STANDARD_DAY_MINUTES);
    }

    private List<AttendanceEngine.Result> days(long... workedMinutes) {
        return java.util.stream.IntStream.range(0, workedMinutes.length)
                .mapToObj(index -> day(workedMinutes[index], AttendanceEngine.Result.Status.PRESENT,
                        false, false))
                .toList();
    }

    private AttendanceEngine.Result day(long workedMinutes, AttendanceEngine.Result.Status status,
            boolean sunday, boolean publicHoliday) {
        return new AttendanceEngine.Result(1, LocalDate.of(2026, 9, 7), status,
                workedMinutes, 0, workedMinutes, workedMinutes, 0, 0, 0,
                sunday, publicHoliday, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }
}
