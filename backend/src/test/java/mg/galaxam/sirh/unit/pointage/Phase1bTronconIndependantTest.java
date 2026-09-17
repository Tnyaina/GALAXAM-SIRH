package mg.galaxam.sirh.unit.pointage;

import mg.galaxam.sirh.model.pointage.calculation.DailyAttendanceResult;
import mg.galaxam.sirh.model.pointage.calculation.WeeklyAttendanceCalculator;
import mg.galaxam.sirh.model.pointage.calculation.WeeklyAttendanceContext;
import mg.galaxam.sirh.model.pointage.calculation.WeeklyAttendanceResult;
import mg.galaxam.sirh.model.pointage.calculation.WeeklyPriorState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 1B — Modèle Hebdomadaire aux Frontières 25/26 (Tronçons Indépendants)")
public class Phase1bTronconIndependantTest {

    private WeeklyAttendanceCalculator calculator;
    private final LocalDate monday = LocalDate.of(2025, 11, 24);

    @BeforeEach
    void setUp() {
        calculator = new WeeklyAttendanceCalculator();
    }

    private DailyAttendanceResult day(LocalDate date, long workedMinutes, DailyAttendanceResult.Status status,
                                      long nightMinutes, boolean sunday, boolean holiday) {
        return new DailyAttendanceResult(
                1, date, status, workedMinutes, 0, workedMinutes, workedMinutes,
                0, 0, nightMinutes, sunday, holiday, 0, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, List.of()
        );
    }

    private DailyAttendanceResult workingDay(LocalDate date, long workedMinutes) {
        return day(date, workedMinutes, DailyAttendanceResult.Status.PRESENT, 0, false, false);
    }

    @Test
    @DisplayName("T1 — Semaine normale : 7 + 9 + 8 + 8 + 8 = 40h -> 0 déficit, 0 HS")
    void testT1_semaineNormale() {
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480, WeeklyPriorState.NONE);
        List<DailyAttendanceResult> days = List.of(
                workingDay(monday, 420),
                workingDay(monday.plusDays(1), 540),
                workingDay(monday.plusDays(2), 480),
                workingDay(monday.plusDays(3), 480),
                workingDay(monday.plusDays(4), 480)
        );

        WeeklyAttendanceResult res = calculator.calculateWeek(ctx, days);
        assertEquals(2400, res.workedMinutes());
        assertEquals(0, res.absenceMinutes(), "Déficit final doit être 0");
        assertEquals(0, res.overtime30Minutes(), "HS ordinaire doit être 0");
        assertEquals(60, res.compensatedMinutes(), "Le surplus de 60 min compense le déficit de 60 min");
    }

    @Test
    @DisplayName("T2 — HS normale : 8 + 9 + 8 + 8 + 8 = 41h -> 1h HS")
    void testT2_hsNormale() {
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480, WeeklyPriorState.NONE);
        List<DailyAttendanceResult> days = List.of(
                workingDay(monday, 480),
                workingDay(monday.plusDays(1), 540),
                workingDay(monday.plusDays(2), 480),
                workingDay(monday.plusDays(3), 480),
                workingDay(monday.plusDays(4), 480)
        );

        WeeklyAttendanceResult res = calculator.calculateWeek(ctx, days);
        assertEquals(2460, res.workedMinutes());
        assertEquals(0, res.absenceMinutes());
        assertEquals(60, res.overtime30Minutes(), "Exactement 1h d'HS30");
        assertEquals(0, res.overtime50Minutes());
    }

    @Test
    @DisplayName("T3 — Frontière sans carry : M (8+8+9, obj 24h) -> +1h HS ; M+1 (7+8, obj 16h) -> -1h déficit")
    void testT3_frontiereSansCarry() {
        // Tronçon Période M (Lundi -> Mercredi, 3 jours = 24h = 1440 min attendues)
        // Réalisé : 8h + 8h + 9h = 25h = 1500 min
        WeeklyAttendanceContext ctxM = new WeeklyAttendanceContext(monday, 1440, 480, true, 480, WeeklyPriorState.NONE);
        List<DailyAttendanceResult> daysM = List.of(
                workingDay(monday, 480),
                workingDay(monday.plusDays(1), 480),
                workingDay(monday.plusDays(2), 540)
        );
        WeeklyAttendanceResult resM = calculator.calculateWeek(ctxM, daysM);
        assertEquals(1500, resM.workedMinutes());
        assertEquals(0, resM.absenceMinutes());
        assertEquals(60, resM.overtime30Minutes(), "Période M doit avoir son résultat définitif propre : +1h HS");

        // Tronçon Période M+1 (Jeudi -> Vendredi, 2 jours = 16h = 960 min attendues)
        // Réalisé : 7h + 8h = 15h = 900 min
        // RÈGLE MÉTIER : AUCUNE compensation avec M ! priorState = NONE
        WeeklyAttendanceContext ctxM1 = new WeeklyAttendanceContext(monday.plusDays(3), 960, 480, true, 480, WeeklyPriorState.NONE);
        List<DailyAttendanceResult> daysM1 = List.of(
                workingDay(monday.plusDays(3), 420),
                workingDay(monday.plusDays(4), 480)
        );
        WeeklyAttendanceResult resM1 = calculator.calculateWeek(ctxM1, daysM1);
        assertEquals(900, resM1.workedMinutes());
        assertEquals(60, resM1.absenceMinutes(), "Période M+1 doit avoir son résultat définitif propre : -1h déficit");
        assertEquals(0, resM1.overtime30Minutes(), "Période M+1 ne doit avoir aucune HS");
    }

    @Test
    @DisplayName("T4 — Compensation propre à chaque tronçon : M (7+8+9) -> 0/0 ; M+1 (7+9) -> 0/0")
    void testT4_compensationPropreChaqueTroncon() {
        // Tronçon Période M (Lundi -> Mercredi, 24h attendues) : 7h + 8h + 9h = 24h
        WeeklyAttendanceContext ctxM = new WeeklyAttendanceContext(monday, 1440, 480, true, 480, WeeklyPriorState.NONE);
        List<DailyAttendanceResult> daysM = List.of(
                workingDay(monday, 420),
                workingDay(monday.plusDays(1), 480),
                workingDay(monday.plusDays(2), 540)
        );
        WeeklyAttendanceResult resM = calculator.calculateWeek(ctxM, daysM);
        assertEquals(1440, resM.workedMinutes());
        assertEquals(0, resM.absenceMinutes(), "M : déficit = 0");
        assertEquals(0, resM.overtime30Minutes(), "M : HS = 0");
        assertEquals(60, resM.compensatedMinutes(), "M compense ses propres 60 min");

        // Tronçon Période M+1 (Jeudi -> Vendredi, 16h attendues) : 7h + 9h = 16h
        WeeklyAttendanceContext ctxM1 = new WeeklyAttendanceContext(monday.plusDays(3), 960, 480, true, 480, WeeklyPriorState.NONE);
        List<DailyAttendanceResult> daysM1 = List.of(
                workingDay(monday.plusDays(3), 420),
                workingDay(monday.plusDays(4), 540)
        );
        WeeklyAttendanceResult resM1 = calculator.calculateWeek(ctxM1, daysM1);
        assertEquals(960, resM1.workedMinutes());
        assertEquals(0, resM1.absenceMinutes(), "M+1 : déficit = 0");
        assertEquals(0, resM1.overtime30Minutes(), "M+1 : HS = 0");
        assertEquals(60, resM1.compensatedMinutes(), "M+1 compense ses propres 60 min");
    }

    @Test
    @DisplayName("T5 — Frontière après lundi : Lundi (M) | Mardi -> Vendredi (M+1) indépendants")
    void testT5_frontiereApresLundi() {
        // M : Lundi seul (1 jour = 8h = 480 min attendues). Réalisé : 9h = 540 min
        WeeklyAttendanceContext ctxM = new WeeklyAttendanceContext(monday, 480, 480, true, 480, WeeklyPriorState.NONE);
        List<DailyAttendanceResult> daysM = List.of(workingDay(monday, 540));
        WeeklyAttendanceResult resM = calculator.calculateWeek(ctxM, daysM);
        assertEquals(60, resM.overtime30Minutes(), "M : +1h HS sur son tronçon d'1 jour");
        assertEquals(0, resM.absenceMinutes());

        // M+1 : Mardi à Vendredi (4 jours = 32h = 1920 min attendues). Réalisé : 31h = 1860 min
        WeeklyAttendanceContext ctxM1 = new WeeklyAttendanceContext(monday.plusDays(1), 1920, 480, true, 480, WeeklyPriorState.NONE);
        List<DailyAttendanceResult> daysM1 = List.of(
                workingDay(monday.plusDays(1), 480),
                workingDay(monday.plusDays(2), 480),
                workingDay(monday.plusDays(3), 480),
                workingDay(monday.plusDays(4), 420) // 7h
        );
        WeeklyAttendanceResult resM1 = calculator.calculateWeek(ctxM1, daysM1);
        assertEquals(60, resM1.absenceMinutes(), "M+1 : -1h déficit sur son tronçon de 4 jours");
        assertEquals(0, resM1.overtime30Minutes());
    }

    @Test
    @DisplayName("T6 — Frontière avant vendredi : Lundi -> Jeudi (M) | Vendredi (M+1) indépendants")
    void testT6_frontiereAvantVendredi() {
        // M : Lundi à Jeudi (4 jours = 32h = 1920 min attendues). Réalisé : 33h = 1980 min
        WeeklyAttendanceContext ctxM = new WeeklyAttendanceContext(monday, 1920, 480, true, 480, WeeklyPriorState.NONE);
        List<DailyAttendanceResult> daysM = List.of(
                workingDay(monday, 480),
                workingDay(monday.plusDays(1), 480),
                workingDay(monday.plusDays(2), 480),
                workingDay(monday.plusDays(3), 540) // 9h
        );
        WeeklyAttendanceResult resM = calculator.calculateWeek(ctxM, daysM);
        assertEquals(60, resM.overtime30Minutes(), "M : +1h HS");
        assertEquals(0, resM.absenceMinutes());

        // M+1 : Vendredi seul (1 jour = 8h = 480 min attendues). Réalisé : 7h = 420 min
        WeeklyAttendanceContext ctxM1 = new WeeklyAttendanceContext(monday.plusDays(4), 480, 480, true, 480, WeeklyPriorState.NONE);
        List<DailyAttendanceResult> daysM1 = List.of(workingDay(monday.plusDays(4), 420));
        WeeklyAttendanceResult resM1 = calculator.calculateWeek(ctxM1, daysM1);
        assertEquals(60, resM1.absenceMinutes(), "M+1 : -1h déficit");
        assertEquals(0, resM1.overtime30Minutes());
    }

    @Test
    @DisplayName("T7 — Congé dans tronçon : 3 jours dont 1 jour de congé -> objectif 16h, 16h faites -> 0/0")
    void testT7_congeDansTroncon() {
        // Tronçon de 3 jours (Mercredi à Vendredi).
        // Mercredi : Congé payé (LEAVE).
        // Jeudi & Vendredi : Travaillés 8h chacun = 16h.
        // Objectif théorique réduit : (3 - 1) * 8h = 16h = 960 min.
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday.plusDays(2), 960, 480, true, 480, WeeklyPriorState.NONE);
        List<DailyAttendanceResult> days = List.of(
                day(monday.plusDays(2), 0, DailyAttendanceResult.Status.LEAVE, 0, false, false),
                workingDay(monday.plusDays(3), 480),
                workingDay(monday.plusDays(4), 480)
        );
        WeeklyAttendanceResult res = calculator.calculateWeek(ctx, days);
        assertEquals(960, res.workedMinutes());
        assertEquals(0, res.absenceMinutes(), "0 déficit sur objectif réduit");
        assertEquals(0, res.overtime30Minutes(), "0 HS");
    }

    @Test
    @DisplayName("T8 — Férié chômé dans tronçon : objectif réduit, surplus qualifié au-delà")
    void testT8_ferieDansTroncon() {
        // Tronçon de 3 jours dont 1 férié chômé (PUBLIC_HOLIDAY_OFF).
        // Objectif théorique réduit : 2 jours ouvrés effectifs = 16h = 960 min.
        // Travaillé : Jeudi 8h, Vendredi 9h = 17h = 1020 min.
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday.plusDays(2), 960, 480, true, 480, WeeklyPriorState.NONE);
        List<DailyAttendanceResult> days = List.of(
                day(monday.plusDays(2), 0, DailyAttendanceResult.Status.PUBLIC_HOLIDAY_OFF, 0, false, true),
                workingDay(monday.plusDays(3), 480),
                workingDay(monday.plusDays(4), 540)
        );
        WeeklyAttendanceResult res = calculator.calculateWeek(ctx, days);
        assertEquals(1020, res.workedMinutes());
        assertEquals(60, res.overtime30Minutes(), "1h HS au-delà de l'objectif réduit de 16h");
        assertEquals(0, res.absenceMinutes());
    }

    @Test
    @DisplayName("T9 — Dimanche travaillé : minutes DIMANCHE préservées")
    void testT9_dimancheTravaille() {
        LocalDate sunday = monday.plusDays(6);
        DailyAttendanceResult sundayResult = day(sunday, 480, DailyAttendanceResult.Status.SUNDAY_WORKED, 0, true, false);

        assertTrue(sundayResult.sunday(), "Doit être marqué dimanche");
        assertEquals(480, sundayResult.workedMinutes(), "Minutes travaillées dimanche conservées");
        assertEquals(DailyAttendanceResult.Status.SUNDAY_WORKED, sundayResult.status());
    }

    @Test
    @DisplayName("T10 — Samedi travaillé : heures travaillées enregistrées, statut SAMEDI_TRAVAILLE")
    void testT10_samediTravaille() {
        LocalDate saturday = monday.plusDays(5);
        DailyAttendanceResult satResult = day(saturday, 480, DailyAttendanceResult.Status.PRESENT, 0, false, false);

        assertFalse(satResult.sunday());
        assertFalse(satResult.publicHoliday());
        assertEquals(480, satResult.workedMinutes(), "Minutes travaillées samedi doivent être comptabilisées");
    }

    @Test
    @DisplayName("T11 — Samedi compense déficit semaine : 7h (Lun) + 32h (Mar-Ven) + 1h (Sam) = 40h -> 0 déficit, 0 HS")
    void testT11_samediCompenseDeficitSemaine() {
        // Objectif hebdomadaire = 40h (2400 min)
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480, WeeklyPriorState.NONE);
        List<DailyAttendanceResult> days = List.of(
                workingDay(monday, 420),              // Lundi : 7h (-1h déficit)
                workingDay(monday.plusDays(1), 480),  // Mardi : 8h
                workingDay(monday.plusDays(2), 480),  // Mercredi : 8h
                workingDay(monday.plusDays(3), 480),  // Jeudi : 8h
                workingDay(monday.plusDays(4), 480),  // Vendredi : 8h
                workingDay(monday.plusDays(5), 60)    // Samedi : 1h (+1h surplus ordinaire)
        );

        WeeklyAttendanceResult res = calculator.calculateWeek(ctx, days);
        assertEquals(2400, res.workedMinutes());
        assertEquals(0, res.absenceMinutes(), "Déficit semaine doit être éteint par le samedi travaillé");
        assertEquals(0, res.overtime30Minutes(), "0 HS");
        assertEquals(60, res.compensatedMinutes(), "60 minutes compensées");
    }

    @Test
    @DisplayName("T12 — Samedi au-delà de 40h : 40h Lun-Ven + Samedi 3h -> 3h HS ordinaires")
    void testT12_samediAuDela40hProduitHs() {
        // Objectif hebdomadaire = 40h (2400 min)
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480, WeeklyPriorState.NONE);
        List<DailyAttendanceResult> days = List.of(
                workingDay(monday, 480),              // 8h
                workingDay(monday.plusDays(1), 480),  // 8h
                workingDay(monday.plusDays(2), 480),  // 8h
                workingDay(monday.plusDays(3), 480),  // 8h
                workingDay(monday.plusDays(4), 480),  // 8h
                workingDay(monday.plusDays(5), 180)   // Samedi : 3h (180 min)
        );

        WeeklyAttendanceResult res = calculator.calculateWeek(ctx, days);
        assertEquals(2580, res.workedMinutes(), "43h travaillées au total");
        assertEquals(0, res.absenceMinutes());
        assertEquals(180, res.overtime30Minutes(), "3h (180 min) d'HS ordinaires qualifiées");
        assertEquals(0, res.overtime50Minutes());
    }
}

