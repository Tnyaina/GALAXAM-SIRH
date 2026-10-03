package mg.galaxam.sirh.pointage.domain.calculation;

public enum NightWorkType {
    NONE,
    // Conservés pour la lecture des snapshots antérieurs ; le nouveau pointage émet OCCASIONNEL.
    NIGHT_UNCLASSIFIED,
    HABITUEL,
    OCCASIONNEL
}
