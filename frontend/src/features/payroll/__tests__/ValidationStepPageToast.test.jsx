// @vitest-environment jsdom
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import ValidationStepPage from '../pages/ValidationStepPage';

const mockToastSuccess = vi.fn();
const mockToastError = vi.fn();

vi.mock('@/components/ui/feedback/Toast', () => ({
    useToast: () => ({
        toast: Object.assign(vi.fn(), {
            success: mockToastSuccess,
            error: mockToastError
        })
    })
}));

vi.mock('@/features/payroll/hooks/usePayrollCampaign', () => ({
    usePayrollCampaign: () => ({
        periodes: [{ id: 4, code: '2026-04' }],
        context: {
            selectedPeriod: { id: 4, code: '2026-04' },
            availableActions: {
                VALIDATE: { allowed: true },
                REOPEN: { allowed: true }
            }
        },
        loading: false,
        refreshSilent: vi.fn().mockResolvedValue({})
    })
}));

vi.mock('@/features/payroll/api/paieService', () => ({
    default: {
        getCockpitPeriode: vi.fn().mockResolvedValue({
            readiness: { effectifAttendu: 2, nbAnomalies: 0 },
            bulletinsPage: { totalElements: 2 },
            kpis: {
                masseSalarialeBrute: 8680000,
                totalNetAPayer: 7004100,
                cotisationsSalariales: 96000,
                irsaTotal: 1579900,
                cotisationsPatronales: 912000
            },
            validationSummary: {
                pointageFige: true,
                resultatsDisponibles: true,
                populationResolue: true,
                pasDeVariationBloquante: true
            }
        }),
        validerPaiePeriode: vi.fn().mockResolvedValue({ success: true })
    }
}));

vi.mock('@/features/attendance/api/attendanceApi', () => ({
    periodePaieService: {
        executeCommand: vi.fn().mockResolvedValue({ success: true })
    }
}));

describe('ValidationStepPage - Toast vs State', () => {
    let container;
    let root;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        mockToastSuccess.mockClear();
        mockToastError.mockClear();
    });

    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        vi.clearAllMocks();
    });

    it('déclenche toast.success sans ajouter d’alerte de feedback locale dans le state', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter initialEntries={['/paie/periodes/4/validation']}>
                    <Routes>
                        <Route path="/paie/periodes/:periodId/validation" element={<ValidationStepPage />} />
                    </Routes>
                </MemoryRouter>
            );
        });

        // Ouvrir la modale de validation
        const validerBtn = [...container.querySelectorAll('button')].find(b =>
            b.textContent.includes('Valider la paie')
        );
        expect(validerBtn).not.toBeUndefined();

        await act(async () => {
            validerBtn.click();
        });

        // Confirmer dans la modale
        const dialog = document.querySelector('[role="dialog"]');
        expect(dialog).not.toBeNull();
        const confirmBtn = [...dialog.querySelectorAll('button')].find(b =>
            b.textContent.includes('Valider la paie')
        );
        expect(confirmBtn).not.toBeUndefined();

        await act(async () => {
            confirmBtn.click();
            await new Promise(r => setTimeout(r, 50));
        });

        // Toast appelé
        expect(mockToastSuccess).toHaveBeenCalledWith(
            'Campagne validée. Les bulletins sont scellés.',
            'Validation réussie'
        );

        // Aucune alerte "Confirmation" dans le body (pas d'Alert feedback de state)
        const feedbackAlert = [...container.querySelectorAll('div')].find(d =>
            d.textContent.includes('Confirmation') && d.textContent.includes('Campagne validée. Les bulletins sont scellés.')
        );
        expect(feedbackAlert).toBeUndefined();
    });
});
