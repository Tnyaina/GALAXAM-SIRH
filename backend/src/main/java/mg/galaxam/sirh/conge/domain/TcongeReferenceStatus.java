package mg.galaxam.sirh.conge.domain;

/**
 * Phase 2A-4C.1 — Statut multidimensionnel de la période de référence TCONGE pour un collaborateur.
 * Indépendant du statut du taux applicable pour permettre la coexistence d'une référence en construction
 * et d'un taux applicable actif.
 */
public enum TcongeReferenceStatus {
    MISSING,
    ACTIVE,
    CLOSED_AWAITING_ACTIVATION
}
