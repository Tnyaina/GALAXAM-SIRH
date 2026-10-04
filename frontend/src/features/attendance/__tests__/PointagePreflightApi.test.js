import { beforeEach, describe, expect, it, vi } from 'vitest';

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock('@/api/apiClient', () => ({ default: { get }, getAccessToken: () => 'session-test' }));

import { pointageSyntheseService } from '../api/attendanceApi';

describe('précontrôle pointage', () => {
    beforeEach(() => get.mockReset());

    it('respecte la décision du serveur même si les listes sont vides', async () => {
        get.mockResolvedValue({ data: { pret: false, nombreEmployes: 1, blocages: [], journeesManquantes: [] } });
        expect((await pointageSyntheseService.getPreflight({ idPeriodePaie: 7 })).pret).toBe(false);
    });

    it('refuse une réponse incomplète au lieu de calculer une décision locale', async () => {
        get.mockResolvedValue({ data: { nombreEmployes: 1, blocages: [], journeesManquantes: [] } });
        await expect(pointageSyntheseService.getPreflight({ idPeriodePaie: 7 }))
            .rejects.toThrow('décision serveur absente');
    });
});
