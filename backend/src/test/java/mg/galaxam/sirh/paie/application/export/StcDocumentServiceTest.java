package mg.galaxam.sirh.paie.application.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class StcDocumentServiceTest {
    @Test
    void pdfA4LisibleAvecMontantsEtAucuneDeclarationDePaiementAjoutee() throws Exception {
        byte[] pdf = new StcDocumentService().render("DECOMPTE STC",
                List.of("Net scelle : 123456.00 Ar", "Ce decompte ne constitue pas une preuve de paiement."));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(1, document.getNumberOfPages());
            assertEquals(595, (int) document.getPage(0).getMediaBox().getWidth());
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("123456.00 Ar"));
            assertTrue(text.contains("ne constitue pas une preuve de paiement"));
            assertFalse(text.contains("integralement regle"));
        }
    }

    @Test
    void paginationConserveLesLignes() throws Exception {
        List<String> lines = java.util.stream.IntStream.range(0, 95)
                .mapToObj(i -> "Ligne numero " + i).toList();
        byte[] pdf = new StcDocumentService().render("BORDEREAU", lines);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() > 1);
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Ligne numero 0"));
            assertTrue(text.contains("Ligne numero 94"));
        }
    }
}
