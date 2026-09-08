package mg.galaxam.sirh.paieconfig.engine;

import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CalculateurPaieEngine")
class CalculateurPaieEngineTest {

    private CalculateurPaieEngine engine;

    @BeforeEach
    void setUp() { engine = new CalculateurPaieEngine(); }

    @Nested @DisplayName("calculerCnaps (plafond 8xSMIG)")
    class CalculerCnaps {

        private static final BigDecimal SMIG = new BigDecimal("262680.00");

        @Test @DisplayName("Salaire sous plafond : cotisation sur salaire reel")
        void sousPlafonnd() {
            // salaire = 500 000, taux sal=1%, taux pat=13% -> assiette=500000
            var r = engine.calculerCnaps(new BigDecimal("500000"), SMIG, 0.01, 0.13);
            assertEquals(0, new BigDecimal("500000").compareTo(r.assiette()));
            assertEquals(0, new BigDecimal("5000.00").compareTo(r.partSalariale()));
            assertEquals(0, new BigDecimal("65000.00").compareTo(r.partPatronale()));
        }

        @Test @DisplayName("Salaire au-dessus plafond : assiette = 8xSMIG")
        void audessusPlafond() {
            // SMIG=262680, 8xSMIG=2101440
            BigDecimal plafond = SMIG.multiply(BigDecimal.valueOf(8));
            BigDecimal salaireEleve = plafond.add(new BigDecimal("500000"));
            var r = engine.calculerCnaps(salaireEleve, SMIG, 0.01, 0.13);
            assertEquals(0, plafond.compareTo(r.assiette()));
        }

        @Test @DisplayName("Salaire null ou 0 -> tout a 0")
        void salaireNulOuZero() {
            var r = engine.calculerCnaps(BigDecimal.ZERO, SMIG, 0.01, 0.13);
            assertEquals(0, BigDecimal.ZERO.compareTo(r.partSalariale()));
            assertEquals(0, BigDecimal.ZERO.compareTo(r.partPatronale()));
        }

        @Test @DisplayName("smig null -> utilise SMIG par defaut (262680)")
        void smigNull() {
            var r = engine.calculerCnaps(new BigDecimal("100000"), null, 0.01, 0.01);
            assertNotNull(r);
            // doit fonctionner sans NPE
            assertTrue(r.partSalariale().compareTo(BigDecimal.ZERO) >= 0);
        }
    }

    @Nested @DisplayName("calculerSante")
    class CalculerSante {

        @Test @DisplayName("Pas de plafond : cotisation sur salaire brut total")
        void cotisationSurBrut() {
            var r = engine.calculerSante(new BigDecimal("1000000"), 0.01, 0.05);
            assertEquals(0, new BigDecimal("10000.00").compareTo(r.partSalariale()));
            assertEquals(0, new BigDecimal("50000.00").compareTo(r.partPatronale()));
        }

        @Test @DisplayName("Salaire zero -> tout a 0")
        void salaireZero() {
            var r = engine.calculerSante(BigDecimal.ZERO, 0.01, 0.05);
            assertEquals(0, BigDecimal.ZERO.compareTo(r.partSalariale()));
        }
    }

    @Nested @DisplayName("calculerIrsa (bareme progressif)")
    class CalculerIrsa {

        private List<CalculateurPaieEngine.TrancheIrsa> tranches() {
            return List.of(
                new CalculateurPaieEngine.TrancheIrsa(BigDecimal.ZERO, new BigDecimal("350000"), 0.0),
                new CalculateurPaieEngine.TrancheIrsa(new BigDecimal("350000"), new BigDecimal("400000"), 5.0),
                new CalculateurPaieEngine.TrancheIrsa(new BigDecimal("400000"), new BigDecimal("500000"), 10.0),
                new CalculateurPaieEngine.TrancheIrsa(new BigDecimal("500000"), null, 20.0)
            );
        }

        @Test @DisplayName("Base <= 350000 -> minimum perception (3000)")
        void exonereMinimum() {
            BigDecimal irsa = engine.calculerIrsa(new BigDecimal("300000"), tranches(), 0);
            assertEquals(0, CalculateurPaieEngine.MINIMUM_PERCEPTION_IRSA.compareTo(irsa));
        }

        @Test @DisplayName("Minimum de perception = 3000 (constante)")
        void minimumPerception() {
            assertEquals(0, new BigDecimal("3000.00")
                .compareTo(CalculateurPaieEngine.MINIMUM_PERCEPTION_IRSA));
        }

        @Test @DisplayName("Deduction par enfant = 2000 (constante)")
        void deductionEnfant() {
            assertEquals(0, new BigDecimal("2000.00")
                .compareTo(CalculateurPaieEngine.DEDUCTION_PAR_ENFANT));
        }

        @Test @DisplayName("Base null ou 0 -> minimum perception")
        void baseNulle() {
            BigDecimal irsa = engine.calculerIrsa(null, tranches(), 0);
            assertEquals(0, CalculateurPaieEngine.MINIMUM_PERCEPTION_IRSA.compareTo(irsa));
        }

        @Test @DisplayName("2 enfants a charge -> reduction de 4000")
        void deuxEnfants() {
            // Base imposable suffisante pour declencher impot
            BigDecimal base = new BigDecimal("600000");
            BigDecimal irsaSansEnfant = engine.calculerIrsa(base, tranches(), 0);
            BigDecimal irsaAvecEnfants = engine.calculerIrsa(base, tranches(), 2);
            assertEquals(0, irsaSansEnfant.subtract(new BigDecimal("4000")).max(CalculateurPaieEngine.MINIMUM_PERCEPTION_IRSA)
                .compareTo(irsaAvecEnfants));
        }

        @Test @DisplayName("Tranches vides -> leve IllegalStateException (aucun fallback silencieux)")
        void tranchesVides() {
            assertThrows(IllegalStateException.class, () ->
                engine.calculerIrsa(new BigDecimal("450000"), List.of(), 0));
        }

        @Test @DisplayName("Tranches null -> leve IllegalStateException (aucun fallback silencieux)")
        void tranchesNull() {
            assertThrows(IllegalStateException.class, () ->
                engine.calculerIrsa(new BigDecimal("450000"), null, 0));
        }

        @Test @DisplayName("Taux à 1% (1.0) : calculé à 0.01 et non 1.0 (100%)")
        void tauxUnPourcent() {
            var tranchesUnPct = List.of(
                new CalculateurPaieEngine.TrancheIrsa(BigDecimal.ZERO, null, 1.0)
            );
            // Sur 1 000 000 Ar à 1%, l'impôt brut est 10 000 Ar
            BigDecimal irsa = engine.calculerIrsa(new BigDecimal("1000000"), tranchesUnPct, 0);
            assertEquals(0, new BigDecimal("10000.00").compareTo(irsa));
        }
    }
}
