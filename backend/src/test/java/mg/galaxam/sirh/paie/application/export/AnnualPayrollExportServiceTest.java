package mg.galaxam.sirh.paie.application.export;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;

import static org.junit.jupiter.api.Assertions.*;

class AnnualPayrollExportServiceTest {
    @Test
    void usesAnnualTemplateAndShowsPartialCoverageWithoutTechnicalColumns() throws Exception {
        List<AnnualPayrollExportService.PayRow> rows = new ArrayList<>();
        for (int employee = 1; employee <= 101; employee++) {
            rows.add(row(employee, "2026-08", 1000));
            rows.add(row(employee, "2026-09", 2000));
        }
        BulletinPaieLigne configured = new BulletinPaieLigne();
        configured.setCodeRubrique("PRIME_EXCEPTIONNELLE");
        configured.setLibelleRubrique("Prime exceptionnelle");
        configured.setSens("GAIN");
        configured.setCategorie("PRIME");
        configured.setEtapeCalcul("BRUT");
        configured.setMontantSalarial(BigDecimal.valueOf(5000));
        BulletinPaieLigne leaveDeduction = new BulletinPaieLigne();
        leaveDeduction.setCodeRubrique("DEDUCTION_CONGE_BASE");
        leaveDeduction.setLibelleRubrique("Déduction de congé");
        leaveDeduction.setSens("RETENUE");
        leaveDeduction.setCategorie("SALAIRE_BASE");
        leaveDeduction.setMontantSalarial(BigDecimal.valueOf(35000));
        var result = new AnnualPayrollExportService().render(2026, List.of("2026-08", "2026-09"), rows, List.of(configured, leaveDeduction));
        assertEquals(2, result.coveredMonths());
        assertEquals(101, result.employees());
        try (var book = WorkbookFactory.create(new ByteArrayInputStream(result.bytes()))) {
            assertEquals(4, book.getNumberOfSheets());
            var summary = book.getSheet("Récap annuel");
            var months = book.getSheet("Synthèse par mois");
            var detail = book.getSheet("Détail mensuel");
            assertEquals("Non inclus", months.getRow(4).getCell(1).getStringCellValue());
            assertEquals("Inclus", months.getRow(11).getCell(1).getStringCellValue());
            assertEquals(101000, months.getRow(11).getCell(3).getNumericCellValue());
            assertEquals(202000, months.getRow(12).getCell(3).getNumericCellValue());
            assertTrue(summary.getRow(1).getCell(0).getStringCellValue().contains("2/12"));
            assertEquals("Impôt retenu", summary.getRow(3).getCell(17).getStringCellValue());
            assertEquals("Type contrat", summary.getRow(3).getCell(5).getStringCellValue());
            assertEquals("Type contrat", detail.getRow(3).getCell(7).getStringCellValue());
            assertEquals(3000, summary.getRow(4).getCell(13).getNumericCellValue());
            assertEquals(303000, summary.getRow(105).getCell(13).getNumericCellValue());
            assertEquals("TOTAL DES PÉRIODES INCLUSES", summary.getRow(105).getCell(0).getStringCellValue());
            assertEquals(202, detail.getLastRowNum() - 3);
            assertEquals("A4:X105", ((org.apache.poi.xssf.usermodel.XSSFSheet) summary).getTables().get(0).getCTTable().getRef());
            assertEquals("A4:AB206", ((org.apache.poi.xssf.usermodel.XSSFSheet) detail).getTables().get(0).getCTTable().getRef());
            assertFalse(detail.isColumnHidden(26));
            assertFalse(detail.isColumnHidden(27));
            assertEquals("REF-1-2026-08", detail.getRow(4).getCell(26).getStringCellValue());
            assertEquals("VALIDE", detail.getRow(4).getCell(27).getStringCellValue());
            assertEquals("09 — Septembre 2026", detail.getRow(105).getCell(1).getStringCellValue());
            var control = book.getSheet("Contrôle rubriques");
            assertEquals("DEDUCTION_CONGE_BASE", control.getRow(4).getCell(0).getStringCellValue());
            assertEquals("Non ventilée séparément", control.getRow(4).getCell(4).getStringCellValue());
            assertEquals("À vérifier", control.getRow(4).getCell(5).getStringCellValue());
            assertEquals("PRIME_EXCEPTIONNELLE", control.getRow(5).getCell(0).getStringCellValue());
            assertEquals("Indemnités / primes", control.getRow(5).getCell(4).getStringCellValue());
            assertEquals("À vérifier", control.getRow(5).getCell(5).getStringCellValue());
        }
    }

    private static AnnualPayrollExportService.PayRow row(int employee, String period, int gross) {
        String[] identity = {"MAT-" + employee, "SALARIE " + employee, "GALAXAM", "SIÈGE", "EMPLOYÉ", "CDI"};
        BigDecimal[] values = new BigDecimal[18];
        for (int i = 0; i < values.length; i++) values[i] = BigDecimal.ZERO;
        values[7] = BigDecimal.valueOf(gross); // Salaire brut, colonne N du récapitulatif.
        values[15] = BigDecimal.valueOf(gross - 100); // Net à payer.
        return new AnnualPayrollExportService.PayRow(employee, period, identity, values, "REF-" + employee + "-" + period, "VALIDE");
    }
}
