import test from 'node:test';
import assert from 'node:assert/strict';
import { hasFixedLeaveDuration } from '../src/features/leave/utils/leaveDurationPolicy.js';

test('un motif familial ne transforme pas une demande à dates libres en durée fixe', () => {
  assert.equal(hasFixedLeaveDuration({ fixedDays: null }), false);
});

test('un type à durée fixe continue à laisser le serveur calculer la date de fin', () => {
  assert.equal(hasFixedLeaveDuration({ fixedDays: 3 }), true);
});

test('l’extension médicale active la durée maximale du congé maternité', () => {
  assert.equal(hasFixedLeaveDuration({ fixedDays: 98, maternityExtension: true, isMaternity: true }), true);
});
