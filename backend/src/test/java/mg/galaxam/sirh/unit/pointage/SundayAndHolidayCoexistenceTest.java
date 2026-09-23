package mg.galaxam.sirh.unit.pointage;

import mg.galaxam.sirh.pointage.domain.calculation.AttendanceTemporalPartitioner;
import mg.galaxam.sirh.pointage.domain.calculation.DaySpecialType;
import mg.galaxam.sirh.pointage.domain.calculation.NightWorkType;
import mg.galaxam.sirh.pointage.domain.calculation.OvertimeProjectionEngine;
import mg.galaxam.sirh.pointage.domain.calculation.OvertimeTier;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket;
import mg.galaxam.sirh.pointage.domain.calculation.TemporalBucket;
import mg.galaxam.sirh.pointage.domain.calculation.WorkInterval;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SundayAndHolidayCoexistenceTest {

    @Test
    @DisplayName("Section 17: Test obligatoire Dimanche + Férié + Nuit + OvertimeTier (aucune qualification supprimée)")
    void section17_sundayAndHolidayCoexistencePreserved() {
        // 2026-11-01 est un DIMANCHE et c'est la TOUSSAINT (PUBLIC_HOLIDAY)
        LocalDate toussaint = LocalDate.of(2026, 11, 1);
        assertThat(toussaint.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.SUNDAY);

        AttendanceTemporalPartitioner partitioner = new AttendanceTemporalPartitioner(
                date -> date.equals(toussaint),
                date -> 100
        );

        // Vacation 22:00 -> 23:00 le dimanche 1er novembre (nuit + dimanche + férié)
        WorkInterval interval = new WorkInterval(
                LocalDateTime.of(2026, 11, 1, 22, 0),
                LocalDateTime.of(2026, 11, 1, 23, 0),
                101, 1, "TOUSSAINT_NIGHT"
        );

        List<TemporalBucket> buckets = partitioner.partitionSingleInterval(interval);

        assertThat(buckets).hasSize(1);
        TemporalBucket tb = buckets.get(0);

        // Vérification des faits temporels objectifs
        assertThat(tb.minutes()).isEqualTo(60);
        assertThat(tb.dayType()).isEqualTo(DaySpecialType.SUNDAY_AND_PUBLIC_HOLIDAY);
        assertThat(tb.isSunday()).isTrue();
        assertThat(tb.isPublicHoliday()).isTrue();
        assertThat(tb.isNight()).isTrue();
        assertThat(tb.nightType()).isEqualTo(NightWorkType.NIGHT_UNCLASSIFIED);
        assertThat(tb.payrollPeriodId()).isEqualTo(100);

        // Projection avec OVERTIME_30 (Phase 1B)
        OvertimeProjectionEngine projectionEngine = new OvertimeProjectionEngine();
        List<QualifiedTimeBucket> qualified = projectionEngine.project(buckets, 0, 60, 0);

        assertThat(qualified).hasSize(1);
        QualifiedTimeBucket qb = qualified.get(0);

        // RÉSULTAT OBLIGATOIRE : OVERTIME_30 + SUNDAY + PUBLIC_HOLIDAY + NIGHT
        assertThat(qb.overtimeTier()).isEqualTo(OvertimeTier.OVERTIME_30);
        assertThat(qb.dayType()).isEqualTo(DaySpecialType.SUNDAY_AND_PUBLIC_HOLIDAY);
        assertThat(qb.isSunday()).isTrue();
        assertThat(qb.isPublicHoliday()).isTrue();
        assertThat(qb.isNight()).isTrue();
        assertThat(qb.nightType()).isEqualTo(NightWorkType.NIGHT_UNCLASSIFIED);
        assertThat(qb.minutes()).isEqualTo(60);

        // Zéro taux ou valorisation financière appliquée ici (réservé à Phase 1C-B)
    }
}
