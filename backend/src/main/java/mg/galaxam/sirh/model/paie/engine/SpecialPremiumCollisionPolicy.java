package mg.galaxam.sirh.model.paie.engine;

/**
 * Politique d'arbitrage en cas de collision de sujétions/majorations spéciales (Nuit, Dimanche, Jour Férié).
 * Par défaut, le moteur refuse de choisir silencieusement (UNRESOLVED) pour garantir
 * la conformité stricte avec l'Art. 3 du Décret 72-226 et les exigences d'audit GALAXAM.
 */
public enum SpecialPremiumCollisionPolicy {
    /** Défaut strict : bloque avec SPECIAL_PREMIUM_COLLISION_UNRESOLVED dès que >= 2 sujétions coïncident sans accord d'entreprise */
    UNRESOLVED,
    /** Retient la majoration spéciale la plus élevée parmi les sujétions actives (doctrine du plus favorable légal) */
    MAX_PREMIUM,
    /** Cumul additif : règle conventionnelle d'entreprise explicite plus favorable autorisant le cumul des sujétions */
    CUMUL_ADDITIF,
    /** Priorité stricte au jour férié chômé légal */
    HOLIDAY_ONLY
}
