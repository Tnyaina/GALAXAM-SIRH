package mg.galaxam.sirh.unit.paie;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mg.galaxam.sirh.paie.api.dto.PayrollCampaignStep;
import org.junit.jupiter.api.Test;

class PayrollCampaignStepTest {

    @Test
    void blockedStepIsNotAccessible() {
        var step = new PayrollCampaignStep("VALIDATION", "Validation", "BLOCKED", "/validation",
                List.of("Le calcul est obsolète."), List.of());

        assertThat(step.accessible()).isFalse();
        assertThat(step.readOnly()).isFalse();
    }

    @Test
    void lockedStepRemainsAccessibleInReadOnlyMode() {
        var step = new PayrollCampaignStep("OUTPUTS", "Sorties", "LOCKED", "/sorties", List.of(), List.of());

        assertThat(step.accessible()).isTrue();
        assertThat(step.readOnly()).isTrue();
    }
}
