export const manualMethod = type => type?.methodeCalcul || 'MONTANT';
export const positiveDecimal = (value, decimals = 6) => {
    const normalized = String(value ?? '').trim().replace(/[\s\u00a0\u202f]/g, '').replace(',', '.');
    if (!new RegExp(`^\\d+(?:\\.\\d{1,${decimals}})?$`).test(normalized)) return null;
    return Number.isFinite(Number(normalized)) && Number(normalized) > 0 ? normalized : null;
};
export function manualValuePayload(type, form) {
    if (type?.configurationError) throw new Error(type.configurationError);
    const method = manualMethod(type);
    if (method === 'MONTANT') {
        const amount = positiveDecimal(form.montant, 2);
        if (!amount) throw new Error('Saisissez un montant positif, avec deux décimales maximum.');
        return { montant: amount };
    }
    if (!['QUANTITE_TAUX', 'POURCENTAGE_SALAIRE_BASE'].includes(method)) throw new Error('Méthode de calcul inconnue.');
    const rate = positiveDecimal(form.tauxSaisi);
    if (!rate) throw new Error('Saisissez un taux positif, avec six décimales maximum.');
    if (method === 'POURCENTAGE_SALAIRE_BASE') {
        if (Number(rate) > 100) throw new Error('Le pourcentage ne peut pas dépasser 100 %.');
        return { tauxSaisi: rate };
    }
    const quantity = positiveDecimal(form.quantiteSaisie);
    if (!quantity) throw new Error('Saisissez une quantité positive, avec six décimales maximum.');
    return { quantiteSaisie: quantity, tauxSaisi: rate };
}
export function manualValueEstimate(type, form, salary) {
    const method = manualMethod(type);
    if (method === 'MONTANT') return Number(positiveDecimal(form.montant, 2) || 0);
    const rate = Number(positiveDecimal(form.tauxSaisi) || 0);
    return method === 'QUANTITE_TAUX'
        ? Number(positiveDecimal(form.quantiteSaisie) || 0) * rate : Number(salary || 0) * rate / 100;
}
