package mg.galaxam.sirh.paie.application.export;

import mg.galaxam.sirh.paie.domain.dto.BulletinPaieComplet;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BulletinIrsaLabelTest {
    @Test
    void exportsHideIntermediateIrsaLineFromPreviouslyStoredBulletin() throws Exception {
        String expected = "IRSA brut arrondi à la dizaine supérieure";
        BulletinPaie bulletin = new BulletinPaie();
        bulletin.setIdBulletin(2);
        bulletin.setIdPeriodePaie(1);
        bulletin.setIdEmploye(101);
        bulletin.setReferenceBulletin("BP-2026-03-EMP00101");
        bulletin.setMatricule("EMP00101");
        bulletin.setNomPrenom("Rakoto Jean");
        bulletin.setIntitulePoste("Chauffeur");
        bulletin.setNomDepartement("Logistique");
        bulletin.setNomSite("Toamasina");
        bulletin.setSalaireBase(new BigDecimal("1000000.00"));
        bulletin.setTotalGainsBruts(new BigDecimal("1000000.00"));
        bulletin.setTotalCotisationsSalariales(new BigDecimal("20000.00"));
        bulletin.setSalaireNetImposable(new BigDecimal("980000.00"));
        bulletin.setRevenuNetImposable(new BigDecimal("980000.00"));
        bulletin.setIrsaNet(new BigDecimal("80000.00"));
        bulletin.setTotalIndemnitesNonImposables(BigDecimal.ZERO);
        bulletin.setTotalRetenuesAvancesAcomptes(new BigDecimal("80000.00"));
        bulletin.setNetAvantArrondi(new BigDecimal("900000.00"));
        bulletin.setNetAPayer(new BigDecimal("900000.00"));

        BulletinPaieLigne irsa = new BulletinPaieLigne();
        irsa.setCodeRubrique("IMPOT_IRSA_BRUT");
        irsa.setLibelleRubrique("IRSA Brut (Barème Progressif 6 Tranches)");
        irsa.setSens("INFO");
        irsa.setMontantSalarial(new BigDecimal("18160.00"));
        irsa.setBaseCalcul(new BigDecimal("490000.00"));
        BulletinPaieComplet complet = new BulletinPaieComplet();
        complet.setBulletin(bulletin);
        complet.setLignes(List.of(irsa));
        BulletinDocumentService.Context context = new BulletinDocumentService.Context(
                "2026-03", "01/03/2026 au 31/03/2026", "GALAXAM LOGISTICS", "Antananarivo", "1234567890", "12345");
        BulletinDocumentService documents = new BulletinDocumentService();

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(documents.excel(complet, context)))) {
            var sheet = workbook.getSheet("Bulletin de paie");
            assertFalse(java.util.stream.StreamSupport.stream(sheet.spliterator(), false)
                    .anyMatch(row -> row.getCell(0) != null && expected.equals(row.getCell(0).getStringCellValue())));
            assertTrue(java.util.stream.StreamSupport.stream(sheet.spliterator(), false)
                    .anyMatch(row -> row.getCell(0) != null && "IRSA dû (net)".equals(row.getCell(0).getStringCellValue())));
        }
        try (PDDocument pdf = Loader.loadPDF(documents.pdf(complet, context))) {
            String text = Normalizer.normalize(new PDFTextStripper().getText(pdf), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").replaceAll("\\s+", " ");
            assertFalse(text.contains("IRSA brut arrondi a la dizaine superieure"));
            assertFalse(text.contains("IRSA Brut (Bareme Progressif 6 Tranches)"));
            assertTrue(text.contains("IRSA du (net)"), text);
        }
    }
}
