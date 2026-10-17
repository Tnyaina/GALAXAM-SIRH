import { fmtDateInput } from '@/utils/dateUtils';

// ── OPTIONS DE DURÉE DE PRÉAVIS ─────────────────────────────────────────────
export const DUREES_PREAVIS = [
    { key: 'AUCUN', label: 'Aucun préavis (0 j)', jours: 0, moisEquiv: 0, desc: 'Sortie immédiate sans préavis.' },
    { key: '15_JOURS', label: '15 jours', jours: 15, moisEquiv: 0.5, desc: 'À sélectionner après vérification du texte applicable.' },
    { key: '1_MOIS', label: '1 mois (30 j)', jours: 30, moisEquiv: 1.0, desc: 'À sélectionner après vérification du texte applicable.' },
    { key: '2_MOIS', label: '2 mois (60 j)', jours: 60, moisEquiv: 2.0, desc: 'À sélectionner après vérification du texte applicable.' },
    { key: '3_MOIS', label: '3 mois (90 j)', jours: 90, moisEquiv: 3.0, desc: 'À sélectionner après vérification du texte applicable.' },
    { key: 'CUSTOM', label: 'Personnalisé (en jours)...', jours: null, moisEquiv: null, desc: 'Durée spécifique en jours calendaires.' },
];

// ── MODALITÉS D'EXÉCUTION DU PRÉAVIS ────────────────────────────────────────
export const MODES_PREAVIS = [
    {
        key: 'EFFECTUE',
        titre: 'Préavis effectué et travaillé',
        badge: 'En poste jusqu\'au terme',
        badgeClass: 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] border-[var(--color-brand-500)]/30',
        impactTitre: 'Sortie différée au terme du préavis',
        impactStc: '0 Ar (salaire versé normalement chaque mois)',
        desc: 'Activité maintenue jusqu\'au terme du préavis. Rémunération standard sans indemnité compensatrice au STC.'
    },
    {
        key: 'DISPENSE_PAYEE',
        titre: 'Dispensé par l\'employeur (Payé)',
        badge: 'Sortie immédiate indemnisée',
        badgeClass: 'bg-[var(--color-warning-500)]/10 text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] border-[var(--color-warning-500)]/30',
        impactTitre: 'Sortie effective immédiate',
        impactStc: 'Indemnité compensatrice versée au STC',
        desc: 'Dispense de travail accordée par l\'employeur avec versement d\'une indemnité compensatrice au STC.'
    },
    {
        key: 'DISPENSE_NON_PAYEE',
        titre: 'Dispensé à la demande du salarié (Non payé)',
        badge: 'Sortie immédiate sans indemnité',
        badgeClass: 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border-[var(--color-border-subtle)]',
        impactTitre: 'Sortie effective immédiate',
        impactStc: '0 Ar (dispense demandée par le salarié)',
        desc: 'Dispense de travail à la demande du salarié sans indemnité compensatrice de préavis.'
    },
];

// Helper de calcul de date d'effet projetée
export function calculerDateEffet(dateNotifStr, dureeKey, modePreavis, customJours = 0) {
    if (!dateNotifStr) return '';
    if (!dureeKey) return '';
    if (modePreavis !== 'EFFECTUE' || dureeKey === 'AUCUN') {
        return dateNotifStr;
    }
    const [y, m, d] = dateNotifStr.split('-').map(Number);
    if (!y || !m || !d) return dateNotifStr;
    const date = new Date(y, m - 1, d);

    if (dureeKey === '15_JOURS') {
        date.setDate(date.getDate() + 15);
    } else if (dureeKey === '1_MOIS') {
        date.setMonth(date.getMonth() + 1);
    } else if (dureeKey === '2_MOIS') {
        date.setMonth(date.getMonth() + 2);
    } else if (dureeKey === '3_MOIS') {
        date.setMonth(date.getMonth() + 3);
    } else if (dureeKey === 'CUSTOM') {
        date.setDate(date.getDate() + (Number(customJours) || 0));
    }
    return fmtDateInput(date);
}

export function getPreavisGuide(form) {
    const dureeInfo = DUREES_PREAVIS.find(item => item.key === form.dureePreavisKey)
        || { label: 'À déterminer', moisEquiv: 0, desc: 'Vérifiez le texte applicable et choisissez le délai.' };
    const joursPersonnalises = Number(form.customJours);
    const dureeValide = Boolean(form.dureePreavisKey)
        && (form.dureePreavisKey !== 'CUSTOM' || (Number.isFinite(joursPersonnalises) && joursPersonnalises > 0));
    const sansPreavis = form.dureePreavisKey === 'AUCUN';
    const modePreavis = sansPreavis ? 'DISPENSE_NON_PAYEE' : form.modePreavis;
    const modeInfo = MODES_PREAVIS.find(mode => mode.key === modePreavis) || MODES_PREAVIS[0];
    const mois = form.dureePreavisKey === 'CUSTOM'
        ? Number((joursPersonnalises / 30).toFixed(2))
        : (dureeInfo.moisEquiv || 0);
    return {
        dureeInfo,
        dureeValide,
        sansPreavis,
        modeInfo,
        libelleDuree: form.dureePreavisKey === 'CUSTOM' && dureeValide
            ? `${joursPersonnalises} jour(s) calendaire(s)` : dureeInfo.label,
        moisIndemnite: modePreavis === 'DISPENSE_PAYEE' && dureeValide ? mois : 0,
        calendrierComplet: Boolean(form.dateNotification && dureeValide && form.dateEffet),
    };
}

export function choisirDureePreavis(form, dureePreavisKey) {
    const modePreavis = dureePreavisKey === 'AUCUN' ? 'DISPENSE_NON_PAYEE'
        : form.dureePreavisKey === 'AUCUN' ? 'EFFECTUE' : form.modePreavis;
    return {
        ...form,
        dureePreavisKey,
        modePreavis,
        dateEffet: calculerDateEffet(form.dateNotification, dureePreavisKey, modePreavis, form.customJours),
    };
}

export function choisirTypeRupture(form, codeTypeEvenement, motifSortieCode) {
    const prochain = codeTypeEvenement === 'FIN_CONTRAT'
        ? choisirDureePreavis(form, 'AUCUN')
        : form.codeTypeEvenement === 'FIN_CONTRAT'
            ? choisirDureePreavis(form, '') : form;
    return { ...prochain, codeTypeEvenement, motifSortieCode };
}
