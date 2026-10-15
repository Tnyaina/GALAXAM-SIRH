// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest';

const { get, post, put } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }));
vi.mock('@/api/apiClient', () => ({ default: { get, post, put }, getAccessToken: () => 'session-test' }));

import { attendanceService, periodePaieService } from '../api/attendanceApi';

describe('partage des fiches de pointage', () => {
    beforeEach(() => {
        get.mockReset();
        post.mockReset();
        put.mockReset();
    });

    it('réutilise la fiche d’une même période entre onglet et modale', async () => {
        get.mockResolvedValue({ data: { idPeriode: 801 } });
        const first = await attendanceService.getFicheEmploye(901, { period: 'periode', idPeriode: 801, date: '2026-01-25' });
        const second = await attendanceService.getFicheEmploye(901, { idPeriode: 801 });
        expect(second).toBe(first);
        expect(get).toHaveBeenCalledTimes(1);
    });

    it('recharge après une écriture réussie', async () => {
        get.mockResolvedValue({ data: { idPeriode: 802 } });
        post.mockResolvedValue({ data: { success: true } });
        await attendanceService.getFicheEmploye(902, { idPeriode: 802 });
        await attendanceService.enregistrerBatch([{ idEmploye: 902 }]);
        await attendanceService.getFicheEmploye(902, { idPeriode: 802 });
        expect(get).toHaveBeenCalledTimes(2);
    });

    it('ne met pas en cache un échec', async () => {
        get.mockRejectedValueOnce(new Error('indisponible')).mockResolvedValueOnce({ data: { idPeriode: 803 } });
        await expect(attendanceService.getFicheEmploye(903, { idPeriode: 803 })).rejects.toThrow('indisponible');
        await attendanceService.getFicheEmploye(903, { idPeriode: 803 });
        expect(get).toHaveBeenCalledTimes(2);
    });

    it('ne conserve pas une réponse lancée avant une écriture', async () => {
        let finishFirst;
        get.mockImplementationOnce(() => new Promise((resolve) => { finishFirst = resolve; }))
            .mockResolvedValueOnce({ data: { idPeriode: 804, version: 2 } });
        put.mockResolvedValue({ data: { success: true } });
        const oldRead = attendanceService.getFicheEmploye(904, { idPeriode: 804 });
        await attendanceService.regulariser(1, { motif: 'correction' });
        finishFirst({ data: { idPeriode: 804, version: 1 } });
        await oldRead;
        const current = await attendanceService.getFicheEmploye(904, { idPeriode: 804 });
        expect(current.version).toBe(2);
        expect(get).toHaveBeenCalledTimes(2);
    });

    it('invalide la fiche après une décision de congé', async () => {
        get.mockResolvedValue({ data: { idPeriode: 805 } });
        await attendanceService.getFicheEmploye(905, { idPeriode: 805 });
        window.dispatchEvent(new Event('conge-data-updated'));
        await attendanceService.getFicheEmploye(905, { idPeriode: 805 });
        expect(get).toHaveBeenCalledTimes(2);
    });

    it('réutilise les options de période puis les recharge après une transition', async () => {
        get.mockResolvedValue({ data: [{ idPeriodePaie: 906 }] });
        post.mockResolvedValue({ data: { success: true } });
        await Promise.all([periodePaieService.getPeriodOptions(), periodePaieService.getPeriodOptions()]);
        await periodePaieService.getPeriodOptions();
        expect(get).toHaveBeenCalledTimes(1);
        await periodePaieService.executeCommand(906, 'REOPEN', { version: 1 });
        await periodePaieService.getPeriodOptions();
        expect(get).toHaveBeenCalledTimes(2);
    });
});
