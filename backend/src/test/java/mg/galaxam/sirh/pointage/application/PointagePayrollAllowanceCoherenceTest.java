package mg.galaxam.sirh.pointage.application;

import org.junit.jupiter.api.Test;
import mg.galaxam.sirh.pointage.domain.calculation.PointageAllowancePolicy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PointagePayrollAllowanceCoherenceTest {
    @Test
    void fullTwentyTwoDayAllowanceMatchesMonthlyReference() {
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        amounts.put("DPLCT", new BigDecimal("1418.18").multiply(BigDecimal.valueOf(22)));
        amounts.put("REPAS", new BigDecimal("44000.00"));
        PointageSyntheseService.reconcileTravelAllowance(amounts, Map.of(), new BigDecimal("31200.00"));
        assertEquals(0, amounts.get("DPLCT").compareTo(new BigDecimal("31200.00")));
        assertEquals(0, amounts.get("REPAS").compareTo(new BigDecimal("44000.00")));
    }

    @Test
    void fullTwentyOneDayAllowanceCanCorrectDownward() {
        Map<String, BigDecimal> amounts = new LinkedHashMap<>(Map.of("DEPLACEMENT", new BigDecimal("31200.03")));
        PointageSyntheseService.reconcileTravelAllowance(amounts, Map.of(), new BigDecimal("31200.00"));
        assertEquals(0, amounts.get("DEPLACEMENT").compareTo(new BigDecimal("31200.00")));
    }

    @Test
    void partialAttendanceKeepsItsDailySum() {
        Map<String, BigDecimal> amounts = new LinkedHashMap<>(Map.of("TRANSPORT", new BigDecimal("1418.18")));
        PointageSyntheseService.reconcileTravelAllowance(amounts, Map.of(), new BigDecimal("1418.18"));
        assertEquals(0, amounts.get("TRANSPORT").compareTo(new BigDecimal("1418.18")));
    }

    @Test
    void leaveDayExplicitlyGrantedIsIncludedInPointageAndPayroll() {
        Map<String, BigDecimal> amounts = new LinkedHashMap<>(Map.of("DPLCT", new BigDecimal("28363.60")));
        assertTrue(PointageAllowancePolicy.eligibleForAllowance(false, true, true));
        PointageSyntheseService.reconcileTravelAllowance(amounts, Map.of(), new BigDecimal("28363.60"));
        assertEquals(0, amounts.get("DPLCT").compareTo(new BigDecimal("28363.60")));
        assertFalse(PointageAllowancePolicy.eligibleForAllowance(false, true, null));
        assertFalse(PointageAllowancePolicy.eligibleForAllowance(false, true, false));
        assertFalse(PointageAllowancePolicy.eligibleForAllowance(false, false, true));
    }

    @Test
    void fixedMonthlyTravelIsPreservedAlongsideDailyTravel() {
        Map<String, BigDecimal> amounts = new LinkedHashMap<>(Map.of("DPLCT", new BigDecimal("32000.00")));
        PointageSyntheseService.reconcileTravelAllowance(amounts,
                Map.of("DPLCT", new BigDecimal("800.00")), new BigDecimal("31200.00"));
        assertEquals(0, amounts.get("DPLCT").compareTo(new BigDecimal("32000.00")));
    }

    @Test
    void missingOrNegativeTravelAllowanceIsRejected() {
        assertThrows(IllegalStateException.class, () ->
                PointageSyntheseService.reconcileTravelAllowance(new LinkedHashMap<>(), Map.of(), new BigDecimal("0.04")));
        assertThrows(IllegalStateException.class, () ->
                PointageSyntheseService.reconcileTravelAllowance(
                        new LinkedHashMap<>(Map.of("DPLCT", new BigDecimal("0.01"))), Map.of(), new BigDecimal("-0.02")));
    }
}
