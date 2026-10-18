package mg.galaxam.sirh.paie.api;

import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.paie.domain.PayrollBusinessTime;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class BulletinReglementJustificatifTest {
    @Test
    void paymentCanOnlyDescribeAnAlreadyCompletedTransaction() {
        assertDoesNotThrow(() -> BulletinReglementController.validatePaymentDate(PayrollBusinessTime.today()));
        assertThrows(SirhBusinessException.class, () -> BulletinReglementController.validatePaymentDate(PayrollBusinessTime.today().plusDays(1)));
        assertThrows(SirhBusinessException.class, () -> BulletinReglementController.validatePaymentDate(null));
    }

    @Test
    void requiresRealProofForEachPayment() throws Exception {
        assertThrows(SirhBusinessException.class, () -> BulletinReglementController.justificatif(null));
        assertThrows(SirhBusinessException.class, () -> BulletinReglementController.justificatif(
                new MockMultipartFile("file", "preuve.pdf", "application/pdf", "texte libre".getBytes())));
        assertThrows(SirhBusinessException.class, () -> BulletinReglementController.justificatif(
                new MockMultipartFile("file", "preuve.pdf", "application/pdf", new byte[5 * 1024 * 1024 + 1])));
        var pdf = BulletinReglementController.justificatif(new MockMultipartFile(
                "file", "releve.pdf", "application/octet-stream", "%PDF-1.7".getBytes()));
        assertEquals("application/pdf", pdf.mime());
        assertEquals("releve.pdf", pdf.nom());
    }
}
