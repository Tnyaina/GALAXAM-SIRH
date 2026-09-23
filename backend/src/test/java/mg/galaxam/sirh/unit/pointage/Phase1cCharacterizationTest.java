package mg.galaxam.sirh.unit.pointage;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.pointage.domain.calculation.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Caractérisation Phase 1C :
 * 1. Objectif réel (40h, 32h avec férié chômé, 32h avec CP, tronçons 24h / 16h)
 * 2. Tranches HS 30% (premières 8h) / 50% (au-delà)
 * 3. Samedi ordinaire (compensation sans majoration spécifique)
 * 4. Étanchéité des tronçons inter-périodes
 */
class Phase1cCharacterizationTest {

    private final WeeklyAttendanceCalculator weekly = new WeeklyAttendanceCalculator();

    private DailyAttendanceResult createDay(LocalDate date, long workedMinutes, DailyAttendanceResult.Status status) {
        boolean isSunday = date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY;
        boolean isHoliday = status == DailyAttendanceResult.Status.PUBLIC_HOLIDAY_OFF
                || status == DailyAttendanceResult.Status.PUBLIC_HOLIDAY_WORKED;
        return new DailyAttendanceResult(
                1, date, status, workedMinutes, 0, workedMinutes, workedMinutes,
                0, 0, 0, isSunday, isHoliday,
                0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()
        );
    }

    @Test
    @DisplayName("23.1. 40h objectif / 40h travail -> 0 HS, 0 déficit")
    void test40hObjectif40hTravail() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        List<DailyAttendanceResult> days = List.of(
                createDay(monday, 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(1), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(2), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(3), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(4), 480, DailyAttendanceResult.Status.PRESENT)
        );
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480);
        WeeklyAttendanceResult res = weekly.calculateWeek(ctx, days);

        assertThat(res.workedMinutes()).isEqualTo(2400);
        assertThat(res.overtime30Minutes()).isZero();
        assertThat(res.overtime50Minutes()).isZero();
        assertThat(res.absenceMinutes()).isZero();
    }

    @Test
    @DisplayName("23.2. 40h objectif / 41h travail -> 1h HS (60 min en HS30)")
    void test40hObjectif41hTravail() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        List<DailyAttendanceResult> days = List.of(
                createDay(monday, 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(1), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(2), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(3), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(4), 540, DailyAttendanceResult.Status.PRESENT) // 9h
        );
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480);
        WeeklyAttendanceResult res = weekly.calculateWeek(ctx, days);

        assertThat(res.workedMinutes()).isEqualTo(2460);
        assertThat(res.overtime30Minutes()).isEqualTo(60);
        assertThat(res.overtime50Minutes()).isZero();
        assertThat(res.absenceMinutes()).isZero();
    }

    @Test
    @DisplayName("23.3. 40h objectif / 48h travail -> 8h tranche 1 (480 min HS30, 0 min HS50)")
    void test40hObjectif48hTravail() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        // 5 jours de 9h36 = 576 min -> 5 * 576 = 2880 min (48h)
        List<DailyAttendanceResult> days = List.of(
                createDay(monday, 576, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(1), 576, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(2), 576, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(3), 576, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(4), 576, DailyAttendanceResult.Status.PRESENT)
        );
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480);
        WeeklyAttendanceResult res = weekly.calculateWeek(ctx, days);

        assertThat(res.workedMinutes()).isEqualTo(2880);
        assertThat(res.overtime30Minutes()).isEqualTo(480); // 8h
        assertThat(res.overtime50Minutes()).isZero();
    }

    @Test
    @DisplayName("23.4. 40h objectif / 49h travail -> 8h tranche 1 (480 min HS30) + 1h tranche 2 (60 min HS50)")
    void test40hObjectif49hTravail() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        // 49h = 2940 min
        List<DailyAttendanceResult> days = List.of(
                createDay(monday, 600, DailyAttendanceResult.Status.PRESENT), // 10h
                createDay(monday.plusDays(1), 600, DailyAttendanceResult.Status.PRESENT), // 10h
                createDay(monday.plusDays(2), 600, DailyAttendanceResult.Status.PRESENT), // 10h
                createDay(monday.plusDays(3), 600, DailyAttendanceResult.Status.PRESENT), // 10h
                createDay(monday.plusDays(4), 540, DailyAttendanceResult.Status.PRESENT)  // 9h
        );
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480);
        WeeklyAttendanceResult res = weekly.calculateWeek(ctx, days);

        assertThat(res.workedMinutes()).isEqualTo(2940);
        assertThat(res.overtime30Minutes()).isEqualTo(480); // 8h
        assertThat(res.overtime50Minutes()).isEqualTo(60);  // 1h
    }

    @Test
    @DisplayName("23.5. 32h objectif férié chômé / 34h travail -> 2h HS ordinaires")
    void test32hObjectifFerieChome34hTravail() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        // Lun 8h, Mar 8h, Mer FERIE_CHOME (0h), Jeu 8h, Ven 10h -> Total 34h
        List<DailyAttendanceResult> days = List.of(
                createDay(monday, 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(1), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(2), 0, DailyAttendanceResult.Status.PUBLIC_HOLIDAY_OFF),
                createDay(monday.plusDays(3), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(4), 600, DailyAttendanceResult.Status.PRESENT)
        );
        // Objectif réel = 4 jours ouvrables exigibles = 32h = 1920 min
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 1920, 480, true, 480);
        WeeklyAttendanceResult res = weekly.calculateWeek(ctx, days);

        assertThat(res.workedMinutes()).isEqualTo(2040); // 34h
        assertThat(res.overtime30Minutes()).isEqualTo(120); // 2h HS
        assertThat(res.overtime50Minutes()).isZero();
        assertThat(res.absenceMinutes()).isZero();
    }

    @Test
    @DisplayName("23.6. 32h objectif CP / 34h travail -> 2h HS ordinaires")
    void test32hObjectifCP34hTravail() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        // Lun CP (0h), Mar 8h, Mer 8h, Jeu 8h, Ven 10h -> Total 34h
        List<DailyAttendanceResult> days = List.of(
                createDay(monday, 0, DailyAttendanceResult.Status.LEAVE),
                createDay(monday.plusDays(1), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(2), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(3), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(4), 600, DailyAttendanceResult.Status.PRESENT)
        );
        // Objectif réel = 32h = 1920 min
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 1920, 480, true, 480);
        WeeklyAttendanceResult res = weekly.calculateWeek(ctx, days);

        assertThat(res.workedMinutes()).isEqualTo(2040); // 34h
        assertThat(res.overtime30Minutes()).isEqualTo(120); // 2h HS
        assertThat(res.overtime50Minutes()).isZero();
        assertThat(res.absenceMinutes()).isZero();
    }

    @Test
    @DisplayName("23.7 & 26. Tronçon 24h / 25h -> 1h HS; Tronçon suivant 16h / 15h -> 1h déficit (Zéro compensation croisée)")
    void testTronconsIndependantsFrontiere2526() {
        // Tronçon Période M : Lun-Mer (3 jours = 24h = 1440 min), travaillé 25h = 1500 min
        LocalDate monday = LocalDate.of(2026, 9, 21);
        List<DailyAttendanceResult> daysM = List.of(
                createDay(monday, 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(1), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(2), 540, DailyAttendanceResult.Status.PRESENT) // Mer 9h
        );
        WeeklyAttendanceContext ctxM = new WeeklyAttendanceContext(monday, 1440, 480, true, 480, WeeklyPriorState.NONE);
        WeeklyAttendanceResult resM = weekly.calculateWeek(ctxM, daysM);

        assertThat(resM.workedMinutes()).isEqualTo(1500);
        assertThat(resM.overtime30Minutes()).isEqualTo(60); // 1h HS
        assertThat(resM.absenceMinutes()).isZero();

        // Tronçon Période M+1 : Jeu-Ven (2 jours = 16h = 960 min), travaillé 15h = 900 min
        // Selon l'arbitrage GALAXAM (Phase 1B), les périodes sont étanches : WeeklyPriorState.NONE
        List<DailyAttendanceResult> daysM1 = List.of(
                createDay(monday.plusDays(3), 480, DailyAttendanceResult.Status.PRESENT), // Jeu 8h
                createDay(monday.plusDays(4), 420, DailyAttendanceResult.Status.PRESENT)  // Ven 7h (1h déficit)
        );
        WeeklyAttendanceContext ctxM1 = new WeeklyAttendanceContext(monday.plusDays(3), 960, 480, true, 480, WeeklyPriorState.NONE);
        WeeklyAttendanceResult resM1 = weekly.calculateWeek(ctxM1, daysM1);

        assertThat(resM1.workedMinutes()).isEqualTo(900);
        assertThat(resM1.overtime30Minutes()).isZero();
        assertThat(resM1.absenceMinutes()).isEqualTo(60); // 1h déficit net
    }

    @Test
    @DisplayName("24.1. Samedi seul : 39h Lun-Ven + 1h Sam -> 40h -> 0 HS, 0 déficit (compensation ordinaire)")
    void testSamediCompenseDeficitSemaine() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        // Lun 7h (deficit 1h), Mar 8h, Mer 8h, Jeu 8h, Ven 8h, Sam 1h -> Total 40h
        List<DailyAttendanceResult> days = List.of(
                createDay(monday, 420, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(1), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(2), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(3), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(4), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(5), 60, DailyAttendanceResult.Status.PRESENT) // Samedi 1h
        );
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480);
        WeeklyAttendanceResult res = weekly.calculateWeek(ctx, days);

        assertThat(res.workedMinutes()).isEqualTo(2400);
        assertThat(res.overtime30Minutes()).isZero();
        assertThat(res.overtime50Minutes()).isZero();
        assertThat(res.absenceMinutes()).isZero();
    }

    @Test
    @DisplayName("24.2. Samedi : 40h Lun-Ven + 1h Sam -> 1h HS ordinaire (HS30)")
    void testSamediAuDela40hDevientHS() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        // Lun-Ven 40h + Samedi 1h -> Total 41h
        List<DailyAttendanceResult> days = List.of(
                createDay(monday, 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(1), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(2), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(3), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(4), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(5), 60, DailyAttendanceResult.Status.PRESENT) // Samedi 1h
        );
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480);
        WeeklyAttendanceResult res = weekly.calculateWeek(ctx, days);

        assertThat(res.workedMinutes()).isEqualTo(2460);
        assertThat(res.overtime30Minutes()).isEqualTo(60); // 1h HS30
        assertThat(res.overtime50Minutes()).isZero();
        assertThat(res.absenceMinutes()).isZero();
    }

    @Test
    @DisplayName("24.3. Samedi : 38h Lun-Ven + 4h Sam -> 42h -> compensation 2h déficit + 2h HS ordinaires")
    void testSamediCompenseEtExcede() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        // Lun 6h (deficit 2h), Mar 8h, Mer 8h, Jeu 8h, Ven 8h, Sam 4h (240 min)
        List<DailyAttendanceResult> days = List.of(
                createDay(monday, 360, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(1), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(2), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(3), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(4), 480, DailyAttendanceResult.Status.PRESENT),
                createDay(monday.plusDays(5), 240, DailyAttendanceResult.Status.PRESENT) // Samedi 4h
        );
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480);
        WeeklyAttendanceResult res = weekly.calculateWeek(ctx, days);

        assertThat(res.workedMinutes()).isEqualTo(2520); // 42h
        assertThat(res.overtime30Minutes()).isEqualTo(120); // 2h HS30 nettes après compensation
        assertThat(res.overtime50Minutes()).isZero();
        assertThat(res.absenceMinutes()).isZero();
    }
}
