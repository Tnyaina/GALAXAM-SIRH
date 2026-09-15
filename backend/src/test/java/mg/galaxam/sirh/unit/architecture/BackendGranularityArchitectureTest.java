package mg.galaxam.sirh.unit.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BackendGranularityArchitectureTest {

    @Test
    void migratedApplicationBoundariesDoNotManageJdbcTransactions() throws Exception {
        String leaveController = source("api/controller/conge/DemandeCongeController.java");
        String contractController = source("api/controller/contrat/ContratTravailController.java");
        String payroll = source("model/paie/service/PayrollService.java");

        assertThat(leaveController).doesNotContain("setAutoCommit(false)", ".commit()", ".rollback()");
        assertThat(contractController).doesNotContain("setAutoCommit(false)", ".commit()", ".rollback()");
        assertThat(payroll).doesNotContain("setAutoCommit(false)", ".commit()", ".rollback()");
    }

    @Test
    void payrollLegalContextHasOneOwner() throws Exception {
        String payroll = source("model/paie/service/PayrollService.java");
        String resolver = source("model/paie/service/PayrollLegalContextResolver.java");

        assertThat(payroll).doesNotContain("chargerParametresLegaux", "resolveHealthContribution");
        assertThat(payroll).containsOnlyOnce("payrollLegalContextResolver.resolve(in, c)");
        assertThat(resolver).contains("public void resolve(PayrollCalculationInput input, Connection connection)");
    }

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/mg/galaxam/sirh").resolve(relative));
    }
}
