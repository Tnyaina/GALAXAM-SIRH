/**
 * Service utilitaire & Règles de Gestion (RG) pour la Carrière & les Workflows RH.
 * Centralise toute la logique métier pour éviter le code dupliqué ou complexe dans les composants React.
 */

import { fmtDateInput } from '@/utils/dateUtils';


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

    let title = `Validation · Mouvement RH (${workflowCpl.libelleTypeEvenement || code || ''})`;
    let description = 'Vérifiez les conditions contractuelles ci-dessous avant validation.';
    let actionBtnLabel = 'Valider le mouvement';
    let badgeEssai = 'ÉVÉNEMENT';
    let badgeVariant = 'primary';

    if (isEmbauche) {
        title = 'Validation · Nouveau contrat d\'embauche';
        description = 'Vérifiez les conditions contractuelles et financières ci-dessous avant activation.';
        actionBtnLabel = 'Valider l\'embauche';
        badgeEssai = 'PÉRIODE D\'ESSAI INITIALE';
        badgeVariant = 'primary';
    } else if (isConfirmation) {
        title = 'Validation · Confirmation d\'essai';
        description = 'Vérifiez la décision ci-dessous avant levée définitive de la période d\'essai.';
        actionBtnLabel = 'Valider la confirmation';
        badgeEssai = 'ESSAI CONFIRMÉ';
        badgeVariant = 'success';
    } else if (isRenouvellement) {
        title = 'Validation · Renouvellement d\'essai';
        description = 'Vérifiez la prolongation ci-dessous avant officialisation du renouvellement.';
        actionBtnLabel = 'Valider le renouvellement';
        badgeEssai = 'ESSAI RENOUVELÉ';
        badgeVariant = 'warning';
    } else if (isRupture) {
        title = `Validation · Fin de contrat (${workflowCpl.libelleTypeEvenement || code || ''})`;
        description = 'Vérifiez les conditions de clôture du contrat ci-dessous.';
        actionBtnLabel = 'Valider la fin de contrat';
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
