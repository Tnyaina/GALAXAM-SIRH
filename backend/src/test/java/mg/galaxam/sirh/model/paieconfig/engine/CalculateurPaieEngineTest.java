package mg.galaxam.sirh.model.paieconfig.engine;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CalculateurPaieEngineTest {

    private CalculateurPaieEngine engine;

    @BeforeEach
    void setUp() {
        engine = new CalculateurPaieEngine();
    }

    @Test
    @DisplayName("Calcul des cotisations CNaPS plafonnées à 8 x SMIG")
    void testCotisationCnaps() {
        BigDecimal brut = new BigDecimal("2500000.00");
        BigDecimal smig = new BigDecimal("262680.00"); // Plafond = 2 101 440 Ar

        var res = engine.calculerCnaps(brut, smig, 0.01, 0.13);
        assertEquals(new BigDecimal("2101440.00"), res.assiette());
        assertEquals(new BigDecimal("21014.40"), res.partSalariale());
        assertEquals(new BigDecimal("273187.20"), res.partPatronale());
    }

    @Test
    @DisplayName("Calcul des cotisations de santé (OSTIE)")
    void testCotisationSante() {
        BigDecimal brut = new BigDecimal("1200000.00");

        var res = engine.calculerSante(brut, 0.01, 0.05);
        assertEquals(new BigDecimal("12000.00"), res.partSalariale());
        assertEquals(new BigDecimal("60000.00"), res.partPatronale());
    }

    @Test
    @DisplayName("Calcul IRSA avec application du minimum de perception (3 000 Ar)")
    void testIrsaMinimumPerception() {
        BigDecimal baseFaible = new BigDecimal("360000.00"); // 10 000 Ar taxables à 20% = 2 000 Ar < 3 000 Ar

        BigDecimal irsa = engine.calculerIrsa(baseFaible, null, 0);
        assertEquals(new BigDecimal("3000.00"), irsa, "Le minimum légal de 3 000 Ar s'applique.");
    }

    @Test
    @DisplayName("Calcul IRSA par tranches progressives avec enfants à charge")
    void testIrsaTranchesProgressives() {
        List<CalculateurPaieEngine.TrancheIrsa> tranches = List.of(
                new CalculateurPaieEngine.TrancheIrsa(BigDecimal.ZERO, new BigDecimal("350000.00"), 0.0),
                new CalculateurPaieEngine.TrancheIrsa(new BigDecimal("350000.00"), new BigDecimal("400000.00"), 0.05),
                new CalculateurPaieEngine.TrancheIrsa(new BigDecimal("400000.00"), new BigDecimal("500000.00"), 0.10),
                new CalculateurPaieEngine.TrancheIrsa(new BigDecimal("500000.00"), new BigDecimal("600000.00"), 0.15),
                new CalculateurPaieEngine.TrancheIrsa(new BigDecimal("600000.00"), null, 0.20)
        );

        BigDecimal base = new BigDecimal("1000000.00");
        // Tranche 1 (0-350k): 0
        // Tranche 2 (350k-400k): 50k * 5% = 2 500
        // Tranche 3 (400k-500k): 100k * 10% = 10 000
        // Tranche 4 (500k-600k): 100k * 15% = 15 000
        // Tranche 5 (>600k): 400k * 20% = 80 000
        // Total brut = 107 500 Ar.
        // Avec 2 enfants (2 x 2000 = -4000) -> 103 500 Ar.

        BigDecimal irsa = engine.calculerIrsa(base, tranches, 2);
        assertEquals(new BigDecimal("103500.00"), irsa);
    }
}
