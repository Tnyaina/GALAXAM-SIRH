import { describe, expect, it } from 'vitest';
import { clearDynamicAllowances, isDayRowModified, isUnenteredNewDay } from '../hooks/usePointagePeriodeEmploye';

describe('saisie journalière sans pointage', () => {
    it('écarte une journée neuve laissée Non saisi du lot malgré les indemnités par défaut', () => {
        const initial = { codeStatut: 'ANOMALIE_SAISIE', heureEntreeMatin: '08:00', heureSortieMatin: '12:30' };
        const current = { idPointage: null, codeStatut: 'NON_SAISI',
            heureEntreeMatin: '', heureSortieMatin: '', heureEntreeAm: '', heureSortieAm: '',
            dynamicIndemnites: { 1: { accordee: true } } };
        expect(isDayRowModified(current, initial)).toBe(true);
        expect(isUnenteredNewDay(current)).toBe(true);
        expect(isUnenteredNewDay({ ...current, idPointage: 12 })).toBe(false);
    });

    it('retire repas et déplacement quand une absence est choisie', () => {
        expect(clearDynamicAllowances({ 1: { accordee: true, fractionJour: 1, matin: true, am: true } }))
            .toEqual({ 1: { accordee: false, fractionJour: 0, matin: false, am: false } });
    });
});
