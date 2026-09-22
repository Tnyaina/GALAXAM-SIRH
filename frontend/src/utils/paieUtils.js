/**
 * Service utilitaire & helpers de formatage pour les modules Paie & Référentiels RH.
 */

export const PERIODICITES_INDEMNITE = [
    { value: 'MENSUEL_PRORATISE', label: 'Mensuel proratisé (selon présences & jours ouvrables)' },
    { value: 'JOURNALIER', label: 'Forfait journalier (par jour présent validé)' },
    { value: 'FIXE_MENSUEL', label: 'Mensuel fixe (indépendant du pointage)' },
];

// ─── Formatteurs Utilitaires Canoniques (Délégués à dateUtils et moneyUtils) ─
export { fmtDateInput, fmtDate, fmtDateFr } from './dateUtils';
export { fmtMoney, fmtPercent } from './moneyUtils';

/**
 * PAIE_STATUS_PRESENTATION
 *
 * Map statut de période → variant de Badge pour l'affichage.
 * PRESENTATION ONLY — ne pas utiliser pour prendre des décisions métier.
 *
 * Les décisions (peut clôturer, peut recalculer, peut valider…) viennent
 * exclusivement de context.availableActions fourni par le backend.
 *
 * Source : PayrollPeriodContextView.PayrollPeriodSummary.state
 * → codes : BROUILLON | EN_COURS | TRANSMIS_DAF | A_VALIDER | VALIDEE | PAIE_VALIDEE | CLOTUREE
 */
export const PAIE_STATUS_PRESENTATION = {
    BROUILLON:     { variant: 'default',   label: 'Brouillon'    },
    EN_COURS:      { variant: 'warning',   label: 'En cours'     },
    TRANSMIS_DAF:  { variant: 'info',      label: 'Transmis DAF' },
    A_VALIDER:     { variant: 'info',      label: 'À valider'    },
    VALIDEE:       { variant: 'success',   label: 'Validée'      },
    PAIE_VALIDEE:  { variant: 'success',   label: 'Paie validée' },
    CLOTUREE:      { variant: 'secondary', label: 'Clôturée'     },
};

/**
 * Retourne le variant Badge pour un statut de période de paie donné.
 * Toujours utiliser PAIE_STATUS_PRESENTATION pour la présentation.
 * @param {string|undefined} state - code statut backend
 * @returns {'default'|'warning'|'info'|'success'|'secondary'}
 */
export function getPaieStatusVariant(state) {
    return PAIE_STATUS_PRESENTATION[state]?.variant || 'default';
}

/**
 * Retourne le libellé humain pour un statut de période de paie.
 * @param {string|undefined} state - code statut backend
 * @returns {string}
 */
export function getPaieStatusLabel(state) {
    return PAIE_STATUS_PRESENTATION[state]?.label || (state ?? '—');
}
