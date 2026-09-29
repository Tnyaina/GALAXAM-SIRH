package mg.galaxam.sirh.pointage.application;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import mg.galaxam.sirh.pointage.api.dto.PointageTronconSemaineDTO;

import static org.junit.jupiter.api.Assertions.*;

class PointageExcelExportServiceTest {
    @Test
    void ficheSalarieContientJournalDatesFrancaisesEtIndemnitesArrondies() throws Exception {
        var data = PointagePdfExportServiceTest.example(31, 1);
        data.fiche().setTotauxIndemnites(java.util.Map.of("PRIME_0", new BigDecimal("28363.60")));
        data = PointageDocumentData.from(data.fiche());
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(new PointageExcelExportService().genererExcelFicheEmploye(data)))) {
            var sheet = book.getSheetAt(0);
            assertEquals("FICHE DE PRÉSENCE", sheet.getRow(0).getCell(0).getStringCellValue());
            assertTrue(sheet.getRow(1).getCell(0).getStringCellValue().contains("01/09/2026"));
            assertEquals("01/09/2026", sheet.getRow(8).getCell(0).getStringCellValue());
            assertEquals("01/10/2026", sheet.getRow(38).getCell(0).getStringCellValue());
            assertEquals("08h00 (8.00h)", sheet.getRow(8).getCell(7).getStringCellValue());
            boolean amountFound = false;
            for (var row : sheet) for (var cell : row) {
                if (cell.getCellType() == CellType.NUMERIC && cell.getNumericCellValue() == 28364d) amountFound = true;
                if (cell.getCellType() == CellType.STRING) {
                    assertFalse(cell.getStringCellValue().contains("BILAN HEBDOMADAIRE"));
                    assertFalse(cell.getStringCellValue().contains("DAF"));
                }
            }
            assertTrue(amountFound);
        }
    }
    @Test
    void afficheSemaineEtAjustementValideSansExposerAnnulation() throws Exception {
        var data = PointagePdfExportServiceTest.example(1, 0);
        var week = new PointageTronconSemaineDTO();
        week.setNumeroSemaine(1); week.setDebutSemaine("2026-09-01"); week.setFinSemaine("2026-09-07");
        week.setSeuilHeuresTroncon(40); week.setTotalHeuresTravaillees(39.5);
        data.fiche().setBilansHebdo(List.of(week));
        data = new PointageDocumentData(data.fiche(), data.edition(), data.provenance(), null, data.indemnites(),
                List.of(new PointageDocumentData.Ajustement(-90, "Correction validée du solde", "2026-09-08", "DAF", "VALIDE"),
                        new PointageDocumentData.Ajustement(60, "Correction annulée", "2026-09-09", "DAF", "ANNULE")), data.bilan());
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(new PointageExcelExportService().genererExcelFicheEmploye(data)))) {
            StringBuilder content = new StringBuilder();
            for (var row : book.getSheetAt(0)) for (var cell : row)
                if (cell.getCellType() == CellType.STRING) content.append(cell.getStringCellValue()).append('\n');
            assertTrue(content.toString().contains("SYNTHÈSE HEBDOMADAIRE"));
            assertTrue(content.toString().contains("40h00\n(40.00h)"));
            assertTrue(content.toString().contains("39h30\n(39.50h)"));
            assertTrue(content.toString().contains("01/09/2026"));
            assertTrue(content.toString().contains("Correction validée du solde"));
            assertTrue(content.toString().contains("−01h30 (−1.50h)"));
            assertFalse(content.toString().contains("Correction annulée"));
        }
    }
}
