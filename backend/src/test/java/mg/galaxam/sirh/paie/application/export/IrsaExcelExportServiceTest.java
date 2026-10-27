package mg.galaxam.sirh.paie.application.export;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IrsaExcelExportServiceTest {
    @Test
    void consolidatedAndCompanySheetsKeepIndependentRowsAndTotals() throws Exception {
        try (var source = getClass().getResourceAsStream("/templates/mensuel/MODELE_IRSA.xls");
             var workbook = new HSSFWorkbook(source)) {
            var all = workbook.getSheetAt(0);
            var va = workbook.cloneSheet(0);
            var glx = workbook.cloneSheet(0);
            var first = new mg.galaxam.sirh.paie.domain.dto.export.SafiRowDto();
            first.setMatricule("VA-1");
            first.setNomPrenoms("Salarié VA");
            first.setImpotDu(java.math.BigDecimal.valueOf(3000));
            var second = new mg.galaxam.sirh.paie.domain.dto.export.SafiRowDto();
            second.setMatricule("GLX-1");
            second.setNomPrenoms("Salarié GLX");
            second.setImpotDu(java.math.BigDecimal.valueOf(5000));
            var header = new IrsaExcelExportService.Header("2026-10", LocalDate.of(2026, 10, 25));
            IrsaExcelExportService.writeSheet(all, header,
                    new PayrollExportContextResolver.Company("Toutes les filiales", "", ""), List.of(first, second));
            IrsaExcelExportService.writeSheet(va, header,
                    new PayrollExportContextResolver.Company("VA", "NIF-VA", "Adresse VA"), List.of(first));
            IrsaExcelExportService.writeSheet(glx, header,
                    new PayrollExportContextResolver.Company("GLX", "NIF-GLX", "Adresse GLX"), List.of(second));
            assertEquals("Salarié VA", all.getRow(15).getCell(4).getStringCellValue());
            assertEquals("Salarié GLX", all.getRow(16).getCell(4).getStringCellValue());
            assertEquals("Salarié VA", va.getRow(15).getCell(4).getStringCellValue());
            assertEquals("Salarié GLX", glx.getRow(15).getCell(4).getStringCellValue());
            assertEquals("NIF-VA", va.getRow(9).getCell(5).getStringCellValue());
            assertEquals("NIF-GLX", glx.getRow(9).getCell(5).getStringCellValue());
            assertEquals("SUM(AC16:AC17)", all.getRow(23).getCell(28).getCellFormula());
            assertEquals("SUM(AC16:AC16)", va.getRow(23).getCell(28).getCellFormula());
            IrsaExcelExportService.evaluateAndMarkForRecalculation(workbook);
            assertEquals(8000, all.getRow(23).getCell(28).getNumericCellValue());
            assertEquals(3000, va.getRow(23).getCell(28).getNumericCellValue());
            assertEquals(5000, glx.getRow(23).getCell(28).getNumericCellValue());
        }
    }

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
