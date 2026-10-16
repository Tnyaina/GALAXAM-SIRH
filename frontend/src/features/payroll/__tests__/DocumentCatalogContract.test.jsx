// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import DeclarationsSortiesPage from '../pages/DeclarationsSortiesPage';
import { usePayrollCampaign } from '../hooks/usePayrollCampaign';
import paieService from '../api/paieService';

vi.mock('../hooks/usePayrollCampaign', () => ({ usePayrollCampaign: vi.fn() }));
vi.mock('../api/paieService', () => ({ default: { getDocumentCatalog: vi.fn() } }));
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

describe('contrat du catalogue documentaire', () => {
    it('suit le refus backend même si le libellé de la période paraît validé', async () => {
        usePayrollCampaign.mockReturnValue({
            context: { selectedPeriod: { id: 11, code: '2026-08', workflowStatuses: { paie: { code: 'VALIDEE', label: 'Paie validée' } } } },
            selectedPeriodeId: 11,
            periodes: [{ idPeriodePaie: 11, code: '2026-08', workflowStatuses: { paie: { code: 'VALIDEE', label: 'Paie validée' } } }],
        });
        paieService.getDocumentCatalog.mockResolvedValue({
            scope: 'mensuel', coveredMonths: 0, totalMonths: 1,
            documents: [{ id: 'grand-livre', name: 'Grand livre de paie', detail: 'État nominatif.',
                format: 'Excel (.xlsx)', available: false, label: 'Recalcul requis', reasonCodes: ['PAYROLL_CALCULATION_STALE'] }],
        });
        await act(async () => root.render(<MemoryRouter><DeclarationsSortiesPage /></MemoryRouter>));
        await act(async () => Promise.resolve());
        expect(paieService.getDocumentCatalog).toHaveBeenCalledWith('mensuel', { periodId: 11 });
        expect(host.textContent).toContain('Recalcul requis');
        expect(host.textContent).not.toContain('Télécharger Excel');
    });
});
