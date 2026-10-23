import test from 'node:test';
import assert from 'node:assert/strict';
import { formatLeaveDaysPreview } from '../src/features/leave/utils/leavePreviewPresentation.js';

test('formats available leave days including legitimate zero and negative values', () => {
  assert.equal(formatLeaveDaysPreview(0), '0.00 j');
  assert.equal(formatLeaveDaysPreview(1.5), '1.50 j');
  assert.equal(formatLeaveDaysPreview(-0.5), '−0.50 j');
});

test('distinguishes loading and unavailable balance previews from zero', () => {
  assert.equal(formatLeaveDaysPreview(null, { loading: true }), 'Chargement…');
  assert.equal(formatLeaveDaysPreview(null, { error: true }), 'Indisponible');
  assert.equal(formatLeaveDaysPreview(null), '—');
});
