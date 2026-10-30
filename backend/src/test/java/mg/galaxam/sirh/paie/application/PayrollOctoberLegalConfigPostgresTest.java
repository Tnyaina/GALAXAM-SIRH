package mg.galaxam.sirh.paie.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.domain.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale;
import org.junit.jupiter.api.Test;

class PayrollOctoberLegalConfigPostgresTest {
    @Test
    void septemberAndOctoberResolveDifferentMinimumsAndContributionCaps() throws Exception {
        String url = System.getenv("GALAXAM_LEGAL_TEST_DB_URL");
        assumeTrue(url != null && url.endsWith("/galaxam_legal_test_20261030"),
                "Base légale jetable migrée requise");
        try (var connection = DriverManager.getConnection(url,
                System.getenv("GALAXAM_LEGAL_TEST_DB_USER"),
                System.getenv("GALAXAM_LEGAL_TEST_DB_PASSWORD"))) {
            var september = PaieConfigLegale.findActiveAt(connection, LocalDate.of(2026, 9, 30));
            var october = PaieConfigLegale.findActiveAt(connection, LocalDate.of(2026, 10, 1));
            assertNotNull(september);
            assertNotNull(october);
            assertAmount("300000", september.getSmeNational());
            assertAmount("2400000", september.getPlafondCotisationMensuel());
            assertAmount("315000", october.getSmeNational());
            assertAmount("320000", october.getSmeAgricole());
            assertAmount("2520000", october.getPlafondCotisationMensuel());
            assertEquals(6, count(connection, "bareme_irsa", october.getIdConfigLegale()));
            assertEquals(5, count(connection, "ref_taux_majoration", october.getIdConfigLegale()));

            var input = new PayrollCalculationInput();
            input.setIdEmploye(1);
            input.setIdPeriodePaie(1);
            input.setMatricule("REC-1");
            input.setNomPrenom("Recette plafond");
            input.setCodePeriode("2026-10");
            input.setDateDebutPeriode(Date.valueOf("2026-09-26"));
            input.setDateFinPeriode(Date.valueOf("2026-10-25"));
            input.setModeCalculPaie("MENSUEL");
            input.setSalaireBaseContractuel(new BigDecimal("3000000"));
            input.setPlafondCotisationMensuel(october.getPlafondCotisationMensuel());
            input.setSoumisCnaps(true);
            input.setSoumisSante(true);
            input.setSoumisIrsa(false);
            input.setTauxCnapsSalarial(new BigDecimal("0.01"));
            input.setTauxCnapsPatronal(new BigDecimal("0.13"));
            input.setTauxSanteSalarial(new BigDecimal("0.01"));
            input.setTauxSantePatronal(new BigDecimal("0.05"));
            input.setTauxFmfpPatronal(BigDecimal.ZERO);
            input.setPlafondAvanceEmployeur(new BigDecimal("0.10"));
            input.setAvancesAcomptes(List.of());
            var bulletin = new PayrollExecutionEngine().calculerBulletin(input).getBulletin();
            assertAmount("50400", bulletin.getTotalCotisationsSalariales());
            assertAmount("2949600", bulletin.getNetAvantArrondi());
        }
    }

    private static int count(java.sql.Connection connection, String table, int configId) throws Exception {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + table + " WHERE id_config_legale=?")) {
            statement.setInt(1, configId);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
