package mg.galaxam.sirh.pointage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import mg.galaxam.sirh.pointage.domain.calculation.OvertimeTier;
import mg.galaxam.sirh.pointage.domain.calculation.NightWorkType;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket;
import mg.galaxam.sirh.pointage.domain.calculation.TemporalAttendanceEngine.AttendanceTemporalPartitioner;
import mg.galaxam.sirh.pointage.domain.calculation.TemporalAttendanceEngine.OvertimeProjectionEngine;
import mg.galaxam.sirh.pointage.domain.calculation.TemporalAttendanceEngine.WorkIntervalFactory;
import org.junit.jupiter.api.Test;

class TemporalAttendanceEngineTest {

    @Test
    void vacationTraversantMinuitConserveMinutesEtQualifications() {
        LocalDate saturday = LocalDate.of(2026, 9, 5);
        var intervals = WorkIntervalFactory.createIntervals(1, 10, saturday,
                LocalTime.of(21, 0), null, null, LocalTime.of(2, 0), true, "TEST");
        assertEquals(1, intervals.size());
        assertEquals(300, intervals.get(0).durationMinutes());

        var partitioner = new AttendanceTemporalPartitioner(date -> false,
                date -> date.equals(saturday) ? 1 : 2);
        var buckets = partitioner.partition(intervals);
        assertEquals(List.of(60L, 120L, 120L), buckets.stream().map(b -> b.minutes()).toList());
        assertTrue(buckets.get(1).isNight());
        assertEquals(NightWorkType.OCCASIONNEL, buckets.get(1).nightType());
        assertEquals(NightWorkType.OCCASIONNEL, buckets.get(2).nightType());
        assertTrue(buckets.get(2).isSunday());
        assertEquals(1, buckets.get(0).payrollPeriodId());
        assertEquals(2, buckets.get(2).payrollPeriodId());

        List<QualifiedTimeBucket> projected = new OvertimeProjectionEngine().project(buckets, 180, 120, 0);
        assertEquals(300, projected.stream().mapToLong(QualifiedTimeBucket::minutes).sum());
        assertEquals(120, projected.stream().filter(b -> b.overtimeTier() == OvertimeTier.OVERTIME_30)
                .mapToLong(QualifiedTimeBucket::minutes).sum());
        assertTrue(projected.get(projected.size() - 1).isSunday());
    }

    @Test
    void badgeIncompletNeCreePasDIntervalle() {
        assertTrue(WorkIntervalFactory.createIntervals(1, 10, LocalDate.of(2026, 9, 5),
                LocalTime.of(8, 0), null, null, null, false, "TEST").isEmpty());
    }
}
