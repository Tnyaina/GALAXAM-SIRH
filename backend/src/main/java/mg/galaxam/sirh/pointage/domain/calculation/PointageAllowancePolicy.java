package mg.galaxam.sirh.pointage.domain.calculation;

/** Règles communes d'attribution des indemnités journalières dans la grille et le contrôle. */
public final class PointageAllowancePolicy {
    private PointageAllowancePolicy() {}

    /** Le défaut s'applique uniquement à une journée éligible, configurée et sans exclusion individuelle. */
    public static boolean defaultGranted(boolean eligibleDay, boolean configuredByDefault, boolean excluded) {
        return eligibleDay && configuredByDefault && !excluded;
    }

    /** Une décision enregistrée, y compris un refus explicite, prime toujours sur le défaut. */
    public static boolean resolve(Boolean recordedDecision, boolean defaultDecision) {
        return recordedDecision != null ? recordedDecision : defaultDecision;
    }
}
