package mg.galaxam.sirh.unit.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkflowAssistantArchitectureTest {

    @Test
    void pointageDoesNotSelectPeriodsOrComposeWorkflowFromStatuses() throws Exception {
        String orchestrator = Files.readString(Path.of("../frontend/src/pages/pointage/GestionPointagesPage.jsx"));
        String followUp = Files.readString(Path.of("../frontend/src/pages/pointage/SuiviAnomaliesPage.jsx"));
        String arbitration = Files.readString(Path.of("../frontend/src/pages/pointage/ArbitrageDafPage.jsx"));

        assertThat(orchestrator).doesNotContain("list[0]", "statutCode ===");
        assertThat(followUp).doesNotContain("resPeriodes[0]", "stCode ===", "canTransmitDaf = isEnCours");
        assertThat(followUp).contains("actions.SUBMIT?.allowed === true");
        assertThat(arbitration).doesNotContain("periodes[0]", "stCode ===");
        assertThat(arbitration).contains("periodActions.APPROVE?.allowed === true");
    }

    @Test
    void leaveActionsAndEmployeeSectionsComeFromStableContracts() throws Exception {
        String leaveTable = Files.readString(Path.of(
                "../frontend/src/components/conges/validation/ValidationTable.jsx"));
        String router = Files.readString(Path.of("../frontend/src/routes/Router.jsx"));

        assertThat(leaveTable).contains(
                "availableActions?.APPROVE?.allowed === true",
                "availableActions?.MODIFY_DATES?.allowed === true");
        assertThat(leaveTable).doesNotContain("new Date(d.dateDebut)");
        assertThat(router).contains("/employes/:id/:section");
    }
}
