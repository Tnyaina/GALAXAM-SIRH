import test from 'node:test';
import assert from 'node:assert/strict';
import {
  etapesAfficheesCampagnePaie,
  libelleRubriqueBulletin,
  progressionCampagnePaie,
} from '../src/features/payroll/utils/payrollPresentation.js';

test('désigne clairement le montant IRSA de la rubrique arrondie', () => {
  assert.equal(libelleRubriqueBulletin({
    codeRubrique: 'IMPOT_IRSA_BRUT',
    libelleRubrique: 'IRSA Brut (Barème Progressif 6 Tranches)',
  }), 'IRSA brut arrondi à la dizaine supérieure');
});

test('conserve le libellé des autres rubriques et la valeur de repli', () => {
  assert.equal(libelleRubriqueBulletin({ codeRubrique: 'SALAIRE_BASE', libelleRubrique: 'Salaire de base' }), 'Salaire de base');
  assert.equal(libelleRubriqueBulletin({ codeRubrique: 'SALAIRE_BASE' }), '');
});

test('garde la progression complète après clôture, quand les étapes passent en lecture seule', () => {
  const steps = [
    { state: 'COMPLETED' },
    { state: 'LOCKED' },
    { state: 'CURRENT' },
  ];

  assert.deepEqual(progressionCampagnePaie(steps, true), { completed: 3, total: 3 });
  assert.deepEqual(progressionCampagnePaie(steps), { completed: 1, total: 3 });
  assert.deepEqual(etapesAfficheesCampagnePaie(steps, true).map(step => step.state), ['COMPLETED', 'COMPLETED', 'CURRENT']);
  assert.deepEqual(etapesAfficheesCampagnePaie(steps).map(step => step.state), ['COMPLETED', 'LOCKED', 'CURRENT']);
});
