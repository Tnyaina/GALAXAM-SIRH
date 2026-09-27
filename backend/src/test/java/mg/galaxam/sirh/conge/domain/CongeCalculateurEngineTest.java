package mg.galaxam.sirh.conge.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CongeCalculateurEngineTest {

    private final CongeCalculateurEngine engine = new CongeCalculateurEngine();

    @Test
    void decompteUneDemiJourneeSansArrondiFlottant() {
        assertEquals(new BigDecimal("0.5"), engine.calculerNombreJours(
                LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 23), true));
    }

    @Test
    void acquisitionProratiseeUtiliseLeTauxEtLeDiviseurFournis() {
        assertEquals(new BigDecimal("1.2500"), engine.calculerAcquisitionMensuelleProratisee(
                new BigDecimal("15"), new BigDecimal("2.5"), new BigDecimal("30")));
    }

    @Test
    void simulationExpliqueSoldePreavisEtPoolSansDerogation() {
        var result = engine.simuler(new CongeCalculateurEngine.SimulationInput(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5), false,
                new BigDecimal("3"), true, null, null, false, true, false,
                LocalDate.of(2026, 9, 23), 3, 2, 15,
                new BigDecimal("8"), new BigDecimal("10")));

        assertEquals(new BigDecimal("5.0"), result.nbJours());
        assertEquals(new BigDecimal("-2.0"), result.soldeApres());
        assertEquals(new BigDecimal("13.0"), result.quotaApres());
        assertTrue(result.violations().contains(CongeCalculateurEngine.LEAVE_BALANCE_INSUFFICIENT));
        assertTrue(result.violations().contains(CongeCalculateurEngine.LEAVE_NOTICE_NOT_RESPECTED));
        assertTrue(result.violations().contains(CongeCalculateurEngine.LEAVE_POOL_EXCEEDED));
        assertFalse(result.canSubmit());
    }

    @Test
    void derogationsTransformentLesBlocagesEnAvertissements() {
        var result = engine.simuler(new CongeCalculateurEngine.SimulationInput(
                LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 28), false,
                BigDecimal.ZERO, true, null, null, true, true, true,
                LocalDate.of(2026, 9, 23), 3, 2, 15,
                new BigDecimal("9"), new BigDecimal("10")));

        assertTrue(result.violations().isEmpty());
        assertEquals(3, result.warnings().size());
        assertTrue(result.requiresOverride());
        assertTrue(result.canSubmit());
    }

    @Test
    void dureeFixeEstCalculeeInclusivement() {
        assertEquals(LocalDate.of(2026, 12, 8), engine.calculerDateFinFixe(
                LocalDate.of(2026, 9, 2), new BigDecimal("98")));
    }

    @Test
    void acquisitionPleineEtBonificationRespectentLesParametres() {
        assertEquals(new BigDecimal("5.0"), engine.calculerAcquisitionMensuelle(2));
        assertEquals(3, engine.calculerBonificationAnciennete(15, 5, 5, 1));
    }

    @Test
    void preavisBasculeExactementApresLeSeuilCourt() {
        assertEquals(2, engine.calculerPreavisRequis(new BigDecimal("3"), true, 3, 2, 15));
        assertEquals(15, engine.calculerPreavisRequis(new BigDecimal("3.5"), true, 3, 2, 15));
        assertEquals(0, engine.calculerPreavisRequis(new BigDecimal("10"), false, 3, 2, 15));
    }

    @Test
    void soldeProjeteNeBougePasPourUnTypeNonDecompte() {
        assertEquals(new BigDecimal("7.5"), engine.calculerSoldeProjete(
                new BigDecimal("7.5"), new BigDecimal("2"), false));
    }

    @Test
    void poolAccepteLaLimiteEtRefuseLeDepassement() {
        BigDecimal plafondConfigure = new BigDecimal("10");
        assertTrue(engine.verifierEligibilitePool(new BigDecimal("8"), new BigDecimal("2"), plafondConfigure));
        assertFalse(engine.verifierEligibilitePool(new BigDecimal("8"), new BigDecimal("2.5"), plafondConfigure));
    }

    @Test
    void tcongeUtiliseBigDecimalEtArronditAuCentime() {
        assertEquals(new BigDecimal("8669.52"), engine.calculerTauxJournalierMoyen(
                new BigDecimal("260085.60"), new BigDecimal("30")));
        assertEquals(new BigDecimal("0.00"), engine.calculerTauxJournalierMoyen(
                new BigDecimal("100"), BigDecimal.ZERO));
    }
}
