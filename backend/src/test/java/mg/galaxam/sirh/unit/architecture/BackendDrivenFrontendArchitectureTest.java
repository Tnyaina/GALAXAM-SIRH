package mg.galaxam.sirh.unit.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
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

    @Test
    void payrollWorkflowRoutesAreShareableAndLegacyBulletinsCannotRenderBlank() throws Exception {
        String router = Files.readString(Path.of("../frontend/src/routes/Router.jsx"));
        String page = Files.readString(Path.of("../frontend/src/pages/paie/GestionPaiePage.jsx"));

        assertThat(router).contains(
                "/paie/periods/:periodId/:step",
                "LegacyPayrollRedirect",
                "/paie/periodes/${periodId}/${canonicalStep}");
        assertThat(page).contains("rawTab === 'bulletins'");
        assertThat(page).doesNotContain("/paie/periods/");
    }

    @Test
    void payrollWorkspaceResponseCarriesTheCanonicalDecisionContext() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/mg/galaxam/sirh/api/controller/paie/PaieController.java"));
        String cockpit = Files.readString(Path.of("../frontend/src/components/paie/CockpitPaieTab.jsx"));

        assertThat(controller).contains("cockpit.put(\"context\", payrollPeriodWorkflowService.describeContext(");
        assertThat(cockpit).contains("const decisionContext = cockpit?.context || periodContext");
    }

    @Test
    void frontendDoesNotHideEmployeesBehindHardCodedBulkLimits() throws Exception {
        try (Stream<Path> files = Files.walk(Path.of("../frontend/src"))) {
            String sources = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".js") || path.toString().endsWith(".jsx"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .reduce("", String::concat);

            assertThat(sources).doesNotContain("size: 1000", "size: 10000", "size=1000", "size=10000");
        }
    }

    @Test
    void payrollResultsUseTheSharedServerPaginationContract() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/mg/galaxam/sirh/api/controller/paie/PaieController.java"));
        String service = Files.readString(Path.of(
                "src/main/java/mg/galaxam/sirh/model/paie/service/PayrollService.java"));
        String cockpit = Files.readString(Path.of("../frontend/src/components/paie/CockpitPaieTab.jsx"));

        assertThat(controller).contains("@RequestParam(value = \"page\"", "@RequestParam(value = \"size\"");
        assertThat(service).contains("cockpit.put(\"bulletinsPage\"", "cockpit.put(\"populationPage\"");
        assertThat(cockpit).contains("<Pagination", "bulletinsPage.totalElements", "useDebounce(searchQuery, 300)");
        assertThat(cockpit).doesNotContain("const matchSearch", "const matchSite", "let matchFilter");
    }

    @Test
    void payrollAdjustmentsUseCommandsAndNotClientStateMutations() throws Exception {
        String paieService = Files.readString(Path.of("../frontend/src/api/paie/paieService.js"));
        assertThat(paieService).contains("paie/ajustements/${idAjustement}/commands");
        assertThat(paieService).doesNotContain("apiClient.put(`paie/ajustements");
    }

    @Test
    void dashboardHeaderUsesBackendDrivenNextStep() throws Exception {
        String header = Files.readString(Path.of("../frontend/src/components/dashboard/DashboardHeader.jsx"));
        assertThat(header).contains("periodContext?.nextStep");
        assertThat(header).doesNotContain("isDaf", "period?.codeStatut === 'VALIDEE'");
    }
}
