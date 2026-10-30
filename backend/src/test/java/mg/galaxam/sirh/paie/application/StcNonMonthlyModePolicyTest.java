package mg.galaxam.sirh.paie.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StcNonMonthlyModePolicyTest {
    @Test
    void salaireMensuelPeutEtreEstimeSansBulletinFinal() {
        assertDoesNotThrow(() -> StcService.exigerBulletinFinalPourModeNonMensuel("MENSUEL", false));
    }

    @Test
    void tauxHoraireEtJournalierExigentLeBulletinFinal() {
        for (String mode : new String[] {"HORAIRE", "JOURNALIER"}) {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> StcService.exigerBulletinFinalPourModeNonMensuel(mode, false));
            assertTrue(error.getMessage().contains("STC_NON_MONTHLY_FINAL_PAYSLIP_REQUIRED"));
            assertDoesNotThrow(() -> StcService.exigerBulletinFinalPourModeNonMensuel(mode, true));
        }
    }

    @Test
    void conventionDeStageNeProduitPasDeStcSalarie() {
        assertThrows(IllegalArgumentException.class,
                () -> StcService.exigerRegimeSalariePourStc("STAGIAIRE"));
        assertDoesNotThrow(() -> StcService.exigerRegimeSalariePourStc("TEMPORAIRE"));
        assertDoesNotThrow(() -> StcService.exigerRegimeSalariePourStc("PERMANENT"));
    }
}
