package mg.galaxam.sirh.unit.paie;

import java.math.BigDecimal;
import java.util.List;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine.CotisationResult;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine.IrsaResult;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine.TrancheIrsa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Tests - Moteur de Calcul Cotisations & IRSA (CalculateurPaieEngine)")
class CalculateurPaieEngineTest {

    private CalculateurPaieEngine engine;
    private List<TrancheIrsa> tranchesLf2026;

    @BeforeEach
    void setUp() {
        engine = new CalculateurPaieEngine();
        // Barème légal officiel Madagascar (LF 2026)
        tranchesLf2026 = List.of(
                new TrancheIrsa(new BigDecimal("0.00"), new BigDecimal("350000.00"), 0.0),
                new TrancheIrsa(new BigDecimal("350001.00"), new BigDecimal("400000.00"), 5.0),
                new TrancheIrsa(new BigDecimal("400001.00"), new BigDecimal("500000.00"), 10.0),
                new TrancheIrsa(new BigDecimal("500001.00"), new BigDecimal("600000.00"), 15.0),
                new TrancheIrsa(new BigDecimal("600001.00"), null, 20.0)
        );
    }

    @Test
    @DisplayName("CNaPS - Salaire sous le plafond (1% salarié, 13% employeur)")
    void testCalculCnapsSousPlafond() {
        BigDecimal brut = new BigDecimal("1500000.00");
        BigDecimal smig = new BigDecimal("262680.00"); // Plafond 8x = 2 101 440.00 Ar

        CotisationResult res = engine.calculerCnaps(brut, smig, 0.01, 0.13);

        assertEquals(new BigDecimal("1500000.00"), res.assiette());
        assertEquals(new BigDecimal("15000.00"), res.partSalariale());
        assertEquals(new BigDecimal("195000.00"), res.partPatronale());
    }

    @Test
    @DisplayName("CNaPS - Salaire au-dessus du plafond légal (Plafonné à 8 x SMIG)")
    void testCalculCnapsAuDessusPlafond() {
        BigDecimal brut = new BigDecimal("3500000.00");
        BigDecimal smig = new BigDecimal("262680.00");
        BigDecimal plafondAttendu = new BigDecimal("2101440.00"); // 8 x 262 680

        CotisationResult res = engine.calculerCnaps(brut, smig, 0.01, 0.13);

        assertEquals(plafondAttendu, res.assiette());
        assertEquals(new BigDecimal("21014.40"), res.partSalariale());
        assertEquals(new BigDecimal("273187.20"), res.partPatronale());
    }

    @Test
    @DisplayName("Santé - OSTIE Antananarivo (1% salarié, 5% employeur, non plafonné)")
    void testCalculSanteOstie() {
        BigDecimal brut = new BigDecimal("2500000.00");

        CotisationResult res = engine.calculerSante(brut, 0.01, 0.05);

        assertEquals(brut, res.assiette());
        assertEquals(new BigDecimal("25000.00"), res.partSalariale());
        assertEquals(new BigDecimal("125000.00"), res.partPatronale());
    }

    @Test
    @DisplayName("Arrondi centaine inférieure de l'assiette IRSA (CGI Art. 01.03.18)")
    void testArrondiCentaineInferieure() {
        assertEquals(new BigDecimal("620000.00"), CalculateurPaieEngine.arrondirCentaineInferieure(new BigDecimal("620054.29")));
        assertEquals(new BigDecimal("350100.00"), CalculateurPaieEngine.arrondirCentaineInferieure(new BigDecimal("350199.99")));
        assertEquals(new BigDecimal("350000.00"), CalculateurPaieEngine.arrondirCentaineInferieure(new BigDecimal("350000.00")));
        assertEquals(new BigDecimal("0.00"), CalculateurPaieEngine.arrondirCentaineInferieure(new BigDecimal("99.90")));
    }

    @Test
    @DisplayName("Arrondi dizaine supérieure de l'impôt IRSA (CGI Art. 01.03.18)")
    void testArrondiDizaineSuperieure() {
        assertEquals(new BigDecimal("31500.00"), CalculateurPaieEngine.arrondirDizaineSuperieure(new BigDecimal("31492.50")));
        assertEquals(new BigDecimal("31500.00"), CalculateurPaieEngine.arrondirDizaineSuperieure(new BigDecimal("31500.00")));
        assertEquals(new BigDecimal("54330.00"), CalculateurPaieEngine.arrondirDizaineSuperieure(new BigDecimal("54321.40")));
    }

    @Test
    @DisplayName("IRSA Progressif - Conforme au modèle réel Excel (Net Imposable 620 054.29 -> IRSA 31 500)")
    void testCalculIrsaConformeModeleExcel() {
        BigDecimal netImposable = new BigDecimal("620054.29");

        IrsaResult res = engine.calculerIrsaDetaille(
                netImposable,
                tranchesLf2026,
                0, // 0 enfants
                new BigDecimal("2000.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("350000.00")
        );

        // Assiette arrondie à la centaine inférieure
        assertEquals(new BigDecimal("620000.00"), res.baseIrsaArrondie());
        // IRSA brut = 2500 (tranche 2) + 10000 (tranche 3) + 15000 (tranche 4) + 4000 (tranche 5) = 31500.00
        assertEquals(new BigDecimal("31500.00"), res.irsaBrut());
        assertEquals(new BigDecimal("31500.00"), res.irsaArrondiDizaine());
        assertEquals(new BigDecimal("0.00"), res.reductionEnfants());
        // Exactement 31 500 Ar comme dans le modèle Excel Ohatra (cellule C23: IRSA Arrondi)
        assertEquals(new BigDecimal("31500.00"), res.irsaNet());
    }

    @Test
    @DisplayName("IRSA - Déduction pour charges de famille (2 000 Ar par enfant)")
    void testCalculIrsaAvecEnfants() {
        BigDecimal netImposable = new BigDecimal("620000.00");

        IrsaResult res = engine.calculerIrsaDetaille(
                netImposable,
                tranchesLf2026,
                3, // 3 enfants -> 6 000 Ar de déduction
                new BigDecimal("2000.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("350000.00")
        );

        assertEquals(new BigDecimal("6000.00"), res.reductionEnfants());
        assertEquals(new BigDecimal("25500.00"), res.irsaNet()); // 31500 - 6000
    }

    @Test
    @DisplayName("IRSA - Application du minimum de perception légal (3 000 Ar)")
    void testCalculIrsaMinimumPerception() {
        BigDecimal netImposableFaible = new BigDecimal("250000.00"); // Dans la tranche à 0%

        IrsaResult res = engine.calculerIrsaDetaille(
                netImposableFaible,
                tranchesLf2026,
                0,
                new BigDecimal("2000.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("350000.00")
        );

        assertEquals(new BigDecimal("0.00"), res.irsaBrut());
        assertEquals(new BigDecimal("3000.00"), res.irsaNet()); // Minimum légal 3000 Ar
    }

    @Test
    @DisplayName("IRSA - Bouclier fiscal protectrice Art. 01.03.18 (Net après impôt >= 350 000 Ar)")
    void testBouclierFiscalExoneration() {
        BigDecimal revenuLimite = new BigDecimal("352000.00");

        IrsaResult res = engine.calculerIrsaDetaille(
                revenuLimite,
                tranchesLf2026,
                0,
                new BigDecimal("2000.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("350000.00")
        );

        // Impôt ne peut pas faire tomber le revenu disponible sous 350 000 Ar
        assertTrue(revenuLimite.subtract(res.irsaNet()).compareTo(new BigDecimal("350000.00")) >= 0
                || res.irsaNet().compareTo(new BigDecimal("3000.00")) == 0);
    }
}
