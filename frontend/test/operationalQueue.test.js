import test from 'node:test';
import assert from 'node:assert/strict';
import { groupOperationalActions, visibleOperationalActions } from '../src/features/dashboard/components/operationalQueue.js';

test('les compteurs et les groupes utilisent les mêmes actions autorisées', () => {
    const actions = [
        { code: 'CONGES_A_VALIDER', count: 2, requiredAuthority: 'CONGE_VALIDATE' },
        { code: 'ANOMALIES_POINTAGE', count: 3, requiredAuthority: 'POINTAGE_READ' },
        { code: 'ECHEANCES_CONTRATS', count: 1 },
    ];
    const visible = visibleOperationalActions(actions, authority => authority === 'POINTAGE_READ');
    const groups = groupOperationalActions(visible);

    assert.deepEqual(visible.map(item => item.code), ['ANOMALIES_POINTAGE', 'ECHEANCES_CONTRATS']);
    assert.deepEqual(groups.map(group => group.label), ['Anomalies à résoudre', 'Échéances à anticiper']);
    assert.equal(groups.flatMap(group => group.items).reduce((sum, item) => sum + item.count, 0), 4);
});
