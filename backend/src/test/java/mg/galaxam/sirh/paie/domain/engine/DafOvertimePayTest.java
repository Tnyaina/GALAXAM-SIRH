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
 @Test void heuresSupplementairesUtilisentLeSalaireDuJourDeChaqueBucket() {
  var december=LocalDateTime.of(2025,12,31,17,0);
  var january=LocalDateTime.of(2026,1,1,17,0);
  var buckets=List.of(
    new QualifiedTimeBucket(december,december.plusHours(1),60,OvertimeTier.OVERTIME_30,DaySpecialType.STANDARD,NightWorkType.NONE,1,1),
    new QualifiedTimeBucket(january,january.plusHours(1),60,OvertimeTier.OVERTIME_30,DaySpecialType.STANDARD,NightWorkType.NONE,1,1));
  var attendance=new PayrollAttendanceInput(1,1,1,1,java.time.Instant.EPOCH,
    120,120,0,0,0,0,0,0,0,
    BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,
    BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,buckets,0,0);
  var input=new PayrollCalculationInput();
  input.setIdEmploye(1);input.setIdPeriodePaie(1);
  input.setDateFinPeriode(java.sql.Date.valueOf("2026-01-25"));
  input.setSalaireBaseContractuel(new BigDecimal("400000"));
  input.setDiviseurMensuelHeures(new BigDecimal("100"));
  input.setSalaireParJour(java.util.Map.of(december.toLocalDate(),new BigDecimal("300000"),
    january.toLocalDate(),new BigDecimal("400000")));
  input.setAttendanceInput(attendance);
  input.setMajorationHs30(new BigDecimal("0.30"));
  var result=new OvertimePayCalculator().calculate(input,new BigDecimal("4000"));
  assertEquals(0,new BigDecimal("9100.00").compareTo(result.hs30()));
  assertEquals(0,new BigDecimal("3000").compareTo(result.bucketDetails().get(0).hourlyRate()));
  assertEquals(0,new BigDecimal("4000").compareTo(result.bucketDetails().get(1).hourlyRate()));
  input.setExonerationHsIrsaActive(true);
  input.setCategorieEligibleExonerationHs(true);
  input.setPlafondMensuelHsExonerees(BigDecimal.ONE);
  var exoneree=new PayrollExecutionEngine().calculerExonerationFiscaleHs(
    input,new BigDecimal("4000"),new mg.galaxam.sirh.paie.persistence.BulletinPaie(),new java.util.ArrayList<>());
  assertEquals(0,new BigDecimal("3900.00").compareTo(exoneree.hsExonereeIrsa()));
 }
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
