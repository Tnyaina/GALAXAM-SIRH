// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import SortiesStepPage from '../pages/SortiesStepPage';
import { usePayrollCampaign } from '../hooks/usePayrollCampaign';
import paieService from '../api/paieService';

vi.mock('../hooks/usePayrollCampaign', () => ({ usePayrollCampaign: vi.fn() }));
vi.mock('../api/paieService', () => ({ default: { getCockpitPeriode: vi.fn() } }));
vi.mock('@/components/ui/feedback/Toast', () => ({ useToast: () => ({ success: vi.fn(), error: vi.fn() }) }));
vi.mock('@/features/payroll/components/workflow/PayrollCampaignShell', () => ({ default: ({ children }) => <div>{children}</div> }));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let host;
let root;

beforeEach(() => {
    host = document.createElement('div');
    document.body.appendChild(host);
    root = createRoot(host);
    vi.clearAllMocks();
});

afterEach(() => {
    act(() => root.unmount());
    host.remove();
});

describe('accès aux sorties de paie', () => {
    it('redirige une URL directe si seul le pointage est validé', async () => {
        usePayrollCampaign.mockReturnValue({
            context: {
                selectedPeriod: { id: 1, state: 'VALIDEE', workflowStatuses: { paie: { code: 'A_PREPARER' } } },
                campaignSteps: [{ code: 'OUTPUTS', state: 'BLOCKED', accessible: false }],
            },
            periodes: [], loading: false, refreshSilent: vi.fn(),
        });
        await act(async () => root.render(
            <MemoryRouter initialEntries={['/paie/periodes/1/sorties']}>
                <Routes>
                    <Route path="/paie/periodes/:periodId/sorties" element={<SortiesStepPage />} />
                    <Route path="/paie" element={<p>Centre de paie</p>} />
                </Routes>
            </MemoryRouter>
        ));
        expect(host.textContent).toContain('Centre de paie');
        expect(host.textContent).not.toContain('Sorties et clôture');
        expect(paieService.getCockpitPeriode).not.toHaveBeenCalled();
    });

    it('suit le blocage backend même lorsque le statut affiché est validé', async () => {
        usePayrollCampaign.mockReturnValue({
            context: {
                selectedPeriod: { id: 1, state: 'PAIE_VALIDEE', workflowStatuses: { paie: { code: 'VALIDEE' } } },
                campaignSteps: [{ code: 'OUTPUTS', state: 'BLOCKED', accessible: false }],
            },
            periodes: [], loading: false, refreshSilent: vi.fn(),
        });
        await act(async () => root.render(
            <MemoryRouter initialEntries={['/paie/periodes/1/sorties']}>
                <Routes>
                    <Route path="/paie/periodes/:periodId/sorties" element={<SortiesStepPage />} />
                    <Route path="/paie" element={<p>Centre de paie</p>} />
                </Routes>
            </MemoryRouter>
        ));
        expect(host.textContent).toContain('Centre de paie');
        expect(paieService.getCockpitPeriode).not.toHaveBeenCalled();
    });
});
