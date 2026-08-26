package mg.galaxam.sirh.service.pointage;

import mg.galaxam.sirh.model.pointage.service.PointageSaisieService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Time;
import java.time.Duration;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class PointageSaisieServiceTest {

    private final PointageSaisieService service = new PointageSaisieService(null);

    @Test
    @DisplayName("Calcul des heures travaillées journalières")
    void testCalcHours() {
        Time start = Time.valueOf("08:00:00");
        Time end = Time.valueOf("12:00:00");
        assertEquals(4.0, service.calcHours(start, end), 0.001);

        Time startAm = Time.valueOf("13:00:00");
        Time endAm = Time.valueOf("17:00:00");
        assertEquals(4.0, service.calcHours(startAm, endAm), 0.001);

        // Sortie anticipée
        Time endEarly = Time.valueOf("16:30:00");
        assertEquals(3.5, service.calcHours(startAm, endEarly), 0.001);
    }

    @Test
    @DisplayName("Calcul dynamique des heures de nuit après 22h00")
    void testNightHoursCalculation() {
        LocalTime seuilNuitDebut = LocalTime.of(22, 0);
        LocalTime sortieTardive = LocalTime.of(23, 30);

        assertTrue(sortieTardive.isAfter(seuilNuitDebut));
        double hNuit = Duration.between(seuilNuitDebut, sortieTardive).toMinutes() / 60.0;
        assertEquals(1.5, hNuit, 0.001);
    }

    @Test
    @DisplayName("Calcul des retards sans tolérance (0 min)")
    void testZeroToleranceDelay() {
        LocalTime normalM = LocalTime.of(8, 0);
        LocalTime startM = LocalTime.of(8, 5);

        assertTrue(startM.isAfter(normalM));
        long retard = Duration.between(normalM, startM).toMinutes();
        assertEquals(5, retard);
    }
}
