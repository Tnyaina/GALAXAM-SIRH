package mg.galaxam.sirh.conge;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.application.CongeGrandRecapExcelExportService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CongeGrandRecapExcelExportServiceTest {
    @Test
    void populationCanExceedTemplateCapacity() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        AtomicInteger position = new AtomicInteger();
        when(result.next()).thenAnswer(invocation -> position.incrementAndGet() <= 101);
        when(result.getString("matricule")).thenAnswer(invocation -> "EMP-" + position.get());
        when(result.getString("nom")).thenReturn("RAKOTO");
        when(result.getString("prenom")).thenReturn("Soa");
        when(result.getBigDecimal("solde")).thenReturn(BigDecimal.ONE);
        when(result.getBigDecimal("taux_journalier")).thenReturn(BigDecimal.ONE);
        byte[] bytes = new CongeGrandRecapExcelExportService(mock(DataSource.class))
                .generer(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 1), connection);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals("EMP-101", sheet.getRow(104).getCell(0).getStringCellValue());
            assertEquals("SUM(O5:O105)", sheet.getRow(105).getCell(14).getCellFormula());
        }
    }

    @Test
    void emptyPopulationHasNoCircularTotal() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(false);
        byte[] bytes = new CongeGrandRecapExcelExportService(mock(DataSource.class))
                .generer(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 1), connection);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var total = workbook.getSheetAt(0).getRow(4);
            assertEquals(0, total.getCell(12).getNumericCellValue());
            assertEquals(0, total.getCell(14).getNumericCellValue());
        }
    }

    @Test
    void exportUsesLedgerBalanceAndHistoricalTconge() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true, false);
        when(result.getString("matricule")).thenReturn("0200");
        when(result.getString("nom")).thenReturn("RAKOTO");
        when(result.getString("prenom")).thenReturn("Soa");
        when(result.getBigDecimal("ouverture")).thenReturn(new BigDecimal("4"));
        when(result.getBigDecimal("acquis")).thenReturn(new BigDecimal("2.5"));
        when(result.getBigDecimal("bonification")).thenReturn(BigDecimal.ZERO);
        when(result.getBigDecimal("ajustement")).thenReturn(BigDecimal.ZERO);
        when(result.getBigDecimal("pris")).thenReturn(new BigDecimal("1"));
        when(result.getBigDecimal("expiration")).thenReturn(BigDecimal.ZERO);
        when(result.getBigDecimal("annulation")).thenReturn(BigDecimal.ZERO);
        when(result.getBigDecimal("solde")).thenReturn(new BigDecimal("5.5"));
        when(result.getBigDecimal("taux_journalier")).thenReturn(new BigDecimal("9000"));
        when(result.getDate("periode_debut")).thenReturn(Date.valueOf("2026-01-01"));
        when(result.getDate("periode_fin")).thenReturn(Date.valueOf("2026-12-31"));

        byte[] bytes = new CongeGrandRecapExcelExportService(dataSource)
                .generer(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 1), connection);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals("0200", sheet.getRow(4).getCell(0).getStringCellValue());
            assertEquals(5.5, sheet.getRow(4).getCell(12).getNumericCellValue());
            assertEquals(49500, sheet.getRow(4).getCell(14).getNumericCellValue());
            assertEquals("SUM(O5:O5)", sheet.getRow(5).getCell(14).getCellFormula());
            assertTrue(sheet.getRow(1).getCell(0).getStringCellValue().contains("01/09/2026"));
        }
        verify(statement, times(15)).setDate(anyInt(), any(Date.class));
    }

    @Test
    void missingTcongeIsNotInvented() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true, false);
        when(result.getString("matricule")).thenReturn("0201");
        when(result.getString("nom")).thenReturn("RAZAFY");
        when(result.getString("prenom")).thenReturn("Lala");
        when(result.getBigDecimal("solde")).thenReturn(new BigDecimal("3"));
        byte[] bytes = new CongeGrandRecapExcelExportService(mock(DataSource.class))
                .generer(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 1), connection);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals(org.apache.poi.ss.usermodel.CellType.BLANK, sheet.getRow(4).getCell(13).getCellType());
            assertEquals(org.apache.poi.ss.usermodel.CellType.BLANK, sheet.getRow(4).getCell(14).getCellType());
            assertEquals(org.apache.poi.ss.usermodel.CellType.BLANK, sheet.getRow(5).getCell(14).getCellType());
            assertTrue(sheet.getRow(1).getCell(0).getStringCellValue().contains("1 solde(s) non nuls sans TCONGE"));
        }
    }
}
