package mg.galaxam.sirh.paie.domain.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BaremeIrsaContinuiteTest {

    @Test
    @DisplayName("Le calculateur IRSA accepte les bornes contiguës officielles canoniques (LF 2024 et LF 2026)")
    void testBaremeCanoniqueContinuite() {
        IrsaCalculator calculator = new IrsaCalculator();

        List<IrsaCalculator.Tranche> tranchesLf2026 = List.of(
            new IrsaCalculator.Tranche(new BigDecimal("0.00"), new BigDecimal("350000.00"), new BigDecimal("0.00")),
            new IrsaCalculator.Tranche(new BigDecimal("350000.00"), new BigDecimal("400000.00"), new BigDecimal("5.00")),
            new IrsaCalculator.Tranche(new BigDecimal("400000.00"), new BigDecimal("500000.00"), new BigDecimal("10.00")),
            new IrsaCalculator.Tranche(new BigDecimal("500000.00"), new BigDecimal("600000.00"), new BigDecimal("15.00")),
            new IrsaCalculator.Tranche(new BigDecimal("600000.00"), new BigDecimal("4000000.00"), new BigDecimal("20.00")),
            new IrsaCalculator.Tranche(new BigDecimal("4000000.00"), null, new BigDecimal("25.00"))
        );

        BigDecimal base = new BigDecimal("5000000.00");
        IrsaCalculator.Result result = calculator.calculate(
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
        IrsaCalculator calculator = new IrsaCalculator();

        // 350000.01 n'est ni canonique (350000.00) ni legacy inclusif (350001.00)
        List<IrsaCalculator.Tranche> tranchesInvalides = List.of(
            new IrsaCalculator.Tranche(new BigDecimal("0.00"), new BigDecimal("350000.00"), new BigDecimal("0.00")),
            new IrsaCalculator.Tranche(new BigDecimal("350000.01"), new BigDecimal("400000.00"), new BigDecimal("5.00"))
        );

        assertThrows(IllegalStateException.class, () -> {
            calculator.calculate(
                new BigDecimal("500000.00"),
                tranchesInvalides,
                0,
                new BigDecimal("2000.00"),
                new BigDecimal("3000.00")
            );
        });
    }
}
