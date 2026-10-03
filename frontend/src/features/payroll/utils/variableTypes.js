export const isManualPayrollVariable = variable =>
    variable?.provenance === 'VARIABLE_DAF' || variable?.provenance === 'NOTE_DE_FRAIS';
