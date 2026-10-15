export const periodState = (period) => {
    const payrollPhase = period?.workflowStatuses?.paie?.code;
    if (payrollPhase) return payrollPhase;
    const persistedState = period?.statutCode || period?.statut || period?.state;
    // VALIDEE signifie que le pointage est figé ; seule PAIE_VALIDEE valide la paie.
    if (persistedState === 'PAIE_VALIDEE') return 'VALIDEE';
    if (persistedState === 'VALIDEE') return 'POINTAGE_VALIDE';
    return persistedState;
};
