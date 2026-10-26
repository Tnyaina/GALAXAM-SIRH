package mg.galaxam.sirh.paie.application.export;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GrandLivrePaieExportServiceTest {
    @Test
    void controlSheetReferencesTheMatchingSummaryTotalsAfterWorkbookRoundTrip() throws Exception {
        try (var book = new XSSFWorkbook()) {
            var summary = book.createSheet("Grand livre");
            var total = summary.createRow(13);
            total.createCell(19).setCellValue(19); // T : autres retenues
            total.createCell(20).setCellValue(20); // U : net à payer
            total.createCell(21).setCellValue(21); // V : cotisations patronales
            var checks = book.createSheet("Contrôle");
            GrandLivrePaieExportService.writeChecks(checks,
                    new GrandLivrePaieExportService.Period("2026-08", LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 25)),
                    1, 1, 13, new GrandLivrePaieExportService.Styles(book));
            book.getCreationHelper().createFormulaEvaluator().evaluateAll();
            var bytes = new ByteArrayOutputStream();
            book.write(bytes);
            try (var reopened = new XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()))) {
                var control = reopened.getSheet("Contrôle");
                assertEquals("'Grand livre'!U14", control.getRow(9).getCell(1).getCellFormula());
                assertEquals("'Grand livre'!V14", control.getRow(10).getCell(1).getCellFormula());
                assertEquals(CellType.NUMERIC, control.getRow(9).getCell(1).getCachedFormulaResultType());
                assertEquals(20, control.getRow(9).getCell(1).getNumericCellValue());
                assertEquals(21, control.getRow(10).getCell(1).getNumericCellValue());
            }
        }
    }
}
