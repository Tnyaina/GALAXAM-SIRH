package mg.galaxam.sirh.unit.paie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import mg.galaxam.sirh.paie.domain.dto.PayrollAttendanceInput;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PayrollAttendanceInputTest {

    @Test
    @DisplayName("Compatibilité constructeur historique et propagation vers PayrollCalculationInput")
    void snapshotProvenanceAndExactMinutesFeedPayrollInput() {
        PayrollAttendanceInput snapshot = new PayrollAttendanceInput(
                42, 3, 7, 9, Instant.parse("2026-09-30T20:00:00Z"),
                10_400, 420, 75, 135, 480, 12,
                new BigDecimal("22"), new BigDecimal("20"), BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal("20"), new BigDecimal("40000"), new BigDecimal("19.5"),
                new BigDecimal("47154.51"));
        PayrollCalculationInput input = new PayrollCalculationInput();
        input.setIdEmploye(7);
        input.setIdPeriodePaie(9);

        input.setAttendanceInput(snapshot);

        assertThat(input.getAttendanceInput().snapshotId()).isEqualTo(42);
        assertThat(input.getHeuresHs30()).isEqualByComparingTo("7.00");
        assertThat(input.getHeuresNuit()).isEqualByComparingTo("2.25");
        assertThat(input.getHeuresDimanche()).isEqualByComparingTo("0.00");
        assertThat(input.getHeuresFeriees()).isEqualByComparingTo("0.00");
        assertThat(input.getIndemniteRepas()).isEqualByComparingTo("40000");
    }

    @Test
    @DisplayName("Enrichissement Phase 1C : sundayMinutes et holidayMinutes transportés et convertis en heures")
    void enrichedSnapshotFeedsSundayAndHolidayHours() {
        // 480 min dimanche = 8.00h, 240 min férié = 4.00h, 120 min samedi = 2.00h
        PayrollAttendanceInput snapshot = new PayrollAttendanceInput(
                100, 1, 10, 5, Instant.parse("2026-09-25T18:00:00Z"),
                12_000, 480, 120, 180,
                480, 240, 120, // sunday, holiday, saturday
                0, 0,
                new BigDecimal("22"), new BigDecimal("22"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("22"), new BigDecimal("44000"), new BigDecimal("22"),
                new BigDecimal("52800"));

        PayrollCalculationInput input = new PayrollCalculationInput();
        input.setIdEmploye(10);
        input.setIdPeriodePaie(5);

        input.setAttendanceInput(snapshot);

        assertThat(input.getHeuresHs30()).isEqualByComparingTo("8.00");
        assertThat(input.getHeuresHs50()).isEqualByComparingTo("2.00");
        assertThat(input.getHeuresNuit()).isEqualByComparingTo("3.00");
        assertThat(input.getHeuresDimanche()).isEqualByComparingTo("8.00");
        assertThat(input.getHeuresFeriees()).isEqualByComparingTo("4.00");
        assertThat(input.getAttendanceInput().saturdayMinutes()).isEqualTo(120);
    }

    @Test
    @DisplayName("Validation contractuelle : les quantités négatives sont strictement rejetées")
    void negativeMinutesThrowException() {
        assertThatThrownBy(() -> new PayrollAttendanceInput(
                1, 1, 1, 1, Instant.now(),
                100, 0, 0, 0, -60, 0, 0, // negative sundayMinutes
                0, 0,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Une quantité Pointage ne peut pas être négative.");
    }
}
