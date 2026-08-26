package mg.galaxam.sirh.service.pointage;


import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CotisationPaieCalculTest {

    @Test
    @DisplayName("Calcul des cotisations CNaPS plafonnées (1% salarié, 13% employeur)")
    void testCotisationsCnaps() {
        BigDecimal salaireBrut = new BigDecimal("2500000.00");
        BigDecimal smig = new BigDecimal("262680.00");
        BigDecimal plafondCnaps = smig.multiply(BigDecimal.valueOf(8)); // Plafond légal = 8 x SMIG

        System.out.println("[TEST CALCUL PAIE] Salaire brut : " + salaireBrut + " Ar | Plafond CNaPS (8x SMIG) : " + plafondCnaps + " Ar.");

        // Assiette de cotisation = min(brut, plafond)
        BigDecimal assietteCnaps = salaireBrut.min(plafondCnaps);
        assertEquals(plafondCnaps, assietteCnaps, "L'assiette doit être plafonnée.");

        // CNaPS Salariale = 1%
        BigDecimal cnapsSalariale = assietteCnaps.multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
        // CNaPS Patronale = 13%
        BigDecimal cnapsPatronale = assietteCnaps.multiply(new BigDecimal("0.13")).setScale(2, RoundingMode.HALF_UP);

        System.out.println("[TEST CALCUL PAIE] CNaPS Salariale (1%) : " + cnapsSalariale + " Ar | Patronale (13%) : " + cnapsPatronale + " Ar.");
        assertTrue(cnapsSalariale.compareTo(BigDecimal.ZERO) > 0);
        assertTrue(cnapsPatronale.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Calcul des cotisations Médicales OSTIE / AMIT (1% salarié, 5% employeur)")
    void testCotisationsMedicales() {
        BigDecimal salaireBrut = new BigDecimal("1200000.00");

        BigDecimal ostieSalariale = salaireBrut.multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ostiePatronale = salaireBrut.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);

        System.out.println("[TEST CALCUL PAIE] OSTIE Salariale : " + ostieSalariale + " Ar (12000 Ar attendus) | Patronale : " + ostiePatronale + " Ar (60000 Ar attendus).");

        assertEquals(new BigDecimal("12000.00"), ostieSalariale);
        assertEquals(new BigDecimal("60000.00"), ostiePatronale);
    }

    @Test
    @DisplayName("Calcul IRSA avec Minimum de Perception légal (3 000 Ar)")
    void testIrsaMinimumPerception() {
        BigDecimal irsaCalcule = new BigDecimal("1500.00"); // Inférieur au minimum
        BigDecimal minimumPerception = new BigDecimal("3000.00");

        BigDecimal irsaRetenu = irsaCalcule.max(minimumPerception);
        System.out.println("[TEST CALCUL PAIE] IRSA calculé: " + irsaCalcule + " Ar -> IRSA retenu (Minimum légal) : " + irsaRetenu + " Ar.");

        assertEquals(minimumPerception, irsaRetenu, "Le minimum légal de 3 000 Ar doit s'appliquer.");
    }
}
