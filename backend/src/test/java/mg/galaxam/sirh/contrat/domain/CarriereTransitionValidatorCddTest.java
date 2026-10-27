package mg.galaxam.sirh.contrat.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CarriereTransitionValidatorCddTest {

    @Test
    void laRechercheDeReembaucheTypeExplicitementLaDateAvantLaSoustractionIntervalle() {
        assertTrue(CarriereTransitionValidator.SQL_RECHERCHE_CDD_REEMBAUCHE
                .contains("CAST(? AS date) - INTERVAL '3 months'"));
    }

    @Test
    void accepteLesTroisMotifsLegauxPourUnCdd() {
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerMotifCDD("CDD", "OUVRAGE_DETERMINE"));
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerMotifCDD("CDD", "SURCROIT_EXTRAORDINAIRE"));
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerMotifCDD("CDD", "TRAVAUX_URGENTS_SECURITE"));
    }

    @Test
    void exigeUnMotifLegalPourUnCdd() {
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> CarriereTransitionValidator.validerMotifCDD("CDD", null))
                .getMessage().contains("motif légal"));
        assertThrows(IllegalArgumentException.class,
                () -> CarriereTransitionValidator.validerMotifCDD("CDD", "MOTIF_LIBRE"));
    }

    @Test
    void interditLeMotifCddSurUnAutreTypeDeContrat() {
        assertDoesNotThrow(() -> CarriereTransitionValidator.validerMotifCDD("CDI", null));
        assertThrows(IllegalArgumentException.class,
                () -> CarriereTransitionValidator.validerMotifCDD("CDI", "OUVRAGE_DETERMINE"));
    }
}
