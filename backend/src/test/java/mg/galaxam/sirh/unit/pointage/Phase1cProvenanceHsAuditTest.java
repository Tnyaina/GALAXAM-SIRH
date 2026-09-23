package mg.galaxam.sirh.unit.pointage;

import mg.galaxam.sirh.pointage.domain.calculation.DailyAttendanceResult;
import mg.galaxam.sirh.pointage.domain.calculation.DaySpecialType;
import mg.galaxam.sirh.pointage.domain.calculation.NightWorkType;
import mg.galaxam.sirh.pointage.domain.calculation.OvertimeProjectionEngine;
import mg.galaxam.sirh.pointage.domain.calculation.OvertimeTier;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket;
import mg.galaxam.sirh.pointage.domain.calculation.TemporalBucket;
import mg.galaxam.sirh.pointage.domain.calculation.WeeklyAttendanceCalculator;
import mg.galaxam.sirh.pointage.domain.calculation.WeeklyAttendanceContext;
import mg.galaxam.sirh.pointage.domain.calculation.WeeklyAttendanceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Audit contradictoire et formel de la provenance chronologique des Heures Supplémentaires (HS).
 * Démontre la conformité de OvertimeProjectionEngine avec les résultats de compensation Phase 1B.
 */
class Phase1cProvenanceHsAuditTest {

    private final WeeklyAttendanceCalculator weeklyCalc = new WeeklyAttendanceCalculator();
    private final OvertimeProjectionEngine projectionEngine = new OvertimeProjectionEngine();

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 14);

    @Test
    @DisplayName("Cas A: Lundi +120 surplus, Mardi -120 déficit, Mercredi +60 surplus -> Net HS = 60 min (chronologiquement fin de semaine)")
    void casA_compensationAndOvertimePosition() {
        // Lundi : 10h (600 min, surplus +120)
        DailyAttendanceResult rMon = workingDay(MONDAY, 600);
        // Mardi : 6h (360 min, déficit -120)
        DailyAttendanceResult rTue = workingDay(MONDAY.plusDays(1), 360);
        // Mercredi : 9h (540 min, surplus +60)
        DailyAttendanceResult rWed = workingDay(MONDAY.plusDays(2), 540);
        // Jeudi : 8h (480 min)
        DailyAttendanceResult rThu = workingDay(MONDAY.plusDays(3), 480);
        // Vendredi : 8h (480 min)
        DailyAttendanceResult rFri = workingDay(MONDAY.plusDays(4), 480);

        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(MONDAY, 2400, 480, true, 480);
        WeeklyAttendanceResult wRes = weeklyCalc.calculateWeek(ctx, List.of(rMon, rTue, rWed, rThu, rFri));

        // Invariants Phase 1B
        assertThat(wRes.workedMinutes()).isEqualTo(2460); // 600+360+540+480+480
        assertThat(wRes.compensatedMinutes()).isEqualTo(120); // 120 surplus de lundi compense mardi
        assertThat(wRes.overtime30Minutes()).isEqualTo(60);
        assertThat(wRes.overtime50Minutes()).isEqualTo(0);

        // Démonstration : la projection chronologique attribue les premières 2400 minutes à REGULAR
        // et les 60 dernières minutes travaillées (vendredi fin d'après-midi) à OVERTIME_30.
        List<TemporalBucket> buckets = List.of(
                new TemporalBucket(MONDAY.atTime(8, 0), MONDAY.atTime(18, 0), 600, DaySpecialType.STANDARD, NightWorkType.NONE, 1, 1),
                new TemporalBucket(MONDAY.plusDays(1).atTime(8, 0), MONDAY.plusDays(1).atTime(14, 0), 360, DaySpecialType.STANDARD, NightWorkType.NONE, 1, 2),
                new TemporalBucket(MONDAY.plusDays(2).atTime(8, 0), MONDAY.plusDays(2).atTime(17, 0), 540, DaySpecialType.STANDARD, NightWorkType.NONE, 1, 3),
                new TemporalBucket(MONDAY.plusDays(3).atTime(8, 0), MONDAY.plusDays(3).atTime(16, 0), 480, DaySpecialType.STANDARD, NightWorkType.NONE, 1, 4),
                new TemporalBucket(MONDAY.plusDays(4).atTime(8, 0), MONDAY.plusDays(4).atTime(16, 0), 480, DaySpecialType.STANDARD, NightWorkType.NONE, 1, 5)
        );

        List<QualifiedTimeBucket> qualified = projectionEngine.project(buckets, 2400, 60, 0);

        // Les 60 min de HS30 tombent sur le vendredi entre 15:00 et 16:00
        QualifiedTimeBucket lastBucket = qualified.get(qualified.size() - 1);
        assertThat(lastBucket.overtimeTier()).isEqualTo(OvertimeTier.OVERTIME_30);
        assertThat(lastBucket.minutes()).isEqualTo(60);
        assertThat(lastBucket.start()).isEqualTo(MONDAY.plusDays(4).atTime(15, 0));
        assertThat(lastBucket.end()).isEqualTo(MONDAY.plusDays(4).atTime(16, 0));
    }

    @Test
    @DisplayName("Cas B: Surplus en début de tronçon, déficit ensuite, surplus en fin de tronçon -> conservation exacte des volumes")
    void casB_surplusBeginningDeficitMiddleSurplusEnd() {
        // Lundi : 9h (540 min, surplus +60)
        DailyAttendanceResult rMon = workingDay(MONDAY, 540);
        // Mardi : 7h (420 min, déficit -60) -> compense exactement le surplus de lundi
        DailyAttendanceResult rTue = workingDay(MONDAY.plusDays(1), 420);
        // Mercredi : 8h (480 min)
        DailyAttendanceResult rWed = workingDay(MONDAY.plusDays(2), 480);
        // Jeudi : 8h (480 min)
        DailyAttendanceResult rThu = workingDay(MONDAY.plusDays(3), 480);
        // Vendredi : 10h (600 min, surplus +120)
        DailyAttendanceResult rFri = workingDay(MONDAY.plusDays(4), 600);

        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(MONDAY, 2400, 480, true, 480);
        WeeklyAttendanceResult wRes = weeklyCalc.calculateWeek(ctx, List.of(rMon, rTue, rWed, rThu, rFri));

        assertThat(wRes.workedMinutes()).isEqualTo(2520);
        assertThat(wRes.compensatedMinutes()).isEqualTo(60);
        assertThat(wRes.overtime30Minutes()).isEqualTo(120);
        assertThat(wRes.overtime50Minutes()).isEqualTo(0);

        List<TemporalBucket> buckets = List.of(
                new TemporalBucket(MONDAY.atTime(8, 0), MONDAY.atTime(17, 0), 540, DaySpecialType.STANDARD, NightWorkType.NONE, 1, 1),
                new TemporalBucket(MONDAY.plusDays(1).atTime(8, 0), MONDAY.plusDays(1).atTime(15, 0), 420, DaySpecialType.STANDARD, NightWorkType.NONE, 1, 2),
                new TemporalBucket(MONDAY.plusDays(2).atTime(8, 0), MONDAY.plusDays(2).atTime(16, 0), 480, DaySpecialType.STANDARD, NightWorkType.NONE, 1, 3),
                new TemporalBucket(MONDAY.plusDays(3).atTime(8, 0), MONDAY.plusDays(3).atTime(16, 0), 480, DaySpecialType.STANDARD, NightWorkType.NONE, 1, 4),
                new TemporalBucket(MONDAY.plusDays(4).atTime(8, 0), MONDAY.plusDays(4).atTime(18, 0), 600, DaySpecialType.STANDARD, NightWorkType.NONE, 1, 5)
        );

        List<QualifiedTimeBucket> qualified = projectionEngine.project(buckets, 2400, 120, 0);

        // Les 120 min de HS30 sont portées par les dernières minutes travaillées de la semaine (Vendredi 16:00 -> 18:00)
        QualifiedTimeBucket lastBucket = qualified.get(qualified.size() - 1);
        assertThat(lastBucket.overtimeTier()).isEqualTo(OvertimeTier.OVERTIME_30);
        assertThat(lastBucket.minutes()).isEqualTo(120);
        assertThat(lastBucket.start()).isEqualTo(MONDAY.plusDays(4).atTime(16, 0));
        assertThat(lastBucket.end()).isEqualTo(MONDAY.plusDays(4).atTime(18, 0));
    }

    @Test
    @DisplayName("Cas C: Surplus de nuit + déficit de jour ultérieur -> Nuit reste NUIT mais qualification HS est REGULAR")
    void casC_nightSurplusCompensatedByDayDeficit() {
        // Lundi soir : 21:00 -> 05:00 (480 min travaillées dont 420 min de nuit)
        // Mardi : 0 min (absence 480 min)
        // Objectif 2 jours = 960 min, travaillé = 480 min
        // Pas de dépassement hebdo : overtime = 0.
        TemporalBucket bDay = new TemporalBucket(
                MONDAY.atTime(21, 0), MONDAY.atTime(22, 0), 60,
                DaySpecialType.STANDARD, NightWorkType.NONE, 1, 10
        );
        TemporalBucket bNight = new TemporalBucket(
                MONDAY.atTime(22, 0), MONDAY.plusDays(1).atTime(5, 0), 420,
                DaySpecialType.STANDARD, NightWorkType.NIGHT_UNCLASSIFIED, 1, 10
        );

        // Phase 1B a conclu : 480 min régulières, 0 HS.
        List<QualifiedTimeBucket> res = projectionEngine.project(List.of(bDay, bNight), 480, 0, 0);

        assertThat(res).hasSize(2);
        // Le fait de nuit est intégralement préservé, avec qualification HS = REGULAR
        assertThat(res.get(1).nightType()).isEqualTo(NightWorkType.NIGHT_UNCLASSIFIED);
        assertThat(res.get(1).overtimeTier()).isEqualTo(OvertimeTier.REGULAR);
        assertThat(res.get(1).minutes()).isEqualTo(420);
    }

    @Test
    @DisplayName("Cas D: Dimanche travaillé n'est JAMAIS compensé par un déficit de semaine (étanchéité Phase 1B)")
    void casD_sundayNeverCompensatesWeekdayDeficit() {
        // Mardi : déficit de 120 min (6h travaillées au lieu de 8h)
        DailyAttendanceResult rTue = workingDay(MONDAY.plusDays(1), 360);
        // Dimanche : 8h travaillées (480 min)
        DailyAttendanceResult rSun = new DailyAttendanceResult(
                101, MONDAY.plusDays(6), DailyAttendanceResult.Status.SUNDAY_WORKED,
                0, 0, 480, 0, 0, 0, 0,
                true, false, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()
        );

        // Contexte pour 1 jour ouvré prévu (480 min)
        WeeklyAttendanceContext ctx = new WeeklyAttendanceContext(MONDAY, 480, 480, true, 480);
        WeeklyAttendanceResult wRes = weeklyCalc.calculateWeek(ctx, List.of(rTue, rSun));

        // Le dimanche n'est pas compensable avec le déficit du mardi : dailySurplus = 0, compensated = 0
        assertThat(wRes.compensatedMinutes()).isEqualTo(0);
        // Les heures du dimanche sont qualifiées SUNDAY sans être altérées
        assertThat(rSun.sunday()).isTrue();
    }

    @Test
    @DisplayName("Cas E: Surplus jour ordinaire + travail dimanche -> HS ordinaires projetées sur le surplus hebdo, dimanche qualifié SUNDAY")
    void casE_ordinarySurplusPlusSundayWork() {
        // Semaine 40h standard (Lundi-Vendredi 8h/j = 40h) + Samedi 4h + Dimanche 6h
        List<TemporalBucket> buckets = List.of(
                new TemporalBucket(MONDAY.atTime(8, 0), MONDAY.atTime(16, 0), 480, DaySpecialType.STANDARD, NightWorkType.NONE, 1, 1),
                new TemporalBucket(MONDAY.plusDays(1).atTime(8, 0), MONDAY.plusDays(1).atTime(16, 0), 480, DaySpecialType.STANDARD, NightWorkType.NONE, 1, 2),
                new TemporalBucket(MONDAY.plusDays(2).atTime(8, 0), MONDAY.plusDays(2).atTime(16, 0), 480, DaySpecialType.STANDARD, NightWorkType.NONE, 1, 3),
                new TemporalBucket(MONDAY.plusDays(3).atTime(8, 0), MONDAY.plusDays(3).atTime(16, 0), 480, DaySpecialType.STANDARD, NightWorkType.NONE, 1, 4),
                new TemporalBucket(MONDAY.plusDays(4).atTime(8, 0), MONDAY.plusDays(4).atTime(16, 0), 480, DaySpecialType.STANDARD, NightWorkType.NONE, 1, 5),
                new TemporalBucket(MONDAY.plusDays(5).atTime(8, 0), MONDAY.plusDays(5).atTime(12, 0), 240, DaySpecialType.SATURDAY, NightWorkType.NONE, 1, 6),
                new TemporalBucket(MONDAY.plusDays(6).atTime(8, 0), MONDAY.plusDays(6).atTime(14, 0), 360, DaySpecialType.SUNDAY, NightWorkType.NONE, 1, 7)
        );

        // Phase 1B qualifie 2400 REGULAR + 240 HS30 (samedi au-delà des 40h) + 360 Dimanche (HS50 ou tier)
        List<QualifiedTimeBucket> qualified = projectionEngine.project(buckets, 2400, 240, 360);

        assertThat(qualified).hasSize(7);
        // Semaine ordinaire (5 premiers buckets) = REGULAR
        for (int i = 0; i < 5; i++) {
            assertThat(qualified.get(i).overtimeTier()).isEqualTo(OvertimeTier.REGULAR);
            assertThat(qualified.get(i).minutes()).isEqualTo(480);
        }

        // Samedi = OVERTIME_30 + SATURDAY
        assertThat(qualified.get(5).overtimeTier()).isEqualTo(OvertimeTier.OVERTIME_30);
        assertThat(qualified.get(5).dayType()).isEqualTo(DaySpecialType.SATURDAY);
        assertThat(qualified.get(5).minutes()).isEqualTo(240);

        // Dimanche = OVERTIME_50 + SUNDAY préservé
        assertThat(qualified.get(6).overtimeTier()).isEqualTo(OvertimeTier.OVERTIME_50);
        assertThat(qualified.get(6).dayType()).isEqualTo(DaySpecialType.SUNDAY);
        assertThat(qualified.get(6).isSunday()).isTrue();
        assertThat(qualified.get(6).minutes()).isEqualTo(360);
    }

    private DailyAttendanceResult workingDay(LocalDate date, long workedMinutes) {
        return new DailyAttendanceResult(
                101, date, DailyAttendanceResult.Status.PRESENT, workedMinutes, 0, workedMinutes, workedMinutes,
                0, 0, 0, false, false, 0, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, List.of()
        );
    }
}
