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

    @Test
    void testAjustementArrondiNeGonflePasLesPrimesOuRetenues() {
        // Point 12 : AJUSTEMENT_ARRONDI ne doit pas être comptabilisé dans Indemnités / primes ni dans Autres retenues
        var gainArrondi = new BulletinPaieLigne();
        gainArrondi.setCodeRubrique("AJUSTEMENT_ARRONDI");
        gainArrondi.setLibelleRubrique("Ajustement Arrondi Monétaire (Écart de Règlement)");
        gainArrondi.setCategorie("NET");
        gainArrondi.setSens("GAIN");
        gainArrondi.setMontantSalarial(new BigDecimal("4.50"));

        var retenueArrondi = new BulletinPaieLigne();
        retenueArrondi.setCodeRubrique("AJUSTEMENT_ARRONDI");
        retenueArrondi.setLibelleRubrique("Ajustement Arrondi Monétaire (Écart de Règlement)");
        retenueArrondi.setCategorie("NET");
        retenueArrondi.setSens("RETENUE");
        retenueArrondi.setMontantSalarial(new BigDecimal("3.20"));

        var bp = new BulletinPaie();
        BigDecimal[] valuesGain = GrandLivrePaieExportService.summaryValues(bp, List.of(gainArrondi));
        // Col 6 : Indemnités / primes
        assertEquals(0, valuesGain[6].compareTo(BigDecimal.ZERO), "AJUSTEMENT_ARRONDI gain ne doit pas gonfler les primes");

        BigDecimal[] valuesRetenue = GrandLivrePaieExportService.summaryValues(bp, List.of(retenueArrondi));
        // Col 14 : Autres retenues
        assertEquals(0, valuesRetenue[14].compareTo(BigDecimal.ZERO), "AJUSTEMENT_ARRONDI retenue ne doit pas gonfler les autres retenues");
    }
}
