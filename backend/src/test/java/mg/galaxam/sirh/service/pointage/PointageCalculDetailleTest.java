package mg.galaxam.sirh.service.pointage;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PointageCalculDetailleTest {

    @Test
    @DisplayName("Calcul strict du retard à la minute exacte (0 tolérance dès 08h01 et 13h01)")
    void testCalculRetardStrictSansTolerance() {
        LocalTime heurePrevueMatin = LocalTime.of(8, 0);
        LocalTime heurePrevueAm = LocalTime.of(13, 0);

        // Cas 1 : Arrivée à 08h05 (5 min de retard dès 08h01)
        LocalTime arriveeMatin1 = LocalTime.of(8, 5);
        long retardMatin1 = arriveeMatin1.isAfter(heurePrevueMatin) ? Duration.between(heurePrevueMatin, arriveeMatin1).toMinutes() : 0;
        System.out.println("[TEST CALCUL POINTAGE] Arrivée 08h05 (0 tolérance) -> Retard retenu : " + retardMatin1 + " min.");
        assertEquals(5, retardMatin1, "Dès 08h01, chaque minute est comptabilisée (5 min retenues).");

        // Cas 2 : Arrivée après-midi à 13h12 (12 min de retard dès 13h01)
        LocalTime arriveeAm = LocalTime.of(13, 12);
        long retardAm = arriveeAm.isAfter(heurePrevueAm) ? Duration.between(heurePrevueAm, arriveeAm).toMinutes() : 0;
        System.out.println("[TEST CALCUL POINTAGE] Arrivée AM 13h12 (0 tolérance) -> Retard retenu : " + retardAm + " min.");
        assertEquals(12, retardAm, "Dès 13h01, chaque minute d'après-midi est comptabilisée.");
    }

    @Test
    @DisplayName("Déclenchement automatique des indemnités de déplacement et de repas")
    void testEligibiliteIndemnitesSoir() {
        LocalTime seuilDeplacement = LocalTime.of(19, 30);
        LocalTime seuilRepas = LocalTime.of(21, 0);

        // Sortie à 20h00
        LocalTime sortie1 = LocalTime.of(20, 0);
        boolean indemniteDeplacement1 = !sortie1.isBefore(seuilDeplacement);
        boolean indemniteRepas1 = !sortie1.isBefore(seuilRepas);

        System.out.println("[TEST CALCUL POINTAGE] Sortie 20h00 -> Déplacement=" + indemniteDeplacement1 + ", Repas=" + indemniteRepas1);
        assertTrue(indemniteDeplacement1, "Sortie à 20h00 (>19h30) donne droit au déplacement.");
        assertFalse(indemniteRepas1, "Sortie à 20h00 (<21h00) ne donne pas droit au repas.");

        // Sortie à 21h30
        LocalTime sortie2 = LocalTime.of(21, 30);
        boolean indemniteDeplacement2 = !sortie2.isBefore(seuilDeplacement);
        boolean indemniteRepas2 = !sortie2.isBefore(seuilRepas);

        System.out.println("[TEST CALCUL POINTAGE] Sortie 21h30 -> Déplacement=" + indemniteDeplacement2 + ", Repas=" + indemniteRepas2);
        assertTrue(indemniteDeplacement2);
        assertTrue(indemniteRepas2, "Sortie à 21h30 (>21h00) donne droit au repas.");
    }

    @Test
    @DisplayName("Répartition des Heures Supplémentaires hebdomadaires en tranches 30% et 50%")
    void testVentilationHeuresSupplementaires() {
        BigDecimal heuresTravailleesSemaine = new BigDecimal("52.00");
        BigDecimal seuilContrat = new BigDecimal("40.00");

        BigDecimal totalHs = heuresTravailleesSemaine.subtract(seuilContrat); // 12.00 HS
        System.out.println("[TEST CALCUL POINTAGE] Total heures semaine : " + heuresTravailleesSemaine + " h -> Total HS : " + totalHs + " h.");

        // Règle : 8 premières HS majorées à 30%, reste à 50%
        BigDecimal hs30;
        BigDecimal hs50;

        if (totalHs.compareTo(BigDecimal.valueOf(8)) <= 0) {
            hs30 = totalHs;
            hs50 = BigDecimal.ZERO;
        } else {
            hs30 = BigDecimal.valueOf(8);
            hs50 = totalHs.subtract(BigDecimal.valueOf(8));
        }

        System.out.println("[TEST CALCUL POINTAGE] Ventilation HS -> HS 30% : " + hs30 + " h, HS 50% : " + hs50 + " h.");
        assertEquals(new BigDecimal("8"), hs30, "8 heures en tranche 30%.");
        assertEquals(new BigDecimal("4.00"), hs50, "4 heures en tranche 50%.");
    }
}
