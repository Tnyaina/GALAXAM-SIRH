package mg.galaxam.sirh.model.conge.engine;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CongeCalculateurEngineTest {

    private CongeCalculateurEngine engine;

    @BeforeEach
    void setUp() {
        engine = new CongeCalculateurEngine();
    }

    @Test
    @DisplayName("Calcul du nombre de jours calendaires (samedi et dimanche inclus)")
    void testCalculNombreJours() {
        LocalDate debut = LocalDate.of(2026, 8, 1);
        LocalDate fin = LocalDate.of(2026, 8, 10);

        BigDecimal jours = engine.calculerNombreJours(debut, fin, false);
        assertEquals(new BigDecimal("10.0"), jours, "Du 1er au 10 août = 10 jours calendaires.");

        BigDecimal demiJournee = engine.calculerNombreJours(debut, debut, true);
        assertEquals(new BigDecimal("0.5"), demiJournee, "Une demi-journée doit valoir 0.5 j.");
    }

    @Test
    @DisplayName("Calcul de l'acquisition mensuelle légale")
    void testAcquisitionMensuelle() {
        BigDecimal acquis12Mois = engine.calculerAcquisitionMensuelle(12);
        assertEquals(new BigDecimal("30.0"), acquis12Mois, "12 mois * 2.5j = 30.0j.");

        BigDecimal acquis6Mois = engine.calculerAcquisitionMensuelle(6);
        assertEquals(new BigDecimal("15.0"), acquis6Mois, "6 mois * 2.5j = 15.0j.");
    }

    @Test
    @DisplayName("Calcul de la bonification d'ancienneté (+1j par tranche de 5 ans)")
    void testBonificationAnciennete() {
        assertEquals(0, engine.calculerBonificationAnciennete(2));
        assertEquals(1, engine.calculerBonificationAnciennete(5));
        assertEquals(1, engine.calculerBonificationAnciennete(8));
        assertEquals(2, engine.calculerBonificationAnciennete(10));
        assertEquals(3, engine.calculerBonificationAnciennete(15));
    }

    @Test
    @DisplayName("Scission automatique de l'arrêt maladie au-delà de 30 jours")
    void testScissionArretMaladie() {
        assertFalse(engine.doitScinderArretMaladie(new BigDecimal("15.0")));
        assertFalse(engine.doitScinderArretMaladie(new BigDecimal("30.0")));
        assertTrue(engine.doitScinderArretMaladie(new BigDecimal("31.0")));
        assertTrue(engine.doitScinderArretMaladie(new BigDecimal("45.0")));
    }
}
