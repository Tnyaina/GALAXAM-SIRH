// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { it, expect, vi } from 'vitest';
import GestionPointagesPage from '../pages/GestionPointagesPage';
import { periodePaieService } from '../api/attendanceApi';

vi.mock('../api/attendanceApi', () => ({ periodePaieService: {
    getPeriodOptions: vi.fn(), getContext: vi.fn()
} }));
vi.mock('@/features/organization/api/organisationService', () => ({ default: { getSites: async () => [{ idSite: 3 }] } }));
vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ hasPermission: () => true }) }));
vi.mock('@/components/system/SEO', () => ({ default: () => null }));
vi.mock('../pages/SaisieQuotidiennePage', () => ({ default: ({ activePeriode, sitesDisponibles }) => (
    <button disabled={!activePeriode.availableActions?.EDIT_POINTAGE?.allowed}>Grille site {sitesDisponibles[0].idSite}</button>
) }));

it('affiche la grille sans attendre les contrôles et conserve les écritures bloquées jusque-là', async () => {
    globalThis.IS_REACT_ACT_ENVIRONMENT = true;
    let resolveContext;
    periodePaieService.getPeriodOptions.mockResolvedValue([{ idPeriodePaie: 9, statutCode: 'EN_COURS', version: 1 }]);
    periodePaieService.getContext.mockReturnValue(new Promise(resolve => { resolveContext = resolve; }));
    const host = document.createElement('div');
    document.body.appendChild(host);
    const root = createRoot(host);
    try {
        await act(async () => {
            root.render(<MemoryRouter initialEntries={['/pointages?tab=saisie&periode=9']}><GestionPointagesPage /></MemoryRouter>);
        });
        const button = [...host.querySelectorAll('button')].find(b => b.textContent === 'Grille site 3');
        expect(button).toBeDefined();
        expect(button.disabled).toBe(true);
        await act(async () => resolveContext({ availableActions: { EDIT_POINTAGE: { allowed: true } } }));
        expect(button.disabled).toBe(false);
        expect(periodePaieService.getPeriodOptions).toHaveBeenCalledTimes(1);
        expect(periodePaieService.getContext).toHaveBeenCalledTimes(1);
    } finally {
        await act(async () => root.unmount());
        host.remove();
    }
});
