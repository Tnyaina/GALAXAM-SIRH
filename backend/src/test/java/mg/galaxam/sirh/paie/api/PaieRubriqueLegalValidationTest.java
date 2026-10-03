package mg.galaxam.sirh.paie.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.paie.persistence.PaieRubrique;
import org.junit.jupiter.api.Test;

class PaieRubriqueLegalValidationTest {
    private PaieRubrique rubrique() {
        var rubrique = new PaieRubrique();
        rubrique.setCode("INDEMNITE_MISSION_TEST");
        rubrique.setLibelle("Indemnité de mission");
        rubrique.setCategorie("INDEMNITE_IMPOSABLE");
        rubrique.setTypeValeur("MONTANT");
        rubrique.setSens("GAIN");
        rubrique.setEtapeCalcul("BRUT");
        rubrique.setMethodeCalcul("MONTANT");
        rubrique.setPolitiqueArrondi("CENTIME");
        rubrique.setConditionApplication("TOUS");
        rubrique.setSoumisIrsa(true);
        rubrique.setSoumisCnaps(true);
        rubrique.setSoumisSante(true);
        rubrique.setSoumisFmfp(true);
        return rubrique;
    }

    @Test
    void refuseFmfpSansAssietteSociale() {
        var rubrique = rubrique();
        rubrique.setSoumisCnaps(false);
        assertThrows(SirhBusinessException.class,
                () -> PaieRubriqueController.validateManualRubrique(rubrique, false));
    }

    @Test
    void refuseAssietteSocialeSansFmfpExplicite() {
        var rubrique = rubrique();
        rubrique.setSoumisFmfp(false);
        assertThrows(SirhBusinessException.class,
                () -> PaieRubriqueController.validateManualRubrique(rubrique, false));
    }

    @Test
    void accepteFmfpHeriteDeLaCnaps() {
        var rubrique = rubrique();
        rubrique.setSoumisFmfp(null);
        assertDoesNotThrow(() -> PaieRubriqueController.validateManualRubrique(rubrique, false));
    }

    @Test
    void accepteAssiettesCnapsEtFmfpIdentiques() {
        var rubrique = rubrique();
        assertDoesNotThrow(() -> PaieRubriqueController.validateManualRubrique(rubrique, false));
    }

    @Test
    void refuseUnAjoutAuNetQuiAlimenteIrsa() {
        var rubrique = rubrique();
        rubrique.setEtapeCalcul("GAIN_NET");
        rubrique.setSoumisCnaps(false);
        rubrique.setSoumisSante(false);
        rubrique.setSoumisFmfp(false);
        assertThrows(SirhBusinessException.class,
                () -> PaieRubriqueController.validateManualRubrique(rubrique, false));
    }
}
