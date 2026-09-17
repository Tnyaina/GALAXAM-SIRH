package mg.galaxam.sirh.model.pointage.calculation;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Segment temporel homogène issu du partitionnement (avant projection des HS).
 * Garantit l'exactitude à la minute près et la conservation de durée.
 */
public record TemporalBucket(
        LocalDateTime start,
        LocalDateTime end,
        long minutes,
        DaySpecialType dayType,
        NightWorkType nightType,
        Integer payrollPeriodId,
        Integer pointageId
) {
    public TemporalBucket {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start et end doivent être non nuls.");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end doit être strictement après start: " + start + " -> " + end);
        }
        if (minutes <= 0) {
            throw new IllegalArgumentException("minutes doit être strictement positif: " + minutes);
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
