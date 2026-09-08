package mg.galaxam.sirh.paie;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CalculateurPaieEngineAdvancedTest — Cas Limites Légaux et Fiscaux (LF 2026 & Loi 2003-044)")
public class CalculateurPaieEngineAdvancedTest {

    private final CalculateurPaieEngine engine = new CalculateurPaieEngine();

    @Test
    @DisplayName("CALC-01 : CNaPS — Plafonnement strict à 8 fois le SME")
    void testCalculerCnapsPlafonnee() {
        // SME = 262 680 Ar -> Plafond 8x = 2 101 440 Ar
        BigDecimal sme = new BigDecimal("262680.00");

        // Cas 1 : Salaire sous plafond (1 500 000 Ar) -> base réelle
        BigDecimal brutSousPlafond = new BigDecimal("1500000.00");
        var res1 = engine.calculerCnaps(brutSousPlafond, sme, 0.01, 0.13);
        assertEquals(0, new BigDecimal("15000.00").compareTo(res1.partSalariale()), "1% salarié sur 1.5M = 15 000 Ar");
        assertEquals(0, new BigDecimal("195000.00").compareTo(res1.partPatronale()), "13% patronal sur 1.5M = 195 000 Ar");

        // Cas 2 : Salaire au-dessus du plafond (5 000 000 Ar) -> base plafonnée à 2 101 440 Ar
        BigDecimal brutHaut = new BigDecimal("5000000.00");
        var res2 = engine.calculerCnaps(brutHaut, sme, 0.01, 0.13);
        assertEquals(0, new BigDecimal("21014.40").compareTo(res2.partSalariale()), "1% sur plafond = 21 014.40 Ar");
        assertEquals(0, new BigDecimal("273187.20").compareTo(res2.partPatronale()), "13% sur plafond = 273 187.20 Ar");
    }

    @Test
    @DisplayName("CALC-02 : Santé — Non plafonnée (OSTIE / OMSI)")
    void testCalculerSanteNonPlafonnee() {
        BigDecimal brut = new BigDecimal("4000000.00");
        var res = engine.calculerSante(brut, 0.01, 0.05); // 1% salarié, 5% patronal
        assertEquals(0, new BigDecimal("40000.00").compareTo(res.partSalariale()));
        assertEquals(0, new BigDecimal("200000.00").compareTo(res.partPatronale()));
    }

    @Test
    @DisplayName("CALC-03 : IRSA — Minimum de perception légal garanti (3 000 Ar)")
    void testCalculerIrsaMinimumPerception() {
        // Salaire net imposable très faible (ex: 200 000 Ar, exonéré au barème)
        BigDecimal baseFaible = new BigDecimal("200000.00");
        BigDecimal irsa = engine.calculerIrsa(baseFaible, null, 0);

        assertEquals(0, new BigDecimal("3000.00").compareTo(irsa), "Même sans impôt calculé, le minimum légal de 3 000 Ar s'applique");
    }

    @Test
    @DisplayName("CALC-04 : IRSA — Déduction pour enfants à charge (2 000 Ar par enfant)")
    void testCalculerIrsaDeductionEnfants() {
        BigDecimal baseImposable = new BigDecimal("800000.00");

        // Barème par défaut : (800 000 - 350 000) * 20% = 90 000 Ar brut
        BigDecimal irsaSansEnfants = engine.calculerIrsa(baseImposable, null, 0);
        assertEquals(0, new BigDecimal("90000.00").compareTo(irsaSansEnfants));

        // Avec 3 enfants : 90 000 - (3 * 2 000) = 84 000 Ar
        BigDecimal irsa3Enfants = engine.calculerIrsa(baseImposable, null, 3);
        assertEquals(0, new BigDecimal("84000.00").compareTo(irsa3Enfants));
    }

    @Test
    @DisplayName("CALC-05 : IRSA — Barème progressif complet à tranches multiples")
    void testCalculerIrsaTranchesMultiples() {
        List<CalculateurPaieEngine.TrancheIrsa> tranches = new ArrayList<>();
        tranches.add(new CalculateurPaieEngine.TrancheIrsa(BigDecimal.ZERO, new BigDecimal("350000.00"), 0.00));
        tranches.add(new CalculateurPaieEngine.TrancheIrsa(new BigDecimal("350000.00"), new BigDecimal("400000.00"), 5.00));
        tranches.add(new CalculateurPaieEngine.TrancheIrsa(new BigDecimal("400000.00"), new BigDecimal("500000.00"), 10.00));
        tranches.add(new CalculateurPaieEngine.TrancheIrsa(new BigDecimal("500000.00"), new BigDecimal("600000.00"), 15.00));
        tranches.add(new CalculateurPaieEngine.TrancheIrsa(new BigDecimal("600000.00"), null, 20.00));

        // Base 550 000 Ar :
        // 0-350k : 0
        // 350k-400k (50k) * 5% = 2 500 Ar
        // 400k-500k (100k) * 10% = 10 000 Ar
        // 500k-550k (50k) * 15% = 7 500 Ar
        // Total = 20 000 Ar
        BigDecimal base = new BigDecimal("550000.00");
        BigDecimal irsa = engine.calculerIrsa(base, tranches, 0);

        assertEquals(0, new BigDecimal("20000.00").compareTo(irsa), "L'impôt calculé par tranches progressives doit être 20 000 Ar");
    }
}
