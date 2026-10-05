package mg.galaxam.sirh.paie.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import javax.sql.DataSource;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paieconfig.application.SocialContributionContextResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class HealthContributionEffectiveDateIntegrationTest {
    @Autowired DataSource dataSource;

    @Test
    void paieEtStcResolventLeTauxSanteHistoriqueAlaDateMetier() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTestDatabase(connection);
            connection.setAutoCommit(false);
            try {
                String suffix = Long.toUnsignedString(System.nanoTime(), 36).toUpperCase();
                String siteCode = "HT" + suffix.substring(Math.max(0, suffix.length() - 8));
                int siteId = insertSite(connection, siteCode);
                int oldRateId = insertHealthRate(connection, "HO" + suffix.substring(0, 8),
                        new BigDecimal("1.00"), new BigDecimal("1.00"),
                        LocalDate.of(2020, 1, 1), LocalDate.of(2024, 12, 31));
                int newRateId = insertHealthRate(connection, "HN" + suffix.substring(0, 8),
                        new BigDecimal("3.00"), new BigDecimal("4.00"),
                        LocalDate.of(2025, 1, 1), null);
                linkSiteRate(connection, siteId, oldRateId);
                linkSiteRate(connection, siteId, newRateId);

                PayrollLegalContextResolver payroll = new PayrollLegalContextResolver();
                assertPayrollRate(payroll, connection, siteCode, LocalDate.of(2024, 6, 1), "0.01", "0.01");
                assertPayrollRate(payroll, connection, siteCode, LocalDate.of(2025, 6, 1), "0.03", "0.04");
                PayrollCalculationInput wrongSite = new PayrollCalculationInput();
                wrongSite.setIdEmploye(1);
                wrongSite.setCodeSite("SITE_INCONNU");
                wrongSite.setNomSite(siteCode);
                wrongSite.setSoumisSante(true);
                assertThrows(mg.galaxam.sirh.common.exception.SirhBusinessException.class,
                        () -> payroll.resolveHealthContribution(wrongSite, LocalDate.of(2025, 6, 1), connection));

                StcService stc = new StcService();
                var oldStcRate = stc.resolveHealthContributionRate(siteCode, siteCode,
                        LocalDate.of(2024, 6, 1), connection);
                var newStcRate = stc.resolveHealthContributionRate(siteCode, siteCode,
                        LocalDate.of(2025, 6, 1), connection);
                assertNotNull(oldStcRate);
                assertNotNull(newStcRate);
                assertEquals("HO" + suffix.substring(0, 8), oldStcRate.code());
                assertEquals("HN" + suffix.substring(0, 8), newStcRate.code());
                assertDecimal("1.00", oldStcRate.employeeRatePercent());
                assertDecimal("1.00", oldStcRate.employerRatePercent());
                assertDecimal("3.00", newStcRate.employeeRatePercent());
                assertDecimal("4.00", newStcRate.employerRatePercent());
                assertEquals(null, stc.resolveHealthContributionRate("SITE_INCONNU", siteCode,
                        LocalDate.of(2025, 6, 1), connection));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void cotisationsEntrepriseChangentSansTrouAuPremierJanvier() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTestDatabase(connection);
            connection.setAutoCommit(false);
            try {
                String suffix = Long.toUnsignedString(System.nanoTime(), 36).toUpperCase();
                insertCompanyRate(connection, "C1" + suffix, "PREVOYANCE", "1.00", "13.00",
                        LocalDate.of(1900, 1, 1), LocalDate.of(1900, 12, 31));
                insertCompanyRate(connection, "F1" + suffix, "FORMATION", "0", "1.00",
                        LocalDate.of(1900, 1, 1), LocalDate.of(1900, 12, 31));
                insertCompanyRate(connection, "C2" + suffix, "PREVOYANCE", "2.00", "14.00",
                        LocalDate.of(1901, 1, 1), LocalDate.of(1901, 12, 31));
                insertCompanyRate(connection, "F2" + suffix, "FORMATION", "0", "2.00",
                        LocalDate.of(1901, 1, 1), LocalDate.of(1901, 12, 31));
                var resolver = new SocialContributionContextResolver();
                assertDecimal("0.01", resolver.companyRates(LocalDate.of(1900, 12, 31), connection).cnapsEmployee());
                assertDecimal("0.02", resolver.companyRates(LocalDate.of(1901, 1, 1), connection).cnapsEmployee());
                assertDecimal("0.02", resolver.companyRates(LocalDate.of(1901, 1, 1), connection).fmfpEmployer());
                assertTrue(assertThrows(IllegalStateException.class,
                        () -> resolver.companyRates(LocalDate.of(1902, 1, 1), connection))
                        .getMessage().contains("MISSING_CONFIGURATION"));
            } finally {
                connection.rollback();
            }
        }
    }

    private void insertCompanyRate(Connection connection, String code, String category,
            String employeeRate, String employerRate, LocalDate start, LocalDate end) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ref_type_cotisation (code,libelle,taux_salarial,taux_patronal,est_actif,"
                        + "date_debut,date_fin,categorie) VALUES (?,?,?, ?,TRUE,?,?,?)")) {
            statement.setString(1, code.substring(0, Math.min(code.length(), 20)));
            statement.setString(2, code);
            statement.setBigDecimal(3, new BigDecimal(employeeRate));
            statement.setBigDecimal(4, new BigDecimal(employerRate));
            statement.setDate(5, Date.valueOf(start));
            statement.setDate(6, Date.valueOf(end));
            statement.setString(7, category);
            statement.executeUpdate();
        }
    }

    private void assertPayrollRate(PayrollLegalContextResolver resolver, Connection connection,
            String siteCode, LocalDate effectiveDate, String employeeRate, String employerRate) throws Exception {
        PayrollCalculationInput input = new PayrollCalculationInput();
        input.setIdEmploye(1);
        input.setCodeSite(siteCode);
        input.setNomSite(siteCode);
        input.setSoumisSante(true);
        resolver.resolveHealthContribution(input, effectiveDate, connection);
        assertDecimal(employeeRate, input.getTauxSanteSalarialDecimal());
        assertDecimal(employerRate, input.getTauxSantePatronalDecimal());
    }

    private int insertSite(Connection connection, String code) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ref_site (code_site, libelle) VALUES (?, ?) RETURNING id_site")) {
            statement.setString(1, code);
            statement.setString(2, code);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private int insertHealthRate(Connection connection, String code, BigDecimal employeeRate,
            BigDecimal employerRate, LocalDate start, LocalDate end) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ref_type_cotisation (code, libelle, taux_salarial, taux_patronal, est_actif, "
                        + "date_debut, date_fin, categorie) VALUES (?, ?, ?, ?, TRUE, ?, ?, 'SANTE') "
                        + "RETURNING id_type_cotisation")) {
            statement.setString(1, code);
            statement.setString(2, "Taux santé test " + code);
            statement.setBigDecimal(3, employeeRate);
            statement.setBigDecimal(4, employerRate);
            statement.setDate(5, Date.valueOf(start));
            if (end == null) statement.setNull(6, java.sql.Types.DATE);
            else statement.setDate(6, Date.valueOf(end));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private void linkSiteRate(Connection connection, int siteId, int rateId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO site_cotisation (id_site, id_type_cotisation, est_actif, est_plafonne_sante) "
                        + "VALUES (?, ?, TRUE, FALSE)")) {
            statement.setInt(1, siteId);
            statement.setInt(2, rateId);
            statement.executeUpdate();
        }
    }

    private void assertTestDatabase(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT current_database()")) {
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals("sirh_galaxam_test", result.getString(1),
                        "Aucun test historique Santé ne doit écrire hors sirh_galaxam_test");
            }
        }
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
