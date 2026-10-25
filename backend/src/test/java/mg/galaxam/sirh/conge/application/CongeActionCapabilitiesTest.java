package mg.galaxam.sirh.conge.application;

import mg.galaxam.sirh.common.dto.ActionCapability;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CongeActionCapabilitiesTest {
    @Test
    void proprietairePeutModifierUneDemandePersonnelleEnAttente() {
        ActionCapability action = CongeService.editCapability("EN_ATTENTE", false, true, false, "INVALID_STATE");
        assertTrue(action.allowed());
    }

    @Test
    void administrateurConserveLaModificationDesDemandesEnAttente() {
        ActionCapability action = CongeService.editCapability("EN_ATTENTE", true, false, false, "INVALID_STATE");
        assertTrue(action.allowed());
    }

    @Test
    void proprietaireNePeutPasModifierUneDemandeValideeParLaModificationGenerique() {
        ActionCapability action = CongeService.editCapability("VALIDE", false, true, false, "INVALID_STATE");
        assertFalse(action.allowed());
        assertEquals("INVALID_STATE", action.reasonCodes().get(0));
    }

    @Test
    void proprieteNeLevePasLeVerrouDePeriodeEtUnTiersResteInterdit() {
        ActionCapability verrou = CongeService.editCapability("EN_ATTENTE", false, true, true, "PAYROLL_PERIOD_CLOSED");
        ActionCapability tiers = CongeService.editCapability("EN_ATTENTE", false, false, false, "INVALID_STATE");
        assertEquals("PAYROLL_PERIOD_CLOSED", verrou.reasonCodes().get(0));
        assertEquals("FORBIDDEN", tiers.reasonCodes().get(0));
    }
}
