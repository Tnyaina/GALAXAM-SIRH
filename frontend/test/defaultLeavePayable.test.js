import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultLeavePayable } from '../src/features/leave/utils/defaultLeavePayable.js';

test('reprend la rémunération définie par le type de congé', () => {
  assert.equal(defaultLeavePayable({ estPaye: true }), true);
  assert.equal(defaultLeavePayable({ estPaye: false }), false);
  assert.equal(defaultLeavePayable(null), false);
});
