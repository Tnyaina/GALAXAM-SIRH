package mg.galaxam.sirh.model.pointage.calculation;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Entité temporelle unitaire qualifiée :
 * Porte à la fois sa qualification HS (fournie par Phase 1B)
 * et ses qualifications calendaires/horaires (résolues par Phase 1C-A).
 * C'est la source canonique de vérité consommée par la Paie pour la valorisation monétaire.
 */
public record QualifiedTimeBucket(
        LocalDateTime start,
        LocalDateTime end,
        long minutes,
        OvertimeTier overtimeTier,
        DaySpecialType dayType,
        NightWorkType nightType,
        Integer payrollPeriodId,
        Integer pointageId
) {
    public QualifiedTimeBucket {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start et end doivent être non nuls.");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end doit être strictement après start: " + start + " -> " + end);
        }
        if (minutes <= 0) {
            throw new IllegalArgumentException("minutes doit être strictement positif: " + minutes);
        }
        if (overtimeTier == null) {
            throw new IllegalArgumentException("overtimeTier ne peut pas être nul.");
        }
        if (dayType == null) {
            throw new IllegalArgumentException("dayType ne peut pas être nul.");
        }
        if (nightType == null) {
            throw new IllegalArgumentException("nightType ne peut pas être nul.");
        }
        long expected = Duration.between(start, end).toMinutes();
        if (minutes != expected) {
            throw new IllegalArgumentException("minutes (" + minutes + ") incohérent avec la durée réelle entre " + start + " et " + end + " (" + expected + ")");
        }
    }

    public boolean isSunday() {
        return dayType.isSunday();
    }

    public boolean isPublicHoliday() {
        return dayType.isPublicHoliday();
    }

    public boolean isSaturday() {
        return dayType.isSaturday();
    }

    public boolean isNight() {
        return nightType != NightWorkType.NONE;
    }
}
