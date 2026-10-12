package mg.galaxam.sirh.contrat;

import mg.galaxam.sirh.contrat.persistence.RefTypeDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefTypeDocumentUploadRulesTest {
    @Test
    void normalizesConfiguredExtensions() throws Exception {
        RefTypeDocument type = validType();
        type.setExtensionAutorisee(".PDF, jpg,PDF");
        type.controler(null);
        assertEquals("pdf,jpg", type.getExtensionAutorisee());
    }

    @Test
    void rejectsEmptyExtensionsAndSizesBeyondTechnicalLimit() {
        RefTypeDocument noExtensions = validType();
        noExtensions.setExtensionAutorisee(" , ");
        assertThrows(IllegalArgumentException.class, () -> noExtensions.controler(null));

        RefTypeDocument tooLarge = validType();
        tooLarge.setTailleMaxMo(RefTypeDocument.MAX_TAILLE_MAX_MO + 1);
        assertThrows(IllegalArgumentException.class, () -> tooLarge.controler(null));
    }

    private static RefTypeDocument validType() {
        RefTypeDocument type = new RefTypeDocument();
        type.setLibelle("Pièce d'identité");
        type.setCategorie("Identité");
        type.setExtensionAutorisee("pdf,jpg");
        type.setTailleMaxMo(10);
        return type;
    }
}
