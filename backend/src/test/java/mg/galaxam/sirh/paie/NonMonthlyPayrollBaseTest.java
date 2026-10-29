package mg.galaxam.sirh.paie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mg.galaxam.sirh.paie.domain.dto.PayrollAttendanceInput;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.domain.engine.DailyPayProration;
import mg.galaxam.sirh.paie.domain.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import org.junit.jupiter.api.Test;

class NonMonthlyPayrollBaseTest {
    private final PayrollExecutionEngine engine = new PayrollExecutionEngine();

    private PayrollCalculationInput input(String mode, String rate, long workedMinutes,
            long overtimeMinutes, int presentDays, long absenceMinutes) {
        var input = new PayrollCalculationInput();
        input.setIdEmploye(10);
        input.setIdPeriodePaie(20);
        input.setModeCalculPaie(mode);
        input.setSalaireBaseContractuel(new BigDecimal(rate));
        input.setHeuresNormalesParJour(new BigDecimal("8"));
        if ("JOURNALIER".equals(mode)) input.setJoursPayablesJournaliers(new BigDecimal(presentDays));
        input.setAttendanceInput(new PayrollAttendanceInput(1, 1, 10, 20, Instant.now(),
                workedMinutes, overtimeMinutes, 0, 0, 0, 0, 0, absenceMinutes, 0,
                new BigDecimal("22"), new BigDecimal(presentDays), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO));
        return input;
    }

    private BigDecimal base(PayrollCalculationInput input) {
        return engine.calculerSalaireBase(input, new BulletinPaie(), new ArrayList<BulletinPaieLigne>());
    }

    @Test
    void hourlyRateMultipliesRegularWorkedHours() {
        var input = input("HORAIRE", "1000", 176 * 60, 0, 22, 0);
        assertEquals(0, base(input).compareTo(new BigDecimal("176000")));
        assertEquals(0, engine.calculerSalaireBrut(input, new BulletinPaie(), new ArrayList<>())
                .compareTo(new BigDecimal("176000")));
        assertEquals(0, input.getTauxHoraireEffectif().compareTo(new BigDecimal("1000")));
    }

    @Test
    void overtimeIsNotPaidTwiceInHourlyBase() {
        var input = input("HORAIRE", "1000", 176 * 60, 8 * 60, 22, 0);
        assertEquals(0, base(input).compareTo(new BigDecimal("168000")));
        assertEquals(0, input.getHeuresNormalesPayables().compareTo(new BigDecimal("168")));
    }

    @Test
    void dailyRateMultipliesPresentDaysWithoutSecondAbsenceDeduction() {
        var input = input("JOURNALIER", "20000", 176 * 60, 0, 22, 8 * 60);
        assertEquals(0, base(input).compareTo(new BigDecimal("440000")));
        assertEquals(0, engine.calculerSalaireBrut(input, new BulletinPaie(), new ArrayList<>())
                .compareTo(new BigDecimal("440000")));
        assertEquals(0, input.getTauxHoraireEffectif().compareTo(new BigDecimal("2500")));
        assertEquals(0, engine.calculerRetenueAbsence(input, base(input), input.getTauxHoraireEffectif(),
                new BulletinPaie(), new ArrayList<>()).compareTo(BigDecimal.ZERO));
    }

    @Test
    void partialDayIsProratedAndOvertimeIsExcludedFromDailyBase() {
        var days = java.util.List.of(
                new DailyPayProration.Day(480, 0, 480),
                new DailyPayProration.Day(240, 0, 480),
                new DailyPayProration.Day(600, 120, 480));
        var input = input("JOURNALIER", "20000", 22 * 60, 2 * 60, 3, 0);
        input.setJoursPayablesJournaliers(DailyPayProration.payableDays(days));
        assertEquals(0, input.getJoursPayablesJournaliers().compareTo(new BigDecimal("2.5")));
        assertEquals(0, base(input).compareTo(new BigDecimal("50000")));
    }

    @Test
    void stageRegimeDoesNotEnableContributions() {
        var input = input("JOURNALIER", "20000", 176 * 60, 0, 22, 0);
        input.setRegimeIntervenant("STAGIAIRE");
        assertFalse(input.isSoumisCnaps());
        assertFalse(input.isSoumisSante());
    }

    @Test
    void nonMonthlyPayRequiresFrozenAttendance() {
        var input = new PayrollCalculationInput();
        input.setModeCalculPaie("HORAIRE");
        assertThrows(IllegalStateException.class, () -> base(input));
    }

    @Test
    void irregularMinutesArePaidExactlyForHourlyAndProratedForDaily() {
        long[] minutes = {0, 1, 443, 479, 480, 481, 600};
        String[] hourly = {"0", "16.67", "7383.33", "7983.33", "8000", "8016.67", "10000"};
        String[] daily = {"0", "41.67", "18458.33", "19958.33", "20000", "20000", "20000"};
        for (int index = 0; index < minutes.length; index++) {
            var hourlyInput = input("HORAIRE", "1000", minutes[index], 0, 1, 0);
            assertEquals(0, base(hourlyInput).compareTo(new BigDecimal(hourly[index])),
                    "Salaire horaire pour " + minutes[index] + " minutes");

            var dailyInput = input("JOURNALIER", "20000", minutes[index], 0, 1, 0);
            dailyInput.setJoursPayablesJournaliers(DailyPayProration.payableDays(
                    List.of(new DailyPayProration.Day(minutes[index], 0, 480))));
            assertEquals(0, base(dailyInput).compareTo(new BigDecimal(daily[index])),
                    "Salaire journalier pour " + minutes[index] + " minutes");
        }
    }

    @Test
    void mixedPeriodAddsEachDayWithoutRoundingItToPresence() {
        var days = List.of(new DailyPayProration.Day(480, 0, 480),
                new DailyPayProration.Day(479, 0, 480),
                new DailyPayProration.Day(443, 0, 480),
                new DailyPayProration.Day(0, 0, 480));
        var input = input("JOURNALIER", "20000", 1402, 0, 3, 0);
        input.setJoursPayablesJournaliers(DailyPayProration.payableDays(days));
        assertEquals(0, base(input).compareTo(new BigDecimal("58416.67")));
    }

    @Test
    void overtimeIsRemovedFromBaseThenPaidAtItsOwnRate() {
        var hourly = input("HORAIRE", "1000", 600, 120, 1, 0);
        hourly.setMajorationHs30(new BigDecimal("0.30"));
        assertEquals(0, base(hourly).compareTo(new BigDecimal("8000")));
        assertEquals(0, engine.initialiserEnTete(hourly).getHeuresNormales().compareTo(new BigDecimal("8")));
        assertEquals(0, engine.calculerSalaireBrut(hourly, new BulletinPaie(), new ArrayList<>())
                .compareTo(new BigDecimal("10600")));

        var daily = input("JOURNALIER", "20000", 600, 120, 1, 0);
        daily.setJoursPayablesJournaliers(DailyPayProration.payableDays(
                List.of(new DailyPayProration.Day(600, 120, 480))));
        daily.setMajorationHs30(new BigDecimal("0.30"));
        assertEquals(0, base(daily).compareTo(new BigDecimal("20000")));
        assertEquals(0, engine.initialiserEnTete(daily).getHeuresNormales().compareTo(new BigDecimal("8")));
        assertEquals(0, engine.calculerSalaireBrut(daily, new BulletinPaie(), new ArrayList<>())
                .compareTo(new BigDecimal("26500")));
    }

    @Test
    void invalidDailyInputsAreRejected() {
        assertThrows(IllegalStateException.class, () -> DailyPayProration.payableDays(
                List.of(new DailyPayProration.Day(60, 61, 480))));
        assertThrows(IllegalStateException.class, () -> DailyPayProration.payableDays(
                List.of(new DailyPayProration.Day(60, 0, 0))));
        var hourly = input("HORAIRE", "1000", 60, 120, 1, 0);
        assertThrows(IllegalStateException.class, () -> base(hourly));
    }

    @Test
    void dailyProrationUsesConfiguredDayLengthAndPreservesPrecision() {
        var input = input("JOURNALIER", "1000000", 443, 0, 1, 0);
        input.setHeuresNormalesParJour(new BigDecimal("7.5"));
        input.setJoursPayablesJournaliers(DailyPayProration.payableDays(
                List.of(new DailyPayProration.Day(443, 0, 450))));
        assertEquals(0, base(input).compareTo(new BigDecimal("984444.44")));
        assertEquals(0, input.getTauxHoraireEffectif().compareTo(new BigDecimal("133333.33333333")));
    }

    @Test
    void rateChangesAndUnconfiguredNonMonthlyLeaveBlockCalculation() {
        var hourly = input("HORAIRE", "1000", 480, 0, 1, 0);
        hourly.setSalaireParJour(Map.of(LocalDate.of(2026, 8, 1), new BigDecimal("1000"),
                LocalDate.of(2026, 8, 2), new BigDecimal("1200")));
        assertThrows(IllegalStateException.class, () -> base(hourly));

        var daily = input("JOURNALIER", "20000", 480, 0, 1, 0);
        daily.setNbJoursConges(BigDecimal.ONE);
        assertThrows(IllegalStateException.class,
                () -> engine.calculerSalaireBrut(daily, new BulletinPaie(), new ArrayList<>()));
    }
}
