import test from 'node:test';
import assert from 'node:assert/strict';
import { getAnomalyTypeLabel } from '../src/features/attendance/utils/anomalyLabels.js';

test('uses the detailed API label for a justified absence', () => {
    assert.equal(getAnomalyTypeLabel({
        typeAnomalie: 'ABSENCE',
        statutCode: 'ABSENCE_JUSTIFIEE',
        statutLibelle: 'Absence justifiée (avec motif)',
    }), 'Absence justifiée (avec motif)');
});

test('distinguishes unjustified absences when the API label is missing', () => {
    assert.equal(getAnomalyTypeLabel({ typeAnomalie: 'ABSENCE', statutCode: 'ABSENCE_NON_JUSTIFIEE' }), 'Absence non justifiée');
});

test('shows a pending request and falls back to the API label otherwise', () => {
    assert.equal(getAnomalyTypeLabel({
        typeAnomalie: 'ABSENCE', statutCode: 'ABSENCE_JUSTIFIEE', statutAnomalie: 'todo',
        demandeEnAttenteLibelle: 'Congé annuel',
    }), 'Absence (Congé annuel en attente)');
    assert.equal(getAnomalyTypeLabel({ typeAnomalie: 'ABSENCE', statutLibelle: 'Absence à vérifier' }), 'Absence à vérifier');
});
