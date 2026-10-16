package mg.galaxam.sirh.paie.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PayrollAdjustmentPaymentServiceTest {
    private final PayrollAdjustmentPaymentService service = new PayrollAdjustmentPaymentService(null);

    @Test
    void confirmationReutiliseLaMemeCapacitePourListeEtCommande() {
        assertTrue(service.canConfirm("ACOMPTE", "APPROUVE", null));
        assertTrue(service.canConfirm("AVANCE", "IMPUTE_PAIE", null));
        assertFalse(service.canConfirm("VARIABLE", "APPROUVE", null));
        assertFalse(service.canConfirm("AVANCE", "EN_ATTENTE", null));
        assertFalse(service.canConfirm("ACOMPTE", "APPROUVE", Timestamp.valueOf("2026-10-16 10:00:00")));
    }

    @Test
    void dossierEtActionsSontDerivesEnsemble() {
        var dossier = service.describe("AVANCE", "IMPUTE_PAIE", null,
                new BigDecimal("100"), new BigDecimal("900"), true);
        assertEquals("VERSEMENT_A_VERIFIER", dossier.etat());
        assertTrue(dossier.actions().get("CONFIRM_VERSEMENT"));
        assertFalse(dossier.actions().get("APPROVE"));
    }
}
