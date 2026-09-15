package mg.galaxam.sirh.unit.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HistoricalContextArchitectureTest {

    @Test
    void criticalHistoricalFlowsDoNotUseLatestOrClockFallbacks() throws Exception {
        String calendar = source("model/pointage/engine/CalendrierOuvrableEngine.java");
        String anomalies = source("model/pointage/service/PointageAnomalieService.java");
        String payroll = source("model/paie/service/PayrollService.java");
        String stc = source("model/paie/service/StcService.java");

        assertThat(calendar).doesNotContain("ORDER BY id_affectation DESC LIMIT 1");
        assertThat(anomalies).doesNotContain(
                "SELECT id_periode_paie FROM periode_paie ORDER BY date_debut DESC LIMIT 1",
                "LocalDate.now().withDayOfMonth");
        assertThat(stc).doesNotContain(
                "sqlLastPer",
                "dateDepart != null ? dateDepart :",
                "req.getDateDepart() != null ? req.getDateDepart().toLocalDate() : LocalDate.now()");
        assertThat(payroll).doesNotContain(
                "dateFin != null ? dateFin.toLocalDate() : java.time.LocalDate.now()",
                "in.getDateFinPeriode() != null\n                ? in.getDateFinPeriode().toLocalDate()\n                : java.time.LocalDate.now()");
    }

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/mg/galaxam/sirh").resolve(relative));
    }
}
