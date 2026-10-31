package mg.galaxam.sirh.paie.application.export;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AnnualPayrollExportServiceTest {
    @Test
    void exportsPartialYearWithTwoValidatedMonths() throws Exception {
        BigDecimal[] values = new BigDecimal[18];
        java.util.Arrays.fill(values, BigDecimal.ZERO);
        String[] identity = {"REC-1", "Nom", "Prénom", "Site", "Fonction", "CDI"};
        var rows = new ArrayList<AnnualPayrollExportService.PayRow>();
        rows.add(new AnnualPayrollExportService.PayRow(1, "2026-08", identity, values));
        rows.add(new AnnualPayrollExportService.PayRow(1, "2026-09", identity, values));
        var result = new AnnualPayrollExportService().render(2026, List.of("2026-08", "2026-09"), rows);
        assertEquals(2, result.coveredMonths());
        assertEquals(1, result.employees());
        assertTrue(result.bytes().length > 1000);
    }

    @Test
    void monthlyOverviewShowsAdvancesLoansAndOtherDeductions() throws Exception {
        BigDecimal[] values = new BigDecimal[18];
        java.util.Arrays.fill(values, BigDecimal.ZERO);
        values[12] = new BigDecimal("1000");
        values[13] = new BigDecimal("2000");
        values[14] = new BigDecimal("3000");
        String[] identity = {"REC-1", "Nom", "Prénom", "Site", "Fonction", "CDI"};
        var rows = List.of(new AnnualPayrollExportService.PayRow(1, "2026-08", identity, values));
        var result = new AnnualPayrollExportService().render(2026, List.of("2026-08"), new ArrayList<>(rows));
        try (var book = WorkbookFactory.create(new ByteArrayInputStream(result.bytes()))) {
            var sheet = book.getSheet("Synthèse par mois");
            assertEquals("Avances et acomptes", sheet.getRow(3).getCell(8).getStringCellValue());
            assertEquals("Prêts", sheet.getRow(3).getCell(9).getStringCellValue());
            assertEquals("Autres retenues", sheet.getRow(3).getCell(10).getStringCellValue());
            assertEquals(1000, sheet.getRow(11).getCell(8).getNumericCellValue());
            assertEquals(2000, sheet.getRow(11).getCell(9).getNumericCellValue());
            assertEquals(3000, sheet.getRow(11).getCell(10).getNumericCellValue());
        }
    }

    @Test
    void exportsSyntheticCampaignFromDisposableDatabase() throws Exception {
        String url = System.getenv("GALAXAM_FIX_TEST_DB_URL");
        assumeTrue(url != null && url.contains("127.0.0.1:15441/galaxam_recette"));
        try (var connection = DriverManager.getConnection(url,
                System.getenv("GALAXAM_FIX_TEST_DB_USER"), System.getenv("GALAXAM_FIX_TEST_DB_PASSWORD"))) {
            var result = new AnnualPayrollExportService().export(2026, connection);
            assertEquals(2, result.coveredMonths());
            assertTrue(result.bytes().length > 1000);
        }
    }
}
