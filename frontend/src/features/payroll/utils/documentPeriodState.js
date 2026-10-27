export const periodState = (period) => {
    return period?.workflowStatuses?.paie?.code || null;
};

export const isPayrollValidated = (period) => ['VALIDEE', 'CLOTUREE'].includes(periodState(period));
