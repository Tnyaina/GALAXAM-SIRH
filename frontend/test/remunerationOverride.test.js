import test from 'node:test';
import assert from 'node:assert/strict';
import { remunerationOverridePayload } from '../src/features/leave/utils/remunerationOverride.js';

test('non-administrator never sends a remuneration override', () => {
  assert.deepEqual(remunerationOverridePayload({ administrator: false, value: true }), {});
  assert.deepEqual(remunerationOverridePayload({ administrator: false, value: false }), {});
});

test('administrator may explicitly choose paid or unpaid', () => {
  assert.deepEqual(remunerationOverridePayload({ administrator: true, value: true }), { estRemunere: true });
  assert.deepEqual(remunerationOverridePayload({ administrator: true, value: false }), { estRemunere: false });
});

test('null keeps type default on create and can restore it during edit', () => {
  assert.deepEqual(remunerationOverridePayload({ administrator: true, value: null }), {});
  assert.deepEqual(remunerationOverridePayload({
    administrator: true, value: null, originalValue: false, editing: true,
  }), { utiliserRemunerationParDefaut: true });
});
