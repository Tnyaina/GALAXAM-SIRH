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
import mg.galaxam.sirh.conge.application.CongeLotValorisationService;
import mg.galaxam.sirh.conge.domain.CongeLotValorisationEngine;
import mg.galaxam.sirh.contrat.application.EmploymentContextResolver;
import mg.galaxam.sirh.contrat.domain.EmploymentContext;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires du Grand Récap Excel congés.
 *
 * Nouveau mapping colonnes (A-S = 19 colonnes, index 0-based) :
 *   A(0)  Matricule    B(1)  Nom         C(2)  Filiale     D(3)  Site        E(4)  Poste
 *   F(5)  Ouverture    G(6)  Acquis      H(7)  Bonif       I(8)  Ajust       J(9)  Pris
 *   K(10) Expir        L(11) Annul       M(12) Solde       N(13) TCONGE      O(14) Valeur FIFO
 *   P(15) Cumul valo   Q(16) SoldeXTCONGE  R(17) Periode  S(18) Statut
 *
 * Ligne en-tete = index 2 (ligne 3 Excel).
 * Ligne 1ere donnee = index 4 (ligne 5 Excel).
 * Ligne TOTAL = index 4 + nb employes.
 */
class CongeGrandRecapExcelExportServiceTest {

    private static CongeGrandRecapExcelExportService exporter(DataSource source, BigDecimal valeur)
            throws Exception {
        return exporter(source, valeur, mock(EmploymentContext.class));
    }

    private static CongeGrandRecapExcelExportService exporter(DataSource source, BigDecimal valeur,
                                                               EmploymentContext contexte) throws Exception {
        CongeLotValorisationService lots = mock(CongeLotValorisationService.class);
        when(lots.situation(anyInt(), any(LocalDate.class), any(Connection.class)))
                .thenReturn(new CongeLotValorisationEngine.Situation(BigDecimal.ONE,
                        valeur, java.util.List.of(), java.util.List.of(), BigDecimal.ZERO));
        EmploymentContextResolver contexts = mock(EmploymentContextResolver.class);
        when(contexts.resolveContext(anyInt(), any(LocalDate.class), any(Connection.class)))
                .thenReturn(contexte);
        return new CongeGrandRecapExcelExportService(source, lots, contexts);
    }

    @Test
    void populationCanExceedTemplateCapacity() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        AtomicInteger position = new AtomicInteger();
        when(result.next()).thenAnswer(invocation -> position.incrementAndGet() <= 101);
        position.set(0);
        when(result.getString("matricule")).thenAnswer(invocation -> "EMP-" + position.get());
        when(result.getString("nom")).thenReturn("RAKOTO");
        when(result.getString("prenom")).thenReturn("Soa");
        when(result.getBigDecimal("solde")).thenReturn(BigDecimal.ONE);
        when(result.getBigDecimal("ouverture")).thenReturn(BigDecimal.ZERO);
        when(result.getBigDecimal("acquis")).thenReturn(BigDecimal.ZERO);
        when(result.getBigDecimal("bonification")).thenReturn(BigDecimal.ZERO);
        when(result.getBigDecimal("ajustement")).thenReturn(BigDecimal.ZERO);
        when(result.getBigDecimal("pris")).thenReturn(BigDecimal.ZERO);
        when(result.getBigDecimal("expiration")).thenReturn(BigDecimal.ZERO);
        when(result.getBigDecimal("annulation")).thenReturn(BigDecimal.ZERO);
        when(result.getBigDecimal("taux_journalier")).thenReturn(BigDecimal.ONE);
        when(result.getBigDecimal("cumul_valo_acquise")).thenReturn(BigDecimal.ZERO);

        byte[] bytes = exporter(mock(DataSource.class), BigDecimal.ONE)
                .generer(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 1), connection);
        assertEquals(102, position.get());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            // 101 employes: donnees index 4..104, TOTAL index 105
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
        byte[] bytes = exporter(mock(DataSource.class), BigDecimal.ZERO)
                .generer(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 1), connection);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            // 0 employes: TOTAL a index 4 (firstData=4, lastData=3, totalRow=max(4,3+1)=4)
            var total = workbook.getSheetAt(0).getRow(4);
            assertNotNull(total, "La ligne TOTAL ne doit pas etre null avec 0 employes");
            assertEquals(0, total.getCell(12).getNumericCellValue(), "Col M(12) solde = 0");
            assertEquals(0, total.getCell(14).getNumericCellValue(), "Col O(14) valeur FIFO = 0");
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
        when(result.getBigDecimal("cumul_valo_acquise")).thenReturn(new BigDecimal("50000"));
        when(result.getDate("periode_debut")).thenReturn(Date.valueOf("2026-01-01"));
        when(result.getDate("periode_fin")).thenReturn(Date.valueOf("2026-12-31"));

        byte[] bytes = exporter(dataSource, new BigDecimal("49500"))
                .generer(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 1), connection);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            var dataRow = sheet.getRow(4);
            assertEquals("0200", dataRow.getCell(0).getStringCellValue());
            assertEquals(5.5, dataRow.getCell(12).getNumericCellValue(),   "Col M(12) = solde");
            assertEquals(49500, dataRow.getCell(14).getNumericCellValue(), "Col O(14) = valeur FIFO historisee");
            assertEquals(50000, dataRow.getCell(15).getNumericCellValue(), "Col P(15) = cumul valo acquise");
            // Col Q(16) = solde x TCONGE = 5.5 x 9000 = 49500
            assertEquals(49500, dataRow.getCell(16).getNumericCellValue(), "Col Q(16) = soldeXTCONGE estimation");
            // Ligne TOTAL = index 5
            assertEquals("SUM(O5:O5)", sheet.getRow(5).getCell(14).getCellFormula());
            assertTrue(sheet.getRow(1).getCell(0).getStringCellValue().contains("01/09/2026"),
                    sheet.getRow(1).getCell(0).getStringCellValue());
        }
        // SQL : 7 x dateDebut + 7 x dateFin + 2 pour jd = 16 setDate au total
        verify(statement, times(16)).setDate(anyInt(), any(Date.class));
    }

    @Test
    void exportUsesDatedEmploymentContextForSiteAndJob() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true, false);
        when(result.getString("matricule")).thenReturn("0300");
        when(result.getString("nom")).thenReturn("RAKOTO");
        when(result.getString("prenom")).thenReturn("Soa");
        when(result.getBigDecimal("cumul_valo_acquise")).thenReturn(BigDecimal.ZERO);
        EmploymentContext contexte = mock(EmploymentContext.class);
        when(contexte.filialeNom()).thenReturn("Filiale janvier");
        when(contexte.nomSite()).thenReturn("Site janvier");
        when(contexte.fonctionNom()).thenReturn("Poste janvier");
        byte[] bytes = exporter(mock(DataSource.class), BigDecimal.ZERO, contexte)
                .generer(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 25), connection);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var row = workbook.getSheetAt(0).getRow(4);
            assertEquals("Filiale janvier", row.getCell(2).getStringCellValue());
            assertEquals("Site janvier", row.getCell(3).getStringCellValue());
            assertEquals("Poste janvier", row.getCell(4).getStringCellValue());
        }
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
        when(result.getBigDecimal("cumul_valo_acquise")).thenReturn(BigDecimal.ZERO);
        when(result.getBigDecimal("taux_journalier")).thenReturn(null);
        byte[] bytes = exporter(mock(DataSource.class), BigDecimal.ZERO)
                .generer(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 1), connection);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            var dataRow = sheet.getRow(4);
            // Col N(13) = TCONGE : BLANK si absent
            assertEquals(org.apache.poi.ss.usermodel.CellType.BLANK, dataRow.getCell(13).getCellType(),
                    "TCONGE absent (col N=13) doit etre BLANK");
            // Col O(14) = valeur FIFO : 0
            assertEquals(0, dataRow.getCell(14).getNumericCellValue());
            // Col Q(16) = SoldeXTCONGE : BLANK si TCONGE nul
            assertEquals(org.apache.poi.ss.usermodel.CellType.BLANK, dataRow.getCell(16).getCellType(),
                    "SoldeXTCONGE (col Q=16) doit etre BLANK si TCONGE absent");
            // Ligne TOTAL = index 5
            assertEquals("SUM(O5:O5)", sheet.getRow(5).getCell(14).getCellFormula());
            assertTrue(sheet.getRow(1).getCell(0).getStringCellValue().contains("lots historiques"),
                    sheet.getRow(1).getCell(0).getStringCellValue());
        }
    }
}
