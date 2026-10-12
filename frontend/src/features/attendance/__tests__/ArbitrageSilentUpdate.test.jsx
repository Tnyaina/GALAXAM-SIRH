// @vitest-environment jsdom
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import ArbitrageDafPage from '../pages/ArbitrageDafPage';
import { periodePaieService, pointageSyntheseService } from '../api/attendanceApi';
import organisationService from '@/features/organization/api/organisationService';

vi.mock('../api/attendanceApi', () => ({
    attendanceService: {
        getSites: vi.fn(),
        getPointageHebdoGrid: vi.fn(),
        getFicheEmploye: vi.fn(),
        updatePointageJour: vi.fn(),
        getAnomalies: vi.fn()
    },
    periodePaieService: {
        getAllPeriodes: vi.fn(),
        getPeriodOptions: vi.fn(),
        getContext: vi.fn()
    },
    pointageSyntheseService: {
        getSynthese: vi.fn(),
        getSemaines: vi.fn(),
        getPreflight: vi.fn(),
        getAjustements: vi.fn(),
        creerAjustement: vi.fn()
    }
}));

vi.mock('@/features/organization/api/organisationService', () => ({
    default: {
        getSites: vi.fn()
    }
}));

vi.mock('@/context/AuthContext', () => ({
    useAuth: () => ({
        user: { id: 1, nom: 'DAF' },
        hasPermission: () => true
    })
}));

vi.mock('@/components/ui/feedback/Toast', () => ({
    useToast: () => ({ toast: vi.fn() })
}));

describe('ArbitrageDafPage - Actualisation silencieuse et réactivité du statut', () => {
    let container;
    let root;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);

        organisationService.getSites.mockResolvedValue([
            { idSite: 1, nom: 'Siège', estActif: true }
        ]);

        periodePaieService.getAllPeriodes.mockResolvedValue([
            {
                idPeriodePaie: 4,
                code: '2026-04',
                libelle: 'Avril 2026',
                statutCode: 'TRANSMIS_DAF',
                version: 1,
                availableActions: {
                    APPROVE: { allowed: true },
                    REJECT: { allowed: true }
                }
            }
        ]);

        pointageSyntheseService.getSynthese.mockResolvedValue({
            synthese: [],
            totalElements: 0,
            kpis: {},
            alertCounts: {}
        });

        pointageSyntheseService.getPreflight.mockResolvedValue({
            pret: true
        });
    });

    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        vi.clearAllMocks();
    });

    it('bascule de "Valider et sceller" vers "Continuer vers la paie" sans recharger la page lorsque le statut change', async () => {
        const initialPeriod = {
            idPeriodePaie: 4,
            code: '2026-04',
            libelle: 'Avril 2026',
            statutCode: 'TRANSMIS_DAF',
            version: 1,
            availableActions: {
                APPROVE: { allowed: true },
                REJECT: { allowed: true }
            }
        };

        await act(async () => {
            root.render(
                <MemoryRouter initialEntries={['/pointages?tab=validation&periode=4']}>
                    <ArbitrageDafPage activePeriode={initialPeriod} />
                </MemoryRouter>
            );
        });

        // Attente du chargement initial
        await act(async () => {
            await new Promise(r => setTimeout(r, 50));
        });

        // Bouton de validation visible en statut TRANSMIS_DAF
        const validerBtn = [...container.querySelectorAll('button')].find(b =>
            b.textContent.includes('Valider et sceller le pointage')
        );
        expect(validerBtn).not.toBeUndefined();

        // Mise à jour silencieuse de activePeriode vers VALIDEE (par exemple via polling ou événement externe)
        const updatedPeriod = {
            ...initialPeriod,
            statutCode: 'VALIDEE',
            version: 2,
            availableActions: {}
        };

        await act(async () => {
            root.render(
                <MemoryRouter initialEntries={['/pointages?tab=validation&periode=4']}>
                    <ArbitrageDafPage activePeriode={updatedPeriod} />
                </MemoryRouter>
            );
        });

        await act(async () => {
            await new Promise(r => setTimeout(r, 50));
        });

        // Le bouton "Valider et sceller" a disparu et "Continuer vers la paie" est apparu
        const validerBtnAfter = [...container.querySelectorAll('button')].find(b =>
            b.textContent.includes('Valider et sceller le pointage')
        );
        expect(validerBtnAfter).toBeUndefined();

        const continuerBtn = [...container.querySelectorAll('button')].find(b =>
            b.textContent.includes('Continuer vers la paie')
        );
        expect(continuerBtn).not.toBeUndefined();
    });

    it('ne bloque pas la décision DAF pendant le chargement de la synthèse paginée', async () => {
        let finishSynthese;
        pointageSyntheseService.getSynthese.mockReturnValue(new Promise(resolve => { finishSynthese = resolve; }));
        const period = {
            idPeriodePaie: 4,
            statutCode: 'TRANSMIS_DAF',
            availableActions: { APPROVE: { allowed: true }, REJECT: { allowed: true } }
        };

        await act(async () => {
            root.render(
                <MemoryRouter initialEntries={['/pointages?tab=validation&periode=4']}>
                    <ArbitrageDafPage activePeriode={period} />
                </MemoryRouter>
            );
            await new Promise(resolve => setTimeout(resolve, 20));
        });

        const validerBtn = [...container.querySelectorAll('button')].find(button =>
            button.textContent.includes('Valider et sceller le pointage')
        );
        expect(validerBtn).toBeDefined();
        expect(validerBtn.disabled).toBe(false);

        await act(async () => finishSynthese({ synthese: [], totalElements: 0, kpis: {}, alertCounts: {} }));
    });
});
