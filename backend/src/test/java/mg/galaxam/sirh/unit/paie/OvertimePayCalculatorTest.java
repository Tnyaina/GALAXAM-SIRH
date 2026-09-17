package mg.galaxam.sirh.unit.paie;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;
import mg.galaxam.sirh.model.paie.engine.OvertimePayCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests unitaires exhaustifs de OvertimePayCalculator selon la matrice normative Phase 1C :
 * PHASE_1C_LEGAL_PREMIUM_MATRIX_2026-09-17.md
 * Règle d'or : ZERO double paiement de l'heure de base.
 */
class OvertimePayCalculatorTest {

    private final OvertimePayCalculator calculator = new OvertimePayCalculator();
    private final BigDecimal TAUX_HORAIRE = new BigDecimal("2000.00"); // 2 000 Ar / heure

    @Test
    @DisplayName("Situation 1 : HS30 seule (41e à 48e heure) -> Facteur 1.30")
    void testSituation1_Hs30Seule() {
        PayrollCalculationInput input = new PayrollCalculationInput();
        input.setHeuresHs30(new BigDecimal("8.00"));
        input.setMajorationHs30(new BigDecimal("0.30"));

        OvertimePayCalculator.Result r = calculator.calculate(input, TAUX_HORAIRE);
        // 8h * 2000 * 1.30 = 20 800 Ar
        assertEquals(new BigDecimal("20800.00"), r.hs30());
        assertEquals(new BigDecimal("20800.00"), r.total());
    }

    @Test
    @DisplayName("Situation 1b : HS50 seule (au-delà de 48h) -> Facteur 1.50")
    void testSituation1b_Hs50Seule() {
        PayrollCalculationInput input = new PayrollCalculationInput();
        input.setHeuresHs50(new BigDecimal("4.00"));
        input.setMajorationHs50(new BigDecimal("0.50"));

        OvertimePayCalculator.Result r = calculator.calculate(input, TAUX_HORAIRE);
        // 4h * 2000 * 1.50 = 12 000 Ar
        assertEquals(new BigDecimal("12000.00"), r.hs50());
        assertEquals(new BigDecimal("12000.00"), r.total());
    }

    @Test
    @DisplayName("Situation 4 : Dimanche seul de jour -> Facteur 1.40 (Base 1.0 + Majoration 0.40)")
    void testSituation4_DimancheSeul() {
        PayrollCalculationInput input = new PayrollCalculationInput();
        input.setHeuresDimanche(new BigDecimal("8.00"));
        input.setMajorationDimanche(new BigDecimal("0.40"));

        OvertimePayCalculator.Result r = calculator.calculate(input, TAUX_HORAIRE);
        // 8h * 2000 * 1.40 = 22 400 Ar
        assertEquals(new BigDecimal("22400.00"), r.dimanche());
        assertEquals(new BigDecimal("22400.00"), r.total());
    }

    @Test
    @DisplayName("Situation 5 : Dimanche + HS30 (cumul additif sans double base) -> Facteur 1.70")
    void testSituation5_DimanchePlusHs30() {
        // Formule unifiée combinée pour 1h qui est à la fois Dimanche et HS30
        BigDecimal montantHeure = calculator.calculateCombinedHourRate(
                TAUX_HORAIRE,
                new BigDecimal("0.30"), // majorationHs
                true, new BigDecimal("0.40"), // Dimanche
                false, BigDecimal.ZERO,       // Férié
                false, BigDecimal.ZERO        // Nuit
        );
        // 1h * 2000 * (1.00 + 0.30 + 0.40) = 3 400 Ar (Facteur 1.70)
        // et NON (1.30 + 1.40 = 2.70 -> 5 400 Ar)
        assertEquals(new BigDecimal("3400.00"), montantHeure);
    }

    @Test
    @DisplayName("Situation 6 : Dimanche + HS50 (cumul additif sans double base) -> Facteur 1.90")
    void testSituation6_DimanchePlusHs50() {
        BigDecimal montantHeure = calculator.calculateCombinedHourRate(
                TAUX_HORAIRE,
                new BigDecimal("0.50"), // majorationHs50
                true, new BigDecimal("0.40"), // Dimanche
                false, BigDecimal.ZERO,
                false, BigDecimal.ZERO
        );
        // 1h * 2000 * (1.00 + 0.50 + 0.40) = 3 800 Ar (Facteur 1.90)
        assertEquals(new BigDecimal("3800.00"), montantHeure);
    }

    @Test
    @DisplayName("Situation 7 : Dimanche + Nuit -> UNRESOLVED par défaut, CUMUL sous convention plus favorable (3 400 Ar), MAX_PREMIUM (2 800 Ar)")
    void testSituation7_DimanchePlusNuit() {
        // Sans politique explicite : blocage obligatoire
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                calculator.calculateCombinedHourRate(
                        TAUX_HORAIRE,
                        BigDecimal.ZERO,
                        true, new BigDecimal("0.40"), // Dimanche
                        false, BigDecimal.ZERO,
                        true, new BigDecimal("0.30")  // Nuit
                )
        );

        // Sous convention collective plus favorable (CUMUL_ADDITIF) : 1h * 2000 * (1.00 + 0.40 + 0.30) = 3 400 Ar
        BigDecimal montantCumul = calculator.calculateCombinedHourRate(
                TAUX_HORAIRE,
                BigDecimal.ZERO,
                true, new BigDecimal("0.40"),
                false, BigDecimal.ZERO,
                true, new BigDecimal("0.30"),
                mg.galaxam.sirh.model.paie.engine.SpecialPremiumCollisionPolicy.CUMUL_ADDITIF
        );
        assertEquals(new BigDecimal("3400.00"), montantCumul);

        // Sous arbitrage MAX_PREMIUM : Dimanche 40% > Nuit 30% -> 1h * 2000 * (1.00 + 0.40) = 2 800 Ar
        BigDecimal montantMax = calculator.calculateCombinedHourRate(
                TAUX_HORAIRE,
                BigDecimal.ZERO,
                true, new BigDecimal("0.40"),
                false, BigDecimal.ZERO,
                true, new BigDecimal("0.30"),
                mg.galaxam.sirh.model.paie.engine.SpecialPremiumCollisionPolicy.MAX_PREMIUM
        );
        assertEquals(new BigDecimal("2800.00"), montantMax);
    }

    @Test
    @DisplayName("Situation 8 : Dimanche + Nuit + HS30 -> UNRESOLVED par défaut, CUMUL (4 000 Ar), MAX_PREMIUM (3 400 Ar)")
    void testSituation8_DimanchePlusNuitPlusHs() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                calculator.calculateCombinedHourRate(
                        TAUX_HORAIRE,
                        new BigDecimal("0.30"),       // HS30
                        true, new BigDecimal("0.40"), // Dimanche
                        false, BigDecimal.ZERO,
                        true, new BigDecimal("0.30")  // Nuit
                )
        );

        // Convention d'entreprise plus favorable
        BigDecimal montantCumul = calculator.calculateCombinedHourRate(
                TAUX_HORAIRE,
                new BigDecimal("0.30"),
                true, new BigDecimal("0.40"),
                false, BigDecimal.ZERO,
                true, new BigDecimal("0.30"),
                mg.galaxam.sirh.model.paie.engine.SpecialPremiumCollisionPolicy.CUMUL_ADDITIF
        );
        // 1h * 2000 * (1.00 + 0.30 + 0.40 + 0.30) = 4 000 Ar
        assertEquals(new BigDecimal("4000.00"), montantCumul);

        // Sous MAX_PREMIUM : HS 30% + Dimanche 40% (absorbe nuit 30%) -> 1h * 2000 * (1.00 + 0.30 + 0.40) = 3 400 Ar
        BigDecimal montantMax = calculator.calculateCombinedHourRate(
                TAUX_HORAIRE,
                new BigDecimal("0.30"),
                true, new BigDecimal("0.40"),
                false, BigDecimal.ZERO,
                true, new BigDecimal("0.30"),
                mg.galaxam.sirh.model.paie.engine.SpecialPremiumCollisionPolicy.MAX_PREMIUM
        );
        assertEquals(new BigDecimal("3400.00"), montantMax);
    }

    @Test
    @DisplayName("Situation 9 : Férié seul de jour -> Facteur 2.00 (Base 1.0 + Majoration 1.00)")
    void testSituation9_FerieSeul() {
        PayrollCalculationInput input = new PayrollCalculationInput();
        input.setHeuresFeriees(new BigDecimal("8.00"));
        input.setMajorationFerie(new BigDecimal("1.00"));

        OvertimePayCalculator.Result r = calculator.calculate(input, TAUX_HORAIRE);
        // 8h * 2000 * 2.00 = 32 000 Ar
        assertEquals(new BigDecimal("32000.00"), r.ferie());
        assertEquals(new BigDecimal("32000.00"), r.total());
    }

    @Test
    @DisplayName("Situation 10 : Férié + HS30 -> Facteur 2.30 (Base 1.0 + HS 0.30 + Férié 1.00)")
    void testSituation10_FeriePlusHs30() {
        BigDecimal montantHeure = calculator.calculateCombinedHourRate(
                TAUX_HORAIRE,
                new BigDecimal("0.30"), // HS30
                false, BigDecimal.ZERO,
                true, new BigDecimal("1.00"), // Férié
                false, BigDecimal.ZERO
        );
        // 1h * 2000 * (1.00 + 0.30 + 1.00) = 4 600 Ar (Facteur 2.30)
        assertEquals(new BigDecimal("4600.00"), montantHeure);
    }

    @Test
    @DisplayName("Situation 11 : Férié + HS50 -> Facteur 2.50 (Base 1.0 + HS 0.50 + Férié 1.00)")
    void testSituation11_FeriePlusHs50() {
        BigDecimal montantHeure = calculator.calculateCombinedHourRate(
                TAUX_HORAIRE,
                new BigDecimal("0.50"), // HS50
                false, BigDecimal.ZERO,
                true, new BigDecimal("1.00"), // Férié
                false, BigDecimal.ZERO
        );
        // 1h * 2000 * (1.00 + 0.50 + 1.00) = 5 000 Ar (Facteur 2.50)
        assertEquals(new BigDecimal("5000.00"), montantHeure);
    }

    @Test
    @DisplayName("Situation 12 : Férié + Nuit -> UNRESOLVED par défaut, CUMUL (4 600 Ar), MAX_PREMIUM (4 000 Ar)")
    void testSituation12_FeriePlusNuit() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                calculator.calculateCombinedHourRate(
                        TAUX_HORAIRE,
                        BigDecimal.ZERO,
                        false, BigDecimal.ZERO,
                        true, new BigDecimal("1.00"), // Férié
                        true, new BigDecimal("0.30")  // Nuit
                )
        );

        BigDecimal montantCumul = calculator.calculateCombinedHourRate(
                TAUX_HORAIRE,
                BigDecimal.ZERO,
                false, BigDecimal.ZERO,
                true, new BigDecimal("1.00"),
                true, new BigDecimal("0.30"),
                mg.galaxam.sirh.model.paie.engine.SpecialPremiumCollisionPolicy.CUMUL_ADDITIF
        );
        assertEquals(new BigDecimal("4600.00"), montantCumul);

        BigDecimal montantMax = calculator.calculateCombinedHourRate(
                TAUX_HORAIRE,
                BigDecimal.ZERO,
                false, BigDecimal.ZERO,
                true, new BigDecimal("1.00"),
                true, new BigDecimal("0.30"),
                mg.galaxam.sirh.model.paie.engine.SpecialPremiumCollisionPolicy.MAX_PREMIUM
        );
        assertEquals(new BigDecimal("4000.00"), montantMax);
    }

    @Test
    @DisplayName("Situation 13 : Férié + Nuit + HS30 -> UNRESOLVED par défaut, CUMUL (5 200 Ar), MAX_PREMIUM (4 600 Ar)")
    void testSituation13_FeriePlusNuitPlusHs() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                calculator.calculateCombinedHourRate(
                        TAUX_HORAIRE,
                        new BigDecimal("0.30"),       // HS30
                        false, BigDecimal.ZERO,
                        true, new BigDecimal("1.00"), // Férié
                        true, new BigDecimal("0.30")  // Nuit
                )
        );

        BigDecimal montantCumul = calculator.calculateCombinedHourRate(
                TAUX_HORAIRE,
                new BigDecimal("0.30"),
                false, BigDecimal.ZERO,
                true, new BigDecimal("1.00"),
                true, new BigDecimal("0.30"),
                mg.galaxam.sirh.model.paie.engine.SpecialPremiumCollisionPolicy.CUMUL_ADDITIF
        );
        assertEquals(new BigDecimal("5200.00"), montantCumul);

        BigDecimal montantMax = calculator.calculateCombinedHourRate(
                TAUX_HORAIRE,
                new BigDecimal("0.30"),
                false, BigDecimal.ZERO,
                true, new BigDecimal("1.00"),
                true, new BigDecimal("0.30"),
                mg.galaxam.sirh.model.paie.engine.SpecialPremiumCollisionPolicy.MAX_PREMIUM
        );
        assertEquals(new BigDecimal("4600.00"), montantMax);
    }

    @Test
    @DisplayName("Situation 14 : Férié tombant un Dimanche -> UNRESOLVED par défaut, MAX_PREMIUM (Férié 1.00 retient 4 000 Ar)")
    void testSituation14_FerieTombantDimanche() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                calculator.calculateCombinedHourRate(
                        TAUX_HORAIRE,
                        BigDecimal.ZERO,
                        true, new BigDecimal("0.40"), // Dimanche
                        true, new BigDecimal("1.00"), // Férié
                        false, BigDecimal.ZERO
                )
        );

        BigDecimal montantHeure = calculator.calculateCombinedHourRate(
                TAUX_HORAIRE,
                BigDecimal.ZERO,
                true, new BigDecimal("0.40"), // Dimanche
                true, new BigDecimal("1.00"), // Férié (prime sur dimanche)
                false, BigDecimal.ZERO,
                mg.galaxam.sirh.model.paie.engine.SpecialPremiumCollisionPolicy.MAX_PREMIUM
        );
        // 1h * 2000 * (1.00 + max(0.40, 1.00)) = 4 000 Ar (Facteur 2.00)
        assertEquals(new BigDecimal("4000.00"), montantHeure);
    }

    @Test
    @DisplayName("Situation 15 : Férié + Dimanche + Nuit -> UNRESOLVED par défaut, MAX_PREMIUM (4 000 Ar), CUMUL (5 400 Ar)")
    void testSituation15_FeriePlusDimanchePlusNuit() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                calculator.calculateCombinedHourRate(
                        TAUX_HORAIRE,
                        BigDecimal.ZERO,
                        true, new BigDecimal("0.40"), // Dimanche
                        true, new BigDecimal("1.00"), // Férié
                        true, new BigDecimal("0.30")  // Nuit
                )
        );

        BigDecimal montantMax = calculator.calculateCombinedHourRate(
                TAUX_HORAIRE,
                BigDecimal.ZERO,
                true, new BigDecimal("0.40"), // Dimanche
                true, new BigDecimal("1.00"), // Férié (absorbe dimanche et nuit)
                true, new BigDecimal("0.30"),  // Nuit
                mg.galaxam.sirh.model.paie.engine.SpecialPremiumCollisionPolicy.MAX_PREMIUM
        );
        // 1h * 2000 * (1.00 + max(0.40, 1.00, 0.30)) = 4 000 Ar (Facteur 2.00)
        assertEquals(new BigDecimal("4000.00"), montantMax);

        BigDecimal montantCumul = calculator.calculateCombinedHourRate(
                TAUX_HORAIRE,
                BigDecimal.ZERO,
                true, new BigDecimal("0.40"),
                true, new BigDecimal("1.00"),
                true, new BigDecimal("0.30"),
                mg.galaxam.sirh.model.paie.engine.SpecialPremiumCollisionPolicy.CUMUL_ADDITIF
        );
        // 1h * 2000 * (1.00 + 0.40 + 1.00 + 0.30) = 5 400 Ar (Facteur 2.70)
        assertEquals(new BigDecimal("5400.00"), montantCumul);
    }

    @Test
    @DisplayName("Nuit seule en jour ouvrable normal -> Incrément seul (+30%)")
    void testNuitSeuleJourOuvrable() {
        PayrollCalculationInput input = new PayrollCalculationInput();
        input.setHeuresNuit(new BigDecimal("5.00"));
        input.setMajorationNuit(new BigDecimal("0.30"));

        OvertimePayCalculator.Result r = calculator.calculate(input, TAUX_HORAIRE);
        // 5h * 2000 * 0.30 = 3 000 Ar (Incrément pur, l'heure de base est dans SALAIRE_BASE)
        assertEquals(new BigDecimal("3000.00"), r.nuit());
        assertEquals(new BigDecimal("3000.00"), r.total());
    }
}
