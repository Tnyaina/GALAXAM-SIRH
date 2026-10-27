package mg.galaxam.sirh.paie.application.export;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuarterlySocialDataServiceTest {
    @Test
    void consolidationCountsPeopleOnceAndSumsThreeMonths() throws Exception {
        var first = new QuarterlySocialDataService.Person(1);
        first.matricule = "TEST-1";
        first.name = "Salarié";
        first.firstNames = "Un";
        add(first, 0, "CNaPS", "10", "20");
        add(first, 1, "CNaPS", "15", "25");
        add(first, 2, "CNaPS", "5", "10");
        var second = new QuarterlySocialDataService.Person(2);
        second.matricule = "TEST-2";
        second.name = "Salarié";
        second.firstNames = "Deux";
        add(second, 1, "CNaPS", "7", "8");
        var data = new QuarterlySocialDataService.Data(2026, 3,
                new String[]{"2026-07", "2026-08", "2026-09"},
                new QuarterlySocialDataService.Employer(1, "Employeur test", "", "", "", "", ""),
                List.of(first, second));
        var bytes = new QuarterlySocialDataService().render(data, "consolidation");
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var summary = book.getSheet("Synthèse");
            assertEquals("CNaPS", summary.getRow(4).getCell(0).getStringCellValue());
            assertEquals(2, summary.getRow(4).getCell(1).getNumericCellValue());
            assertEquals(37, summary.getRow(4).getCell(2).getNumericCellValue());
            assertEquals(63, summary.getRow(4).getCell(3).getNumericCellValue());
            assertEquals(100, summary.getRow(5).getCell(4).getNumericCellValue());
        }
    }

    @Test
    void organismTemplatesOpenWithSyntheticContributions() throws Exception {
        var person = new QuarterlySocialDataService.Person(1);
        person.matricule = "TEST-1";
        person.cnaps = "TEST-CNAPS";
        person.name = "Salarié";
        person.firstNames = "Test";
        person.months[0].gross = new BigDecimal("1000");
        person.months[0].cnapsEmployee = new BigDecimal("10");
        person.months[0].cnapsEmployer = new BigDecimal("20");
        person.months[0].fmfpEmployer = new BigDecimal("5");
        person.months[0].ostieEmployee = new BigDecimal("3");
        person.months[0].ostieEmployer = new BigDecimal("4");
        person.months[0].omsiEmployee = new BigDecimal("2");
        person.months[0].omsiEmployer = new BigDecimal("3");
        add(person, 0, "CNaPS", "10", "20");
        add(person, 0, "FMFP", "0", "5");
        add(person, 0, "OSTIE", "3", "4");
        add(person, 0, "OMSI", "2", "3");
        var data = new QuarterlySocialDataService.Data(2026, 3,
                new String[]{"2026-07", "2026-08", "2026-09"},
                new QuarterlySocialDataService.Employer(1, "Employeur test", templateOmsiNif(), "", "", "", ""),
                List.of(person));
        var service = new QuarterlySocialDataService();
        for (String type : List.of("cnaps", "fmfp", "ostie", "omsi")) {
            byte[] bytes = service.render(data, type);
            try (var book = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
                assertTrue(book.getNumberOfSheets() > 0, type);
            }
        }
    }

    private static void add(QuarterlySocialDataService.Person person, int month, String org,
                            String employee, String employer) {
        var amounts = person.months[month].getOrganisme(org);
        amounts.employee = new BigDecimal(employee);
        amounts.employer = new BigDecimal(employer);
    }

    private static String templateOmsiNif() throws Exception {
        try (var input = QuarterlySocialDataServiceTest.class.getResourceAsStream(
                "/templates/trimestriel/DNS_OMSI.xlsx");
             var book = WorkbookFactory.create(input)) {
            return new DataFormatter().formatCellValue(book.getSheetAt(0).getRow(5).getCell(2)).trim();
        }
    }
}
