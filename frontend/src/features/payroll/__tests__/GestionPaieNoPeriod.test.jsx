// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import GestionPaiePage from '../pages/GestionPaiePage';
import { periodePaieService } from '@/features/attendance/api/attendanceApi';

vi.mock('@/features/attendance/api/attendanceApi', () => ({
    periodePaieService: { getPeriodOptions: vi.fn(), getContext: vi.fn() },
}));
vi.mock('@/features/payroll/api/paieService', () => ({
    default: { getCockpitPeriode: vi.fn() },
}));
vi.mock('@/components/system/SEO', () => ({ default: () => null }));
vi.mock('@/context/AuthContext', () => ({
    useAuth: () => ({
        user: { role: 'ROLE_ADMIN' },
        hasPermission: () => true,
        hasAnyPermission: () => true,
        hasAllPermissions: () => true,
        hasRole: () => true,
    }),
}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let host;
let root;

beforeEach(() => {
    host = document.createElement('div');
    document.body.appendChild(host);
    root = createRoot(host);
    periodePaieService.getPeriodOptions.mockResolvedValue([]);
    periodePaieService.getContext.mockResolvedValue({
        selectedPeriod: null,
        currentPeriod: null,
        issues: [{ code: 'PAYROLL_PERIOD_CONTEXT_REQUIRED', message: 'Sélectionnez explicitement une période de paie.' }],
    });
});

afterEach(() => {
    act(() => root.unmount());
    host.remove();
    vi.clearAllMocks();
});

it("n'affiche pas de faux bloqueur lorsqu'aucune période n'existe", async () => {
    await act(async () => {
        root.render(<MemoryRouter initialEntries={['/paie']}><GestionPaiePage /></MemoryRouter>);
    });

    expect(host.textContent).toContain('Aucune campagne active');
    expect(host.textContent).not.toContain('1 bloquant');
    expect(host.textContent).not.toContain('Sélectionnez explicitement une période de paie.');
});
