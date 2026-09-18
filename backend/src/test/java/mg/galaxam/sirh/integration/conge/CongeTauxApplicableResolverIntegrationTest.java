package mg.galaxam.sirh.integration.conge;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.model.conge.engine.TauxCongeResolution;
import mg.galaxam.sirh.model.conge.engine.TcongeSourceProvenance;
import mg.galaxam.sirh.model.conge.service.CongeTauxApplicableService;
import mg.galaxam.sirh.model.paie.service.StcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 2A-4C — Tests d'intégration du résolveur canonique CongeTauxApplicableService.
 * Couvre l'as-of strict, la détection d'ambiguïté (>1 taux), l'initialisation sans historique,
 * la reprise KAJY sans faux bulletins et l'alignement Paie/STC.
 */
@DisplayName("Phase 2A-4C : Résolveur canonique TCONGE applicable")
public class CongeTauxApplicableResolverIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CongeTauxApplicableService congeTauxApplicableService;

    private int testEmployeId;

    @BeforeEach
    public void setup() {
        databaseCleaner.cleanTransactionalData();
        testEmployeId = employeFixtures.createActiveEmploye("EMP-TCONGE-01", "RABARY", "Jean", 1).idEmploye;
    }

    @Test
    @DisplayName("shouldResolveBase30WhenNewEmployeeWithoutHistory")
    void shouldResolveBase30WhenNewEmployeeWithoutHistory() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            BigDecimal base = new BigDecimal("600000.00");
            LocalDate refDate = LocalDate.of(2026, 3, 15);

            TauxCongeResolution res = congeTauxApplicableService.resoudreTauxCongeApplicable(testEmployeId, refDate, base, c);

            assertThat(res).isNotNull();
            assertThat(res.getTauxJournalier()).isEqualByComparingTo("20000.00"); // 600 000 / 30
            assertThat(res.getSourceProvenance()).isEqualTo(TcongeSourceProvenance.INITIALISATION_BASE_30);
            assertThat(res.getIdTauxApplicable()).isNull(); // Pas d'insertion en BDD pour un fallback
        }
    }

    @Test
    @DisplayName("shouldThrowConfigurationMissingWhenNoRateAndNoSalaryAvailable")
    void shouldThrowConfigurationMissingWhenNoRateAndNoSalaryAvailable() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            LocalDate refDate = LocalDate.of(2026, 3, 15);

            assertThatThrownBy(() -> congeTauxApplicableService.resoudreTauxCongeApplicable(testEmployeId, refDate, null, c))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TCONGE_CONFIGURATION_MISSING");
        }
    }

    @Test
    @DisplayName("shouldApplyKajyMigrationRateWithoutFakeHistory")
    void shouldApplyKajyMigrationRateWithoutFakeHistory() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            BigDecimal tauxKajy = new BigDecimal("8669.52");
            LocalDate dateEffet = LocalDate.of(2026, 1, 1);
            LocalDate dateFin = LocalDate.of(2026, 8, 1);

            int idTaux = congeTauxApplicableService.enregistrerTauxApplicableMigration(
                    testEmployeId, tauxKajy, dateEffet, dateFin, "Reprise KAJY historique", 1, c
            );
            assertThat(idTaux).isPositive();

            // Résolution pendant la période d'application
            LocalDate datePendant = LocalDate.of(2026, 4, 15);
            TauxCongeResolution res = congeTauxApplicableService.resoudreTauxCongeApplicable(
                    testEmployeId, datePendant, new BigDecimal("400000.00"), c
            );

            assertThat(res.getTauxJournalier()).isEqualByComparingTo("8669.52");
            assertThat(res.getSourceProvenance()).isEqualTo(TcongeSourceProvenance.KAJY_MIGRATION);
            assertThat(res.getIdTauxApplicable()).isEqualTo(idTaux);

            // Alignement Paie et STC : StcService résout exactement la même valeur
            BigDecimal tauxStc = StcService.getTauxJournalierApplicable(testEmployeId, datePendant, new BigDecimal("400000.00"), c);
            assertThat(tauxStc).isEqualByComparingTo("8669.52");
        }
    }

    @Test
    @DisplayName("shouldStrictlyRespectAsOfDateIntervalWithoutFutureContamination")
    void shouldStrictlyRespectAsOfDateIntervalWithoutFutureContamination() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            BigDecimal tauxA = new BigDecimal("8328.95");
            LocalDate dateA = LocalDate.of(2026, 1, 1);
            LocalDate finA = LocalDate.of(2026, 8, 1);

            BigDecimal tauxB = new BigDecimal("8669.52");
            LocalDate dateB = LocalDate.of(2026, 8, 1);

            congeTauxApplicableService.enregistrerTauxApplicableMigration(
                    testEmployeId, tauxA, dateA, finA, "Taux A", 1, c
            );
            congeTauxApplicableService.enregistrerTauxApplicableMigration(
                    testEmployeId, tauxB, dateB, null, "Taux B", 1, c
            );

            // 1. Avant le 01/01/2026 -> ni A ni B ne doivent s'appliquer (aucun taux futur)
            LocalDate avantDateA = LocalDate.of(2025, 12, 31);
            TauxCongeResolution resAvant = congeTauxApplicableService.resoudreTauxCongeApplicable(
                    testEmployeId, avantDateA, new BigDecimal("300000.00"), c
            );
            assertThat(resAvant.getSourceProvenance()).isEqualTo(TcongeSourceProvenance.INITIALISATION_BASE_30);
            assertThat(resAvant.getTauxJournalier()).isEqualByComparingTo("10000.00");

            // 2. Le 31/07/2026 -> Taux A applicable
            LocalDate finPeriodeA = LocalDate.of(2026, 7, 31);
            TauxCongeResolution resA = congeTauxApplicableService.resoudreTauxCongeApplicable(
                    testEmployeId, finPeriodeA, new BigDecimal("350000.00"), c
            );
            assertThat(resA.getTauxJournalier()).isEqualByComparingTo("8328.95");

            // 3. Le 01/08/2026 (borne inclusive de B, exclusive de A) -> Taux B applicable
            LocalDate debutPeriodeB = LocalDate.of(2026, 8, 1);
            TauxCongeResolution resB = congeTauxApplicableService.resoudreTauxCongeApplicable(
                    testEmployeId, debutPeriodeB, new BigDecimal("350000.00"), c
            );
            assertThat(resB.getTauxJournalier()).isEqualByComparingTo("8669.52");
        }
    }

    @Test
    @DisplayName("shouldThrowOverlapDetectedWhenInsertingOverlappingRate")
    void shouldThrowOverlapDetectedWhenInsertingOverlappingRate() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            BigDecimal taux1 = new BigDecimal("8000.00");
            LocalDate date1 = LocalDate.of(2026, 1, 1);
            LocalDate fin1 = LocalDate.of(2026, 6, 30);

            congeTauxApplicableService.enregistrerTauxApplicableMigration(
                    testEmployeId, taux1, date1, fin1, "Période 1", 1, c
            );

            // Taux qui chevauche : début le 01/05/2026 (alors que fin1 = 30/06/2026)
            BigDecimal tauxOverlap = new BigDecimal("9000.00");
            LocalDate dateOverlap = LocalDate.of(2026, 5, 1);

            assertThatThrownBy(() -> congeTauxApplicableService.enregistrerTauxApplicableMigration(
                    testEmployeId, tauxOverlap, dateOverlap, null, "Overlap", 1, c
            )).isInstanceOf(IllegalStateException.class)
              .hasMessageContaining("TCONGE_OVERLAP_DETECTED");
        }
    }

    @Test
    @DisplayName("shouldThrowAmbiguousExceptionWhenDatabaseContainsMultipleActiveRates")
    void shouldThrowAmbiguousExceptionWhenDatabaseContainsMultipleActiveRates() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Injection forcée directe en base pour tester le résolveur défensif
            String rawSql = "INSERT INTO conge_taux_applicable (id_employe, taux_journalier, date_effet, date_fin, source_provenance) " +
                    "VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = c.prepareStatement(rawSql)) {
                ps.setInt(1, testEmployeId);
                ps.setBigDecimal(2, new BigDecimal("8000.00"));
                ps.setDate(3, Date.valueOf("2026-01-01"));
                ps.setDate(4, Date.valueOf("2026-12-31"));
                ps.setString(5, "KAJY_MIGRATION");
                ps.executeUpdate();

                ps.setInt(1, testEmployeId);
                ps.setBigDecimal(2, new BigDecimal("9000.00"));
                ps.setDate(3, Date.valueOf("2026-06-01"));
                ps.setDate(4, Date.valueOf("2026-12-31"));
                ps.setString(5, "CALCUL_PERIODE_REFERENCE");
                ps.executeUpdate();
            }

            // Résolution à une date couverte par les deux taux simultanément (ex: 15/07/2026)
            LocalDate conflictDate = LocalDate.of(2026, 7, 15);
            assertThatThrownBy(() -> congeTauxApplicableService.resoudreTauxCongeApplicable(
                    testEmployeId, conflictDate, new BigDecimal("500000.00"), c
            )).isInstanceOf(IllegalStateException.class)
              .hasMessageContaining("TCONGE_APPLICABLE_AMBIGUOUS");
        }
    }
}
