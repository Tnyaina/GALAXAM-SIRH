import test from 'node:test';
import assert from 'node:assert/strict';
import {
  etapesAfficheesCampagnePaie,
  libelleRubriqueBulletin,
  progressionCampagnePaie,
  presentationParcoursCampagnePaie,
  rubriquesPaieAffichables,
} from '../src/features/payroll/utils/payrollPresentation.js';

test('masque la ligne intermédiaire d’IRSA sans modifier les données de calcul', () => {
  const lignes = [
    { codeRubrique: 'IMPOT_IRSA_BRUT', libelleRubrique: 'IRSA brut' },
    { codeRubrique: 'IMPOT_IRSA_NET', libelleRubrique: 'IRSA net' },
    { codeRubrique: 'SALAIRE_BASE', libelleRubrique: 'Salaire de base' },
  ];
  assert.deepEqual(rubriquesPaieAffichables(lignes), lignes.slice(1));
  assert.equal(lignes.length, 3);
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

test('présente pareil une campagne clôturée depuis le centre et une page d’étape', () => {
  const steps = [
    { code: 'PREPARATION', state: 'LOCKED', accessible: true, readOnly: true },
    { code: 'OUTPUTS', state: 'CURRENT', accessible: true, readOnly: true },
  ];
  const presentation = presentationParcoursCampagnePaie(steps, { workflowStatuses: { paie: { code: 'CLOTUREE' } } });
  assert.equal(presentation.closed, true);
  assert.deepEqual(presentation.progress, { completed: 2, total: 2 });
  assert.deepEqual(presentation.steps.map(step => step.state), ['COMPLETED', 'CURRENT']);
  assert.equal(presentation.steps[0].readOnly, true);
  assert.equal(presentation.steps[0].accessible, true);
});
