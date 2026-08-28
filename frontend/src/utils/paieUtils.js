/**
 * Service utilitaire & helpers de formatage pour les modules Paie & Référentiels RH.
 */

export const PERIODICITES_INDEMNITE = [
    { value: 'MENSUEL_PRORATISE', label: 'Mensuel proratisé (selon présences & jours ouvrables)' },
    { value: 'JOURNALIER', label: 'Forfait journalier (par jour présent validé)' },
    { value: 'FIXE_MENSUEL', label: 'Mensuel fixe (indépendant du pointage)' },
];

/**
 * Formate une date pour les champs <input type="date" /> (format "YYYY-MM-DD") de manière timezone-safe.
 */
export function fmtDateInput(d) {
    if (!d) return '';
    if (d instanceof Date) {
        if (isNaN(d.getTime())) return '';
        const yyyy = d.getFullYear();
        const mm = String(d.getMonth() + 1).padStart(2, '0');
        const dd = String(d.getDate()).padStart(2, '0');
        return `${yyyy}-${mm}-${dd}`;
    }
    if (typeof d === 'string') {
        const s = d.trim();
        if (s.includes('T')) return s.split('T')[0];
        if (s.includes(' ')) return s.split(' ')[0];
        if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return s;
        const parsed = new Date(s);
        if (!isNaN(parsed.getTime())) {
            const yyyy = parsed.getFullYear();
            const mm = String(parsed.getMonth() + 1).padStart(2, '0');
            const dd = String(parsed.getDate()).padStart(2, '0');
            return `${yyyy}-${mm}-${dd}`;
        }
        return s.substring(0, 10);
    }
    return '';
}

/**
 * Formate une date ISO (ex: "2026-01-01T00:00:00.000+03:00" ou "2026-01-01") en "DD/MM/YYYY" pour l'affichage UI.
 */
export function fmtDate(d) {
    if (!d) return '-';
    const str = fmtDateInput(d);
    if (!str || str.length < 10) return String(d);
    const [year, month, day] = str.substring(0, 10).split('-');
    if (!year || !month || !day) return String(d);
    return `${day}/${month}/${year}`;
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
