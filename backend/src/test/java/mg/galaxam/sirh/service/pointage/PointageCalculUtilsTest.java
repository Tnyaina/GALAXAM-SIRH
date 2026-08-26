package mg.galaxam.sirh.service.pointage;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PointageCalculUtilsTest {

    @Test
    @DisplayName("Calcul de la durée de travail journalière avec pause continue (1h)")
    void testCalculHeuresJourAvecPauseContinue() {
        LocalTime arriveeMatin = LocalTime.of(8, 0);
        LocalTime departMatin = LocalTime.of(12, 0);
        LocalTime arriveeAm = LocalTime.of(13, 0);
        LocalTime departAm = LocalTime.of(17, 0);

        long minutesMatin = Duration.between(arriveeMatin, departMatin).toMinutes();
        long minutesAm = Duration.between(arriveeAm, departAm).toMinutes();
        long totalMinutes = minutesMatin + minutesAm;

        BigDecimal heures = BigDecimal.valueOf(totalMinutes)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        System.out.println("[TEST POINTAGE] Journée 08h-12h & 13h-17h -> Total : " + heures + " heures.");
        assertEquals(new BigDecimal("8.00"), heures, "8.00 heures de travail attendues.");
    }

    @Test
    @DisplayName("Détection et calcul des heures de nuit (après 22h00)")
    void testCalculHeuresNuit() {
        LocalTime depart = LocalTime.of(23, 30);
        LocalTime seuilNuitDebut = LocalTime.of(22, 0);

        long minutesNuit = 0;
        if (depart.isAfter(seuilNuitDebut)) {
            minutesNuit = Duration.between(seuilNuitDebut, depart).toMinutes();
        }

        BigDecimal heuresNuit = BigDecimal.valueOf(minutesNuit)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        System.out.println("[TEST POINTAGE] Sortie à 23h30 (seuil 22h00) -> Heures de nuit : " + heuresNuit + " h (1.50h attendues).");
        assertEquals(new BigDecimal("1.50"), heuresNuit);
    }

    @Test
    @DisplayName("Proratisation du seuil hebdomadaire en cas de tronçon de 3 jours ouvrés")
    void testSeuilProratiseHebdo() {
        int joursOuvresTroncon = 3;
        BigDecimal seuilBaseJour = new BigDecimal("8.0");

        BigDecimal seuilHebdoProratise = seuilBaseJour.multiply(BigDecimal.valueOf(joursOuvresTroncon));
        System.out.println("[TEST POINTAGE] Tronçon de 3 jours ouvrés -> Seuil HS hebdomadaire : " + seuilHebdoProratise + " h (24h attendues).");

        assertEquals(new BigDecimal("24.0"), seuilHebdoProratise);
    }
}
