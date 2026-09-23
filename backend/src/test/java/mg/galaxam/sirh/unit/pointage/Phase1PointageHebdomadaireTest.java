package mg.galaxam.sirh.unit.pointage;

import mg.galaxam.sirh.pointage.domain.calculation.DailyAttendanceResult;
import mg.galaxam.sirh.pointage.domain.calculation.WeeklyAttendanceCalculator;
import mg.galaxam.sirh.pointage.domain.calculation.WeeklyAttendanceContext;
import mg.galaxam.sirh.pointage.domain.calculation.WeeklyAttendanceResult;
import mg.galaxam.sirh.pointage.domain.calculation.WeeklyPriorState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 1 — Pointage Hebdomadaire & Continuité des Semaines Tronquées")
public class Phase1PointageHebdomadaireTest {

    private WeeklyAttendanceCalculator calculator;
    private final LocalDate monday = LocalDate.of(2026, 9, 28);

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

    // =========================================================================
    // 1. COMPORTEMENTS HEBDOMADAIRES NOMINAUX ET SANCTUARISÉS (Tests 1 à 6)
    // =========================================================================

    @Test
    @DisplayName("Test 1 : Semaine nominale 40h -> 0 déficit, 0 HS")
    void test1_semaineNominale40h() {
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480);
        List<DailyAttendanceResult> days = List.of(
                workingDay(monday, 480),
                workingDay(monday.plusDays(1), 480),
                workingDay(monday.plusDays(2), 480),
                workingDay(monday.plusDays(3), 480),
                workingDay(monday.plusDays(4), 480)
        );

        WeeklyAttendanceResult res = calculator.calculateWeek(ctx, days);

        assertEquals(2400, res.expectedMinutes());
        assertEquals(2400, res.workedMinutes());
        assertEquals(0, res.absenceMinutes());
        assertEquals(0, res.overtime30Minutes());
        assertEquals(0, res.overtime50Minutes());
    }

    @Test
    @DisplayName("Test 2 : Compensation 7h + 9h + 8h + 8h + 8h -> 0 déficit, 0 HS, 60 min compensées")
    void test2_compensation7_9_8_8_8() {
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480);
        List<DailyAttendanceResult> days = List.of(
                workingDay(monday, 420), // 7h (-1h déficit)
                workingDay(monday.plusDays(1), 540), // 9h (+1h surplus)
                workingDay(monday.plusDays(2), 480),
                workingDay(monday.plusDays(3), 480),
                workingDay(monday.plusDays(4), 480)
        );

        WeeklyAttendanceResult res = calculator.calculateWeek(ctx, days);

        assertEquals(2400, res.workedMinutes());
        assertEquals(0, res.absenceMinutes());
        assertEquals(0, res.overtime30Minutes());
        assertEquals(60, res.compensatedMinutes());
    }

    @Test
    @DisplayName("Test 3 : Semaine avec déficit net (39h = 2340 min) -> déficit 60 min, 0 HS")
    void test3_semaine39hDeficit() {
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480);
        List<DailyAttendanceResult> days = List.of(
                workingDay(monday, 420), // 7h
                workingDay(monday.plusDays(1), 480),
                workingDay(monday.plusDays(2), 480),
                workingDay(monday.plusDays(3), 480),
                workingDay(monday.plusDays(4), 480)
        );

        WeeklyAttendanceResult res = calculator.calculateWeek(ctx, days);

        assertEquals(2340, res.workedMinutes());
        assertEquals(60, res.absenceMinutes());
        assertEquals(0, res.overtime30Minutes());
    }

    @Test
    @DisplayName("Test 4 : Semaine avec dépassement net (41h = 2460 min) -> 0 déficit, 60 min HS30")
    void test4_semaine41hSurplus() {
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480);
        List<DailyAttendanceResult> days = List.of(
                workingDay(monday, 540), // 9h
                workingDay(monday.plusDays(1), 480),
                workingDay(monday.plusDays(2), 480),
                workingDay(monday.plusDays(3), 480),
                workingDay(monday.plusDays(4), 480)
        );

        WeeklyAttendanceResult res = calculator.calculateWeek(ctx, days);

        assertEquals(2460, res.workedMinutes());
        assertEquals(0, res.absenceMinutes());
        assertEquals(60, res.overtime30Minutes());
        assertEquals(0, res.overtime50Minutes());
    }

    @Test
    @DisplayName("Test 5 : Férié chômé Modèle A (Objectif réduit à 32h) -> 0 déficit, 0 HS")
    void test5_ferieChomeObjectif32h() {
        // Lundi férié chômé : expectedMinutes = 1920 (32h), standardDay = 480 min
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 1920, 480, true, 480);
        List<DailyAttendanceResult> days = List.of(
                day(monday, 0, DailyAttendanceResult.Status.PUBLIC_HOLIDAY_OFF, 0, false, true),
                workingDay(monday.plusDays(1), 480),
                workingDay(monday.plusDays(2), 480),
                workingDay(monday.plusDays(3), 480),
                workingDay(monday.plusDays(4), 480)
        );

        WeeklyAttendanceResult res = calculator.calculateWeek(ctx, days);

        assertEquals(1920, res.workedMinutes());
        assertEquals(0, res.absenceMinutes());
        assertEquals(0, res.overtime30Minutes());
    }

    @Test
    @DisplayName("Test 6 : Férié chômé + 33h travaillées -> objectif 32h, dépassement 1h qualifié en HS30")
    void test6_ferieChomeAvec33hTravaillees() {
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 1920, 480, true, 480);
        List<DailyAttendanceResult> days = List.of(
                day(monday, 0, DailyAttendanceResult.Status.PUBLIC_HOLIDAY_OFF, 0, false, true),
                workingDay(monday.plusDays(1), 540), // 9h (+1h)
                workingDay(monday.plusDays(2), 480),
                workingDay(monday.plusDays(3), 480),
                workingDay(monday.plusDays(4), 480)
        );

        WeeklyAttendanceResult res = calculator.calculateWeek(ctx, days);

        assertEquals(1980, res.workedMinutes()); // 33h
        assertEquals(0, res.absenceMinutes());
        assertEquals(60, res.overtime30Minutes());
        assertEquals(0, res.overtime50Minutes());
    }

    // =========================================================================
    // 2. CONGÉS ET SEMAINES PARTIELLES (Tests 7 à 9)
    // =========================================================================

    @Test
    @DisplayName("Test 7 : Congé plein jour -> réduction objectif (32h), 32h travaillées -> aucun déficit artificiel")
    void test7_congePleinJourObjectifHebdomadaire() {
        // Semaine avec 1 jour de congé validé le lundi : expectedMinutes = 1920 min (32h)
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 1920, 480, true, 480);
        List<DailyAttendanceResult> days = List.of(
                day(monday, 0, DailyAttendanceResult.Status.LEAVE, 0, false, false),
                workingDay(monday.plusDays(1), 480),
                workingDay(monday.plusDays(2), 480),
                workingDay(monday.plusDays(3), 480),
                workingDay(monday.plusDays(4), 480)
        );

        WeeklyAttendanceResult res = calculator.calculateWeek(ctx, days);

        assertEquals(1920, res.workedMinutes());
        assertEquals(0, res.absenceMinutes());
        assertEquals(0, res.overtime30Minutes());
    }

    @Test
    @DisplayName("Test 8 : Semaine partielle entrée contrat mercredi -> objectif 24h, 24h travaillées -> 0 déficit, 0 HS")
    void test8_entreeContratMercredi() {
        // Embauche le mercredi : 3 jours ouvrés = 24h (1440 min)
        LocalDate mercredi = monday.plusDays(2);
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(mercredi, 1440, 480, true, 480);
        List<DailyAttendanceResult> days = List.of(
                workingDay(mercredi, 480),
                workingDay(mercredi.plusDays(1), 480),
                workingDay(mercredi.plusDays(2), 480)
        );

        WeeklyAttendanceResult res = calculator.calculateWeek(ctx, days);

        assertEquals(1440, res.workedMinutes());
        assertEquals(0, res.absenceMinutes());
        assertEquals(0, res.overtime30Minutes());
    }

    @Test
    @DisplayName("Test 9 : Semaine partielle sortie contrat milieu de semaine (mercredi) -> objectif 24h, 24h travaillées -> 0 déficit, 0 HS")
    void test9_sortieContratMercredi() {
        // Fin de contrat le mercredi soir : 3 jours travaillés (Lundi, Mardi, Mercredi) = 24h (1440 min)
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 1440, 480, true, 480);
        List<DailyAttendanceResult> days = List.of(
                workingDay(monday, 480),
                workingDay(monday.plusDays(1), 480),
                workingDay(monday.plusDays(2), 480)
        );

        WeeklyAttendanceResult res = calculator.calculateWeek(ctx, days);

        assertEquals(1440, res.workedMinutes());
        assertEquals(0, res.absenceMinutes());
        assertEquals(0, res.overtime30Minutes());
    }

    // =========================================================================
    // 3. CONTINUITÉ DES SEMAINES TRONQUÉES (Tests 10 à 12)
    // =========================================================================

    @Test
    @DisplayName("Test 10 : Semaine tronquée Déficit (120 min) puis Surplus (180 min) -> Déficit final 0, HS résiduelle 60 min")
    void test10_semaineTronqueeDeficitPuisSurplus() {
        // Tronçon 1 (Mois M : Lundi & Mardi, 16h = 960 min attendues)
        // 2x 7h travaillées = 14h (840 min) -> déficit de 120 min (2h)
        WeeklyAttendanceContext ctxTroncon1 = new WeeklyAttendanceContext(monday, 960, 480, true, 480);
        List<DailyAttendanceResult> daysT1 = List.of(
                workingDay(monday, 420),
                workingDay(monday.plusDays(1), 420)
        );
        WeeklyAttendanceResult resT1 = calculator.calculateWeek(ctxTroncon1, daysT1);

        assertEquals(120, resT1.absenceMinutes(), "Tronçon 1 enregistre un déficit provisoire de 120 min");
        assertEquals(0, resT1.overtime30Minutes());

        // Tronçon 2 (Mois M+1 : Mercredi, Jeudi, Vendredi, 24h = 1440 min attendues)
        // 3x 9h travaillées = 27h (1620 min) -> surplus de 180 min (3h)
        // Chaînage avec le priorState du Tronçon 1
        WeeklyPriorState prior = resT1.toPriorState();
        WeeklyAttendanceContext ctxTroncon2 = new WeeklyAttendanceContext(monday, 1440, 480, true, 480, prior);
        List<DailyAttendanceResult> daysT2 = List.of(
                workingDay(monday.plusDays(2), 540),
                workingDay(monday.plusDays(3), 540),
                workingDay(monday.plusDays(4), 540)
        );
        WeeklyAttendanceResult resT2 = calculator.calculateWeek(ctxTroncon2, daysT2);

        // VÉRIFICATION DE CONTINUITÉ HEBDOMADAIRE (Semaine Civile Complète) :
        // 41h travaillées pour 40h attendues :
        // - Le surplus de 180 min compense les 120 min de déficit antérieur
        // - Déficit final de la semaine = 0
        // - HS résiduelle de la semaine = 60 min (1h HS30)
        assertEquals(0, resT2.cumulativeAbsenceMinutes(), "Déficit final de la semaine complète doit être 0");
        assertEquals(60, resT2.cumulativeOvertime30Minutes(), "HS30 résiduelle de la semaine complète doit être 60 min");
        assertEquals(0, resT2.cumulativeOvertime50Minutes());

        // VÉRIFICATION DE LA QUOTE-PART IMPUTABLE AU TRONÇON 2 :
        assertEquals(120, resT2.compensatedMinutes(), "Tronçon 2 absorbe 120 min de compensation pour éteindre le déficit");
        assertEquals(60, resT2.overtime30Minutes(), "Tronçon 2 dégage l'excédent résiduel net de 60 min");
        assertEquals(0, resT2.absenceMinutes(), "Tronçon 2 n'a aucune absence");
    }

    @Test
    @DisplayName("Test 11 : Semaine tronquée Surplus (180 min) puis Déficit (120 min) -> Déficit final 0, HS résiduelle 60 min")
    void test11_semaineTronqueeSurplusPuisDeficit() {
        // Tronçon 1 (Mois M : Lundi 9h + Mardi 10h = 19h = 1140 min, pour 16h = 960 min attendues)
        // Surplus de 180 min (3h)
        WeeklyAttendanceContext ctxTroncon1 = new WeeklyAttendanceContext(monday, 960, 480, true, 480);
        List<DailyAttendanceResult> daysT1 = List.of(
                workingDay(monday, 540),
                workingDay(monday.plusDays(1), 600)
        );
        WeeklyAttendanceResult resT1 = calculator.calculateWeek(ctxTroncon1, daysT1);

        assertEquals(0, resT1.absenceMinutes());
        assertEquals(180, resT1.overtime30Minutes());

        // Tronçon 2 (Mois M+1 : Mercredi 7h, Jeudi 7h, Vendredi 8h = 22h = 1320 min, pour 24h = 1440 min attendues)
        // Déficit de 120 min (2h)
        WeeklyPriorState prior = resT1.toPriorState();
        WeeklyAttendanceContext ctxTroncon2 = new WeeklyAttendanceContext(monday, 1440, 480, true, 480, prior);
        List<DailyAttendanceResult> daysT2 = List.of(
                workingDay(monday.plusDays(2), 420),
                workingDay(monday.plusDays(3), 420),
                workingDay(monday.plusDays(4), 480)
        );
        WeeklyAttendanceResult resT2 = calculator.calculateWeek(ctxTroncon2, daysT2);

        // VÉRIFICATION DE LA SYMÉTRIE ÉCONOMIQUE (Résultat identique au Test 10) :
        // 41h travaillées sur 40h attendues :
        // - Déficit final de la semaine = 0
        // - HS résiduelle de la semaine = 60 min (1h HS30)
        assertEquals(0, resT2.cumulativeAbsenceMinutes(), "Déficit final de la semaine complète doit être 0");
        assertEquals(60, resT2.cumulativeOvertime30Minutes(), "HS30 globale de la semaine doit être 60 min");
        assertEquals(0, resT2.cumulativeOvertime50Minutes());
    }

    @Test
    @DisplayName("Test 12 : Continuité stricte des tranches HS30/HS50 (6h M + 6h M+1 -> HS30 = 8h, HS50 = 4h)")
    void test12_semaineTronqueeContinuiteTranchesHs() {
        // Tronçon 1 (Mois M : 2 jours à 11h = 22h = 1320 min pour 16h = 960 min attendues)
        // Surplus = 6h (360 min)
        WeeklyAttendanceContext ctxTroncon1 = new WeeklyAttendanceContext(monday, 960, 480, true, 480);
        List<DailyAttendanceResult> daysT1 = List.of(
                workingDay(monday, 660),
                workingDay(monday.plusDays(1), 660)
        );
        WeeklyAttendanceResult resT1 = calculator.calculateWeek(ctxTroncon1, daysT1);

        assertEquals(360, resT1.overtime30Minutes(), "Tronçon 1 consomme 6h de la tranche 1 (HS30)");
        assertEquals(0, resT1.overtime50Minutes());

        // Tronçon 2 (Mois M+1 : 3 jours à 10h = 30h = 1800 min pour 24h = 1440 min attendues)
        // Surplus = 6h (360 min)
        // Total semaine = 12h d'HS nettes (720 min)
        WeeklyPriorState prior = resT1.toPriorState();
        WeeklyAttendanceContext ctxTroncon2 = new WeeklyAttendanceContext(monday, 1440, 480, true, 480, prior);
        List<DailyAttendanceResult> daysT2 = List.of(
                workingDay(monday.plusDays(2), 600),
                workingDay(monday.plusDays(3), 600),
                workingDay(monday.plusDays(4), 600)
        );
        WeeklyAttendanceResult resT2 = calculator.calculateWeek(ctxTroncon2, daysT2);

        // VÉRIFICATION DE LA TRANCHE GLOBALE :
        // Plafond tranche 1 = 8h (480 min). Tronçon 1 a consommé 6h (360 min).
        // Il ne reste que 2h (120 min) de HS30 pour Tronçon 2. Le surplus restant (4h = 240 min) bascule en HS50 !
        assertEquals(480, resT2.cumulativeOvertime30Minutes(), "HS30 globale doit plafonner exactement à 8h (480 min)");
        assertEquals(240, resT2.cumulativeOvertime50Minutes(), "HS50 globale doit recevoir le solde de 4h (240 min)");

        // Quote-part incrémentale de Tronçon 2 :
        assertEquals(120, resT2.overtime30Minutes(), "Tronçon 2 reçoit les 2h restantes de la tranche HS30");
        assertEquals(240, resT2.overtime50Minutes(), "Tronçon 2 reçoit les 4h de la tranche HS50");

        // SOMME DES DEUX TRONÇONS :
        long sommeHs30 = resT1.overtime30Minutes() + resT2.overtime30Minutes();
        long sommeHs50 = resT1.overtime50Minutes() + resT2.overtime50Minutes();
        assertEquals(480, sommeHs30, "Somme M + M+1 en HS30 = 8h (480 min)");
        assertEquals(240, sommeHs50, "Somme M + M+1 en HS50 = 4h (240 min)");
    }

    // =========================================================================
    // 4. TESTS FRONTIÈRE TRANCHE HS (Tests 13 à 15)
    // =========================================================================

    @Test
    @DisplayName("Test 13 : Frontière 479 min d'HS -> HS30 = 479 min, HS50 = 0")
    void test13_frontiere479Minutes() {
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480);
        // Semaine travaillée : 2400 + 479 = 2879 min
        List<DailyAttendanceResult> days = List.of(
                workingDay(monday, 480 + 479),
                workingDay(monday.plusDays(1), 480),
                workingDay(monday.plusDays(2), 480),
                workingDay(monday.plusDays(3), 480),
                workingDay(monday.plusDays(4), 480)
        );

        WeeklyAttendanceResult res = calculator.calculateWeek(ctx, days);

        assertEquals(479, res.overtime30Minutes());
        assertEquals(0, res.overtime50Minutes());
    }

    @Test
    @DisplayName("Test 14 : Frontière 480 min d'HS pile (8h) -> HS30 = 480 min, HS50 = 0")
    void test14_frontiere480Minutes() {
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480);
        // Semaine travaillée : 2400 + 480 = 2880 min
        List<DailyAttendanceResult> days = List.of(
                workingDay(monday, 480 + 480),
                workingDay(monday.plusDays(1), 480),
                workingDay(monday.plusDays(2), 480),
                workingDay(monday.plusDays(3), 480),
                workingDay(monday.plusDays(4), 480)
        );

        WeeklyAttendanceResult res = calculator.calculateWeek(ctx, days);

        assertEquals(480, res.overtime30Minutes());
        assertEquals(0, res.overtime50Minutes());
    }

    @Test
    @DisplayName("Test 15 : Frontière 481 min d'HS -> HS30 = 480 min, HS50 = 1 min")
    void test15_frontiere481Minutes() {
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480);
        // Semaine travaillée : 2400 + 481 = 2881 min
        List<DailyAttendanceResult> days = List.of(
                workingDay(monday, 480 + 481),
                workingDay(monday.plusDays(1), 480),
                workingDay(monday.plusDays(2), 480),
                workingDay(monday.plusDays(3), 480),
                workingDay(monday.plusDays(4), 480)
        );

        WeeklyAttendanceResult res = calculator.calculateWeek(ctx, days);

        assertEquals(480, res.overtime30Minutes());
        assertEquals(1, res.overtime50Minutes());
    }

    // =========================================================================
    // 5. PRÉSERVATION DES QUALIFICATIONS SPÉCIALES (Tests 16 à 18)
    // =========================================================================

    @Test
    @DisplayName("Test 16 : Préservation des heures de nuit (nightMinutes non altérées par la compensation)")
    void test16_preservationTempsNuit() {
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480);
        List<DailyAttendanceResult> days = List.of(
                day(monday, 480, DailyAttendanceResult.Status.PRESENT, 120, false, false), // 2h de nuit
                day(monday.plusDays(1), 480, DailyAttendanceResult.Status.PRESENT, 60, false, false), // 1h de nuit
                workingDay(monday.plusDays(2), 480),
                workingDay(monday.plusDays(3), 480),
                workingDay(monday.plusDays(4), 480)
        );

        WeeklyAttendanceResult res = calculator.calculateWeek(ctx, days);

        long totalNuit = res.days().stream().mapToLong(DailyAttendanceResult::nightMinutes).sum();
        assertEquals(180, totalNuit, "Les 180 min de nuit doivent être fidèlement conservées");
    }

    @Test
    @DisplayName("Test 17 : Préservation du temps travaillé le dimanche")
    void test17_preservationTempsDimanche() {
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480);
        LocalDate dimanche = monday.plusDays(6);
        List<DailyAttendanceResult> days = List.of(
                workingDay(monday, 480),
                workingDay(monday.plusDays(1), 480),
                workingDay(monday.plusDays(2), 480),
                workingDay(monday.plusDays(3), 480),
                workingDay(monday.plusDays(4), 480),
                day(dimanche, 300, DailyAttendanceResult.Status.SUNDAY_WORKED, 0, true, false) // 5h dimanche
        );

        WeeklyAttendanceResult res = calculator.calculateWeek(ctx, days);

        long minutesDimanche = res.days().stream()
                .filter(DailyAttendanceResult::sunday)
                .mapToLong(DailyAttendanceResult::workedMinutes).sum();
        assertEquals(300, minutesDimanche, "Les 300 min travaillées le dimanche doivent être conservées sans être écrasées");
    }

    @Test
    @DisplayName("Test 18 : Préservation du temps travaillé un jour férié")
    void test18_preservationTempsFerieTravaille() {
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480);
        LocalDate ferie = monday.plusDays(2); // Mercredi férié travaillé
        List<DailyAttendanceResult> days = List.of(
                workingDay(monday, 480),
                workingDay(monday.plusDays(1), 480),
                day(ferie, 480, DailyAttendanceResult.Status.PUBLIC_HOLIDAY_WORKED, 0, false, true),
                workingDay(monday.plusDays(3), 480),
                workingDay(monday.plusDays(4), 480)
        );

        WeeklyAttendanceResult res = calculator.calculateWeek(ctx, days);

        long minutesFerie = res.days().stream()
                .filter(DailyAttendanceResult::publicHoliday)
                .mapToLong(DailyAttendanceResult::workedMinutes).sum();
        assertEquals(480, minutesFerie, "Les 480 min de férié travaillé doivent être fidèlement conservées");
    }
}
