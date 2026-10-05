package mg.galaxam.sirh.conge;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.time.LocalDate;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.application.CongeGrandRecapExcelExportService;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CongeGrandRecapExcelExportIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired CongeGrandRecapExcelExportService exportService;

    @Test
    void recapUsesMovementDateAndEffectiveRate() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            CongeTestFixtureHelper.cleanDatabase(connection);
            int employeeId = CongeTestFixtureHelper.createEmploye(connection, "EMP-RECAP-001", "RAKOTO", "Soa");
            int contractId = CongeTestFixtureHelper.createContratActif(connection, employeeId, LocalDate.of(2026, 1, 1));
            CongeTestFixtureHelper.createAffectation(connection, employeeId, contractId,
                    LocalDate.of(2026, 1, 1), new BigDecimal("370000"));
            try (var statement = connection.prepareStatement(
                    "INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, date_mouvement, valeur_source) VALUES (?, ?, ?, ?, ?)")) {
                statement.setInt(1, employeeId);
                statement.setString(2, "ACQUISITION");
                statement.setBigDecimal(3, new BigDecimal("1.00"));
                statement.setBigDecimal(5, new BigDecimal("9000"));
                statement.setDate(4, Date.valueOf("2026-02-01"));
                statement.executeUpdate();
                statement.setBigDecimal(3, new BigDecimal("2.50"));
                statement.setBigDecimal(5, new BigDecimal("22500"));
                statement.setDate(4, Date.valueOf("2026-06-01"));
                statement.executeUpdate();
                statement.setString(2, "PRISE");
                statement.setBigDecimal(5, null);
                statement.setBigDecimal(3, new BigDecimal("-1.00"));
                statement.setDate(4, Date.valueOf("2026-10-01"));
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement(
                    "INSERT INTO conge_taux_applicable (id_employe, taux_journalier, date_effet, source_provenance) VALUES (?, ?, ?, ?)")) {
                statement.setInt(1, employeeId);
                statement.setBigDecimal(2, new BigDecimal("9000"));
                statement.setDate(3, Date.valueOf("2026-01-01"));
                statement.setString(4, "INITIALISATION_BASE_30");
                statement.executeUpdate();
            }
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(
                exportService.generer(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 9, 1))))) {
            // README onglet 2, row 5 (index 5) contient la description Col O vs Q
            assertTrue(workbook.getSheet("README").getRow(5).getCell(1).getStringCellValue()
                    .contains("FIFO historisée"));
            // Ligne de donnees a index 4 (1ere ligne apres en-tete a index 3)
            var row = workbook.getSheetAt(0).getRow(4);
            assertEquals("EMP-RECAP-001", row.getCell(0).getStringCellValue());
            // Col F(5) = solde ouverture (mouvements avant la periode 04/2026 : acq 02/01 et 06/01 -> 1j avant)
            assertEquals(1, row.getCell(5).getNumericCellValue(),  "Col F(5) = ouverture");
            // Col G(6) = droits acquis sur la periode (acq 06/01 = 2.5j dans 04-09/2026)
            assertEquals(2.5, row.getCell(6).getNumericCellValue(), "Col G(6) = acquis sur periode");
            // Col M(12) = solde disponible
            assertEquals(3.5, row.getCell(12).getNumericCellValue(), "Col M(12) = solde");
            // Col O(14) = valeur FIFO historisee
            assertEquals(31500, row.getCell(14).getNumericCellValue(), "Col O(14) = valeur FIFO");
        }
    }
}
