/** Types saisis par la paie pour corriger un bulletin. */
export const isManualPayrollVariableType = type =>
    type?.nature === 'VARIABLE_DAF'
    || type?.nature === 'RETENUE_NETTE'
    || type?.nature === 'NOTE_DE_FRAIS'
    || (!type?.nature && !['ACOMPTE', 'AVANCE', 'REGULARISATION'].includes(type?.categorie));

export const isManualPayrollVariable = variable =>
    (variable?.provenance === 'VARIABLE_DAF' || variable?.provenance === 'NOTE_DE_FRAIS' || variable?.provenance == null)
    && isManualPayrollVariableType(variable);
