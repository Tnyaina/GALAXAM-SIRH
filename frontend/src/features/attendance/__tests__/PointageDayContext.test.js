import { describe, expect, it, vi } from 'vitest';
import { loadPointageDayContext, selectPointageDayAllowances } from '../utils/pointageDayContext';

describe('contexte commun des corrections de pointage', () => {
    it('charge le salarié demandé dans la grille du jour', async () => {
        const grid = { grilleData: [{ idEmploye: 8 }, { idEmploye: 9 }], siteIndemnites: [{ idIndemnite: 1 }] };
        const getGrid = vi.fn().mockResolvedValue(grid);
        const result = await loadPointageDayContext(getGrid, { idSite: 2, date: '2026-08-07', idEmploye: '9' });
        expect(getGrid).toHaveBeenCalledWith({ idSite: 2, date: '2026-08-07' });
        expect(result.row.idEmploye).toBe(9);
        expect(result.allowances).toEqual(grid.siteIndemnites);
    });

    it('préserve la décision enregistrée puis applique une correction explicite', () => {
        const allowances = [{ idIndemnite: 1, fractionnable: true }];
        const row = { dynamicIndemnites: { 1: { accordee: true, matin: false, am: false } } };
        expect(selectPointageDayAllowances(allowances, row)['1'].accordee).toBe(true);
        expect(selectPointageDayAllowances(allowances, row, {
            overrides: { 1: { matin: true, am: false } }
        })['1']).toEqual({ accordee: true, matin: true, am: false });
        expect(selectPointageDayAllowances(allowances, row, { clearWithoutHours: true })['1'])
            .toEqual({ accordee: false, matin: false, am: false });
    });
});
