package mg.galaxam.sirh.conge.engine;

import mg.galaxam.sirh.model.conge.engine.CongeCalculateurEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CongeCalculateurEngine")
class CongeCalculateurEngineTest {

    private CongeCalculateurEngine engine;

    @BeforeEach
    void setUp() { engine = new CongeCalculateurEngine(); }

    @Nested @DisplayName("calculerNombreJours")
    class CalculerNombreJours {

        @Test @DisplayName("Jour unique = 1.0")
        void jourUnique() {
            LocalDate d = LocalDate.of(2024, 1, 15);
            assertEquals(new BigDecimal("1.0"), engine.calculerNombreJours(d, d, false));
        }

        @Test @DisplayName("5 jours = 5.0")
        void cinqJours() {
            assertEquals(new BigDecimal("5.0"),
                engine.calculerNombreJours(LocalDate.of(2024,1,1), LocalDate.of(2024,1,5), false));
        }

        @Test @DisplayName("Demi-journee meme date = 0.5")
        void demiJournee() {
            LocalDate d = LocalDate.of(2024, 6, 10);
            assertEquals(new BigDecimal("0.5"), engine.calculerNombreJours(d, d, true));
        }

        @Test @DisplayName("Demi-journee dates differentes -> exception")
        void demiJourneeDatesifferentes() {
            assertThrows(IllegalArgumentException.class,
                () -> engine.calculerNombreJours(LocalDate.of(2024,1,1), LocalDate.of(2024,1,2), true));
        }

        @Test @DisplayName("Fin avant debut -> 0")
        void finAvantDebut() {
            assertEquals(BigDecimal.ZERO,
                engine.calculerNombreJours(LocalDate.of(2024,5,10), LocalDate.of(2024,5,1), false));
        }

        @Test @DisplayName("debut null -> exception")
        void debutNull() {
            assertThrows(IllegalArgumentException.class,
                () -> engine.calculerNombreJours(null, LocalDate.now(), false));
        }

        @Test @DisplayName("fin null -> exception")
        void finNull() {
            assertThrows(IllegalArgumentException.class,
                () -> engine.calculerNombreJours(LocalDate.now(), null, false));
        }
    }

    @Nested @DisplayName("calculerAcquisitionMensuelle")
    class CalculerAcquisitionMensuelle {

        @Test @DisplayName("1 mois = 2.5")
        void unMois() { assertEquals(new BigDecimal("2.5"), engine.calculerAcquisitionMensuelle(1)); }

        @Test @DisplayName("12 mois = 30.0")
        void douzeMois() { assertEquals(new BigDecimal("30.0"), engine.calculerAcquisitionMensuelle(12)); }

        @Test @DisplayName("0 mois -> 0")
        void zeroMois() { assertEquals(BigDecimal.ZERO, engine.calculerAcquisitionMensuelle(0)); }

        @Test @DisplayName("Negatif -> 0")
        void negatif() { assertEquals(BigDecimal.ZERO, engine.calculerAcquisitionMensuelle(-5)); }
    }

    @Nested @DisplayName("calculerBonificationAnciennete")
    class CalculerBonificationAnciennete {

        @Test @DisplayName("4 ans -> 0")
        void quatreAns() { assertEquals(0, engine.calculerBonificationAnciennete(4)); }

        @Test @DisplayName("5 ans -> 1")
        void cinqAns() { assertEquals(1, engine.calculerBonificationAnciennete(5)); }

        @Test @DisplayName("10 ans -> 2")
        void dixAns() { assertEquals(2, engine.calculerBonificationAnciennete(10)); }

        @Test @DisplayName("7 ans -> 1 (division entiere)")
        void septAns() { assertEquals(1, engine.calculerBonificationAnciennete(7)); }
    }

    @Nested @DisplayName("doitScinderArretMaladie (Art. 13, plafond 30j)")
    class DoitScinderArretMaladie {

        @Test @DisplayName("29j -> false")
        void vingNeufJ() { assertFalse(engine.doitScinderArretMaladie(new BigDecimal("29"))); }

        @Test @DisplayName("30j -> false")
        void trenteJ() { assertFalse(engine.doitScinderArretMaladie(new BigDecimal("30"))); }

        @Test @DisplayName("31j -> true")
        void trenteEtUnJ() { assertTrue(engine.doitScinderArretMaladie(new BigDecimal("31"))); }

        @Test @DisplayName("null -> false")
        void nullVal() { assertFalse(engine.doitScinderArretMaladie(null)); }
    }

    @Nested @DisplayName("verifierEligibilitePoolFamilial (plafond 10j/an)")
    class VerifierEligibilitePoolFamilial {

        @Test @DisplayName("0 + 5 -> eligible")
        void eligible() { assertTrue(engine.verifierEligibilitePoolFamilial(BigDecimal.ZERO, new BigDecimal("5"))); }

        @Test @DisplayName("5 + 5 -> eligible exact")
        void eligibleExact() { assertTrue(engine.verifierEligibilitePoolFamilial(new BigDecimal("5"), new BigDecimal("5"))); }

        @Test @DisplayName("5 + 6 -> non eligible")
        void nonEligible() { assertFalse(engine.verifierEligibilitePoolFamilial(new BigDecimal("5"), new BigDecimal("6"))); }

        @Test @DisplayName("null consomme -> traite comme 0")
        void nullConsomme() { assertTrue(engine.verifierEligibilitePoolFamilial(null, new BigDecimal("3"))); }
    }
}
