package mg.galaxam.sirh.paie.application.export;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import mg.galaxam.sirh.paie.domain.dto.BulletinPaieComplet;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BulletinDocumentServiceTest {
    @Test
    void longRubriqueRemainsReadableAndWorkbookHasNavigation() throws Exception {
        BulletinPaie bulletin = new BulletinPaie();
        bulletin.setNomPrenom("AGENT Y TNR");
        bulletin.setMatricule("199");
        bulletin.setReferenceBulletin("BP-2026-08-199");
        bulletin.setStatut("VALIDE");
        bulletin.setTotalGainsBruts(new BigDecimal("411361.01"));
        bulletin.setNetAPayer(new BigDecimal("399120"));
        BulletinPaieLigne ligne = new BulletinPaieLigne();
        ligne.setLibelleRubrique("Retenue pour Absences Décomptées (Heures réelles)");
        ligne.setSens("RETENUE");
        ligne.setMontantSalarial(new BigDecimal("673.09"));
        BulletinPaieComplet complet = new BulletinPaieComplet(bulletin, List.of(ligne));
        BulletinDocumentService service = new BulletinDocumentService();
        BulletinDocumentService.Context context = new BulletinDocumentService.Context(
                "2026-08", "26/07/2026 au 25/08/2026", "Galaxam Logistics", "Antananarivo", "123", "456");

        try (var pdf = Loader.loadPDF(service.pdf(complet, context))) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Heures réelles"), text);
            assertTrue(text.contains("NET À PAYER"));
        }
        try (var excel = new XSSFWorkbook(new ByteArrayInputStream(service.excel(complet, context)))) {
            var sheet = excel.getSheetAt(0);
            assertEquals("BULLETIN DE PAIE", sheet.getRow(0).getCell(0).getStringCellValue());
            assertTrue(sheet.getNumMergedRegions() > 0);
            assertNotNull(sheet.getPaneInformation());
            assertTrue(sheet.getCTWorksheet().isSetAutoFilter());
        }
    }
}
