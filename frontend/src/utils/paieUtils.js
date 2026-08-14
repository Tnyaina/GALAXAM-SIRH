/**
 * Service utilitaire & helpers de formatage pour les modules Paie & Référentiels RH.
 */

export const PERIODICITES_INDEMNITE = [
    { value: 'JOURNALIER', label: 'Journalier' },
    { value: 'MENSUEL', label: 'Mensuel' },
    { value: 'HORAIRE', label: 'Horaire' },
];

/**
 * Formate une date ISO (ex: "2026-01-01T00:00:00.000+03:00" ou "2026-01-01") en "DD/MM/YYYY" pour l'affichage UI.
 */
export function fmtDate(d) {
    if (!d) return '-';
    try {
        const str = typeof d === 'string' ? d.substring(0, 10) : new Date(d).toISOString().substring(0, 10);
        const [year, month, day] = str.split('-');
        if (!year || !month || !day) return String(d);
        return `${day}/${month}/${year}`;
    } catch {
        return String(d);
    }
}

/**
 * Formate une date pour les champs <input type="date" /> (format "YYYY-MM-DD").
 */
export function fmtDateInput(d) {
    if (!d) return '';
    if (typeof d === 'string') return d.substring(0, 10);
    try {
        return new Date(d).toISOString().substring(0, 10);
    } catch {
        return '';
    }
}

/**
 * Formate un montant numérique en Ariary (ex: 250 000 Ar).
 */
export function fmtMoney(val) {
    if (val === null || val === undefined || val === '') return '-';
    const num = Number(val);
    if (isNaN(num)) return String(val);
    return num.toLocaleString('fr-FR') + ' Ar';
}

/**
 * Formate un taux en pourcentage (ex: 1.5 -> "1.5%" ou 0.30 -> "30%").
 */
export function fmtPercent(val) {
    if (val === null || val === undefined || val === '') return '-';
    const num = Number(val);
    if (isNaN(num)) return String(val);
    return `${num}%`;
}

