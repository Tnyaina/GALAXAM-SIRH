/** Classement de lecture commun au journal salarié et à la rectification DAF. */
export function isPointageException(day = {}) {
    const code = String(day.statut || day.codeStatut || '').toUpperCase();
    return Boolean(day.isConge || day.congeApprouve || day.isFerie || day.isFerieChome || day.isFerieTravaille
        || day.isRepos || day.isAbsent || day.isDemiJournee
        || ['CONGE', 'CONGE_PAYE', 'DEMI_JOURNEE', 'FERIE_CHOME', 'FERIE_TRAVAILLE', 'REPOS',
            'SAMEDI_TRAVAILLE', 'DIMANCHE_TRAVAILLE', 'ABSENT', 'ABSENCE_JUSTIFIEE', 'ABSENCE_NON_JUSTIFIEE'].includes(code));
}

export function isPointageAnomaly(day = {}) {
    const code = String(day.statut || day.codeStatut || '').toUpperCase();
    return Boolean(day.isAnomalie || day.anomalieSaisie || day.isRetard || day.isAbsent
        || Number(day.minutesRetard) > 0
        || ['ANOMALIE_SAISIE', 'ANOMALIE', 'RETARD', 'ABSENT', 'ABSENCE_NON_JUSTIFIEE', 'NON_SAISI'].includes(code));
}

export function filterPointageDays(days = [], filter = 'ALL') {
    if (filter === 'EXCEPTIONS') return days.filter(isPointageException);
    if (filter === 'ANOMALIES') return days.filter(isPointageAnomaly);
    return days;
}
