package mg.galaxam.sirh.pointage.domain.calculation;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/** Correction du dernier badge de sortie valorisé, sans modifier le badge source. */
public final class CorrectedPointageTime {
    private CorrectedPointageTime() {}

    public record Exits(LocalTime morning, LocalTime afternoon) {}

    public static Exits apply(LocalTime morningEntry, LocalTime morningExit,
            LocalTime afternoonEntry, LocalTime afternoonExit, int correctionMinutes) {
        if (correctionMinutes == 0) return new Exits(morningExit, afternoonExit);
        if (Math.abs((long) correctionMinutes) > 480)
            throw new IllegalArgumentException("Correction limitée à 480 minutes par journée.");
        boolean afternoon = afternoonExit != null;
        LocalTime entry = afternoon ? (afternoonEntry != null ? afternoonEntry : morningEntry) : morningEntry;
        LocalTime exit = afternoon ? afternoonExit : morningExit;
        if (entry == null || exit == null)
            throw new IllegalArgumentException("La journée doit avoir un intervalle travaillé complet.");
        LocalTime corrected = exit.plusMinutes(correctionMinutes);
        long originalLength = ChronoUnit.MINUTES.between(entry, exit);
        if (originalLength < 0) originalLength += 1440;
        long correctedLength = originalLength + correctionMinutes;
        if (correctedLength < 0 || correctedLength > 1440)
            throw new IllegalArgumentException("La correction dépasse l'intervalle travaillé.");
        return afternoon ? new Exits(morningExit, corrected) : new Exits(corrected, afternoonExit);
    }
}
