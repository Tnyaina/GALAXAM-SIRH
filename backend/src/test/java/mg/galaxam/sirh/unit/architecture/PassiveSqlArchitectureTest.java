package mg.galaxam.sirh.unit.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PassiveSqlArchitectureTest {

    private static final List<Path> ACTIVE_SCHEMAS = List.of(
            Path.of("conception/bdd/01_schema_core_securite.sql"),
            Path.of("conception/bdd/02_schema_referentiels_rh.sql"),
            Path.of("conception/bdd/03_schema_employes_contrats.sql"),
            Path.of("conception/bdd/04_schema_conges.sql"),
            Path.of("conception/bdd/05_schema_pointage.sql"),
            Path.of("conception/bdd/06_schema_paie.sql"));

    @Test
    void activeSchemaDoesNotOwnWorkdayOrLegalConfigurationResolution() throws Exception {
        String activeSql = readActiveSchemas().toLowerCase();

        assertThat(activeSql)
                .doesNotContain("create or replace function fn_jours_ouvrables")
                .doesNotContain("create or replace view v_config_legale_active");
    }

    @Test
    void taxApplicabilityHasNoImplicitTrueFallback() throws Exception {
        String activeSql = readActiveSchemas().toLowerCase();
        String payrollSource = Files.readString(Path.of(
                "src/main/java/mg/galaxam/sirh/model/paie/service/PayrollService.java")).toLowerCase();

        assertThat(activeSql).doesNotContain(
                "coalesce(tri.soumis_cnaps, true)",
                "coalesce(tri.soumis_sante, true)",
                "coalesce(tri.soumis_irsa, true)");
        assertThat(payrollSource).doesNotContain(
                "coalesce(tri.soumis_cnaps, true)",
                "coalesce(pr.soumis_cnaps, true)",
                "coalesce(pr.soumis_irsa, true)");
    }

    private static String readActiveSchemas() throws Exception {
        StringBuilder result = new StringBuilder();
        for (Path schema : ACTIVE_SCHEMAS) result.append(Files.readString(schema));
        return result.toString();
    }
}
