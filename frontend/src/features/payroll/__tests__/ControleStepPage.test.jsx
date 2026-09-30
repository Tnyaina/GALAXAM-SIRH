// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import ControleStepPage from '../pages/ControleStepPage';
import paieService from '../api/paieService';

vi.mock('@/components/system/SEO', () => ({ default: () => null }));
vi.mock('@/features/payroll/components/workflow/PayrollCampaignShell', () => ({
    default: ({ children, primaryAction }) => <div>{children}<button disabled={primaryAction.disabled}>{primaryAction.label}</button></div>,
}));
vi.mock('@/features/payroll/hooks/usePayrollCampaign', () => ({
    usePayrollCampaign: () => ({
        periodes: [],
        context: { availableActions: { VALIDATE: { allowed: false } }, issueSummary: { blockerCount: 2 } },
        loading: false,
        refreshSilent: vi.fn(),
    }),
}));
vi.mock('../api/paieService', () => ({
    default: { getCockpitPeriode: vi.fn() },
}));
vi.mock('@/features/payroll/components/views/BulletinPaieDrawer', () => ({ default: () => null }));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let host;
let root;

beforeEach(() => {
    host = document.createElement('div');
    document.body.appendChild(host);
    root = createRoot(host);
    paieService.getCockpitPeriode.mockResolvedValue({
        kpis: { effectifCalcule: 25, totalNetAPayer: 12000000 },
        readiness: { nbAnomalies: 7 },
        bulletinsPage: { totalElements: 25 },
        bulletins: [{
            idBulletin: 1,
            matricule: '199',
            nomPrenom: 'Agent Test',
            totalGainsBruts: 500000,
            totalCotisationsSalariales: 5000,
            irsaNet: 3000,
            netAPayer: 492000,
            aAlerte: true,
        }],
    });
});

afterEach(() => {
    act(() => root.unmount());
    host.remove();
    vi.clearAllMocks();
});

describe('ControleStepPage', () => {
    it('uses backend totals, alert filter and server pagination', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter initialEntries={['/paie/periodes/1/controle']}>
                    <Routes><Route path="/paie/periodes/:periodId/controle" element={<ControleStepPage />} /></Routes>
                </MemoryRouter>
            );
        });

        expect(host.textContent).toContain('25');
        expect(host.textContent).toContain('7');
        expect(host.textContent).toContain('Agent Test');
        const validationAction = [...host.querySelectorAll('button')].find(button => button.textContent.includes('Continuer vers la validation'));
        expect(validationAction?.disabled).toBe(true);

        const alertTab = [...host.querySelectorAll('button')].find(button => button.textContent.includes('À examiner'));
        await act(async () => alertTab.click());
        expect(paieService.getCockpitPeriode).toHaveBeenLastCalledWith(1, expect.objectContaining({ filter: 'ALERTS', page: 1, size: 20 }));

        const nextPage = host.querySelector('button[aria-label="Page suivante"]');
        await act(async () => nextPage.click());
        expect(paieService.getCockpitPeriode).toHaveBeenLastCalledWith(1, expect.objectContaining({ filter: 'ALERTS', page: 2, size: 20 }));
    });
});
