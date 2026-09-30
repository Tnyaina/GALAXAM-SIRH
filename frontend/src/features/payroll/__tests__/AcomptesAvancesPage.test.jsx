// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import AcomptesAvancesPage from '../pages/AcomptesAvancesPage';

vi.mock('@/context/AuthContext', () => ({
    useAuth: () => ({
        hasPermission: () => true,
        user: { idEmploye: 1, email: 'daf@galaxam.com' }
    })
}));

vi.mock('@/features/attendance/api/attendanceApi', () => ({
    periodePaieService: {
        getAllPeriodes: vi.fn().mockResolvedValue([
            { id: 1, codePeriode: '2026-08', statut: 'PREPARATION' }
        ])
    }
}));

vi.mock('@/features/payroll/api/paieService', () => ({
    default: {
        listerAjustements: vi.fn().mockResolvedValue([]),
        syntheseAcomptesAvances: vi.fn().mockResolvedValue({
            aApprouver: 0,
            aVerser: 0,
            enRecouvrement: 0,
            encoursVerse: 0,
            aVerifier: 0
        }),
        listerModesReglement: vi.fn().mockResolvedValue([
            { code: 'VIREMENT', libelle: 'Virement bancaire' }
        ]),
        getAllPeriodes: vi.fn().mockResolvedValue([
            { id: 1, codePeriode: '2026-08', statut: 'PREPARATION' }
        ]),
        listerTypesMouvements: vi.fn().mockResolvedValue([
            { code: 'ACOMPTE_15', libelle: 'Acompte sur salaire', categorie: 'ACOMPTE', sens: 'RETENUE' }
        ]),
        creerAjustement: vi.fn().mockResolvedValue({ idAvance: 123 })
    }
}));

vi.mock('@/components/system/SEO', () => ({ default: () => null }));
vi.mock('@/components/ui/navigation/PageHeader', () => ({
    default: ({ title, subtitle }) => <div><h1>{title}</h1><p>{subtitle}</p></div>
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

describe('AcomptesAvancesPage', () => {
    it('renders with 2xl:max-w-[1600px] w-full container and Saisir une demande button', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter initialEntries={['/paie/acomptes-avances']}>
                    <AcomptesAvancesPage />
                </MemoryRouter>
            );
        });

        const container = host.querySelector('.max-w-7xl');
        expect(container).not.toBeNull();
        expect(container.className).toContain('2xl:max-w-[1600px]');
        expect(container.className).toContain('w-full');
        expect(container.className).toContain('space-y-6');

        const saisirBtn = Array.from(host.querySelectorAll('button')).find(
            b => b.textContent.includes('Saisir une demande')
        );
        expect(saisirBtn).toBeDefined();

        // Clique pour ouvrir la modal
        await act(async () => {
            saisirBtn.click();
        });

        expect(document.body.innerHTML).toContain("Saisir une demande d'acompte ou d'avance");
    });
});
