package mg.galaxam.sirh.paie.application;

import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import org.springframework.web.multipart.MultipartFile;

/** Pièce externe attestant un paiement déjà effectué. */
public record PaymentEvidence(String name, String mime, byte[] bytes) {
    public static PaymentEvidence from(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024)
            throw invalid();
        byte[] bytes = file.getBytes();
        if (bytes.length == 0 || bytes.length > 5L * 1024 * 1024) throw invalid();
        String mime;
        if (bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-')
            mime = "application/pdf";
        else if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G')
            mime = "image/png";
        else if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff)
            mime = "image/jpeg";
        else throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, "Le justificatif doit être un PDF, PNG ou JPEG valide.");
        String original = file.getOriginalFilename() == null ? "justificatif" : file.getOriginalFilename().replace('\\', '/');
        String name = original.substring(original.lastIndexOf('/') + 1).replaceAll("[\\r\\n]", "");
        return new PaymentEvidence(name.isBlank() ? "justificatif" : name, mime, bytes);
    }

    private static SirhBusinessException invalid() {
        return new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                "Un justificatif PDF, PNG ou JPEG de 5 Mo maximum est obligatoire.");
    }
}
