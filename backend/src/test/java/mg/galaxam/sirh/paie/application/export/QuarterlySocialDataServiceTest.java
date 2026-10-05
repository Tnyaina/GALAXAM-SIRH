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

    @Test
    void testSousPlafondAvecIndemniteNonCotisable() throws Exception {
        // Cas sous-plafond obligatoire : brut 500k, indemnité non-cotisable 100k -> base déclarée 400k (et non 500k).
        Person p = new Person(1);
        p.matricule = "EMP-001"; p.cnaps = "123456"; p.cin = "101001";
        p.name = "RAKOTO"; p.firstNames = "Jean";
        p.hired = LocalDate.of(2025, 1, 1);
        for (var m : p.months) {
            m.gross = new BigDecimal("500000"); // Brut avec indemnité
            m.cap = new BigDecimal("2400000");
            m.cnapsBaseNonPlafonnee = new BigDecimal("400000"); // Base sans l'indemnité non cotisable
            m.cnapsBase = new BigDecimal("400000");
            m.cnapsEmployee = new BigDecimal("4000");
            m.cnapsEmployer = new BigDecimal("52000");
            m.fmfpBaseNonPlafonnee = new BigDecimal("400000");
            m.fmfpBase = new BigDecimal("400000");
            m.fmfpEmployer = new BigDecimal("4000");
            m.ostieBaseNonPlafonnee = new BigDecimal("400000");
            m.ostieBase = new BigDecimal("400000");
            m.ostieEmployee = new BigDecimal("4000");
            m.ostieEmployer = new BigDecimal("20000");
        }
        Data data = new Data(2026, 1, new String[]{"2026-01", "2026-02", "2026-03"},
                new Employer(1, "STE GALAXAM LOGISTICS", "3000019434", "STAT TEST", "ANTANANARIVO", "0340000000", "daf@galaxam.com"),
                List.of(p));
        QuarterlySocialDataService service = new QuarterlySocialDataService();

        // 1. CNaPS : le salaire déclaré doit être 400 000 et non le brut 500 000
        byte[] cnapsBytes = service.render(data, "cnaps");
        try (var book = WorkbookFactory.create(new ByteArrayInputStream(cnapsBytes))) {
            var sheet = book.getSheetAt(0);
            assertEquals(400_000.0, sheet.getRow(2).getCell(7).getNumericCellValue(), "M1 CNaPS doit être 400 000");
            assertEquals(400_000.0, sheet.getRow(2).getCell(11).getNumericCellValue(), "M2 CNaPS doit être 400 000");
            assertEquals(400_000.0, sheet.getRow(2).getCell(15).getNumericCellValue(), "M3 CNaPS doit être 400 000");
        }

        // 2. FMFP : salaire du mois et total non plafonné doivent être 400 000
        byte[] fmfpBytes = service.render(data, "fmfp");
        try (var book = WorkbookFactory.create(new ByteArrayInputStream(fmfpBytes))) {
            var m1Sheet = book.getSheetAt(1);
            assertEquals(400_000.0, m1Sheet.getRow(3).getCell(7).getNumericCellValue(), "FMFP M1 salaire du mois");
            assertEquals(400_000.0, m1Sheet.getRow(3).getCell(10).getNumericCellValue(), "FMFP M1 total non plafonné");
            var empSheet = book.getSheetAt(0);
            assertEquals(400_000.0, empSheet.getRow(23).getCell(2).getNumericCellValue(), "FMFP recap non plafonné M1");
        }

        // 3. OSTIE : colonnes mensuelles et totaux non plafonnés doivent être 400 000
        byte[] ostieBytes = service.render(data, "ostie");
        try (var book = WorkbookFactory.create(new ByteArrayInputStream(ostieBytes))) {
            var dns = book.getSheetAt(0);
            assertEquals(400_000.0, dns.getRow(14).getCell(11).getNumericCellValue(), "OSTIE M1 non plafonné");
            assertEquals(1_200_000.0, dns.getRow(14).getCell(14).getNumericCellValue(), "OSTIE total non plafonné");
            var recap = book.getSheetAt(1);
            assertEquals(400_000.0, recap.getRow(23).getCell(2).getNumericCellValue(), "OSTIE recap non plafonné M1");
        }
    }

    @Test
    void testOstieFormulesEvalueesEtTotauxPrecalcules() throws Exception {
        // Point 8 : S'assurer que les formules OSTIE sont évaluées et précalculées (non 0 en vue en cache)
        List<Person> people = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            Person p = new Person(i);
            p.matricule = "EMP-" + i; p.cnaps = "CN-" + i; p.cin = "CIN-" + i;
            p.name = "NOM-" + i; p.firstNames = "PRENOM-" + i;
            p.hired = LocalDate.of(2026, 1, 1);
            for (var m : p.months) {
                m.gross = new BigDecimal("2000000");
                m.cap = new BigDecimal("2400000");
                m.ostieBaseNonPlafonnee = new BigDecimal("2000000");
                m.ostieBase = new BigDecimal("2000000");
                m.ostieEmployee = new BigDecimal("20000");
                m.ostieEmployer = new BigDecimal("100000");
            }
            people.add(p);
        }
        Data data = new Data(2026, 1, new String[]{"2026-01", "2026-02", "2026-03"},
                new Employer(1, "STE GALAXAM LOGISTICS", "3000019434", "STAT TEST", "ANTANANARIVO", "0340000000", "daf@galaxam.com"),
                people);
        QuarterlySocialDataService service = new QuarterlySocialDataService();
        byte[] bytes = service.render(data, "ostie");

        try (var book = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var dns = book.getSheetAt(0);
            int totalRow = 24; // 14 + 10 capacity
            // Formules DNS évaluées et non nulles
            assertEquals(4_000_000.0, dns.getRow(totalRow).getCell(11).getNumericCellValue(), "DNS Total M1");
            assertEquals(12_000_000.0, dns.getRow(totalRow).getCell(14).getNumericCellValue(), "DNS Total non plafonné");
            assertEquals(12_000_000.0, dns.getRow(totalRow).getCell(15).getNumericCellValue(), "DNS Total plafonné");
            assertEquals(600_000.0, dns.getRow(totalRow).getCell(16).getNumericCellValue(), "DNS Total part patronale");
            assertEquals(120_000.0, dns.getRow(totalRow).getCell(17).getNumericCellValue(), "DNS Total part travailleur");

            var recap = book.getSheetAt(1);
            // Formules RECAP COTISATION col F (colonne 5, 0-indexed) évaluées et non 0
            assertEquals(12_000_000.0, recap.getRow(23).getCell(5).getNumericCellValue(), "RECAP TOTAUX Salaires non plafonnés");
            assertEquals(12_000_000.0, recap.getRow(24).getCell(5).getNumericCellValue(), "RECAP TOTAUX Salaires plafonnés");
            assertEquals(600_000.0, recap.getRow(25).getCell(5).getNumericCellValue(), "RECAP TOTAUX Cotisations Employeur");
            assertEquals(120_000.0, recap.getRow(26).getCell(5).getNumericCellValue(), "RECAP TOTAUX Cotisations travailleurs");
            assertEquals(720_000.0, recap.getRow(27).getCell(5).getNumericCellValue(), "RECAP TOTAUX Total Cotisations");
        }
    }
}
