package mg.galaxam.sirh.model.conge.engine;

/**
 * Sources canoniques de provenance d'un taux journalier de congé (TCONGE).
 * Classification : GALAXAM_CURRENT_BUSINESS_RULE
 */
public enum TcongeSourceProvenance {
    /** Taux repris lors de la migration historique sans faux bulletins ni faux droits (ex: KAJY Havana = 8669.52) */
    KAJY_MIGRATION,

    /** Taux calculé à l'achèvement d'une période de référence de 12 mois (CumulValorisation / CumulDroits) */
    CALCUL_PERIODE_REFERENCE,

    /** Amorçage canonique contractuel pour un salarié sans historique (SalaireBase / 30) */
    INITIALISATION_BASE_30,

    /** Taux repris lors du cutover initial explicite (solde + TCONGE + optionnellement cumuls) */
    REPRISE_CUTOVER
}
