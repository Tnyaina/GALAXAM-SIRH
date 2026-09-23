package mg.galaxam.sirh.integration.paie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.paieconfig.persistence.RefTauxMajoration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class Phase1cDatabaseAndIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("28. Audit des taux réels dans ref_taux_majoration")
    void auditRefTauxMajoration() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            List<RefTauxMajoration> list = RefTauxMajoration.listAll(c);
            assertThat(list).isNotEmpty();

            LocalDate testDate = LocalDate.of(2026, 9, 17);
            RefTauxMajoration hs30 = RefTauxMajoration.resolveApplicable(2, "HS_30", testDate, c);
            assertThat(hs30.getTaux()).isEqualByComparingTo("0.30");

            RefTauxMajoration hs50 = RefTauxMajoration.resolveApplicable(2, "HS_50", testDate, c);
            assertThat(hs50.getTaux()).isEqualByComparingTo("0.50");

            RefTauxMajoration nuit = RefTauxMajoration.resolveApplicable(2, "NUIT", testDate, c);
            assertThat(nuit.getTaux()).isEqualByComparingTo("0.30");

            RefTauxMajoration dimanche = RefTauxMajoration.resolveApplicable(2, "DIMANCHE", testDate, c);
            assertThat(dimanche.getTaux()).isEqualByComparingTo("0.40");

            RefTauxMajoration ferie = RefTauxMajoration.resolveApplicable(2, "FERIE", testDate, c);
            assertThat(ferie.getTaux()).isEqualByComparingTo("1.00");
        }
    }

    @Test
    @DisplayName("27. Résolution stricte sans fallback de RefTauxMajoration")
    void testRefTauxMajorationStrictResolution() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            LocalDate testDate = LocalDate.of(2026, 9, 17);

            // Type inexistant -> exception explicite
            assertThatThrownBy(() -> RefTauxMajoration.resolveApplicable(2, "TYPE_INCONNU", testDate, c))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MAJORATION_CONFIGURATION_INVALID");

            // Config inexistante -> exception explicite
            assertThatThrownBy(() -> RefTauxMajoration.resolveApplicable(9999, "HS_30", testDate, c))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MAJORATION_CONFIGURATION_INVALID");
        }
    }

    @Test
    @DisplayName("28.2. Application de la migration Phase 1C et vérification des colonnes de pointage_cumul_periode")
    void auditAndApplyMigrationPointageCumulPeriodeColumns() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (Statement s = c.createStatement()) {
                s.execute("ALTER TABLE pointage_cumul_periode " +
                          "ADD COLUMN IF NOT EXISTS heures_dimanche_total DECIMAL(6,2) NOT NULL DEFAULT 0, " +
                          "ADD COLUMN IF NOT EXISTS heures_ferie_total DECIMAL(6,2) NOT NULL DEFAULT 0;");
            }
            DatabaseMetaData meta = c.getMetaData();
            List<String> columns = new ArrayList<>();
            try (ResultSet rs = meta.getColumns(null, null, "pointage_cumul_periode", null)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
            }
            System.out.println("Colonnes pointage_cumul_periode après migration : " + columns);
            assertThat(columns).contains("total_heures_travaillees", "heures_hs_30", "heures_hs_50", "heures_nuit_total",
                    "heures_dimanche_total", "heures_ferie_total");
        }
    }
}
