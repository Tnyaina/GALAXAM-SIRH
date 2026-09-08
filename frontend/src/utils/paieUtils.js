/**
 * Service utilitaire & helpers de formatage pour les modules Paie & Référentiels RH.
 */

export const PERIODICITES_INDEMNITE = [
    { value: 'MENSUEL_PRORATISE', label: 'Mensuel proratisé (selon présences & jours ouvrables)' },
    { value: 'JOURNALIER', label: 'Forfait journalier (par jour présent validé)' },
    { value: 'FIXE_MENSUEL', label: 'Mensuel fixe (indépendant du pointage)' },
];

// ─── Formatteurs Utilitaires Canoniques (Délégués à dateUtils et moneyUtils) ─
export { fmtDateInput, fmtDate } from './dateUtils';
export { fmtMoney, fmtPercent } from './moneyUtils';

