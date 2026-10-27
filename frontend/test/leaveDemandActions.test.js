import test from 'node:test';
import assert from 'node:assert/strict';
import { canEditLeaveDemand } from '../src/features/leave/utils/leaveDemandActions.js';

test('le panneau personnel propose la modification uniquement si l’API l’autorise', () => {
  assert.equal(canEditLeaveDemand({ availableActions: { EDIT: { allowed: true } } }), true);
  assert.equal(canEditLeaveDemand({ availableActions: { EDIT: { allowed: false } } }), false);
  assert.equal(canEditLeaveDemand({}), false);
});
