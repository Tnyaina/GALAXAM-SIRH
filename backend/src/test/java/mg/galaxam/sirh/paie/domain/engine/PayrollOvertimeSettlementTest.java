package mg.galaxam.sirh.paie.domain.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.List;
import mg.galaxam.sirh.paie.domain.dto.PayrollAttendanceInput;
import mg.galaxam.sirh.pointage.domain.calculation.DaySpecialType;
import mg.galaxam.sirh.pointage.domain.calculation.NightWorkType;
import mg.galaxam.sirh.pointage.domain.calculation.OvertimeTier;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket;
import org.junit.jupiter.api.Test;

class PayrollOvertimeSettlementTest {
    @Test
    void compenseLesHs30SansPerdreLesSujétionsEtLesHs50() {
        LocalDateTime monday = LocalDateTime.of(2026, 8, 3, 17, 0);
        LocalDateTime sunday = LocalDateTime.of(2026, 8, 9, 10, 0);
        List<QualifiedTimeBucket> source = List.of(
                bucket(monday, 120, OvertimeTier.OVERTIME_30, DaySpecialType.STANDARD),
                bucket(sunday, 129, OvertimeTier.OVERTIME_30, DaySpecialType.SUNDAY),
                bucket(sunday.plusMinutes(129), 60, OvertimeTier.OVERTIME_50, DaySpecialType.SUNDAY));

        List<QualifiedTimeBucket> result = PayrollOvertimeSettlement.apply(source, 249, 90);

        assertEquals(90, result.stream().filter(b -> b.overtimeTier() == OvertimeTier.OVERTIME_30)
                .mapToLong(QualifiedTimeBucket::minutes).sum());
        assertEquals(60, result.stream().filter(b -> b.overtimeTier() == OvertimeTier.OVERTIME_50)
                .mapToLong(QualifiedTimeBucket::minutes).sum());
        assertEquals(309, result.stream().mapToLong(QualifiedTimeBucket::minutes).sum());
        assertEquals(189, result.stream().filter(QualifiedTimeBucket::isSunday)
                .mapToLong(QualifiedTimeBucket::minutes).sum());
    }

    @Test
    void refuseLesHsSansSemaineEtConserveLaMinuteExacte() {
        LocalDateTime monday = LocalDateTime.of(2026, 8, 3, 17, 0);
        List<QualifiedTimeBucket> source = List.of(
                bucket(monday, 249, OvertimeTier.OVERTIME_30, DaySpecialType.STANDARD));

        assertThrows(IllegalArgumentException.class, () -> PayrollOvertimeSettlement.apply(source, 249, 250));
        assertEquals(0, PayrollOvertimeSettlement.apply(source, 249, 0).stream()
                .filter(b -> b.overtimeTier() == OvertimeTier.OVERTIME_30)
                .mapToLong(QualifiedTimeBucket::minutes).sum());
        assertEquals(20, PayrollAttendanceInput.hoursToMinutes(PayrollAttendanceInput.minutesToHours(20)));
    }

    @Test
    void reventileTouteLaPeriodeSansEffacerDimancheEtFerie() {
        var start=LocalDateTime.of(2026,9,1,8,0);
        var source=List.of(bucket(start,480,OvertimeTier.OVERTIME_30,DaySpecialType.SUNDAY),
                bucket(start.plusDays(1),480,OvertimeTier.OVERTIME_50,DaySpecialType.PUBLIC_HOLIDAY));
        var result=PayrollOvertimeSettlement.applyPeriod(source,480,480,480,60);
        assertEquals(480,result.stream().filter(b->b.overtimeTier()==OvertimeTier.OVERTIME_30).mapToLong(QualifiedTimeBucket::minutes).sum());
        assertEquals(60,result.stream().filter(b->b.overtimeTier()==OvertimeTier.OVERTIME_50).mapToLong(QualifiedTimeBucket::minutes).sum());
        assertEquals(960,result.stream().mapToLong(QualifiedTimeBucket::minutes).sum());
        assertEquals(480,result.stream().filter(QualifiedTimeBucket::isSunday).mapToLong(QualifiedTimeBucket::minutes).sum());
        assertEquals(480,result.stream().filter(QualifiedTimeBucket::isPublicHoliday).mapToLong(QualifiedTimeBucket::minutes).sum());
    }
    @Test
    void unComplementDafNeCreeAucunHoraireNiPointage() {
        var start=LocalDateTime.of(2026,9,1,8,0);
        var source=List.of(bucket(start,60,OvertimeTier.REGULAR,DaySpecialType.STANDARD));
        assertEquals(source,PayrollOvertimeSettlement.applyPeriod(source,0,0,120,0));
    }

    private static QualifiedTimeBucket bucket(LocalDateTime start, long minutes,
            OvertimeTier tier, DaySpecialType dayType) {
        return new QualifiedTimeBucket(start, start.plusMinutes(minutes), minutes, tier, dayType,
                NightWorkType.NONE, 1, 1);
    }
}
