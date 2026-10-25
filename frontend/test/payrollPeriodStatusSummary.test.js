import test from 'node:test';
import assert from 'node:assert/strict';
import { resumeStatutsPeriode } from '../src/features/payroll/utils/payrollPresentation.js';

test('le centre de paie expose les statuts distincts du pointage et de la paie', () => {
  assert.equal(resumeStatutsPeriode({
    statutCode: 'VALIDEE',
    workflowStatuses: {
      pointage: { code: 'FIGE', label: 'Pointage figé' },
      paie: { code: 'A_RECALCULER', label: 'À recalculer' },
    },
  }), 'Pointage : Pointage figé · Paie : À recalculer');
});

test('utilise le libellé du statut persistant pour les réponses sans statuts détaillés', () => {
  assert.equal(resumeStatutsPeriode({ statutCode: 'VALIDEE', statutLibelle: 'Pointage scellé' }), 'Pointage scellé');
});
