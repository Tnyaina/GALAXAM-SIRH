package mg.galaxam.sirh.paie;

import mg.galaxam.sirh.paie.domain.dto.PayrollAttendanceInput;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.domain.engine.DailyPayProration;
import mg.galaxam.sirh.paie.domain.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.paie.domain.engine.PayrollTaxCalculations;
import mg.galaxam.sirh.paie.persistence.PaieAvanceAcompte;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PayrollModeEndToEndTest {
    private final PayrollExecutionEngine engine = new PayrollExecutionEngine();

    @Test
    void hourlyIrregularDayThroughNetAndAdvance() {
        var input = input("HORAIRE", "1000", 479);
        var result = engine.calculerBulletin(input);
        assertMoney("7983.33", result.getMontantLigneSalariale("SALAIRE_BASE"));
        assertMoney("7983.33", result.getBulletin().getTotalGainsBruts());
        assertMoney("6285.00", result.getBulletin().getNetAvantArrondi());
        assertMoney("6290", result.getBulletin().getNetAPayer());
    }

    @Test
    void dailyIrregularDayThroughNetAndAdvance() {
        var input = input("JOURNALIER", "20000", 443);
        input.setJoursPayablesJournaliers(DailyPayProration.payableDays(
                List.of(new DailyPayProration.Day(443, 0, 480))));
        var result = engine.calculerBulletin(input);
        assertMoney("18458.33", result.getMontantLigneSalariale("SALAIRE_BASE"));
        assertMoney("18458.33", result.getBulletin().getTotalGainsBruts());
        assertMoney("15712.50", result.getBulletin().getNetAvantArrondi());
        assertMoney("15710", result.getBulletin().getNetAPayer());
    }

    @Test
    void monthlyContractRemainsMonthlyThroughNetAndAdvance() {
        var input = input("MENSUEL", "440000", 0);
        var result = engine.calculerBulletin(input);
        assertMoney("440000", result.getMontantLigneSalariale("SALAIRE_BASE"));
        assertMoney("440000", result.getBulletin().getTotalGainsBruts());
        assertMoney("395100", result.getBulletin().getNetAvantArrondi());
        assertMoney("395100", result.getBulletin().getNetAPayer());
    }

    @Test
    void hourlyPayslipWithFullTransportAllowanceMatchesExpectedGrossAndNet() {
        var input = input("HORAIRE", "1000", 176 * 60);
        input.setAvancesAcomptes(List.of());
        input.setIndemniteRepas(new BigDecimal("44000"));
        input.setIndemniteTransport(new BigDecimal("31200"));
        input.setSoumisCnaps(true);
        input.setSoumisSante(true);
        input.setSoumisIrsa(true);
        input.setTauxCnapsSalarial(new BigDecimal("0.01"));
        input.setTauxCnapsPatronal(new BigDecimal("0.13"));
        input.setTauxSanteSalarial(new BigDecimal("0.01"));
        input.setTauxSantePatronal(new BigDecimal("0.05"));
        input.setTauxFmfpPatronal(new BigDecimal("0.01"));
        input.setMinimumPerceptionIrsa(new BigDecimal("3000"));
        input.setDeductionParEnfant(BigDecimal.ZERO);
        input.setTranchesIrsa(List.of(new PayrollTaxCalculations.Tranche(
                BigDecimal.ZERO, null, BigDecimal.ZERO)));
        var result = engine.calculerBulletin(input);
        assertMoney("176000", result.getMontantLigneSalariale("SALAIRE_BASE"));
        assertMoney("31200", result.getMontantLigneSalariale("INDEMNITE_TRANSPORT"));
        assertMoney("44000", result.getMontantLigneSalariale("INDEMNITE_REPAS"));
        assertMoney("251200", result.getBulletin().getTotalGainsBruts());
        assertMoney("5024", result.getBulletin().getTotalCotisationsSalariales());
        assertMoney("243180", result.getBulletin().getNetAPayer());
    }

    @Test
    void hourlyPaidLeaveUsesHistoricalTcongeWithoutDeductingWorkedHoursAgain() {
        var input = input("HORAIRE", "1000", 479);
        input.setAvancesAcomptes(List.of());
        input.setNbJoursConges(BigDecimal.ONE);
        input.setIndemniteCongeLots(new BigDecimal("24000"));
        var result = engine.calculerBulletin(input);
        assertMoney("7983.33", result.getMontantLigneSalariale("SALAIRE_BASE"));
        assertMoney("24000", result.getMontantLigneSalariale("INDEMNITE_CONGE_PAYE"));
        assertMoney("0", result.getMontantLigneSalariale("DEDUCTION_CONGE_BASE"));
        assertMoney("31983.33", result.getBulletin().getTotalGainsBruts());
    }

    @Test
    void dailyHalfDayLeaveUsesHalfHistoricalTconge() {
        var input = input("JOURNALIER", "20000", 443);
        input.setJoursPayablesJournaliers(DailyPayProration.payableDays(
                List.of(new DailyPayProration.Day(443, 0, 480))));
        input.setAvancesAcomptes(List.of());
        input.setNbJoursConges(new BigDecimal("0.5"));
        input.setIndemniteCongeLots(new BigDecimal("12000"));
        var result = engine.calculerBulletin(input);
        assertMoney("18458.33", result.getMontantLigneSalariale("SALAIRE_BASE"));
        assertMoney("12000", result.getMontantLigneSalariale("INDEMNITE_CONGE_PAYE"));
        assertMoney("0", result.getMontantLigneSalariale("DEDUCTION_CONGE_BASE"));
        assertMoney("30458.33", result.getBulletin().getTotalGainsBruts());
    }

    private static PayrollCalculationInput input(String mode, String rate, long workedMinutes) {
        var input = new PayrollCalculationInput();
        input.setIdEmploye(42);
        input.setIdPeriodePaie(7);
        input.setMatricule("0042");
        input.setNomPrenom("Test Paie");
        input.setCodePeriode("2026-08");
        input.setDateDebutPeriode(Date.valueOf(LocalDate.of(2026, 7, 26)));
        input.setDateFinPeriode(Date.valueOf(LocalDate.of(2026, 8, 25)));
        input.setModeCalculPaie(mode);
        input.setSalaireBaseContractuel(new BigDecimal(rate));
        input.setHeuresNormalesParJour(new BigDecimal("8"));
        input.setHeuresNormales(new BigDecimal("176"));
        input.setNbJoursOuvrables(new BigDecimal("22"));
        input.setNbJoursPresents(new BigDecimal("22"));
        input.setPlafondCotisationMensuel(new BigDecimal("2400000"));
        input.setTauxCnapsSalarial(BigDecimal.ZERO);
        input.setTauxCnapsPatronal(BigDecimal.ZERO);
        input.setTauxSanteSalarial(BigDecimal.ZERO);
        input.setTauxSantePatronal(BigDecimal.ZERO);
        input.setTauxFmfpPatronal(BigDecimal.ZERO);
        input.setSoumisCnaps(false);
        input.setSoumisSante(false);
        input.setSoumisIrsa(false);
        input.setPlafondAvanceEmployeur(new BigDecimal("0.10"));
        input.setAvancesAcomptes(List.of(movement("ACOMPTE_15", "1000"), movement("AVANCE_SPECIALE", "100000")));
        if (!"MENSUEL".equals(mode)) {
            input.setAttendanceInput(new PayrollAttendanceInput(1, 1, 42, 7, Instant.now(),
                    workedMinutes, 0, 0, 0, 0, 0, 0, 0, 0,
                    new BigDecimal("22"), BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        }
        return input;
    }

    private static PaieAvanceAcompte movement(String type, String amount) {
        var movement = new PaieAvanceAcompte();
        movement.setTypeMouvement(type);
        movement.setMontant(new BigDecimal(amount));
        return movement;
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, actual.compareTo(new BigDecimal(expected)));
    }
}
