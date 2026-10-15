// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import CarrieresSupervisionPage from '../pages/CarrieresSupervisionPage';
import contratService from '../api/contratService';

vi.mock('../api/contratService', () => ({ default: {
    getContratsGlobal: vi.fn(),
    getStatsSupervision: vi.fn(),
    getAlertesPrioritaires: vi.fn(),
    getWorkflowsGlobal: vi.fn(),
    getTypesEvenementRh: vi.fn(),
    getStatutsWorkflows: vi.fn(),
    getFiltresContrats: vi.fn(),
} }));
vi.mock('@/features/organization/api/organisationService', () => ({ default: { getSites: vi.fn().mockResolvedValue([]) } }));
vi.mock('@/components/system/SEO', () => ({ default: () => null }));
vi.mock('@/components/ui/navigation/PageHeader', () => ({ default: ({ title, actions }) => <header><h1>{title}</h1>{actions}</header> }));
vi.mock('@/features/auth/components/HasPermission', () => ({ default: ({ children }) => children }));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let host;
let root;

beforeEach(() => {
    host = document.createElement('div');
    document.body.appendChild(host);
    root = createRoot(host);
    contratService.getContratsGlobal.mockResolvedValue({
        content: [
            { idContrat: 1, idEmploye: 7, nomPrenom: 'Rakoto Jean', matricule: 'M007',
                numeroContrat: 'CTR-001', libelleTypeContrat: 'CDD', codeStatutContrat: 'CLOTURE',
                dateDebut: '2025-01-01', dateFin: '2025-12-31', nomSite: 'Agence' },
            { idContrat: 2, idEmploye: 7, nomPrenom: 'Rakoto Jean', matricule: 'M007',
                numeroContrat: 'CTR-002', libelleTypeContrat: 'CDI', codeStatutContrat: 'ACTIF',
                dateDebut: '2026-01-01', nomSite: 'Agence' },
        ],
        totalElements: 1,
        totalContrats: 2,
    });
    contratService.getStatsSupervision.mockResolvedValue({ totalContrats: 2, contratsActifs: 1 });
    contratService.getAlertesPrioritaires.mockResolvedValue([]);
    contratService.getFiltresContrats.mockResolvedValue({
        statuts: [{ code: 'ALL', libelle: 'Tous les états' }],
        echeances: [{ code: 'ALL', libelle: 'Toutes les échéances' }],
    });
    contratService.getTypesEvenementRh.mockResolvedValue([]);
    contratService.getStatutsWorkflows.mockResolvedValue([{ code: 'PLANIFIE', libelle: 'Planifié' }]);
    contratService.getWorkflowsGlobal.mockResolvedValue({ content: [{
        idWorkflow: 5, idEmploye: 7, nomPrenomEmploye: 'Rakoto Jean', matriculeEmploye: 'M007',
        codeTypeEvenement: 'MUTATION', libelleTypeEvenement: 'Mutation',
        codeStatutWorkflow: 'PLANIFIE', libelleStatutWorkflow: 'Planifié',
        isPlanifie: true, dateEffet: '2026-11-01', libelleFonction: 'Gestionnaire', libelleSite: 'Agence',
    }], totalElements: 1 });
});
afterEach(async () => {
    await act(async () => root.unmount());
    host.remove();
    vi.clearAllMocks();
});

describe('Supervision carrières', () => {
    it('présente les résultats par salarié et déplie ses contrats à la demande', async () => {
        await act(async () => root.render(<MemoryRouter><CarrieresSupervisionPage /></MemoryRouter>));
        expect(host.textContent).toContain('1 collaborateur(s) · 2 contrat(s) trouvé(s)');
        expect(host.textContent).not.toContain('CTR-001');
        const employeeButton = [...host.querySelectorAll('button')].find(button => button.getAttribute('aria-label')?.includes('contrats de Rakoto Jean'));
        await act(async () => employeeButton.click());
        expect(host.textContent).toContain('CTR-001');
        expect(host.textContent).toContain('CTR-002');
    });

    it('affiche un journal condensé et le détail du mouvement', async () => {
        await act(async () => root.render(<MemoryRouter><CarrieresSupervisionPage /></MemoryRouter>));
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent.includes('Mouvements')).click());
        for (let attempt = 0; attempt < 20 && !host.textContent.includes('Mutation'); attempt += 1) {
            await act(async () => { await new Promise(resolve => setTimeout(resolve, 10)); });
        }
        expect(host.textContent).toContain('Mutation');
        expect(host.textContent).toContain('Effet planifié');
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Voir').click());
        expect(document.body.textContent).toContain('Ouvrir la carrière');
    });

    it('charge tous les contrats du salarié avant de comparer un doublon potentiel', async () => {
        const page = await contratService.getContratsGlobal();
        contratService.getContratsGlobal.mockResolvedValue({
            ...page,
            content: page.content.map(contract => ({ ...contract, isDuplicate: true })),
        });
        await act(async () => root.render(<MemoryRouter><CarrieresSupervisionPage /></MemoryRouter>));
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Comparer').click());
        expect(contratService.getContratsGlobal).toHaveBeenCalledWith({ idEmploye: 7, page: 1, size: 50 });
        expect(document.body.textContent).toContain('Vérification des Doublons');
    });
});
