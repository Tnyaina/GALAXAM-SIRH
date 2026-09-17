package mg.galaxam.sirh.model.pointage.calculation;

/**
 * Qualification calendaire objective d'une date (ou segment temporel).
 * Préserve formellement la coexistence entre DIMANCHE et JOUR FÉRIÉ (SUNDAY_AND_PUBLIC_HOLIDAY).
 * Ne décide d'aucune priorité financière (réservée à la Phase 1C-B).
 */
public enum DaySpecialType {
    STANDARD,
    SATURDAY,
    SUNDAY,
    PUBLIC_HOLIDAY,
    SUNDAY_AND_PUBLIC_HOLIDAY;

    public boolean isSunday() {
        return this == SUNDAY || this == SUNDAY_AND_PUBLIC_HOLIDAY;
    }

    public boolean isPublicHoliday() {
        return this == PUBLIC_HOLIDAY || this == SUNDAY_AND_PUBLIC_HOLIDAY;
    }

    public boolean isSaturday() {
        return this == SATURDAY;
    }

    public boolean isStandard() {
        return this == STANDARD;
    }
}
