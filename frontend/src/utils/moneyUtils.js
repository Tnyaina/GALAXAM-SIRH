/**
 * Utilitaires & Formatteurs Monétaires et Pourcentages — SIRH GALAXAM.
 * 
 * Source de Vérité Unique pour le formatage monétaire (Ariary - MGA) et pourcentages.
 * Remplace et unifie les implémentations dispersées (carriereUtils, paieUtils).
 */

/**
 * Formate un montant numérique en Ariary avec séparateur d'espace français (ex: 250 000 Ar).
 * 
 * @param {number|string|null|undefined} val
 * @returns {string} Ex: "1 500 000 Ar" ou "-" si vide
 */
export function fmtMoney(val) {
    if (val === null || val === undefined || val === '') return '-';
    const num = Number(val);
    if (isNaN(num)) return String(val);
    return num.toLocaleString('fr-FR', { minimumFractionDigits: 0, maximumFractionDigits: 2 }) + ' Ar';
}

/**
 * Helper monétaire canonique pour le module Paie (Sprint Paie UI P0/P1).
 * Comportement attendu :
 * - null -> '—'
 * - undefined -> '—'
 * - NaN -> '—'
 * - '' -> '—'
 * - 0 -> '0 Ar'
 * - montant -> valeur formatée Ar (ex: '1 250 000 Ar')
 *
 * @param {number|string|null|undefined} val
 * @returns {string}
 */
export function formatMoneyAr(val) {
    if (val === null || val === undefined || val === '') return '—';
    const num = Number(val);
    if (isNaN(num)) return '—';
    if (num === 0) return '0 Ar';
    return num.toLocaleString('fr-FR', { minimumFractionDigits: 0, maximumFractionDigits: 2 }).replace(/\u202F/g, ' ') + ' Ar';
}

/** Format d'affichage des indemnités de pointage : entier arrondi, zéro explicite. */
export function formatRoundedMoneyAr(val) {
    if (!val || isNaN(val) || Number(val) === 0) return '0 Ar';
    return new Intl.NumberFormat('fr-FR').format(Math.round(Number(val))) + ' Ar';
}

/**
 * Formate un montant monétaire selon la locale malgache officielle (fr-MG) avec devise MGA.
 * 
 * @param {number|string|null|undefined} amount
 * @returns {string} Ex: "1 500 000 Ar" ou "" si vide
 */
export function fmtMoneyMGA(amount) {
    if (amount === undefined || amount === null || amount === '') return '';
    return new Intl.NumberFormat('fr-MG', { style: 'currency', currency: 'MGA', maximumFractionDigits: 0 }).format(Number(amount));
}

/**
 * Formate un taux en pourcentage (ex: 1.5 -> "1.5%" ou 20 -> "20%").
 * 
 * @param {number|string|null|undefined} val
 * @returns {string} Ex: "20%" ou "-" si vide
 */
export function fmtPercent(val) {
    if (val === null || val === undefined || val === '') return '-';
    const num = Number(val);
    if (isNaN(num)) return String(val);
    return `${num}%`;
}
