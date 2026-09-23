package mg.galaxam.sirh.unit.pointage;

import mg.galaxam.sirh.pointage.domain.calculation.DaySpecialType;
import mg.galaxam.sirh.pointage.domain.calculation.NightWorkType;
import mg.galaxam.sirh.pointage.domain.calculation.OvertimeProjectionEngine;
import mg.galaxam.sirh.pointage.domain.calculation.OvertimeTier;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket;
import mg.galaxam.sirh.pointage.domain.calculation.TemporalBucket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OvertimeProjectionEngineTest {

    private final OvertimeProjectionEngine engine = new OvertimeProjectionEngine();

    @Test
    @DisplayName("Section 43: Test anti-régression fondamental : Objectif réduit (R=1920, HS30=180, Total=2100 < 2400) -> AUCUN recalcul 40h")
    void section43_reducedObjectiveAntiRegressionNo40hThreshold() {
        // Semaine avec jour férié chômé ou temps partiel : objectif réel = 1920 min (32h).
        // Travaillé = 2100 min (35h).
        // Phase 1B a décidé : 1920 min REGULAR, 180 min HS30, 0 min HS50.
        // Interdiction absolue de recalculer par rapport à 40h (2400 min) !
        LocalDateTime start = LocalDateTime.of(2026, 9, 14, 8, 0);
        TemporalBucket bucket = new TemporalBucket(
                start, start.plusMinutes(2100), 2100,
                DaySpecialType.STANDARD, NightWorkType.NONE, 1, 10
        );

        List<QualifiedTimeBucket> result = engine.project(List.of(bucket), 1920, 180, 0);

        assertThat(result).hasSize(2);

        QualifiedTimeBucket r1 = result.get(0);
        assertThat(r1.overtimeTier()).isEqualTo(OvertimeTier.REGULAR);
        assertThat(r1.minutes()).isEqualTo(1920);

        QualifiedTimeBucket r2 = result.get(1);
        assertThat(r2.overtimeTier()).isEqualTo(OvertimeTier.OVERTIME_30);
        assertThat(r2.minutes()).isEqualTo(180);

        long regularSum = result.stream().filter(b -> b.overtimeTier() == OvertimeTier.REGULAR).mapToLong(QualifiedTimeBucket::minutes).sum();
        long hs30Sum = result.stream().filter(b -> b.overtimeTier() == OvertimeTier.OVERTIME_30).mapToLong(QualifiedTimeBucket::minutes).sum();
        long hs50Sum = result.stream().filter(b -> b.overtimeTier() == OvertimeTier.OVERTIME_50).mapToLong(QualifiedTimeBucket::minutes).sum();

        assertThat(regularSum).isEqualTo(1920);
        assertThat(hs30Sum).isEqualTo(180);
        assertThat(hs50Sum).isEqualTo(0);
    }

    @Test
    @DisplayName("Section 44: Transition REGULAR -> HS30 pendant la nuit (22:00 -> 00:00 avec scission à 90 min)")
    void section44_splitDuringNightShift() {
        // TemporalBucket de nuit : 22:00 -> 00:00 (120 min)
        // Quotas restants pour ce tronçon : 90 min REGULAR, 30 min HS30.
        LocalDateTime t22 = LocalDateTime.of(2026, 9, 17, 22, 0);
        LocalDateTime t00 = LocalDateTime.of(2026, 9, 18, 0, 0);
        TemporalBucket nightBucket = new TemporalBucket(
                t22, t00, 120,
                DaySpecialType.STANDARD, NightWorkType.NIGHT_UNCLASSIFIED, 1, 10
        );

        List<QualifiedTimeBucket> result = engine.project(List.of(nightBucket), 90, 30, 0);

        assertThat(result).hasSize(2);

        // 1er sous-segment : 22:00 -> 23:30 (90 min REGULAR + NIGHT)
        QualifiedTimeBucket q1 = result.get(0);
        assertThat(q1.start()).isEqualTo(LocalDateTime.of(2026, 9, 17, 22, 0));
        assertThat(q1.end()).isEqualTo(LocalDateTime.of(2026, 9, 17, 23, 30));
        assertThat(q1.minutes()).isEqualTo(90);
        assertThat(q1.overtimeTier()).isEqualTo(OvertimeTier.REGULAR);
        assertThat(q1.nightType()).isEqualTo(NightWorkType.NIGHT_UNCLASSIFIED);

        // 2e sous-segment : 23:30 -> 00:00 (30 min OVERTIME_30 + NIGHT)
        QualifiedTimeBucket q2 = result.get(1);
        assertThat(q2.start()).isEqualTo(LocalDateTime.of(2026, 9, 17, 23, 30));
        assertThat(q2.end()).isEqualTo(LocalDateTime.of(2026, 9, 18, 0, 0));
        assertThat(q2.minutes()).isEqualTo(30);
        assertThat(q2.overtimeTier()).isEqualTo(OvertimeTier.OVERTIME_30);
        assertThat(q2.nightType()).isEqualTo(NightWorkType.NIGHT_UNCLASSIFIED);

        assertThat(result.stream().mapToLong(QualifiedTimeBucket::minutes).sum()).isEqualTo(120);
    }

    @Test
    @DisplayName("Section 45: Transition HS30 -> HS50 (scission exacte lors du franchissement de palier)")
    void section45_splitHs30ToHs50() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 18, 14, 0);
        TemporalBucket bucket = new TemporalBucket(
                start, start.plusMinutes(120), 120,
                DaySpecialType.STANDARD, NightWorkType.NONE, 1, 10
        );

        // 0 min REGULAR restantes, 40 min HS30, 80 min HS50
        List<QualifiedTimeBucket> result = engine.project(List.of(bucket), 0, 40, 80);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).overtimeTier()).isEqualTo(OvertimeTier.OVERTIME_30);
        assertThat(result.get(0).minutes()).isEqualTo(40);

        assertThat(result.get(1).overtimeTier()).isEqualTo(OvertimeTier.OVERTIME_50);
        assertThat(result.get(1).minutes()).isEqualTo(80);
    }

    @Test
    @DisplayName("Section 47: Préservation complète des intersections orthogonales (HS x Calendrier x Nuit)")
    void section47_orthogonalIntersectionsPreserved() {
        // Dimanche de nuit : 2026-09-20 22:00 -> 2026-09-21 00:00 (SUNDAY + NIGHT)
        LocalDateTime tSunNight = LocalDateTime.of(2026, 9, 20, 22, 0);
        TemporalBucket sunNightBucket = new TemporalBucket(
                tSunNight, tSunNight.plusMinutes(120), 120,
                DaySpecialType.SUNDAY, NightWorkType.NIGHT_UNCLASSIFIED, 1, 10
        );

        List<QualifiedTimeBucket> result = engine.project(List.of(sunNightBucket), 60, 60, 0);

        assertThat(result).hasSize(2);

        // Intersection 1: REGULAR + SUNDAY + NIGHT
        QualifiedTimeBucket b1 = result.get(0);
        assertThat(b1.overtimeTier()).isEqualTo(OvertimeTier.REGULAR);
        assertThat(b1.dayType()).isEqualTo(DaySpecialType.SUNDAY);
        assertThat(b1.nightType()).isEqualTo(NightWorkType.NIGHT_UNCLASSIFIED);
        assertThat(b1.minutes()).isEqualTo(60);

        // Intersection 2: OVERTIME_30 + SUNDAY + NIGHT
        QualifiedTimeBucket b2 = result.get(1);
        assertThat(b2.overtimeTier()).isEqualTo(OvertimeTier.OVERTIME_30);
        assertThat(b2.dayType()).isEqualTo(DaySpecialType.SUNDAY);
        assertThat(b2.nightType()).isEqualTo(NightWorkType.NIGHT_UNCLASSIFIED);
        assertThat(b2.minutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("Section 48: Tests d'erreur stricts (timeline != quotas 1B, quotas négatifs)")
    void section48_errorCasesFailExplicitly() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 14, 8, 0);
        TemporalBucket bucket = new TemporalBucket(
                start, start.plusMinutes(480), 480,
                DaySpecialType.STANDARD, NightWorkType.NONE, 1, 10
        );

        // Timeline (480) > Quotas (420)
        assertThatThrownBy(() -> engine.project(List.of(bucket), 420, 0, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONSERVATION_VIOLATION_PROJECTION");

        // Timeline (480) < Quotas (500)
        assertThatThrownBy(() -> engine.project(List.of(bucket), 500, 0, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONSERVATION_VIOLATION_PROJECTION");

        // Quotas négatifs
        assertThatThrownBy(() -> engine.project(List.of(bucket), -10, 490, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Les quotas Phase 1B ne peuvent pas être négatifs");
    }
}
