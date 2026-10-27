package mg.galaxam.sirh.contrat.application;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import mg.galaxam.sirh.contrat.api.dto.EmployeRequest;
import org.junit.jupiter.api.Test;

class EmployeServiceSatelliteNullTest {
    @Test
    void lesHelpersRefusentUnElementNullAvantToutAccesBase() {
        EmployeService service = new EmployeService(null, null, null);

        EmployeRequest enfantNull = new EmployeRequest();
        enfantNull.setEnfants(Arrays.asList((EmployeRequest.EnfantRequest) null));
        assertThrows(IllegalArgumentException.class, () -> service.appliquerEnfants(1, enfantNull, null));

        EmployeRequest contactNull = new EmployeRequest();
        contactNull.setContactsUrgence(Arrays.asList((EmployeRequest.ContactUrgenceRequest) null));
        assertThrows(IllegalArgumentException.class, () -> service.appliquerContactsUrgence(1, contactNull, null));
    }
}
