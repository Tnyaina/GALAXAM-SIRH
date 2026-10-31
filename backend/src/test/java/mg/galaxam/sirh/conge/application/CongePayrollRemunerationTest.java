package mg.galaxam.sirh.conge.application;

import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.domain.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CongePayrollRemunerationTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);
    private static final BigDecimal SALARY = new BigDecimal("300000");
    private final PayrollExecutionEngine engine = new PayrollExecutionEngine();

    private PayrollCalculationInput input(Boolean override, boolean typeDefault, BigDecimal leaveDays,
            BigDecimal absenceHours) {
        PayrollCalculationInput input = new PayrollCalculationInput();
        input.setIdEmploye(1);
        input.setDateDebutPeriode(Date.valueOf(DAY));
        input.setDateFinPeriode(Date.valueOf(DAY));
        input.setSalaireBaseContractuel(SALARY);
        input.setDiviseurMensuelHeures(new BigDecimal("173.33"));
        input.setNbJoursConges(CongePayrollPolicy.estRemunere(override, typeDefault) ? leaveDays : BigDecimal.ZERO);
        if (CongePayrollPolicy.estRemunere(override, typeDefault) && leaveDays.signum() > 0) {
            input.setJoursCongeValorisesParDate(Map.of(DAY, leaveDays));
            input.setSalaireParJour(Map.of(DAY, SALARY));
            input.setIndemniteCongeLots(new BigDecimal("12000").multiply(leaveDays));
        }
        input.setNbHeuresAbsence(absenceHours);
        return input;
    }

    private List<BulletinPaieLigne> calculateLeaveLines(PayrollCalculationInput input) {
        List<BulletinPaieLigne> lines = new ArrayList<>();
        engine.calculerConges(input, new BulletinPaie(), lines);
        return lines;
    }

    private BigDecimal leaveNet(PayrollCalculationInput input) {
        return engine.calculerConges(input, new BulletinPaie(), new ArrayList<>());
    }

    private BigDecimal amount(List<BulletinPaieLigne> lines, String code) {
        return lines.stream().filter(line -> code.equals(line.getCodeRubrique()))
                .map(BulletinPaieLigne::getMontantSalarial).findFirst().orElse(null);
    }

    @Test
    void remunerationEffectiveUsesAdministrativeOverrideAndFallsBackToType() {
        assertEquals(true, CongePayrollPolicy.estRemunere(null, true));
        assertEquals(false, CongePayrollPolicy.estRemunere(null, false));
        assertEquals(true, CongePayrollPolicy.estRemunere(true, false));
        assertEquals(false, CongePayrollPolicy.estRemunere(false, true));
        assertEquals(false, CongePayrollPolicy.estRemunere(null, null));
    }

    @Test
    void paidFullDayGeneratesBaseDeductionAndFifoIndemnity() {
        PayrollCalculationInput input = input(null, true, BigDecimal.ONE, BigDecimal.ZERO);
        List<BulletinPaieLigne> lines = calculateLeaveLines(input);
        assertEquals(new BigDecimal("10000.00"), amount(lines, "DEDUCTION_CONGE_BASE"));
        assertEquals(new BigDecimal("12000.00"), amount(lines, "INDEMNITE_CONGE_PAYE"));
        assertEquals(new BigDecimal("2000.00"), leaveNet(input));
    }

    @Test
    void unpaidFullDayGeneratesOnlyThePointageAbsenceDeduction() {
        PayrollCalculationInput input = input(false, true, BigDecimal.ONE, new BigDecimal("8"));
        List<BulletinPaieLigne> leaveLines = calculateLeaveLines(input);
        List<BulletinPaieLigne> absenceLines = new ArrayList<>();
        engine.calculerRetenueAbsence(input, SALARY, input.getTauxHoraireEffectif(), new BulletinPaie(), absenceLines);
        assertNull(amount(leaveLines, "DEDUCTION_CONGE_BASE"));
        assertNull(amount(leaveLines, "INDEMNITE_CONGE_PAYE"));
        assertEquals(new BigDecimal("13846.42"), amount(absenceLines, "RETENUE_ABSENCE"));
        assertEquals(BigDecimal.ZERO, leaveNet(input));
    }

    @Test
    void paidHalfDayRetainsHalfDayValuation() {
        PayrollCalculationInput input = input(null, true, new BigDecimal("0.5"), BigDecimal.ZERO);
        List<BulletinPaieLigne> lines = calculateLeaveLines(input);
        assertEquals(new BigDecimal("5000.00"), amount(lines, "DEDUCTION_CONGE_BASE"));
        assertEquals(new BigDecimal("6000.00"), amount(lines, "INDEMNITE_CONGE_PAYE"));
        assertEquals(new BigDecimal("1000.00"), leaveNet(input));
    }

    @Test
    void unpaidHalfDayUsesThePointageAbsenceWithoutPaidLeaveLines() {
        PayrollCalculationInput input = input(false, true, new BigDecimal("0.5"), new BigDecimal("4"));
        List<BulletinPaieLigne> leaveLines = calculateLeaveLines(input);
        List<BulletinPaieLigne> absenceLines = new ArrayList<>();
        engine.calculerRetenueAbsence(input, SALARY, input.getTauxHoraireEffectif(), new BulletinPaie(), absenceLines);
        assertNull(amount(leaveLines, "DEDUCTION_CONGE_BASE"));
        assertNull(amount(leaveLines, "INDEMNITE_CONGE_PAYE"));
        assertEquals(new BigDecimal("6923.21"), amount(absenceLines, "RETENUE_ABSENCE"));
        assertEquals(BigDecimal.ZERO, leaveNet(input));
    }

    @Test
    void partialPointageCoveragePaysOnlyCoveredHalfAndDeductsUncoveredHalfAsAbsence() {
        PayrollCalculationInput input = input(null, true, new BigDecimal("0.5"), new BigDecimal("4"));
        List<BulletinPaieLigne> leaveLines = calculateLeaveLines(input);
        List<BulletinPaieLigne> absenceLines = new ArrayList<>();
        engine.calculerRetenueAbsence(input, SALARY, input.getTauxHoraireEffectif(), new BulletinPaie(), absenceLines);
        assertEquals(new BigDecimal("5000.00"), amount(leaveLines, "DEDUCTION_CONGE_BASE"));
        assertEquals(new BigDecimal("6000.00"), amount(leaveLines, "INDEMNITE_CONGE_PAYE"));
        assertEquals(new BigDecimal("6923.21"), amount(absenceLines, "RETENUE_ABSENCE"));
        assertEquals(new BigDecimal("1000.00"), leaveNet(input));
    }

    @Test
    void employee592MatchesOctoberSpreadsheetWithThreeCalendarDaysAnd244PaidDays() {
        PayrollCalculationInput input = input(null, true, new BigDecimal("2.44"), new BigDecimal("2.75"));
        input.setSalaireBaseContractuel(new BigDecimal("1000000"));
        input.setJoursCongeBaseParDate(Map.of(
                LocalDate.of(2026, 10, 23), BigDecimal.ONE,
                LocalDate.of(2026, 10, 24), BigDecimal.ONE,
                LocalDate.of(2026, 10, 25), BigDecimal.ONE));
        input.setJoursCongeValorisesParDate(Map.of(
                LocalDate.of(2026, 10, 23), BigDecimal.ONE,
                LocalDate.of(2026, 10, 24), BigDecimal.ONE,
                LocalDate.of(2026, 10, 25), new BigDecimal("0.44")));
        input.setSalaireParJour(Map.of(
                LocalDate.of(2026, 10, 23), new BigDecimal("1000000"),
                LocalDate.of(2026, 10, 24), new BigDecimal("1000000"),
                LocalDate.of(2026, 10, 25), new BigDecimal("1000000")));
        input.setIndemniteCongeLots(new BigDecimal("2.44").multiply(new BigDecimal("31914.926")));
        List<BulletinPaieLigne> lines = calculateLeaveLines(input);
        List<BulletinPaieLigne> absenceLines = new ArrayList<>();
        engine.calculerRetenueAbsence(input, new BigDecimal("1000000"),
                input.getTauxHoraireEffectif(), new BulletinPaie(), absenceLines);
        assertEquals(new BigDecimal("100000.00"), amount(lines, "DEDUCTION_CONGE_BASE"));
        assertEquals(new BigDecimal("77872.42"), amount(lines, "INDEMNITE_CONGE_PAYE"));
        assertEquals(new BigDecimal("15865.69"), amount(absenceLines, "RETENUE_ABSENCE"));
    }
}
