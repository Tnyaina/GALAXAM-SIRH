package mg.galaxam.sirh.unit.pointage;

import mg.galaxam.sirh.model.pointage.calculation.WorkInterval;
import mg.galaxam.sirh.model.pointage.calculation.WorkIntervalFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkIntervalFactoryTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 17);

    @Test
    @DisplayName("W01: Journée standard avec pause déjeuner -> 2 intervalles distincts (480 min au total)")
    void w01_standardDayProducesTwoIntervalsPreservingBreak() {
        List<WorkInterval> intervals = WorkIntervalFactory.createIntervals(
                101, 1, DATE,
                LocalTime.of(8, 0), LocalTime.of(12, 0),
                LocalTime.of(13, 0), LocalTime.of(17, 0),
                false, "TEST"
        );

        assertThat(intervals).hasSize(2);

        WorkInterval mat = intervals.get(0);
        assertThat(mat.start()).isEqualTo(LocalDateTime.of(2026, 9, 17, 8, 0));
        assertThat(mat.end()).isEqualTo(LocalDateTime.of(2026, 9, 17, 12, 0));
        assertThat(mat.durationMinutes()).isEqualTo(240);

        WorkInterval am = intervals.get(1);
        assertThat(am.start()).isEqualTo(LocalDateTime.of(2026, 9, 17, 13, 0));
        assertThat(am.end()).isEqualTo(LocalDateTime.of(2026, 9, 17, 17, 0));
        assertThat(am.durationMinutes()).isEqualTo(240);

        long total = intervals.stream().mapToLong(WorkInterval::durationMinutes).sum();
        assertThat(total).isEqualTo(480);
    }

    @Test
    @DisplayName("W02: Vacation de nuit traversant minuit (21:00 -> 05:00) -> cross-midnight valide (480 min)")
    void w02_crossMidnightProducesValidInterval() {
        WorkInterval interval = WorkIntervalFactory.resolveSingleInterval(
                101, 1, DATE,
                LocalTime.of(21, 0), LocalTime.of(5, 0),
                "TEST_CROSS"
        );

        assertThat(interval).isNotNull();
        assertThat(interval.start()).isEqualTo(LocalDateTime.of(2026, 9, 17, 21, 0));
        assertThat(interval.end()).isEqualTo(LocalDateTime.of(2026, 9, 18, 5, 0));
        assertThat(interval.durationMinutes()).isEqualTo(480);
    }

    @Test
    @DisplayName("W03: 08:00 -> 05:00 reconstruit fidèlement en J -> J+1 (21h = 1260 min) sans rejet arbitraire de durée")
    void w03_longShiftReconstructedFaithfullyWithoutArbitraryCap() {
        WorkInterval interval = WorkIntervalFactory.resolveSingleInterval(
                101, 1, DATE,
                LocalTime.of(8, 0), LocalTime.of(5, 0),
                "TEST_LONG"
        );

        assertThat(interval).isNotNull();
        assertThat(interval.start()).isEqualTo(LocalDateTime.of(2026, 9, 17, 8, 0));
        assertThat(interval.end()).isEqualTo(LocalDateTime.of(2026, 9, 18, 5, 0));
        assertThat(interval.durationMinutes()).isEqualTo(1260); // 21h * 60 = 1260 min
    }

    @Test
    @DisplayName("W04: Début identique à la fin (08:00 -> 08:00) -> rejet explicite (durée nulle)")
    void w04_sameStartEndThrowsException() {
        assertThatThrownBy(() -> WorkIntervalFactory.resolveSingleInterval(
                101, 1, DATE,
                LocalTime.of(8, 0), LocalTime.of(8, 0),
                "TEST_ZERO"
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Intervalle nul");
    }

    @Test
    @DisplayName("W05: Pointage incomplet (entrée présente, sortie absente) -> aucun intervalle inventé")
    void w05_incompletePunchEntryWithoutExitReturnsNull() {
        WorkInterval interval = WorkIntervalFactory.resolveSingleInterval(
                101, 1, DATE,
                LocalTime.of(8, 0), null,
                "TEST_INCOMPLETE"
        );
        assertThat(interval).isNull();

        List<WorkInterval> intervals = WorkIntervalFactory.createIntervals(
                101, 1, DATE,
                LocalTime.of(8, 0), null,
                null, null,
                false, "TEST_INCOMPLETE"
        );
        assertThat(intervals).isEmpty();
    }

    @Test
    @DisplayName("W06: Pointage incomplet (sortie présente, entrée absente) -> aucun intervalle inventé")
    void w06_incompletePunchExitWithoutEntryReturnsNull() {
        WorkInterval interval = WorkIntervalFactory.resolveSingleInterval(
                101, 1, DATE,
                null, LocalTime.of(17, 0),
                "TEST_INCOMPLETE"
        );
        assertThat(interval).isNull();

        List<WorkInterval> intervals = WorkIntervalFactory.createIntervals(
                101, 1, DATE,
                null, null,
                null, LocalTime.of(17, 0),
                false, "TEST_INCOMPLETE"
        );
        assertThat(intervals).isEmpty();
    }
}
