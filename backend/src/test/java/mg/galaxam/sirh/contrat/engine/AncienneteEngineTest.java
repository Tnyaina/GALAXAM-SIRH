package mg.galaxam.sirh.contrat.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import mg.galaxam.sirh.model.contrat.engine.AncienneteEngine;
import mg.galaxam.sirh.model.contrat.engine.AncienneteEngine.AncienneteBilan;
import mg.galaxam.sirh.model.contrat.engine.AncienneteEngine.IntervalleContrat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AncienneteEngine — Tests unitaires de calcul d'ancienneté")
class AncienneteEngineTest {

    private AncienneteEngine engine;

    @BeforeEach
    void setUp() {
        engine = new AncienneteEngine();
    }

    @Test
    @DisplayName("1. Contrat unique fermé d'un an")
    void testContratUniqueFerme() {
        IntervalleContrat c1 = new IntervalleContrat(
                1,
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2023, 12, 31),
                null
        );

        AncienneteBilan bilan = engine.evaluerAnciennete(List.of(c1), 1, LocalDate.of(2024, 1, 1));

        assertNotNull(bilan);
        assertEquals(1, bilan.ansEffectifs());
        assertEquals(0, bilan.moisRestantsEffectifs());
        assertEquals(12, bilan.moisEffectifs());
        assertEquals("1 an", bilan.ancienneteEffectiveTexte());
        assertEquals(1, bilan.nombreContratsChaine());
    }

    @Test
    @DisplayName("2. Contrat en cours sans date de fin")
    void testContratEnCours() {
        IntervalleContrat c1 = new IntervalleContrat(
                10,
                LocalDate.of(2024, 1, 1),
                null,
                null
        );

        // Évaluation 2 ans et 6 mois plus tard : 1er Juillet 2026
        LocalDate refDate = LocalDate.of(2026, 7, 1);
        AncienneteBilan bilan = engine.evaluerAnciennete(List.of(c1), 10, refDate);

        assertNotNull(bilan);
        assertEquals(2, bilan.ansEffectifs());
        assertEquals(6, bilan.moisRestantsEffectifs());
        assertEquals(30, bilan.moisEffectifs());
        assertEquals("2 ans 6 mois", bilan.ancienneteEffectiveTexte());
        assertEquals(1, bilan.nombreContratsChaine());
    }

    @Test
    @DisplayName("3. Chaîne de 3 contrats successifs (Stage -> CDD -> CDI)")
    void testChaineTroisContrats() {
        IntervalleContrat stage = new IntervalleContrat(
                1,
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2020, 6, 30),
                null
        );
        IntervalleContrat cdd = new IntervalleContrat(
                2,
                LocalDate.of(2020, 7, 1),
                LocalDate.of(2021, 6, 30),
                1 // lié au stage
        );
        IntervalleContrat cdi = new IntervalleContrat(
                3,
                LocalDate.of(2021, 7, 1),
                null,
                2 // lié au cdd
        );

        // Évaluation au 1er Juillet 2024 (exactement 4 ans et 6 mois au total)
        LocalDate refDate = LocalDate.of(2024, 7, 1);
        AncienneteBilan bilan = engine.evaluerAnciennete(List.of(stage, cdd, cdi), 3, refDate);

        assertNotNull(bilan);
        assertEquals(3, bilan.nombreContratsChaine());
        assertEquals(4, bilan.ansEffectifs());
        assertEquals(6, bilan.moisRestantsEffectifs());
        assertEquals("4 ans 6 mois", bilan.ancienneteEffectiveTexte());

        // L'ancienneté spécifique au CDI actuel doit être de 3 ans
        assertEquals(3, bilan.moisContratActuel() / 12);
        assertEquals("3 ans", bilan.ancienneteContratTexte());
    }

    @Test
    @DisplayName("4. Durée inférieure à 1 mois (ex: 15 jours)")
    void testMoinsDunMois() {
        IntervalleContrat court = new IntervalleContrat(
                5,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 15),
                null
        );

        AncienneteBilan bilan = engine.evaluerAnciennete(List.of(court), 5, LocalDate.of(2026, 2, 1));
        assertNotNull(bilan);
        assertEquals(0, bilan.ansEffectifs());
        assertEquals(0, bilan.moisRestantsEffectifs());
        assertEquals("Moins d'un mois", bilan.ancienneteEffectiveTexte());
    }

    @Test
    @DisplayName("5. Cas limite : liste vide ou contrat introuvable")
    void testCasLimites() {
        AncienneteBilan vide1 = engine.evaluerAnciennete(List.of(), 1, LocalDate.now());
        assertEquals("—", vide1.ancienneteEffectiveTexte());
        assertEquals(0, vide1.nombreContratsChaine());

        AncienneteBilan vide2 = engine.evaluerAnciennete(null, null, null);
        assertEquals("—", vide2.ancienneteEffectiveTexte());
    }

    @Test
    @DisplayName("6. Protection contre les cycles dans la chaîne de contrats")
    void testProtectionCycle() {
        // Cycle artificiel A -> B -> A
        IntervalleContrat a = new IntervalleContrat(1, LocalDate.of(2022, 1, 1), LocalDate.of(2022, 6, 30), 2);
        IntervalleContrat b = new IntervalleContrat(2, LocalDate.of(2022, 7, 1), LocalDate.of(2022, 12, 31), 1);

        // Ne doit jamais boucler indéfiniment
        AncienneteBilan bilan = engine.evaluerAnciennete(List.of(a, b), 2, LocalDate.of(2023, 1, 1));
        assertNotNull(bilan);
        assertEquals(2, bilan.nombreContratsChaine());
    }
}
