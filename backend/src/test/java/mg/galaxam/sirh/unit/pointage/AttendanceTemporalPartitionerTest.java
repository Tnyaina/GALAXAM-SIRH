package mg.galaxam.sirh.unit.pointage;

import mg.galaxam.sirh.model.pointage.calculation.AttendanceTemporalPartitioner;
import mg.galaxam.sirh.model.pointage.calculation.DaySpecialType;
import mg.galaxam.sirh.model.pointage.calculation.NightWorkType;
import mg.galaxam.sirh.model.pointage.calculation.TemporalBucket;
import mg.galaxam.sirh.model.pointage.calculation.WorkInterval;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceTemporalPartitionerTest {

    private final AttendanceTemporalPartitioner standardPartitioner = new AttendanceTemporalPartitioner(
            date -> false,
            date -> 10
    );

    @Test
    @DisplayName("T01: 09:00 -> 17:00 (Journée normale diurne, STANDARD)")
    void t01_daytimeSchedule() {
        WorkInterval interval = new WorkInterval(
                LocalDateTime.of(2026, 9, 17, 9, 0),
                LocalDateTime.of(2026, 9, 17, 17, 0),
                101, 1, "T01"
        );

        List<TemporalBucket> buckets = standardPartitioner.partitionSingleInterval(interval);

        assertThat(buckets).hasSize(1);
        TemporalBucket b = buckets.get(0);
        assertThat(b.minutes()).isEqualTo(480);
        assertThat(b.nightType()).isEqualTo(NightWorkType.NONE);
        assertThat(b.dayType()).isEqualTo(DaySpecialType.STANDARD);
        assertThat(b.payrollPeriodId()).isEqualTo(10);
    }

    @Test
    @DisplayName("T02: 21:00 -> 23:00 (Traversée de la frontière 22:00 : DAY -> NIGHT)")
    void t02_nightStartBoundary() {
        WorkInterval interval = new WorkInterval(
                LocalDateTime.of(2026, 9, 17, 21, 0),
                LocalDateTime.of(2026, 9, 17, 23, 0),
                101, 1, "T02"
        );

        List<TemporalBucket> buckets = standardPartitioner.partitionSingleInterval(interval);

        assertThat(buckets).hasSize(2);

        TemporalBucket b1 = buckets.get(0);
        assertThat(b1.start()).isEqualTo(LocalDateTime.of(2026, 9, 17, 21, 0));
        assertThat(b1.end()).isEqualTo(LocalDateTime.of(2026, 9, 17, 22, 0));
        assertThat(b1.minutes()).isEqualTo(60);
        assertThat(b1.nightType()).isEqualTo(NightWorkType.NONE);

        TemporalBucket b2 = buckets.get(1);
        assertThat(b2.start()).isEqualTo(LocalDateTime.of(2026, 9, 17, 22, 0));
        assertThat(b2.end()).isEqualTo(LocalDateTime.of(2026, 9, 17, 23, 0));
        assertThat(b2.minutes()).isEqualTo(60);
        assertThat(b2.nightType()).isEqualTo(NightWorkType.NIGHT_UNCLASSIFIED);

        assertThat(buckets.stream().mapToLong(TemporalBucket::minutes).sum()).isEqualTo(120);
    }

    @Test
    @DisplayName("T03: 23:00 -> 02:00 (Traversée de minuit en pleine nuit)")
    void t03_midnightBoundaryDuringNight() {
        WorkInterval interval = new WorkInterval(
                LocalDateTime.of(2026, 9, 17, 23, 0),
                LocalDateTime.of(2026, 9, 18, 2, 0),
                101, 1, "T03"
        );

        List<TemporalBucket> buckets = standardPartitioner.partitionSingleInterval(interval);

        assertThat(buckets).hasSize(2);

        TemporalBucket b1 = buckets.get(0);
        assertThat(b1.start()).isEqualTo(LocalDateTime.of(2026, 9, 17, 23, 0));
        assertThat(b1.end()).isEqualTo(LocalDateTime.of(2026, 9, 18, 0, 0));
        assertThat(b1.minutes()).isEqualTo(60);
        assertThat(b1.nightType()).isEqualTo(NightWorkType.NIGHT_UNCLASSIFIED);

        TemporalBucket b2 = buckets.get(1);
        assertThat(b2.start()).isEqualTo(LocalDateTime.of(2026, 9, 18, 0, 0));
        assertThat(b2.end()).isEqualTo(LocalDateTime.of(2026, 9, 18, 2, 0));
        assertThat(b2.minutes()).isEqualTo(120);
        assertThat(b2.nightType()).isEqualTo(NightWorkType.NIGHT_UNCLASSIFIED);

        assertThat(buckets.stream().mapToLong(TemporalBucket::minutes).sum()).isEqualTo(180);
    }

    @Test
    @DisplayName("T04: 04:00 -> 07:00 (Traversée de la frontière 05:00 : NIGHT -> DAY)")
    void t04_nightEndBoundary() {
        WorkInterval interval = new WorkInterval(
                LocalDateTime.of(2026, 9, 18, 4, 0),
                LocalDateTime.of(2026, 9, 18, 7, 0),
                101, 1, "T04"
        );

        List<TemporalBucket> buckets = standardPartitioner.partitionSingleInterval(interval);

        assertThat(buckets).hasSize(2);

        TemporalBucket b1 = buckets.get(0);
        assertThat(b1.start()).isEqualTo(LocalDateTime.of(2026, 9, 18, 4, 0));
        assertThat(b1.end()).isEqualTo(LocalDateTime.of(2026, 9, 18, 5, 0));
        assertThat(b1.minutes()).isEqualTo(60);
        assertThat(b1.nightType()).isEqualTo(NightWorkType.NIGHT_UNCLASSIFIED);

        TemporalBucket b2 = buckets.get(1);
        assertThat(b2.start()).isEqualTo(LocalDateTime.of(2026, 9, 18, 5, 0));
        assertThat(b2.end()).isEqualTo(LocalDateTime.of(2026, 9, 18, 7, 0));
        assertThat(b2.minutes()).isEqualTo(120);
        assertThat(b2.nightType()).isEqualTo(NightWorkType.NONE);

        assertThat(buckets.stream().mapToLong(TemporalBucket::minutes).sum()).isEqualTo(180);
    }

    @Test
    @DisplayName("T05: 21:00 -> 05:00 (Traversée complète 22:00 et 00:00 jusqu'à 05:00)")
    void t05_fullNightShift() {
        WorkInterval interval = new WorkInterval(
                LocalDateTime.of(2026, 9, 17, 21, 0),
                LocalDateTime.of(2026, 9, 18, 5, 0),
                101, 1, "T05"
        );

        List<TemporalBucket> buckets = standardPartitioner.partitionSingleInterval(interval);

        assertThat(buckets).hasSize(3);

        // 21h -> 22h DAY
        assertThat(buckets.get(0).minutes()).isEqualTo(60);
        assertThat(buckets.get(0).nightType()).isEqualTo(NightWorkType.NONE);

        // 22h -> 00h NIGHT
        assertThat(buckets.get(1).minutes()).isEqualTo(120);
        assertThat(buckets.get(1).nightType()).isEqualTo(NightWorkType.NIGHT_UNCLASSIFIED);

        // 00h -> 05h NIGHT
        assertThat(buckets.get(2).minutes()).isEqualTo(300);
        assertThat(buckets.get(2).nightType()).isEqualTo(NightWorkType.NIGHT_UNCLASSIFIED);

        assertThat(buckets.stream().mapToLong(TemporalBucket::minutes).sum()).isEqualTo(480);
    }

    @Test
    @DisplayName("T06: Samedi 21:00 -> Dimanche 05:00 (Section 17 : Sam 21h-22h SAT/DAY, Sam 22h-00h SAT/NIGHT, Dim 00h-05h SUN/NIGHT)")
    void t06_saturdayToSundayTransition() {
        // 2026-09-19 est un samedi, 2026-09-20 est un dimanche
        WorkInterval interval = new WorkInterval(
                LocalDateTime.of(2026, 9, 19, 21, 0),
                LocalDateTime.of(2026, 9, 20, 5, 0),
                101, 1, "T06"
        );

        List<TemporalBucket> buckets = standardPartitioner.partitionSingleInterval(interval);

        assertThat(buckets).hasSize(3);

        TemporalBucket b1 = buckets.get(0);
        assertThat(b1.start()).isEqualTo(LocalDateTime.of(2026, 9, 19, 21, 0));
        assertThat(b1.end()).isEqualTo(LocalDateTime.of(2026, 9, 19, 22, 0));
        assertThat(b1.dayType()).isEqualTo(DaySpecialType.SATURDAY);
        assertThat(b1.nightType()).isEqualTo(NightWorkType.NONE);
        assertThat(b1.minutes()).isEqualTo(60);

        TemporalBucket b2 = buckets.get(1);
        assertThat(b2.start()).isEqualTo(LocalDateTime.of(2026, 9, 19, 22, 0));
        assertThat(b2.end()).isEqualTo(LocalDateTime.of(2026, 9, 20, 0, 0));
        assertThat(b2.dayType()).isEqualTo(DaySpecialType.SATURDAY);
        assertThat(b2.nightType()).isEqualTo(NightWorkType.NIGHT_UNCLASSIFIED);
        assertThat(b2.minutes()).isEqualTo(120);

        TemporalBucket b3 = buckets.get(2);
        assertThat(b3.start()).isEqualTo(LocalDateTime.of(2026, 9, 20, 0, 0));
        assertThat(b3.end()).isEqualTo(LocalDateTime.of(2026, 9, 20, 5, 0));
        assertThat(b3.dayType()).isEqualTo(DaySpecialType.SUNDAY);
        assertThat(b3.nightType()).isEqualTo(NightWorkType.NIGHT_UNCLASSIFIED);
        assertThat(b3.minutes()).isEqualTo(300);

        assertThat(buckets.stream().mapToLong(TemporalBucket::minutes).sum()).isEqualTo(480);
    }

    @Test
    @DisplayName("T07: Dimanche 21:00 -> Lundi 05:00 (Section 18 : Dim 21h-22h SUN/DAY, Dim 22h-00h SUN/NIGHT, Lun 00h-05h STD/NIGHT - Zéro contamination)")
    void t07_sundayToMondayTransition() {
        // 2026-09-20 est un dimanche, 2026-09-21 est un lundi
        WorkInterval interval = new WorkInterval(
                LocalDateTime.of(2026, 9, 20, 21, 0),
                LocalDateTime.of(2026, 9, 21, 5, 0),
                101, 1, "T07"
        );

        List<TemporalBucket> buckets = standardPartitioner.partitionSingleInterval(interval);

        assertThat(buckets).hasSize(3);

        TemporalBucket b1 = buckets.get(0);
        assertThat(b1.dayType()).isEqualTo(DaySpecialType.SUNDAY);
        assertThat(b1.nightType()).isEqualTo(NightWorkType.NONE);
        assertThat(b1.minutes()).isEqualTo(60);

        TemporalBucket b2 = buckets.get(1);
        assertThat(b2.dayType()).isEqualTo(DaySpecialType.SUNDAY);
        assertThat(b2.nightType()).isEqualTo(NightWorkType.NIGHT_UNCLASSIFIED);
        assertThat(b2.minutes()).isEqualTo(120);

        TemporalBucket b3 = buckets.get(2);
        assertThat(b3.dayType()).isEqualTo(DaySpecialType.STANDARD);
        assertThat(b3.nightType()).isEqualTo(NightWorkType.NIGHT_UNCLASSIFIED);
        assertThat(b3.minutes()).isEqualTo(300);

        assertThat(buckets.stream().mapToLong(TemporalBucket::minutes).sum()).isEqualTo(480);
    }

    @Test
    @DisplayName("T08: Veille férié 21:00 -> Férié 05:00 (Section 19 : bascule stricte à minuit)")
    void t08_eveToHolidayTransition() {
        LocalDate holiday = LocalDate.of(2026, 9, 18);
        AttendanceTemporalPartitioner holidayPartitioner = new AttendanceTemporalPartitioner(
                date -> date.equals(holiday),
                date -> 10
        );

        WorkInterval interval = new WorkInterval(
                LocalDateTime.of(2026, 9, 17, 21, 0),
                LocalDateTime.of(2026, 9, 18, 5, 0),
                101, 1, "T08"
        );

        List<TemporalBucket> buckets = holidayPartitioner.partitionSingleInterval(interval);

        assertThat(buckets).hasSize(3);
        assertThat(buckets.get(0).dayType()).isEqualTo(DaySpecialType.STANDARD);
        assertThat(buckets.get(1).dayType()).isEqualTo(DaySpecialType.STANDARD);
        assertThat(buckets.get(2).dayType()).isEqualTo(DaySpecialType.PUBLIC_HOLIDAY);
        assertThat(buckets.get(2).nightType()).isEqualTo(NightWorkType.NIGHT_UNCLASSIFIED);
    }

    @Test
    @DisplayName("T09: Férié 21:00 -> Lendemain normal 05:00 (Section 19 : fin férié à minuit)")
    void t09_holidayToNormalDayTransition() {
        LocalDate holiday = LocalDate.of(2026, 9, 17);
        AttendanceTemporalPartitioner holidayPartitioner = new AttendanceTemporalPartitioner(
                date -> date.equals(holiday),
                date -> 10
        );

        WorkInterval interval = new WorkInterval(
                LocalDateTime.of(2026, 9, 17, 21, 0),
                LocalDateTime.of(2026, 9, 18, 5, 0),
                101, 1, "T09"
        );

        List<TemporalBucket> buckets = holidayPartitioner.partitionSingleInterval(interval);

        assertThat(buckets).hasSize(3);
        assertThat(buckets.get(0).dayType()).isEqualTo(DaySpecialType.PUBLIC_HOLIDAY);
        assertThat(buckets.get(1).dayType()).isEqualTo(DaySpecialType.PUBLIC_HOLIDAY);
        assertThat(buckets.get(2).dayType()).isEqualTo(DaySpecialType.STANDARD);
    }

    @Test
    @DisplayName("T10: Frontière de période 25/MM 21:00 -> 26/MM 05:00 (Section 20 : bascule période M vers M+1 à minuit)")
    void t10_periodBoundaryTransition() {
        AttendanceTemporalPartitioner periodPartitioner = new AttendanceTemporalPartitioner(
                date -> false,
                date -> date.getDayOfMonth() <= 25 ? 100 : 101
        );

        WorkInterval interval = new WorkInterval(
                LocalDateTime.of(2026, 9, 25, 21, 0),
                LocalDateTime.of(2026, 9, 26, 5, 0),
                101, 1, "T10"
        );

        List<TemporalBucket> buckets = periodPartitioner.partitionSingleInterval(interval);

        assertThat(buckets).hasSize(3);

        // 25/09 21h-22h (Période M = 100)
        assertThat(buckets.get(0).payrollPeriodId()).isEqualTo(100);
        // 25/09 22h-00h (Période M = 100)
        assertThat(buckets.get(1).payrollPeriodId()).isEqualTo(100);
        // 26/09 00h-05h (Période M+1 = 101)
        assertThat(buckets.get(2).payrollPeriodId()).isEqualTo(101);

        assertThat(buckets.stream().mapToLong(TemporalBucket::minutes).sum()).isEqualTo(480);
    }
}
