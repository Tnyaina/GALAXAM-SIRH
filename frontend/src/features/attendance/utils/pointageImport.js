export const importHalfDayMoment = (pointage) => {
    if (pointage?.heureEntreeMatin && pointage?.heureSortieMatin
        && !pointage?.heureEntreeAm && !pointage?.heureSortieAm) return 'APRES_MIDI';
    if (!pointage?.heureEntreeMatin && !pointage?.heureSortieMatin
        && pointage?.heureEntreeAm && pointage?.heureSortieAm) return 'MATIN';
    return null;
};

export const importLineNeedsReview = (line, approvedConflicts = {}) => Boolean(
    line.errors?.length || line._previewPending || line._previewError || line.proposed?.hasValidationError
    || (line.conflict && !approvedConflicts[line.ligne])
);
