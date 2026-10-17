/** ancienneteMois est un total de mois fourni par AncienneteEngine. */
export function formatStcAnciennete(annees, moisTotal) {
    if (moisTotal === null || moisTotal === undefined || moisTotal === '') {
        return `${Number(annees) || 0} an(s)`;
    }
    const mois = Number(moisTotal);
    if (Number.isFinite(mois) && mois >= 0) {
        return `${Math.floor(mois / 12)} an(s) ${mois % 12} mois`;
    }
    return `${Number(annees) || 0} an(s)`;
}
