package mg.galaxam.sirh.paie.application.export;

import mg.galaxam.sirh.paie.application.export.QuarterlySocialDataService.Data;
import mg.galaxam.sirh.paie.application.export.QuarterlySocialDataService.Employer;
import mg.galaxam.sirh.paie.application.export.QuarterlySocialDataService.Person;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuarterlySocialDataServiceTest {
    @Test
    void fillsAllProvidedTemplatesWithThreeMonthsAndMoreEmployeesThanDefaultRows() throws Exception {
        List<Person> people = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            Person p = new Person(i);
            p.matricule = "TEST-" + i; p.cnaps = "CN-" + i; p.cin = "CIN-" + i;
            p.name = "NOM-" + i; p.firstNames = "PRENOM-" + i;
            p.hired = LocalDate.of(2026, 7, 1);
            for (var m : p.months) {
                m.gross = new BigDecimal("1000000");
                m.cap = new BigDecimal("2400000");
                m.cnapsBase = m.gross; m.cnapsEmployee = new BigDecimal("10000");
                m.cnapsEmployer = new BigDecimal("130000"); m.fmfpEmployer = new BigDecimal("10000");
                if (i % 2 == 0) {
                    m.ostieBase = m.gross; m.ostieEmployee = new BigDecimal("10000");
                    m.ostieEmployer = new BigDecimal("50000");
                } else {
                    m.omsiBase = m.gross; m.omsiEmployee = new BigDecimal("15000");
                    m.omsiEmployer = new BigDecimal("55000");
                }
            }
            people.add(p);
        }
        Data data = new Data(2026, 3, new String[]{"2026-07", "2026-08", "2026-09"},
                new Employer(1, "STE GALAXAM LOGISTICS", "3000019434", "STAT TEST", "ANTANANARIVO", "0340000000", "daf@example.test"), people);
        var service = new QuarterlySocialDataService();
        for (String type : List.of("consolidation", "cnaps", "fmfp", "ostie", "omsi")) {
            try (var book = WorkbookFactory.create(new ByteArrayInputStream(service.render(data, type)))) {
                assertTrue(book.getNumberOfSheets() >= 1, type);
                if (type.equals("cnaps")) {
                    assertEquals("969317              ", book.getSheetAt(0).getSheetName());
                    assertEquals("CN-12", book.getSheetAt(0).getRow(13).getCell(0).getStringCellValue());
                    assertEquals(1_000_000, book.getSheetAt(0).getRow(13).getCell(7).getNumericCellValue());
                }
                if (type.equals("fmfp")) assertEquals("NOM-12", book.getSheetAt(1).getRow(14).getCell(1).getStringCellValue());
                if (type.equals("ostie")) {
                    assertEquals(6_000_000, book.getSheetAt(1).getRow(23).getCell(2).getNumericCellValue());
                    assertEquals(300_000, book.getSheetAt(0).getRow(3).getCell(16).getNumericCellValue());
                    assertTrue(book.getSheetAt(0).getRow(2).getCell(0).getStringCellValue().contains("Zamenhof"));
                }
                if (type.equals("omsi")) {
                    assertEquals("NOM-11", book.getSheetAt(1).getRow(15).getCell(2).getStringCellValue());
                    assertEquals("3e", book.getSheetAt(1).getRow(1).getCell(9).getStringCellValue());
                    assertEquals("1768", book.getSheetAt(0).getRow(3).getCell(2).getStringCellValue());
                    assertEquals(300_000, book.getSheetAt(0).getRow(11).getCell(8).getNumericCellValue());
                    assertEquals(2_400_000, book.getSheetAt(1).getRow(6).getCell(14).getNumericCellValue());
                    assertEquals("OMSI", book.getSheetAt(1).getRow(3).getCell(10).getStringCellValue());
                }
            }
        }
        Data anotherEmployer = new Data(2026, 3, data.months(),
                new Employer(2, "AUTRE ENTREPRISE", "NIF DIFFERENT", "", "", "", ""), people);
        assertThrows(IllegalStateException.class, () -> service.render(anotherEmployer, "omsi"));
    }
}
