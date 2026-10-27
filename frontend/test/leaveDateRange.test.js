import assert from 'node:assert/strict';
import test from 'node:test';
import { getDateFinFromRepriseInput, getRepriseInputValue } from '../src/features/leave/utils/leaveDateRange.js';

test('la date de reprise est calculée après le dernier jour de congé', () => {
  const range = { estDemiJournee: false, dateDebut: '2026-10-12', dateFin: '2026-10-14' };

  assert.equal(getRepriseInputValue(range), '2026-10-15');
  assert.equal(getDateFinFromRepriseInput('2026-10-15'), '2026-10-14');
});

test('une demi-journée reprend le lendemain de la date de début', () => {
  assert.equal(getRepriseInputValue({ estDemiJournee: true, dateDebut: '2026-10-12', dateFin: '2026-10-12' }), '2026-10-13');
});

test('une reprise effacée efface le dernier jour de congé et une date invalide ne produit aucune date', () => {
  assert.equal(getDateFinFromRepriseInput(''), '');
  assert.equal(getDateFinFromRepriseInput('date-invalide'), '');
});
