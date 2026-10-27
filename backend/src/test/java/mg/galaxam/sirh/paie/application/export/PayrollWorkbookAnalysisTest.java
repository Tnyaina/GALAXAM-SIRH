package mg.galaxam.sirh.paie.application.export;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PayrollWorkbookAnalysisTest {
    private PayrollWorkbookAnalysis.Entry entry(String month, String company, int employee, int bulletin, String amount) {
        BigDecimal value = new BigDecimal(amount);
        return new PayrollWorkbookAnalysis.Entry(month, bulletin, employee, "B-" + bulletin, "VALIDE", company, company,
                value, value, value, value, value, value, value,
                new TreeMap<>(Map.of("CNaPS", new PayrollWorkbookAnalysis.Contribution(value, value))), false);
    }
    private PayrollWorkbookAnalysis.Data data(List<String> expected, List<PayrollWorkbookAnalysis.Entry> entries) {
        Set<String> periods = new TreeSet<>(); entries.forEach(e -> periods.add(e.period()));
        return new PayrollWorkbookAnalysis.Data(expected, periods, entries);
    }
    @Test void noHistoryLeavesComparisonsBlankInBothExcelFormats() throws Exception {
        var now = data(List.of("2026-08"), List.of(entry("2026-08", "VA", 1, 1, "100")));
        var before = data(List.of("2026-07"), List.of());
        for (Workbook book : List.of(new XSSFWorkbook(), new HSSFWorkbook())) {
            try (book) {
                book.createSheet("Détail").createFreezePane(6, 8);
                PayrollWorkbookAnalysis.render(book, PayrollWorkbookAnalysis.Kind.PAIE, now, before, null);
                Sheet pilot = book.getSheetAt(0);
                assertEquals("Vue de pilotage", pilot.getSheetName());
                assertTrue(pilot.getRow(2).getCell(0).getStringCellValue().contains("historique absent"));
                assertEquals(100, pilot.getRow(5).getCell(1).getNumericCellValue());
                assertNull(pilot.getRow(5).getCell(2));
                assertNull(pilot.getRow(5).getCell(4));
                for (Sheet sheet : book) assertNull(sheet.getPaneInformation(), sheet.getSheetName());
            }
        }
    }
    @Test void sameScopeComparesValuesAndZeroDenominatorHasNoPercentage() throws Exception {
        var now = data(List.of("2026-08"), List.of(entry("2026-08", "VA", 1, 2, "150")));
        var before = data(List.of("2026-07"), List.of(entry("2026-07", "VA", 1, 1, "100")));
        assertNull(PayrollWorkbookAnalysis.comparisonReason(now, before));
        assertEquals(0, new BigDecimal("0.5").compareTo(PayrollWorkbookAnalysis.rate(new BigDecimal("150"), new BigDecimal("100"))));
        assertNull(PayrollWorkbookAnalysis.rate(BigDecimal.ONE, BigDecimal.ZERO));
        try (var book = new XSSFWorkbook()) {
            PayrollWorkbookAnalysis.render(book, PayrollWorkbookAnalysis.Kind.PAIE, now, before, null);
            assertEquals(50, book.getSheetAt(0).getRow(5).getCell(3).getNumericCellValue());
            assertEquals(0.5, book.getSheetAt(0).getRow(5).getCell(4).getNumericCellValue());
        }
    }
    @Test void partialAndChangedScopeDoNotGenerateMisleadingComparisons() {
        var now = data(List.of("2026-07", "2026-08", "2026-09"), List.of(entry("2026-08", "VA", 1, 1, "100")));
        var before = data(List.of("2026-04", "2026-05", "2026-06"), List.of());
        assertTrue(PayrollWorkbookAnalysis.comparisonReason(now, before).contains("partielle"));
        now = data(List.of("2026-08"), List.of(entry("2026-08", "VA", 1, 1, "100")));
        before = data(List.of("2026-07"), List.of(entry("2026-07", "GLX", 1, 2, "100")));
        assertTrue(PayrollWorkbookAnalysis.comparisonReason(now, before).contains("filiales différent"));
    }
    @Test void absentMonthsStayBlankAndEmployeesAreNotDoubleCounted() throws Exception {
        var now = data(List.of("2026-07", "2026-08", "2026-09"), List.of(entry("2026-08", "VA", 1, 1, "100"), entry("2026-08", "GLX", 1, 2, "50")));
        try (var book = new XSSFWorkbook()) {
            PayrollWorkbookAnalysis.render(book, PayrollWorkbookAnalysis.Kind.PAIE, now, data(List.of(), List.of()), null);
            Sheet series = book.getSheet("Évolution et couverture");
            assertNull(series.getRow(4).getCell(3));
            assertEquals(1, series.getRow(5).getCell(2).getNumericCellValue());
            assertEquals(150, series.getRow(5).getCell(3).getNumericCellValue());
            assertEquals("Non inclus", series.getRow(6).getCell(1).getStringCellValue());
        }
    }

    @Test void partialYearComparesOnlyMonthsWithAValidatedEquivalent() throws Exception {
        var now = data(List.of("2026-07", "2026-08", "2026-09"),
                List.of(entry("2026-08", "VA", 1, 2, "150")));
        var before = data(List.of("2025-07", "2025-08", "2025-09"),
                List.of(entry("2025-08", "VA", 1, 1, "100")));
        try (var book = new XSSFWorkbook()) {
            PayrollWorkbookAnalysis.render(book, PayrollWorkbookAnalysis.Kind.PAIE, now, before, null);
            assertNull(book.getSheetAt(0).getRow(5).getCell(3));
            var series = book.getSheet("Évolution et couverture");
            assertNull(series.getRow(4).getCell(4));
            assertEquals(50, series.getRow(5).getCell(4).getNumericCellValue());
            assertEquals("Écart avec 2025-08", series.getRow(5).getCell(5).getStringCellValue());
        }
    }
}
