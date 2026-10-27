import test from 'node:test';
import assert from 'node:assert/strict';
import { ACQUISITION_CORRECTION_LIMIT, ACQUISITION_CORRECTION_STEPS } from '../src/features/leave/utils/acquisitionCorrectionGuidance.js';

test('explique les deux parcours d’acquisition sans référence manuelle non supportée', () => {
  assert.equal(ACQUISITION_CORRECTION_STEPS.length, 2);
  assert.match(ACQUISITION_CORRECTION_STEPS[0], /cycle ouvert.*lot non consommé/i);
  assert.match(ACQUISITION_CORRECTION_STEPS[1], /cycle clôturé.*lot déjà consommé/i);
  assert.match(ACQUISITION_CORRECTION_LIMIT, /reprise historique/i);
  assert.match(ACQUISITION_CORRECTION_LIMIT, /référence technique manuelle/i);
  assert.doesNotMatch(`${ACQUISITION_CORRECTION_STEPS.join(' ')} ${ACQUISITION_CORRECTION_LIMIT}`, /JOURNAL_CONGE#/);
});
