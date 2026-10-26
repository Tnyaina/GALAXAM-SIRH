package mg.galaxam.sirh.paie.application.export;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IrsaExcelExportServiceTest {
    @Test
    void totalFormulaHasUsableCachedValueBeforeExcelRecalculates() throws Exception {
        try (var workbook = new HSSFWorkbook()) {
            var sheet = workbook.createSheet("IRSA");
            sheet.createRow(0).createCell(0).setCellValue(12);
            sheet.createRow(1).createCell(0).setCellValue(8);
            sheet.createRow(2).createCell(0).setCellFormula("SUM(A1:A2)");
            IrsaExcelExportService.evaluateAndMarkForRecalculation(workbook);
            var output = new ByteArrayOutputStream();
            workbook.write(output);
            try (var reopened = new HSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
                var total = reopened.getSheet("IRSA").getRow(2).getCell(0);
                assertEquals(CellType.NUMERIC, total.getCachedFormulaResultType());
                assertEquals(20, total.getNumericCellValue());
            }
        }
    }
}
