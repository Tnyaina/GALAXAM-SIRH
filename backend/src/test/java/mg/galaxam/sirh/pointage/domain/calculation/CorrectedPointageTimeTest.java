package mg.galaxam.sirh.pointage.domain.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class CorrectedPointageTimeTest {
    @Test
    void conserveLeBadgeEtCorrigeSeulementLaSortieValorisee() {
        LocalTime badge = LocalTime.of(17, 3);
        var original = CorrectedPointageTime.apply(LocalTime.of(8, 26), LocalTime.of(12, 45),
                LocalTime.of(13, 46), badge, 0);
        var corrected = CorrectedPointageTime.apply(LocalTime.of(8, 26), LocalTime.of(12, 45),
                LocalTime.of(13, 46), badge, -7);
        assertEquals(badge, original.afternoon());
        assertEquals(LocalTime.of(16, 56), corrected.afternoon());
    }

    @Test
    void appliqueUneDemiJourneeEtRejetteUnIntervalleNegatif() {
        var corrected = CorrectedPointageTime.apply(LocalTime.of(8, 0), LocalTime.of(12, 0),
                null, null, -7);
        assertEquals(LocalTime.of(11, 53), corrected.morning());
        assertThrows(IllegalArgumentException.class, () -> CorrectedPointageTime.apply(
                LocalTime.of(8, 0), LocalTime.of(12, 0), null, null, -241));
    }

    @Test
    void dixHuitCorrectionsDeSeptMinutesRepresententDeuxHeuresSix() {
        int minutes = 0;
        for (int day = 0; day < 18; day++) {
            var exit = CorrectedPointageTime.apply(LocalTime.of(8, 26), LocalTime.of(12, 45),
                    LocalTime.of(13, 46), LocalTime.of(17, 3), -7);
            minutes += java.time.temporal.ChronoUnit.MINUTES.between(exit.afternoon(), LocalTime.of(17, 3));
        }
        assertEquals(126, minutes);
    }
}
