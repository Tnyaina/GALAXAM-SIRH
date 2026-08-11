/**
 * Service utilitaire & Règles de Gestion (RG) pour la Carrière & les Workflows RH.
 * Centralise toute la logique métier pour éviter le code dupliqué ou complexe dans les composants React.
 */

// ─── Formatteurs Utilitaires ───────────────────────────────────────────────

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

export function fmtDateInput(d) {
    if (!d) return '';
    if (typeof d === 'string') return d.substring(0, 10);
    return new Date(d).toISOString().substring(0, 10);
}

export function fmtDateFr(d) {
    const str = fmtDateInput(d);
    if (!str) return '';
    const [yyyy, mm, dd] = str.split('-');
    return `${dd}/${mm}/${yyyy}`;
}

export function fmtMoney(val) {
    if (val === null || val === undefined || val === '') return '-';
    const num = Number(val);
    if (isNaN(num)) return String(val);
    return num.toLocaleString('fr-FR') + ' Ar';
}

export function fmtMoneyMGA(amount) {
    if (amount === undefined || amount === null || amount === '') return '';
    return new Intl.NumberFormat('fr-MG', { style: 'currency', currency: 'MGA', maximumFractionDigits: 0 }).format(Number(amount));
}

export function resolveId(list, idDirect, idField, libelleDirect, libelleFields = ['libelle']) {
    if (idDirect !== undefined && idDirect !== null && idDirect !== '') return String(idDirect);
    if (!libelleDirect || !Array.isArray(list) || list.length === 0) return '';
    const match = list.find((item) =>
        libelleFields.some((lf) => item[lf] && String(item[lf]).trim().toLowerCase() === String(libelleDirect).trim().toLowerCase())
    );
    return match ? String(match[idField]) : '';
}

export function calculerDateFinEssai(dateDebutStr, dureeMoisStr) {
    if (!dateDebutStr || !dureeMoisStr) return '';
    const d = new Date(dateDebutStr);
    if (isNaN(d.getTime())) return '';
    d.setMonth(d.getMonth() + Number(dureeMoisStr));
    return d.toISOString().substring(0, 10);
}

// ─── Identification des Événements RH ──────────────────────────────────────

export function isEmbaucheEvent(codeTypeEvenement) {
    if (!codeTypeEvenement) return false;
    const upper = String(codeTypeEvenement).toUpperCase();
    return upper === 'EMBAUCHE_INITIALE' || upper === 'EMBAUCHE';
}

export function isConfirmationEssaiEvent(codeTypeEvenement) {
    return codeTypeEvenement === 'CONFIRMATION_ESSAI';
}

export function isRenouvellementEssaiEvent(codeTypeEvenement) {
    return codeTypeEvenement === 'RENOUVELLEMENT_ESSAI';
}

export function isRuptureEvent(codeTypeEvenement) {
    if (!codeTypeEvenement) return false;
    const upper = String(codeTypeEvenement).toUpperCase();
    return [
        'FIN_CONTRAT', 'RUPTURE_ESSAI', 'DEMISSION', 'LICENCIEMENT', 
        'RUPTURE_CONVENTIONNELLE', 'DEPART_RETRAITE', 'DECES', 
        'CLOTURE', 'CLOTURE_CONTRAT', 'RUPTURE'
    ].includes(upper) || upper.includes('RUPTURE') || upper.includes('CLOTURE') || upper.includes('FIN_CONTRAT');
}

/**
 * RG : Générateur des libellés UI pour le récapitulatif & révision.
 */
export function getWorkflowLibellesUI(workflowCpl) {
    if (!workflowCpl) return {};

    const code = workflowCpl.codeTypeEvenement;
    const isEmbauche = isEmbaucheEvent(code);
    const isConfirmation = isConfirmationEssaiEvent(code);
    const isRenouvellement = isRenouvellementEssaiEvent(code);
    const isRupture = isRuptureEvent(code);

    let title = `Validation RH — Mouvement RH (${workflowCpl.libelleTypeEvenement || code})`;
    let description = 'Veuillez relire les termes de cette transition RH avant officialisation.';
    let actionBtnLabel = 'Valider le Mouvement RH';
    let badgeEssai = 'ÉVÉNEMENT RH';
    let badgeVariant = 'primary';

    if (isEmbauche) {
        title = 'Pre-Validation DAF — Nouvel Contrat d\'Embauche';
        description = 'Veuillez relire l\'ensemble des conditions contractuelles et financières ci-dessous. En cliquant sur "Valider", le contrat passera au statut ACTIF.';
        actionBtnLabel = 'Valider Définitivement (Activer)';
        badgeEssai = 'PÉRIODE D\'ESSAI INITIALE';
        badgeVariant = 'primary';
    } else if (isConfirmation) {
        title = 'Validation RH — Confirmation Définitive d\'Essai';
        description = 'Veuillez relire la décision ci-dessous. En cliquant sur "Valider", l\'essai sera définitivement levé et l\'employé confirmé.';
        actionBtnLabel = 'Valider la Confirmation d\'Essai';
        badgeEssai = 'ESSAI CONFIRMÉ';
        badgeVariant = 'success';
    } else if (isRenouvellement) {
        title = 'Validation RH — Renouvellement de Période d\'Essai';
        description = 'Veuillez relire la prolongation ci-dessous. En cliquant sur "Valider", la 2ème période d\'essai sera officialisée.';
        actionBtnLabel = 'Valider le Renouvellement d\'Essai';
        badgeEssai = 'ESSAI RENOUVELÉ';
        badgeVariant = 'warning';
    } else if (isRupture) {
        title = `Validation RH — Rupture / Fin de Contrat (${workflowCpl.libelleTypeEvenement || code})`;
        description = 'Veuillez relire les conditions de rupture de contrat. En cliquant sur "Valider", le contrat sera rompu/clôturé et le statut de l\'employé sera mis à jour.';
        actionBtnLabel = 'Valider la Rupture & Clôturer le Contrat';
        badgeEssai = 'FIN DE CONTRAT';
        badgeVariant = 'danger';
    }

    return {
        isEmbauche,
        isConfirmation,
        isRenouvellement,
        isRupture,
        title,
        description,
        actionBtnLabel,
        badgeEssai,
        badgeVariant,
    };
}

export function trouverWorkflowPlanifieFutur(workflowsList, contratActifId = null, todayStr = new Date().toISOString().substring(0, 10)) {
    if (!Array.isArray(workflowsList)) return null;
    return workflowsList.find(
        w => w.codeStatutWorkflow === 'VALIDE' &&
             !isConfirmationEssaiEvent(w.codeTypeEvenement) &&
             !isRenouvellementEssaiEvent(w.codeTypeEvenement) &&
             w.dateEffet && String(w.dateEffet).substring(0, 10) > todayStr &&
             (!contratActifId || Number(w.idContratSource) === Number(contratActifId) || Number(w.idContratCible) === Number(contratActifId))
    ) || null;
}

export function calculerDateFinEssaiContextuelle(workflowCpl) {
    if (!workflowCpl) return null;
    if (isEmbaucheEvent(workflowCpl.codeTypeEvenement) && workflowCpl.dateDebutContrat && workflowCpl.dureeEssaiMois) {
        return calculerDateFinEssai(workflowCpl.dateDebutContrat, workflowCpl.dureeEssaiMois);
    }
    return workflowCpl.dateFinEssai || workflowCpl.dateEffet;
}
