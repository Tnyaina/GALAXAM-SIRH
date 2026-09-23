package mg.galaxam.sirh.pointage.domain.calculation;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Représentation canonique et continue d'un intervalle de travail réel.
 * Ne contient pas de payrollPeriodId car un intervalle continu peut traverser minuit
 * et donc chevaucher deux périodes de paie distinctes (ex: 25/MM 21h -> 26/MM 05h).
 */
public record WorkInterval(
        LocalDateTime start,
        LocalDateTime end,
        Integer employeeId,
        Integer pointageId,
        String sourceRef
) {
    public WorkInterval {
        if (start == null || end == null) {
            throw new IllegalArgumentException("WorkInterval start et end doivent être non nuls.");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("WorkInterval end doit être strictement après start: " + start + " -> " + end);
        }
    }

    public long durationMinutes() {
        return Duration.between(start, end).toMinutes();
    }
}
