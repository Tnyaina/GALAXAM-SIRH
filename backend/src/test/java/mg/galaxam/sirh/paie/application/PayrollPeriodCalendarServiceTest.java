package mg.galaxam.sirh.paie.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class PayrollPeriodCalendarServiceTest {
    private final PayrollPeriodCalendarService service = new PayrollPeriodCalendarService(
            Clock.fixed(Instant.parse("2026-10-16T04:00:00Z"), ZoneId.of("Indian/Antananarivo")));

    @Test
    void laDateDu26EntreDansLaCampagneSuivante() {
        var suggestion = service.forDate(LocalDate.of(2026, 12, 26));
        assertEquals("2027-01", suggestion.code());
        assertEquals(LocalDate.of(2026, 12, 26), suggestion.dateDebut());
        assertEquals(LocalDate.of(2027, 1, 25), suggestion.dateFin());
    }

    @Test
    void laSuiteEtLeCycleCivilOntLeMemeProprietaire() {
        assertEquals("2026-11", service.next(LocalDate.of(2026, 10, 25), "Octobre 2026").code());
        assertEquals("2026-10", service.next(LocalDate.of(2026, 8, 31), "Août 2026").code());
        var civil = service.forCode("2026-02", true);
        assertEquals(LocalDate.of(2026, 2, 1), civil.dateDebut());
        assertEquals(LocalDate.of(2026, 2, 28), civil.dateFin());
    }
}
