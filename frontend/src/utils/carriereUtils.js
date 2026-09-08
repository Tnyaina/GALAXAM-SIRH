/**
 * Service utilitaire & Règles de Gestion (RG) pour la Carrière & les Workflows RH.
 * Centralise toute la logique métier pour éviter le code dupliqué ou complexe dans les composants React.
 */

// ─── Formatteurs Utilitaires Canoniques (Délégués à dateUtils et moneyUtils) ─
export { fmtDateInput, fmtDate, fmtDateFr } from './dateUtils';
export { fmtMoney, fmtMoneyMGA } from './moneyUtils';


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
    const cleanStr = fmtDateInput(dateDebutStr);
    if (!cleanStr) return '';
    const [y, m, day] = cleanStr.split('-').map(Number);
    if (!y || !m || !day) return '';
    const target = new Date(y, m - 1 + Number(dureeMoisStr), day);
    return fmtDateInput(target);
}

// ─── Identification des Événements RH ──────────────────────────────────────

export function isEmbaucheEvent(wfOrCode) {
    if (!wfOrCode) return false;
    if (typeof wfOrCode === 'object') {
        if (wfOrCode.isEmbauche !== undefined) return wfOrCode.isEmbauche;
        return isEmbaucheEvent(wfOrCode.codeTypeEvenement);
    }
    const upper = String(wfOrCode).toUpperCase();
    return upper === 'EMBAUCHE_INITIALE' || upper === 'EMBAUCHE' || upper === 'REPRISE_ANCIENNETE';
}

export function isConfirmationEssaiEvent(wfOrCode) {
    if (!wfOrCode) return false;
    if (typeof wfOrCode === 'object') {
        if (wfOrCode.isConfirmationEssai !== undefined) return wfOrCode.isConfirmationEssai;
        return isConfirmationEssaiEvent(wfOrCode.codeTypeEvenement);
    }
    return String(wfOrCode).toUpperCase() === 'CONFIRMATION_ESSAI';
}

export function isRenouvellementEssaiEvent(wfOrCode) {
    if (!wfOrCode) return false;
    if (typeof wfOrCode === 'object') {
        if (wfOrCode.isRenouvellementEssai !== undefined) return wfOrCode.isRenouvellementEssai;
        return isRenouvellementEssaiEvent(wfOrCode.codeTypeEvenement);
    }
    return String(wfOrCode).toUpperCase() === 'RENOUVELLEMENT_ESSAI';
}

export function isRuptureEvent(wfOrCode) {
    if (!wfOrCode) return false;
    if (typeof wfOrCode === 'object') {
        if (wfOrCode.isRupture !== undefined) return wfOrCode.isRupture;
        return isRuptureEvent(wfOrCode.codeTypeEvenement);
    }
    const upper = String(wfOrCode).toUpperCase();
    return [
        'FIN_CONTRAT', 'RUPTURE_ESSAI', 'DEMISSION', 'LICENCIEMENT', 
        'RUPTURE_CONVENTIONNELLE', 'DEPART_RETRAITE', 'DECES', 
        'CLOTURE', 'CLOTURE_CONTRAT', 'RUPTURE', 'RUPTURE_ESSAI_EMPLOYEUR', 'RUPTURE_ESSAI_SALARIE'
    ].includes(upper) || upper.includes('RUPTURE') || upper.includes('CLOTURE') || upper.includes('FIN_CONTRAT');
}

/**
 * RG : Générateur des libellés UI pour le récapitulatif & révision.
 */
export function getWorkflowLibellesUI(workflowCpl) {
    if (!workflowCpl) return {};

    const isEmbauche = workflowCpl.isEmbauche ?? isEmbaucheEvent(workflowCpl);
    const isConfirmation = workflowCpl.isConfirmationEssai ?? isConfirmationEssaiEvent(workflowCpl);
    const isRenouvellement = workflowCpl.isRenouvellementEssai ?? isRenouvellementEssaiEvent(workflowCpl);
    const isRupture = workflowCpl.isRupture ?? isRuptureEvent(workflowCpl);
    const code = workflowCpl.codeTypeEvenement;

    let title = `Validation RH — Mouvement RH (${workflowCpl.libelleTypeEvenement || code || ''})`;
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
        title = `Validation RH — Rupture / Fin de Contrat (${workflowCpl.libelleTypeEvenement || code || ''})`;
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

export function trouverWorkflowPlanifieFutur(workflowsList, contratActifId = null, todayStr = fmtDateInput(new Date())) {
    if (!Array.isArray(workflowsList)) return null;
    return workflowsList.find(
        w => (w.isPlanifie ?? (w.codeStatutWorkflow === 'PLANIFIE')) &&
             !(w.isAnnule ?? (w.codeStatutWorkflow === 'ANNULE')) &&
             w.dateEffet && fmtDateInput(w.dateEffet) > todayStr &&
             (!contratActifId || Number(w.idContratSource) === Number(contratActifId) || Number(w.idContratCible) === Number(contratActifId))
    ) || null;
}

export function calculerDateFinEssaiContextuelle(workflowCpl) {
    if (!workflowCpl) return null;
    if (isEmbaucheEvent(workflowCpl) && workflowCpl.dateDebutContrat && workflowCpl.dureeEssaiMois) {
        return calculerDateFinEssai(workflowCpl.dateDebutContrat, workflowCpl.dureeEssaiMois);
    }
    return workflowCpl.dateFinEssai || workflowCpl.dateEffet;
}

