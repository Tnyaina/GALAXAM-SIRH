/**
 * Service utilitaire & helpers de formatage pour les modules Paie & Référentiels RH.
 */

export const PERIODICITES_INDEMNITE = [
    { value: 'MENSUEL_PRORATISE', label: 'Mensuel proratisé (selon présences & jours ouvrables)' },
    { value: 'JOURNALIER', label: 'Forfait journalier (par jour présent validé)' },
    { value: 'FIXE_MENSUEL', label: 'Mensuel fixe (indépendant du pointage)' },
];

/** Libellés de présentation des rubriques dont le code métier est stable. */
export function libelleRubriqueBulletin(ligne) {
    if (ligne?.codeRubrique === 'IMPOT_IRSA_BRUT') return 'IRSA brut arrondi à la dizaine supérieure';
    return ligne?.libelleRubrique || '';
}

/** La clôture signifie que le parcours de campagne est terminé, même si ses étapes deviennent inaccessibles. */
export function progressionCampagnePaie(steps, closed = false) {
    const safeSteps = Array.isArray(steps) ? steps : [];
    return {
        completed: closed ? safeSteps.length : safeSteps.filter(step => step.state === 'COMPLETED').length,
        total: safeSteps.length,
    };
}

/** Les étapes verrouillées après clôture sont achevées, mais restent non navigables. */
export function etapesAfficheesCampagnePaie(steps, closed = false) {
    const safeSteps = Array.isArray(steps) ? steps : [];
    if (!closed) return safeSteps;
    return safeSteps.map(step => step.state === 'LOCKED' ? { ...step, state: 'COMPLETED' } : step);
}
