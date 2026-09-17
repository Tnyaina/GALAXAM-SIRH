package mg.galaxam.sirh.model.pointage.calculation;

/**
 * État hebdomadaire antérieur à préserver lors du calcul d'une semaine
 * civile chevauchant deux périodes de paie (semaines tronquées).
 * Permet la continuité exacte du déficit, du surplus compensable et des tranches HS30/HS50.
 */
public record WeeklyPriorState(
        long expectedMinutes,
        long workedMinutes,
        long dailySurplusMinutes,
        long compensatedMinutes,
        long deficitMinutes,
        long grossOvertimeMinutes,
        long overtime30Minutes,
        long overtime50Minutes
) {
    public static final WeeklyPriorState NONE = new WeeklyPriorState(0, 0, 0, 0, 0, 0, 0, 0);

    public WeeklyPriorState {
        if (expectedMinutes < 0 || workedMinutes < 0 || dailySurplusMinutes < 0
                || compensatedMinutes < 0 || deficitMinutes < 0 || grossOvertimeMinutes < 0
                || overtime30Minutes < 0 || overtime50Minutes < 0) {
            throw new IllegalArgumentException("L'état hebdomadaire antérieur ne peut pas contenir de valeurs négatives.");
        }
    }
}
