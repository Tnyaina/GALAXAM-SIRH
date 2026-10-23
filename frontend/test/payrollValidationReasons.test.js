import test from 'node:test';
import assert from 'node:assert/strict';
import { explainPayrollActionReasons, explainPayrollCloseReasons } from '../src/features/payroll/utils/validationActionReasons.js';

test('explique les causes connues de désactivation de la validation', () => {
  assert.deepEqual(
    explainPayrollActionReasons(['PAYROLL_NOT_CALCULATED', 'PAYROLL_CALCULATION_STALE']),
    [
      'Aucun bulletin calculé n’est disponible pour cette campagne.',
      'Les données sources ont changé depuis le calcul. Recalculez les bulletins avant validation.',
    ],
  );
});

test('ignore les codes inconnus sans exposer de détail technique', () => {
  assert.deepEqual(explainPayrollActionReasons(['UNKNOWN_CODE']), []);
});

test('distingue le refus de permission des conditions métier de clôture', () => {
  assert.deepEqual(
    explainPayrollCloseReasons(['FORBIDDEN', 'PAYROLL_NOT_VALIDATED']),
    [
      'Votre compte ne dispose pas du droit de clôturer cette période.',
      'Validez la paie avant de clôturer la période.',
    ],
  );
});
