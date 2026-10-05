package mg.galaxam.sirh.paie.domain.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BaremeIrsaContinuiteTest {

    @Test
    void isiUsesResolvedPercentageWithoutChangingRounding() {
        PayrollTaxCalculations calculator = new PayrollTaxCalculations();
        assertEquals(new BigDecimal("5000.00"),
                calculator.calculateIsi(new BigDecimal("100000.00"), new BigDecimal("5.00")));
        assertThrows(IllegalStateException.class,
                () -> calculator.calculateIsi(new BigDecimal("100000.00"), null));
    }

    @Test
    @DisplayName("Le calculateur IRSA accepte les bornes contiguës officielles canoniques (LF 2024 et LF 2026)")
    void testBaremeCanoniqueContinuite() {
        PayrollTaxCalculations calculator = new PayrollTaxCalculations();

        List<PayrollTaxCalculations.Tranche> tranchesLf2026 = List.of(
            new PayrollTaxCalculations.Tranche(new BigDecimal("0.00"), new BigDecimal("350000.00"), new BigDecimal("0.00")),
            new PayrollTaxCalculations.Tranche(new BigDecimal("350000.00"), new BigDecimal("400000.00"), new BigDecimal("5.00")),
            new PayrollTaxCalculations.Tranche(new BigDecimal("400000.00"), new BigDecimal("500000.00"), new BigDecimal("10.00")),
            new PayrollTaxCalculations.Tranche(new BigDecimal("500000.00"), new BigDecimal("600000.00"), new BigDecimal("15.00")),
            new PayrollTaxCalculations.Tranche(new BigDecimal("600000.00"), new BigDecimal("4000000.00"), new BigDecimal("20.00")),
            new PayrollTaxCalculations.Tranche(new BigDecimal("4000000.00"), null, new BigDecimal("25.00"))
        );

        BigDecimal base = new BigDecimal("5000000.00");
        PayrollTaxCalculations.Result result = calculator.calculateIrsa(
            base,
            tranchesLf2026,
            0,
            new BigDecimal("2000.00"),
            new BigDecimal("3000.00")
        );

        assertNotNull(result);
        assertTrue(result.irsaDu().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Le calculateur IRSA rejette un barème avec discontinuité (non contigu)")
    void testBaremeDiscontinuRejete() {
        PayrollTaxCalculations calculator = new PayrollTaxCalculations();

        // 350000.01 n'est ni canonique (350000.00) ni legacy inclusif (350001.00)
        List<PayrollTaxCalculations.Tranche> tranchesInvalides = List.of(
            new PayrollTaxCalculations.Tranche(new BigDecimal("0.00"), new BigDecimal("350000.00"), new BigDecimal("0.00")),
            new PayrollTaxCalculations.Tranche(new BigDecimal("350000.01"), new BigDecimal("400000.00"), new BigDecimal("5.00"))
        );

        assertThrows(IllegalStateException.class, () -> {
            calculator.calculateIrsa(
                new BigDecimal("500000.00"),
                tranchesInvalides,
                0,
                new BigDecimal("2000.00"),
                new BigDecimal("3000.00")
            );
        });
    }
}
