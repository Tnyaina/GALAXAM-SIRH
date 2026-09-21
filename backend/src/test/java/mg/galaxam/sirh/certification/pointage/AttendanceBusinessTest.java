package mg.galaxam.sirh.certification.pointage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import mg.galaxam.sirh.model.pointage.calculation.DailyAttendanceCalculator;
import mg.galaxam.sirh.model.pointage.calculation.DailyAttendanceContext;
import mg.galaxam.sirh.model.pointage.calculation.DailyAttendanceResult;
import mg.galaxam.sirh.model.pointage.calculation.WeeklyAttendanceCalculator;
import mg.galaxam.sirh.model.pointage.calculation.WeeklyAttendanceContext;
import mg.galaxam.sirh.model.pointage.calculation.WeeklyAttendanceResult;
import mg.galaxam.sirh.oracle.AttendanceIndependentOracle;

@DisplayName("CERTIFICATION POINTAGE — CALCULS MÉTIER & ORACLE INDÉPENDANT")
public class AttendanceBusinessTest {

    private final DailyAttendanceCalculator dailyCalculator = new DailyAttendanceCalculator();
    private final WeeklyAttendanceCalculator weeklyCalculator = new WeeklyAttendanceCalculator();

    @Test
    @DisplayName("1. Journée nominale 8h : conformité exacte avec l'Oracle Indépendant")
    void testJourneeNominale() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        LocalTime schedIn = LocalTime.of(8, 0);
        LocalTime schedOut = LocalTime.of(17, 0); // 9h avec 1h pause = 8h effectives

        DailyAttendanceContext ctx = new DailyAttendanceContext(
                1, date,
                schedIn, LocalTime.of(12, 0), LocalTime.of(13, 0), schedOut,
                schedIn, LocalTime.of(12, 0), LocalTime.of(13, 0), schedOut,
                LocalTime.of(22, 0), LocalTime.of(5, 0),
                false, false, false,
                DailyAttendanceContext.LeavePart.NONE, false,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false
        );

        DailyAttendanceResult runtime = dailyCalculator.calculateDay(ctx);

        // Oracle indépendant
        AttendanceIndependentOracle.DailyShift shift = new AttendanceIndependentOracle.DailyShift(
                date,
                schedIn, LocalTime.of(12, 0), LocalTime.of(13, 0), schedOut,
                schedIn, LocalTime.of(12, 0), LocalTime.of(13, 0), schedOut,
                false, false, false, false
        );
        AttendanceIndependentOracle.AttendanceExpectedResult oracle = AttendanceIndependentOracle.calculatePeriod(
                List.of(shift), 2400, 480
        );

        // Vérification de stricte égalité minute par minute
        assertEquals(480, runtime.workedMinutes(), "Minutes travaillées conformes (480 min = 8h)");
        assertEquals(0, runtime.lateMinutes(), "Zéro retard");
        assertEquals(0, runtime.absenceMinutes(), "Zéro absence");
        assertEquals(0, runtime.nightMinutes(), "Zéro travail de nuit");
        assertEquals(DailyAttendanceResult.Status.PRESENT, runtime.status());
    }

    @Test
    @DisplayName("2. Retard, départ anticipé et absence injustifiée : décompte strict sans compensation")
    void testRetardEtAbsence() {
        LocalDate date = LocalDate.of(2026, 9, 2);
        LocalTime schedIn = LocalTime.of(8, 0);
        LocalTime schedOut = LocalTime.of(17, 0);
        LocalTime actualIn = LocalTime.of(8, 30);  // 30 min retard
        LocalTime actualOut = LocalTime.of(16, 45); // 15 min départ anticipé

        // Actual: arrival 08:30 (30 min late), exit 16:45 (15 min early)
        DailyAttendanceContext ctx = new DailyAttendanceContext(
                1, date,
                actualIn, LocalTime.of(12, 0), LocalTime.of(13, 0), actualOut,
                schedIn, LocalTime.of(12, 0), LocalTime.of(13, 0), schedOut,
                LocalTime.of(22, 0), LocalTime.of(5, 0),
                false, false, false,
                DailyAttendanceContext.LeavePart.NONE, false,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false
        );

        DailyAttendanceResult runtime = dailyCalculator.calculateDay(ctx);

        assertEquals(30, runtime.lateMinutes(), "30 minutes de retard détectées");
        assertEquals(435, runtime.workedMinutes(), "480 - 45 = 435 minutes travaillées");
        assertEquals(45, runtime.absenceMinutes(), "45 minutes d'absence (30 min retard + 15 min sortie anticipée)");
    }

    @Test
    @DisplayName("3. Anti-double déduction : Une journée de congé payé ne génère AUCUNE absence injustifiée")
    void testAntiDoubleDeductionConge() {
        LocalDate date = LocalDate.of(2026, 9, 3);
        LocalTime schedIn = LocalTime.of(8, 0);
        LocalTime schedOut = LocalTime.of(17, 0);

        DailyAttendanceContext ctx = new DailyAttendanceContext(
                1, date,
                null, null, null, null, // Non pointé en raison du congé
                schedIn, LocalTime.of(12, 0), LocalTime.of(13, 0), schedOut,
                LocalTime.of(22, 0), LocalTime.of(5, 0),
                false, false, false,
                DailyAttendanceContext.LeavePart.FULL_DAY, false, // Congé payé journée complète
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false
        );

        DailyAttendanceResult runtime = dailyCalculator.calculateDay(ctx);

        assertEquals(DailyAttendanceResult.Status.LEAVE, runtime.status(), "Statut reconnu comme CONGE (LEAVE)");
        assertEquals(0, runtime.lateMinutes(), "Zéro retard décompté sur congé");
        assertEquals(0, runtime.absenceMinutes(), "Zéro absence injustifiée sur congé payé");
        assertEquals(0, runtime.workedMinutes(), "0 min travaillées");
        // Les anomalies ne doivent pas classer le congé en absence injustifiée
        assertThat(runtime.anomalies()).noneMatch(a -> a.contains("ABSENCE_INJUSTIFIEE"));
    }

    @Test
    @DisplayName("4. Semaine 40h standard vs Heures supplémentaires décomposées (30% et 50%)")
    void testSemaineHeuresSupplementaires() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        List<DailyAttendanceResult> days = new ArrayList<>();

        // 5 jours de 10h de travail effectif (600 min/jour) = 50h dans la semaine (3000 min)
        for (int i = 0; i < 5; i++) {
            LocalDate d = monday.plusDays(i);
            DailyAttendanceContext ctx = new DailyAttendanceContext(
                    1, d,
                    LocalTime.of(8, 0), null, null, LocalTime.of(18, 0), // Actual : 10h journée continue
                    LocalTime.of(8, 0), null, null, LocalTime.of(17, 0), // Sched : 8h journée continue
                    LocalTime.of(22, 0), LocalTime.of(5, 0),
                    true, false, false, // continuousSchedule = true
                    DailyAttendanceContext.LeavePart.NONE, false,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false
            );
            days.add(dailyCalculator.calculateDay(ctx));
        }

        // Seuil légal : 40h (2400 min), Seuil Tranche 1 : 8h (480 min)
        WeeklyAttendanceContext wCtx = new WeeklyAttendanceContext(monday, 2400, 480, true, 480);
        WeeklyAttendanceResult wResult = weeklyCalculator.calculateWeek(wCtx, days);

        assertEquals(3000, wResult.workedMinutes(), "Total 3000 min = 50h travaillées");
        assertEquals(600, wResult.grossOvertimeMinutes(), "Total 600 min = 10h supplémentaires brutes");
        assertEquals(480, wResult.overtime30Minutes(), "8h (480 min) en tranche 1 (+30%)");
        assertEquals(120, wResult.overtime50Minutes(), "2h (120 min) en tranche 2 (+50%)");
    }

    @Test
    @DisplayName("5. Majorations spécifiques : Nuit (22h-05h), Dimanche et Férié")
    void testMajorationsSpecifiques() {
        LocalDate sunday = LocalDate.of(2026, 9, 6);
        // Vacation du dimanche soir : 20h00 à 02h00 du matin (6h = 360 min)
        // Heures de nuit : 22h00 à 02h00 = 4h (240 min)
        DailyAttendanceContext ctx = new DailyAttendanceContext(
                1, sunday,
                LocalTime.of(20, 0), null, null, LocalTime.of(2, 0), // Actual
                null, null, null, null,                              // Sched (repos/dimanche)
                LocalTime.of(22, 0), LocalTime.of(5, 0),
                true, false, false, // continuousSchedule = true
                DailyAttendanceContext.LeavePart.NONE, false,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false
        );

        DailyAttendanceResult runtime = dailyCalculator.calculateDay(ctx);

        assertEquals(360, runtime.workedMinutes(), "6h de travail effectif");
        assertEquals(true, runtime.sunday(), "Jour de dimanche détecté");
        assertEquals(240, runtime.nightMinutes(), "4h de travail de nuit (22h-02h) (+30%)");
    }
}
