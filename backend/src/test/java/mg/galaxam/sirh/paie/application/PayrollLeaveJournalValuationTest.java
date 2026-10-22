package mg.galaxam.sirh.paie.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayrollLeaveJournalValuationTest {
    private static BulletinPaieLigne ligne(String code, String sens, String categorie, String montant) {
        BulletinPaieLigne ligne = new BulletinPaieLigne();
        ligne.setCodeRubrique(code);
        ligne.setSens(sens);
        ligne.setCategorie(categorie);
        ligne.setMontantSalarial(new BigDecimal(montant));
        return ligne;
    }

    @Test
    void sansHeuresSupplementairesEtAvecAjustementsDeBase() {
        BigDecimal base = PayrollLeaveJournalService.calculerBaseValorisation(new BigDecimal("900000"), List.of(
                ligne("RETENUE_ABSENCE", "RETENUE", "SALAIRE_BASE", "30000"),
                ligne("INDEMNITE_CONGE_PAYE", "GAIN", "SALAIRE_BASE", "10000")));
        assertEquals(new BigDecimal("880000"), base);
        assertEquals(new BigDecimal("73333.33"), base.multiply(new BigDecimal("2.5"))
                .divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP));
    }

    @Test
    void seulsLesGainsHsEntrentUneFoisDansLeLotEtLeCumul() {
        List<BulletinPaieLigne> lignes = List.of(
                ligne("HEURES_SUP_30", "GAIN", "HEURES_SUP", "13000"),
                ligne("HEURES_SUP_50", "GAIN", "HEURES_SUP", "15000"),
                ligne("MAJORATION_NUIT", "GAIN", "HEURES_SUP", "2000"),
                ligne("HS_IMPOSABLE_IRSA", "INFO", "HEURES_SUP", "28000"),
                ligne("HS_EXONEREE_IRSA", "INFO", "HEURES_SUP", "5000"),
                ligne("HS_AUTRE_INFO", "INFO", "HEURES_SUP", "1000"),
                ligne("HS_FAUX_GAIN", "GAIN", "AUTRE", "9000"));
        BigDecimal base = PayrollLeaveJournalService.calculerBaseValorisation(new BigDecimal("900000"), lignes);
        assertEquals(new BigDecimal("930000"), base);
        BigDecimal valeurMois = base.multiply(new BigDecimal("2.5"))
                .divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("77500.00"), valeurMois);
        BigDecimal cumulDroits = new BigDecimal("2.5").add(new BigDecimal("2.5"));
        BigDecimal cumulValeur = new BigDecimal("75000.00").add(valeurMois);
        assertEquals(new BigDecimal("30500.00"),
                PayrollLeaveJournalService.calculerTauxJournalier(cumulValeur, cumulDroits));
    }
}
