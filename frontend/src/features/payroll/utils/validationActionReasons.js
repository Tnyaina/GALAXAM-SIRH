const REASON_LABELS = {
  FORBIDDEN: 'Votre compte ne dispose pas du droit de valider cette campagne.',
  PERIOD_CLOSED: 'Cette période est déjà clôturée.',
  POINTAGE_NOT_FROZEN: 'Le pointage doit être validé et figé avant le calcul de paie.',
  PAYROLL_POPULATION_INCOMPLETE: 'Tous les salariés attendus ne disposent pas encore d’un bulletin calculé.',
  PAYROLL_CALCULATION_STALE: 'Les données sources ont changé depuis le calcul. Recalculez les bulletins avant validation.',
  PAYROLL_NOT_CALCULATED: 'Aucun bulletin calculé n’est disponible pour cette campagne.',
};

export function explainPayrollActionReasons(reasonCodes = []) {
  return [...new Set(reasonCodes)]
    .map(code => REASON_LABELS[code])
    .filter(Boolean);
}

const CLOSE_REASON_LABELS = {
  FORBIDDEN: 'Votre compte ne dispose pas du droit de clôturer cette période.',
  PERIOD_NOT_ENDED: 'La période doit être terminée avant sa clôture.',
  PAYROLL_NOT_VALIDATED: 'Validez la paie avant de clôturer la période.',
  PAYROLL_CALCULATION_STALE: 'Le calcul est obsolète. Recalculez la paie avant la clôture.',
  PAYROLL_POPULATION_INCOMPLETE: 'Chaque salarié inclus doit avoir un bulletin validé.',
};

export function explainPayrollCloseReasons(reasonCodes = []) {
  return [...new Set(reasonCodes)]
    .map(code => CLOSE_REASON_LABELS[code])
    .filter(Boolean);
}
