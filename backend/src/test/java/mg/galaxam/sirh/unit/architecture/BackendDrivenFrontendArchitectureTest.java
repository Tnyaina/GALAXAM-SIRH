package mg.galaxam.sirh.unit.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BackendDrivenFrontendArchitectureTest {

    @Test
    void payrollPageDoesNotSelectTheCurrentPeriodOrRebuildActionRules() throws Exception {
        String page = Files.readString(Path.of("../frontend/src/pages/paie/GestionPaiePage.jsx"));
        String cockpit = Files.readString(Path.of("../frontend/src/components/paie/CockpitPaieTab.jsx"));

        assertThat(page).doesNotContain("liste[0]", ".find(p => p.codeStatut");
        assertThat(cockpit).doesNotContain(
                "isCloture", "isValide",
                "disabled={actionLoading !== null || bulletins.length === 0}",
                "disabled={actionLoading !== null || !pointageScelle}");
        assertThat(cockpit).contains("availableActions[action]?.allowed === true");
    }
}
