package mg.galaxam.sirh.model.pointage.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Conversions réservées aux contrats DTO/export historiques. */
public final class PointageTimeBoundaryMapper {
    private PointageTimeBoundaryMapper() {}

    public static double toLegacyDecimalHours(long minutes) {
        return BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static String formatHoursMinutes(long minutes) {
        long safe = Math.max(0, minutes);
        return String.format("%02dh%02d", safe / 60, safe % 60);
    }
}
