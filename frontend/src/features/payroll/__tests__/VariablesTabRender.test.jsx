// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import GestionPaiePage from '../pages/GestionPaiePage';

vi.mock('@/features/payroll/api/paieService', () => ({
    default: {
        getAllPeriodes: vi.fn().mockResolvedValue([
            { id: 1, codePeriode: '2026-08', statut: 'PREPARATION', statutPointage: 'VALIDE' }
        ]),
        getCockpitPeriode: vi.fn().mockResolvedValue({
            selectedPeriod: { id: 1, codePeriode: '2026-08', statut: 'PREPARATION' },
            currentPeriod: { id: 1, codePeriode: '2026-08', statut: 'PREPARATION' },
            campaignSteps: [],
            currentStep: 'PREPARATION',
            population: [],
            kpis: {}
        }),
        getWorkflowContext: vi.fn().mockResolvedValue({
            selectedPeriod: { id: 1, codePeriode: '2026-08', statut: 'PREPARATION' },
            currentPeriod: { id: 1, codePeriode: '2026-08', statut: 'PREPARATION' },
            campaignSteps: [],
            currentStep: 'PREPARATION',
            availableActions: { ADD_VARIABLE: { allowed: true } }
        }),
        listerAjustements: vi.fn().mockResolvedValue([]),
        listerTypesMouvements: vi.fn().mockResolvedValue([])
    }
}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let host;
let root;

beforeEach(() => {
    host = document.createElement('div');
    document.body.appendChild(host);
    root = createRoot(host);
});

afterEach(() => {
    act(() => { root.unmount(); });
    host.remove();
});

describe('GestionPaiePage tab=variables', () => {
    it('renders without crashing on tab=variables', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter initialEntries={['/paie?tab=variables']}>
                    <GestionPaiePage />
                </MemoryRouter>
            );
        });
        expect(host.innerHTML).toBeDefined();
    });
});
