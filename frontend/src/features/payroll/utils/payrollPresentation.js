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
    return ligne?.libelleRubrique || '';
}

/** La ligne intermédiaire d'IRSA reste disponible pour le calcul, sans figurer sur le bulletin. */
export function rubriquesPaieAffichables(lignes) {
    return (Array.isArray(lignes) ? lignes : []).filter(ligne => ligne?.codeRubrique !== 'IMPOT_IRSA_BRUT');
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

/** Source unique de la présentation du parcours, sur le centre et les pages d'étape. */
export function presentationParcoursCampagnePaie(steps, period) {
    const closed = period?.workflowStatuses?.paie?.code === 'CLOTUREE'
        || period?.statutCode === 'CLOTUREE'
        || period?.state === 'CLOTUREE';
    return {
        closed,
        steps: etapesAfficheesCampagnePaie(steps, closed),
        progress: progressionCampagnePaie(steps, closed),
    };
}

/** Présente toujours séparément l'avancement du pointage et celui de la paie. */
export function resumeStatutsPeriode(period) {
    const workflowStatuses = period?.workflowStatuses;
    const labels = [
        workflowStatuses?.pointage?.label && `Pointage : ${workflowStatuses.pointage.label}`,
        workflowStatuses?.paie?.label && `Paie : ${workflowStatuses.paie.label}`,
    ].filter(Boolean);

    if (labels.length) return labels.join(' · ');
    return period?.statutLibelle || period?.statutCode || period?.statut || period?.state || 'Statut indisponible';
}
