export const POINTAGE_TIME_FIELDS = ['heureEntreeMatin', 'heureSortieMatin', 'heureEntreeAm', 'heureSortieAm'];

export const cleanPointageTime = (value) => {
    if (value == null || ['—', '-', '--:--', '', 'null', 'undefined'].includes(String(value))) return '';
    return String(value).trim();
};

/** Compare uniquement l'intention de saisie, jamais les résultats calculés par le serveur. */
export const isPointageRowModified = (current, initial) => {
    if (!current || !initial) return false;
    if (POINTAGE_TIME_FIELDS.some(field => cleanPointageTime(current[field]) !== cleanPointageTime(initial[field]))) return true;
    if (Boolean(current.horaireContinu) !== Boolean(initial.horaireContinu)) return true;
    if (['codeStatut', 'motifAjustement', 'typeCongeJour'].some(field =>
        (current[field] ?? '') !== (initial[field] ?? ''))) return true;
    return JSON.stringify(current.dynamicIndemnites || {}) !== JSON.stringify(initial.dynamicIndemnites || {});
};

export const pointagePreviewSignature = (row) => JSON.stringify([
    row.idEmploye, row.idSite, row.datePointage,
    ...POINTAGE_TIME_FIELDS.map(field => cleanPointageTime(row[field])),
    row.codeStatut, row.motifAjustement, row.typeCongeJour, row.horaireContinu,
    row.estAutonome, row.ferieJour ?? row.ferie, row.congePaye, row.congeDecompteSolde,
    row.dynamicIndemnites
]);

export const markPointageForPreview = (row) => ({
    ...row,
    hasValidationError: false,
    validationMessages: [],
    _previewPending: true,
    _previewError: null
});

export const clearPointageAllowances = (allowances = {}) => Object.fromEntries(
    Object.entries(allowances).map(([key, value]) => [key, {
        ...value, accordee: false, fractionJour: 0, matin: false, am: false
    }])
);

/** Toute modification d'heures redemande au serveur la qualification de la journée. */
export const changePointageHours = (row, hours) => markPointageForPreview({
    ...row, ...hours, codeStatut: 'NON_SAISI', motifAjustement: '', typeCongeJour: null
});

export const changePointageStatus = (row, codeStatut, motifAjustement = '') => {
    const absence = ['ABSENCE_JUSTIFIEE', 'ABSENCE_NON_JUSTIFIEE'].includes(codeStatut);
    return markPointageForPreview({
        ...row, codeStatut, motifAjustement,
        typeCongeJour: codeStatut === 'CONGE' ? 'FULL_DAY' : null,
        ...(absence ? {
            dynamicIndemnites: clearPointageAllowances(row.dynamicIndemnites),
            indemniteRepas: false, deplacementMatin: false, deplacementAm: false
        } : {})
    });
};

export const changePointageAllowance = (current = {}, value, moment) => {
    const next = { ...current };
    const granted = Boolean(value);
    if (moment === 'MATIN') next.matin = granted;
    else if (moment === 'APRES_MIDI') next.am = granted;
    else next.accordee = granted;
    if (moment === 'MATIN' || moment === 'APRES_MIDI') {
        next.accordee = Boolean(next.matin || next.am);
        next.fractionJour = (next.matin ? 0.5 : 0) + (next.am ? 0.5 : 0);
    } else {
        next.fractionJour = granted ? 1 : 0;
    }
    return next;
};

export const applyPointagePreview = (row, preview) => ({
    ...row,
    heuresMatin: preview.heuresMatin,
    heuresAm: preview.heuresAm,
    totalHeures: preview.totalHeures,
    heuresNuit: preview.heuresNuit,
    minutesRetard: preview.minutesRetard,
    codeStatut: preview.codeStatut || row.codeStatut,
    libelleStatut: preview.libelleStatut,
    hasValidationError: Boolean(preview.hasValidationError),
    validationMessages: preview.validationMessages || [],
    statusActions: preview.statusActions || [],
    statutBadgeVariant: preview.statutBadgeVariant,
    statutBadgeLabel: preview.statutBadgeLabel,
    present: preview.present,
    retard: preview.retard,
    conge: preview.conge,
    absenceJustifiee: preview.absenceJustifiee,
    absenceNonJustifiee: preview.absenceNonJustifiee,
    nonSaisi: preview.nonSaisi,
    anomalie: preview.anomalie,
    dispensePointage: preview.dispensePointage,
    ferieTravaille: preview.ferieTravaille,
    ferieChome: preview.ferieChome,
    samediTravaille: preview.samediTravaille,
    dimancheTravaille: preview.dimancheTravaille,
    repos: preview.repos,
    _previewPending: false,
    _previewError: null
});
