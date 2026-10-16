import { describe, expect, it } from 'vitest';
import { periodState, isPayrollValidated } from '../utils/documentPeriodState';

describe('statut de paie des documents et exports', () => {
    it('ne confond pas pointage validé et paie validée', () => {
        expect(periodState({ statutCode: 'VALIDEE' })).toBeNull();
        expect(periodState({ statutCode: 'VALIDEE', workflowStatuses: { paie: { code: 'A_PREPARER' } } })).toBe('A_PREPARER');
        expect(isPayrollValidated({ statutCode: 'VALIDEE' })).toBe(false);
    });

    it('reconnaît la paie validée et respecte un recalcul requis', () => {
        expect(periodState({ statutCode: 'PAIE_VALIDEE' })).toBeNull();
        expect(isPayrollValidated({ workflowStatuses: { paie: { code: 'VALIDEE' } } })).toBe(true);
        expect(periodState({ statutCode: 'PAIE_VALIDEE', workflowStatuses: { paie: { code: 'A_RECALCULER' } } })).toBe('A_RECALCULER');
    });
});
