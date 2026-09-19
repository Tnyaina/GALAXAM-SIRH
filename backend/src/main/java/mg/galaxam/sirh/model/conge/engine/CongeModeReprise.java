package mg.galaxam.sirh.model.conge.engine;

/**
 * Modes de reprise et de démarrage des périodes de référence des congés.
 */
public enum CongeModeReprise {
    /** Historique complet disponible et reconstitué mois par mois */
    FULL_HISTORY,

    /** Reprise avec agrégats connus (Solde + TCONGE + Cumuls du cycle en cours) */
    AGGREGATED_OPENING,

    /** Reprise minimale (Solde + TCONGE uniquement, cumuls antérieurs UNKNOWN) */
    MINIMAL_OPENING,

    /** Nouveau salarié embauché post-cutover (démarrage propre à zéro) */
    NEW_HIRE
}
