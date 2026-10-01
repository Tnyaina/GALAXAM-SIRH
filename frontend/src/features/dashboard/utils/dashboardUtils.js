/**
 * dashboardUtils.js
 * Fonctions utilitaires pures du domaine Dashboard (sans hooks React ni fetch HTTP).
 */

export function fmtDate(d) {
    if (!d) return '—';
    return new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' }).replace(/\s+/g, ' ');
}

export function fmtAriary(val) {
    if (val === null || val === undefined || isNaN(Number(val))) return '—';
    return Number(val).toLocaleString('fr-FR', { minimumFractionDigits: 0, maximumFractionDigits: 0 });
}
