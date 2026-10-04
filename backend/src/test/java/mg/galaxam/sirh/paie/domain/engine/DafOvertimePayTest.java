package mg.galaxam.sirh.paie.domain.engine;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import mg.galaxam.sirh.pointage.domain.calculation.*;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.OvertimeTier;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.DaySpecialType;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket.NightWorkType;
import mg.galaxam.sirh.paie.domain.dto.*;
import static org.junit.jupiter.api.Assertions.*;
class DafOvertimePayTest {
 @Test void valoriseDeuxHeuresDafSansFauxBadgeOuMajorationSpeciale() {
  var start=LocalDateTime.of(2026,9,1,8,0);
  var bucket=new QualifiedTimeBucket(start,start.plusHours(1),60,OvertimeTier.REGULAR,DaySpecialType.STANDARD,NightWorkType.NONE,1,1);
  var attendance=new PayrollAttendanceInput(1,
    1,
    1,
    1,
    java.time.Instant.EPOCH,
    60,
    120,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    BigDecimal.ZERO,
    BigDecimal.ZERO,
    BigDecimal.ZERO,
    BigDecimal.ZERO,
    BigDecimal.ZERO,
    BigDecimal.ZERO,
    BigDecimal.ZERO,
    BigDecimal.ZERO,
    List.of(bucket),
    120,
    0);
  var input=new PayrollCalculationInput();input.setIdEmploye(1);input.setIdPeriodePaie(1);
  input.setDateFinPeriode(java.sql.Date.valueOf("2026-09-30"));input.setAttendanceInput(attendance);
  input.setMajorationHs30(new BigDecimal("0.30"));input.setMajorationHs50(new BigDecimal("0.50"));
  var result=new OvertimePayCalculator().calculate(input,new BigDecimal("1000"));
  assertEquals(0,new BigDecimal("2600").compareTo(result.hs30()));
  assertEquals(0,result.dimanche().signum());assertEquals(0,result.ferie().signum());
  assertEquals(0,BigDecimal.ONE.compareTo(result.totalHours()));
  assertEquals(1,result.bucketDetails().size());
 }
}
