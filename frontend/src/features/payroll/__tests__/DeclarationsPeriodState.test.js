import { describe, expect, it } from 'vitest';
import { periodState } from '../utils/documentPeriodState';

describe('statut de paie des documents et exports', () => {
    it('ne confond pas pointage validé et paie validée', () => {
        expect(periodState({ statutCode: 'VALIDEE' })).toBe('POINTAGE_VALIDE');
        expect(periodState({ statutCode: 'VALIDEE', workflowStatuses: { paie: { code: 'A_PREPARER' } } })).toBe('A_PREPARER');
    });

    it('reconnaît la paie validée et respecte un recalcul requis', () => {
        expect(periodState({ statutCode: 'PAIE_VALIDEE' })).toBe('VALIDEE');
        expect(periodState({ statutCode: 'PAIE_VALIDEE', workflowStatuses: { paie: { code: 'A_RECALCULER' } } })).toBe('A_RECALCULER');
    });
});
