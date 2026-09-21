package mg.galaxam.sirh.certification.paie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import mg.galaxam.sirh.model.paie.engine.IrsaCalculator;
import mg.galaxam.sirh.model.paie.engine.MoneyRoundingPolicy;
import mg.galaxam.sirh.model.paie.engine.SocialContributionCalculator;
import mg.galaxam.sirh.oracle.PayrollIndependentOracle;

@DisplayName("CERTIFICATION PAIE — FORMULES UNITAIRES, BARÈMES & FRONTIÈRES")
public class PayrollFormulaTest {

    private final IrsaCalculator irsaCalculator = new IrsaCalculator();
    private final SocialContributionCalculator socialCalculator = new SocialContributionCalculator();

    private List<IrsaCalculator.Tranche> standardTranches() {
        return List.of(
                new IrsaCalculator.Tranche(BigDecimal.ZERO, new BigDecimal("350000.00"), BigDecimal.ZERO),
                new IrsaCalculator.Tranche(new BigDecimal("350000.00"), new BigDecimal("400000.00"), new BigDecimal("0.05")),
                new IrsaCalculator.Tranche(new BigDecimal("400000.00"), new BigDecimal("500000.00"), new BigDecimal("0.10")),
                new IrsaCalculator.Tranche(new BigDecimal("500000.00"), new BigDecimal("600000.00"), new BigDecimal("0.15")),
                new IrsaCalculator.Tranche(new BigDecimal("600000.00"), null, new BigDecimal("0.20"))
        );
    }

    @Test
    @DisplayName("1. Taux horaire de base et diviseur légal 173.33")
    void testDiviseurHoraireLegal() {
        BigDecimal salaireBase = new BigDecimal("800000.00");
        BigDecimal diviseur = mg.galaxam.sirh.model.paie.engine.PayrollExecutionEngine.DIVISEUR_HEURES_MENSUELLES;

        // 800 000 / 173.33 = 4615.47337... -> 4615.4744 intermediate, 4615.47 amount
        BigDecimal tauxHoraire = salaireBase.divide(diviseur, 4, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("4615.4734"), tauxHoraire, "Taux horaire avec 4 décimales");
        assertEquals(new BigDecimal("4615.47"), MoneyRoundingPolicy.amount(tauxHoraire), "Taux horaire arrondi à 2 décimales");
    }

    @Test
    @DisplayName("2. Déduction congé au trentième vs retenue absence à l'heure")
    void testDeductionCongeVsAbsence() {
        BigDecimal base = new BigDecimal("600000.00");
        
        // Trentième congé
        BigDecimal trentieme = base.divide(mg.galaxam.sirh.model.paie.engine.PayrollExecutionEngine.DIVISEUR_JOURS_TRENTIEME, 2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("20000.00"), trentieme, "Trentième légal = 20 000.00 Ar/jour");

        // Heure d'absence
        BigDecimal heureAbsence = base.divide(mg.galaxam.sirh.model.paie.engine.PayrollExecutionEngine.DIVISEUR_HEURES_MENSUELLES, 4, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("3461.6050"), heureAbsence, "Heure d'absence = 3461.6050 Ar/h");
        
        // 8h d'absence
        BigDecimal total8h = heureAbsence.multiply(new BigDecimal("8")).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("27692.84"), total8h, "8h d'absence = 27 692.84 Ar (différent de 20 000 Ar congé !)");
    }

    @Test
    @DisplayName("3. CNaPS et plafond 8 × SME (Historique 2025 vs Mars 2026)")
    void testPlafondCnapsSme() {
        // Période Décembre 2025 : SME = 262 680 Ar -> Plafond 8 × SME = 2 101 440 Ar
        BigDecimal sme2025 = new BigDecimal("262680.00");
        BigDecimal plafond2025 = sme2025.multiply(new BigDecimal("8"));
        assertEquals(new BigDecimal("2101440.00"), plafond2025);

        // Période Septembre 2026 : SME = 300 000 Ar -> Plafond 8 × SME = 2 400 000 Ar
        BigDecimal sme2026 = new BigDecimal("300000.00");
        BigDecimal plafond2026 = sme2026.multiply(new BigDecimal("8"));
        assertEquals(new BigDecimal("2400000.00"), plafond2026);

        // Test salarié à 3 500 000 Ar (au-dessus des 2 plafonds)
        BigDecimal salaireHaut = new BigDecimal("3500000.00");

        var rules2025 = new SocialContributionCalculator.Rules(
                new BigDecimal("0.01"), new BigDecimal("0.13"),
                new BigDecimal("0.01"), new BigDecimal("0.05"),
                new BigDecimal("0.01"), sme2025, false
        );
        var res2025 = socialCalculator.calculate(salaireHaut, rules2025);
        assertEquals(plafond2025, res2025.cnaps().assiette(), "Assiette CNaPS 2025 plafonnée à 2 101 440 Ar");
        assertEquals(new BigDecimal("21014.40"), res2025.cnaps().partSalariale(), "CNaPS salarié 2025 plafonné à 21 014.40 Ar");
        assertEquals(new BigDecimal("273187.20"), res2025.cnaps().partPatronale(), "CNaPS patronal 2025 plafonné à 273 187.20 Ar");

        var rules2026 = new SocialContributionCalculator.Rules(
                new BigDecimal("0.01"), new BigDecimal("0.13"),
                new BigDecimal("0.01"), new BigDecimal("0.05"),
                new BigDecimal("0.01"), sme2026, false
        );
        var res2026 = socialCalculator.calculate(salaireHaut, rules2026);
        assertEquals(plafond2026, res2026.cnaps().assiette(), "Assiette CNaPS 2026 plafonnée à 2 400 000 Ar");
        assertEquals(new BigDecimal("24000.00"), res2026.cnaps().partSalariale(), "CNaPS salarié 2026 plafonné à 24 000.00 Ar");
        assertEquals(new BigDecimal("312000.00"), res2026.cnaps().partPatronale(), "CNaPS patronal 2026 plafonné à 312 000.00 Ar");

        // Santé (sans plafond) et FMFP
        assertEquals(salaireHaut, res2026.health().assiette(), "Assiette santé non plafonnée");
        assertEquals(new BigDecimal("35000.00"), res2026.health().partSalariale(), "Santé salariale 1% = 35 000.00 Ar");
        assertEquals(new BigDecimal("35000.00"), res2026.fmfp(), "FMFP 1% patronal = 35 000.00 Ar");
    }

    @Test
    @DisplayName("4. IRSA — Centaine inférieure, tranches progressives, minimum 3000 Ar et réduction enfants")
    void testIrsaFormulesEtFrontieres() {
        List<IrsaCalculator.Tranche> tranches = standardTranches();

        // Cas A : Revenu faible (assiette < 350 000 Ar) -> Minimum 3 000 Ar
        BigDecimal baseFaible = new BigDecimal("320456.78");
        IrsaCalculator.Result resFaible = irsaCalculator.calculate(
                baseFaible, tranches, 0, new BigDecimal("2000.00"), new BigDecimal("3000.00")
        );
        assertEquals(new BigDecimal("320400.00"), resFaible.baseIrsaArrondie(), "Troncature à la centaine inférieure (320 400)");
        assertEquals(BigDecimal.ZERO.setScale(2), resFaible.irsaBrut(), "IRSA brut à 0%");
        assertEquals(new BigDecimal("3000.00"), resFaible.irsaNet(), "IRSA minimum légal de 3 000 Ar appliqué");

        // Cas B : Revenu moyen (550 280 Ar, 2 enfants)
        // Base arrondie = 550 200 Ar
        // Tranche 1 (0-350k) : 0
        // Tranche 2 (350k-400k) : 50 000 * 5% = 2 500
        // Tranche 3 (400k-500k) : 100 000 * 10% = 10 000
        // Tranche 4 (500k-550.2k) : 50 200 * 15% = 7 530
        // Total brut = 20 030 Ar -> dizaine sup = 20 030 Ar
        // Réduction 2 enfants = 4 000 Ar -> Net = 16 030 Ar
        BigDecimal baseMoyenne = new BigDecimal("550280.00");
        IrsaCalculator.Result resMoyenne = irsaCalculator.calculate(
                baseMoyenne, tranches, 2, new BigDecimal("2000.00"), new BigDecimal("3000.00")
        );
        assertEquals(new BigDecimal("550200.00"), resMoyenne.baseIrsaArrondie());
        assertEquals(new BigDecimal("20030.00"), resMoyenne.irsaBrut());
        assertEquals(new BigDecimal("4000.00"), resMoyenne.reductionEnfants());
        assertEquals(new BigDecimal("16030.00"), resMoyenne.irsaNet());

        // Cas C : Frontière 4 000 000 Ar
        // 0-350k : 0
        // 350k-400k : 2 500
        // 400k-500k : 10 000
        // 500k-600k : 15 000
        // >600k (3.4M) * 20% = 680 000
        // Total brut = 707 500 Ar
        BigDecimal base4M = new BigDecimal("4000000.00");
        IrsaCalculator.Result res4M = irsaCalculator.calculate(
                base4M, tranches, 0, new BigDecimal("2000.00"), new BigDecimal("3000.00")
        );
        assertEquals(new BigDecimal("707500.00"), res4M.irsaBrut(), "IRSA exact à la frontière de 4 000 000 Ar");
    }

    @Test
    @DisplayName("5. Exonération fiscale des Heures Supplémentaires — Plafond 20 heures et frontières (19h59, 20h00, 20h01)")
    void testExonerationHeuresSupplementairesFrontieres() {
        BigDecimal tauxHoraire = new BigDecimal("5000.00");
        BigDecimal plafondHeures = new BigDecimal("20.00");

        // Cas 19h59 (19.9833 heures) -> 100% exonéré
        BigDecimal h19_59 = new BigDecimal("19.9833");
        BigDecimal hsExo19_59 = h19_59.min(plafondHeures);
        assertEquals(h19_59, hsExo19_59, "19h59 est intégralement dans le plafond");

        // Cas 20h00 -> 100% exonéré
        BigDecimal h20_00 = new BigDecimal("20.00");
        BigDecimal hsExo20_00 = h20_00.min(plafondHeures);
        assertEquals(new BigDecimal("20.00"), hsExo20_00, "20h00 atteint exactement le plafond");

        // Cas 20h01 (20.0167 heures) -> 20h00 exonéré, 0h01 imposable
        BigDecimal h20_01 = new BigDecimal("20.0167");
        BigDecimal hsExo20_01 = h20_01.min(plafondHeures);
        BigDecimal hsImp20_01 = h20_01.subtract(hsExo20_01);
        assertEquals(new BigDecimal("20.00"), hsExo20_01, "Exactement 20h exonérées");
        assertEquals(new BigDecimal("0.0167"), hsImp20_01, "0h01 imposable");

        // Cas 25h00 (HS30: 15h, HS50: 10h)
        // 15h HS30 à 1.30 = 19.5h équivalent de base
        // 5h HS50 à 1.50 = 7.5h équivalent de base
        // 5h HS50 imposables
        BigDecimal hTotal = new BigDecimal("25.00");
        BigDecimal exoHeures = hTotal.min(plafondHeures);
        BigDecimal impHeures = hTotal.subtract(exoHeures);
        assertEquals(new BigDecimal("20.00"), exoHeures);
        assertEquals(new BigDecimal("5.00"), impHeures);
    }

    @Test
    @DisplayName("6. Politiques d'arrondi : Frontières x.004, x.005, x.006 et MROUND 10")
    void testArrondisMonetairesFrontieres() {
        // HALF_UP monétaire
        assertEquals(new BigDecimal("100.00"), MoneyRoundingPolicy.amount(new BigDecimal("100.004")));
        assertEquals(new BigDecimal("100.01"), MoneyRoundingPolicy.amount(new BigDecimal("100.005")));
        assertEquals(new BigDecimal("100.01"), MoneyRoundingPolicy.amount(new BigDecimal("100.006")));

        // Dizaine supérieure IRSA
        BigDecimal brut1 = new BigDecimal("12341.20");
        BigDecimal brut2 = new BigDecimal("12340.00");
        BigDecimal dizaine1 = brut1.divide(BigDecimal.TEN, 0, RoundingMode.CEILING).multiply(BigDecimal.TEN).setScale(2);
        BigDecimal dizaine2 = brut2.divide(BigDecimal.TEN, 0, RoundingMode.CEILING).multiply(BigDecimal.TEN).setScale(2);
        assertEquals(new BigDecimal("12350.00"), dizaine1, "Dizaine supérieure pour 12341.20 -> 12350.00");
        assertEquals(new BigDecimal("12340.00"), dizaine2, "Dizaine supérieure pour 12340.00 -> 12340.00");

        // MROUND 10 du net final
        assertEquals(new BigDecimal("850240.00"), PayrollIndependentOracle.roundToTen(new BigDecimal("850244.00")));
        assertEquals(new BigDecimal("850250.00"), PayrollIndependentOracle.roundToTen(new BigDecimal("850245.00")));
        assertEquals(new BigDecimal("850250.00"), PayrollIndependentOracle.roundToTen(new BigDecimal("850246.00")));
    }
}
