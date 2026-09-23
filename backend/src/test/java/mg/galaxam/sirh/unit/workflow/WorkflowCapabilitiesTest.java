package mg.galaxam.sirh.unit.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.time.LocalDate;
import mg.galaxam.sirh.conge.persistence.VDemandeCongeCpl;
import org.junit.jupiter.api.Test;

class WorkflowCapabilitiesTest {

    @Test
    void pendingLeaveExposesOneCoherentActionMessage() {
        VDemandeCongeCpl request = new VDemandeCongeCpl();
        request.setCodeStatutDemande("EN_ATTENTE");
        request.setDateDebut(Date.valueOf(LocalDate.now().plusDays(5)));

        assertThat(request.getAvailableActions().get("APPROVE").allowed()).isTrue();
        assertThat(request.getAvailableActions().get("REJECT").allowed()).isTrue();
        assertThat(request.getAvailableActions().get("EDIT").allowed()).isTrue();
        assertThat(request.getAvailableActions().get("MODIFY_DATES").allowed()).isFalse();
    }

    @Test
    void startedApprovedLeaveCannotHaveItsDatesChangedByTheClient() {
        VDemandeCongeCpl request = new VDemandeCongeCpl();
        request.setCodeStatutDemande("VALIDE");
        request.setDateDebut(Date.valueOf(LocalDate.now().minusDays(1)));

        assertThat(request.getAvailableActions().get("CANCEL").allowed()).isTrue();
        assertThat(request.getAvailableActions().get("MODIFY_DATES").allowed()).isFalse();
        assertThat(request.getAvailableActions().get("MODIFY_DATES").reasonCodes())
                .containsExactly("LEAVE_ALREADY_STARTED");
    }
}
