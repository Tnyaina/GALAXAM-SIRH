// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest';

const { get, post, put, deleteRequest } = vi.hoisted(() => ({
    get: vi.fn(), post: vi.fn(), put: vi.fn(), deleteRequest: vi.fn()
}));
vi.mock('@/api/apiClient', () => ({
    default: { get, post, put, delete: deleteRequest },
    getAccessToken: () => 'pointage-cache-session'
}));

import organisationService from '@/features/organization/api/organisationService';
import paieConfigService from '@/features/administration/api/paieConfigService';
import pointageParametreService from '@/features/administration/api/pointageParametreService';

describe('cache des référentiels Pointage', () => {
    beforeEach(() => {
        get.mockReset().mockResolvedValue({ data: [] });
        post.mockReset().mockResolvedValue({ data: { success: true } });
        put.mockReset().mockResolvedValue({ data: { success: true } });
        deleteRequest.mockReset().mockResolvedValue({ data: { success: true } });
    });

    it('réutilise les sites et invalide après une modification de site', async () => {
        await Promise.all([organisationService.getSites(), organisationService.getSites()]);
        expect(get).toHaveBeenCalledTimes(1);

        await organisationService.toggleSitePlancher(4, { estActif: true });
        await organisationService.getSites();
        expect(get).toHaveBeenCalledTimes(2);
    });

    it('met en cache calendrier, indemnités et périodes chômées jusqu’à leur modification', async () => {
        await Promise.all([paieConfigService.getCalendrierFerie(), paieConfigService.getCalendrierFerie()]);
        await paieConfigService.getSiteFeries();
        await paieConfigService.getPeriodesChomees();
        await paieConfigService.getIndemnites();
        await paieConfigService.getSiteIndemnites();
        expect(get).toHaveBeenCalledTimes(5);

        await paieConfigService.updatePeriodeChomee(2, { libelle: 'Fermeture' });
        await Promise.all([paieConfigService.getCalendrierFerie(), paieConfigService.getPeriodesChomees()]);
        expect(get).toHaveBeenCalledTimes(7);
    });

    it('met en cache tolérances et dérogations puis invalide après écriture', async () => {
        await pointageParametreService.getTolerances();
        await pointageParametreService.getTolerances();
        await pointageParametreService.getDerogations();
        expect(get).toHaveBeenCalledTimes(2);

        await pointageParametreService.saveTolerance({ idSite: 4, estActif: false });
        await pointageParametreService.getTolerances();
        expect(get).toHaveBeenCalledTimes(3);
    });
});
