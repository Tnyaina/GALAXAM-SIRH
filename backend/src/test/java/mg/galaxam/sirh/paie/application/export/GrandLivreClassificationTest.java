package mg.galaxam.sirh.paie.application.export;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import org.junit.jupiter.api.Test;

class GrandLivreClassificationTest {
    @Test
    void libellesDeVariablesNeDictentPasLesColonnesDeSynthese() {
        var gain = new BulletinPaieLigne();
        gain.setCodeRubrique("PRIME_CONGE_HS_CNAPS");
        gain.setCategorie("PRIME");
        gain.setSens("GAIN");
        gain.setEtapeCalcul("BRUT");
        gain.setMontantSalarial(new BigDecimal("10000"));
        var retenue = new BulletinPaieLigne();
        retenue.setCodeRubrique("RETENUE_PRET_EXCEPTION");
        retenue.setCategorie("RETENUE_DIVERSE");
        retenue.setSens("RETENUE");
        retenue.setEtapeCalcul("RETENUE_NET");
        retenue.setMontantSalarial(new BigDecimal("500"));
        BigDecimal[] values = GrandLivrePaieExportService.summaryValues(new BulletinPaie(), List.of(gain, retenue));
        assertEquals(0, values[4].compareTo(BigDecimal.ZERO));
        assertEquals(0, values[6].compareTo(new BigDecimal("10000")));
        assertEquals(0, values[8].compareTo(BigDecimal.ZERO));
        assertEquals(0, values[13].compareTo(BigDecimal.ZERO));
        assertEquals(0, values[14].compareTo(new BigDecimal("500")));
    }
}
