import test from 'node:test';
import assert from 'node:assert/strict';
import { resolvePendingTcongeResult } from '../src/features/leave/utils/pendingTcongeState.js';

test('distingue une réponse vide réussie d’une erreur de chargement', () => {
  assert.deepEqual(resolvePendingTcongeResult({ status: 'fulfilled', value: [] }), {
    status: 'success', periods: [], error: null,
  });
  const failure = new Error('indisponible');
  assert.deepEqual(resolvePendingTcongeResult({ status: 'rejected', reason: failure }), {
    status: 'error', periods: [], error: failure,
  });
});

test('traite une réponse mal formée comme indisponible, jamais comme une liste vide', () => {
  assert.equal(resolvePendingTcongeResult({ status: 'fulfilled', value: null }).status, 'error');
});
