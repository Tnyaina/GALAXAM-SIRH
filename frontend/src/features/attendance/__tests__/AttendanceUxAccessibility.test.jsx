// @vitest-environment jsdom
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import ArbitrageDafPage from '../pages/ArbitrageDafPage';
import SuiviAnomaliesPage from '../pages/SuiviAnomaliesPage';
import PointageGridTable from '../components/views/PointageGridTable';
import DafAjustementPointageModal from '../components/modals/DafAjustementPointageModal';
import { attendanceService, periodePaieService, pointageSyntheseService } from '../api/attendanceApi';
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
    useToast: () => ({
        toast: vi.fn(),
        success: vi.fn(),
        error: vi.fn(),
        warning: vi.fn(),
        info: vi.fn()
    })
}));

describe('Attendance UX & Accessibilité (WCAG AA & Retours visuels)', () => {
    let container;
    let root;

    beforeEach(() => {
        vi.clearAllMocks();
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);

        periodePaieService.getPeriodOptions.mockImplementation(() => periodePaieService.getAllPeriodes());
        periodePaieService.getAllPeriodes.mockResolvedValue([
            { idPeriodePaie: 1, libelle: 'Septembre 2026', statut: 'EN_COURS' }
        ]);
        periodePaieService.getContext.mockResolvedValue({
            selectedPeriod: { id: 1, libelle: 'Septembre 2026' }
        });

        attendanceService.getSites.mockResolvedValue([
            { idSite: 1, nomSite: 'Site Central' }
        ]);
        organisationService.getSites.mockResolvedValue([
            { idSite: 1, nomSite: 'Site Central' }
        ]);

        pointageSyntheseService.getPreflight.mockResolvedValue({});
        pointageSyntheseService.getSemaines.mockResolvedValue({ semaines: [] });
    });

    afterEach(async () => {
        if (root) {
            await act(async () => {
                root.unmount();
            });
        }
        if (container && container.parentNode) {
            container.parentNode.removeChild(container);
        }
    });

    const render = async (element) => {
        await act(async () => {
            root.render(<MemoryRouter>{element}</MemoryRouter>);
        });
    };

    it('ArbitrageDafPage fournit des boutons d action compacts et des aria-labels nominatifs sur les actions', async () => {
        pointageSyntheseService.getSynthese.mockResolvedValue({
            synthese: [
                {
                    idEmploye: 42,
                    nom: 'Rakoto',
                    prenom: 'Jean',
                    matricule: 'EMP042',
                    departement: 'Logistique',
                    fonction: 'Magasinier',
                    totalHeures: 40,
                    retardTotalMinutes: 15,
                    soldePeriode: -120,
                    soldePeriodeAjuste: -60,
                    ajustementPeriodeMinutes: 60,
                    ajustementsCount: 1,
                    nbJoursTravailles: 5,
                    nbAbsences: 0
                }
            ],
            totalElements: 1,
            kpis: {},
            alertCounts: {}
        });

        await render(<ArbitrageDafPage />);

        // Attendre le chargement
        await act(async () => {
            await new Promise((r) => setTimeout(r, 50));
        });

        const soldeBtn = container.querySelector('button[aria-label="Voir le solde horaire de période de Jean Rakoto"]');
        const detailBtn = container.querySelector('button[aria-label="Voir le détail des pointages de Jean Rakoto"]');
        const ajusterBtn = container.querySelector('button[aria-label="Ajuster le pointage de Jean Rakoto"]');

        expect(soldeBtn).not.toBeNull();
        expect(detailBtn).not.toBeNull();
        expect(ajusterBtn).not.toBeNull();

        // Vérifier les classes de boutons compacts w-7 h-7
        expect(soldeBtn.className).toContain('w-7');
        expect(soldeBtn.className).toContain('h-7');
        expect(detailBtn.className).toContain('w-7');
        expect(detailBtn.className).toContain('h-7');
        expect(ajusterBtn.className).toContain('w-7');
        expect(ajusterBtn.className).toContain('h-7');
    });

    it('SuiviAnomaliesPage propose un bouton d action conforme WCAG AA avec aria-label descriptif', async () => {
        attendanceService.getAnomalies.mockResolvedValue({
            anomalies: [
                {
                    idPointage: 101,
                    idEmploye: 10,
                    typeAnomalie: 'RETARD',
                    datePointage: '2026-09-15',
                    nomComplet: 'Rasoa Marie',
                    nom: 'Rasoa',
                    prenom: 'Marie',
                    matricule: 'EMP010',
                    site: 'Site Central',
                    statutAnomalie: 'todo',
                    minutesRetard: 25,
                    severite: 'MOYENNE'
                }
            ],
            stats: { total: 1, retards: 1, moyennes: 1 },
            semaines: [],
            kpis: {}
        });

        await render(<SuiviAnomaliesPage />);

        await act(async () => {
            await new Promise((r) => setTimeout(r, 50));
        });

        const regulariserBtn = container.querySelector('button[aria-label="Régulariser l\'anomalie de Rasoa Marie du 2026-09-15"]');
        expect(regulariserBtn).not.toBeNull();
        expect(regulariserBtn.className).toContain('min-h-[30px]');
    });

    it('PointageGridTable affiche TableEmpty quand aucun collaborateur ne correspond aux filtres', async () => {
        await render(
            <PointageGridTable
                grilleData={[]}
                siteIndemnites={[]}
                selectedStatutFilter="ALL"
                onStatutFilterChange={vi.fn()}
                onUpdateHours={vi.fn()}
                onUpdateStatut={vi.fn()}
                onUpdateDynamicIndemnite={vi.fn()}
                onBulkStandardHours={vi.fn()}
                onBulkStatut={vi.fn()}
                onBulkIndemnite={vi.fn()}
                onRegisterInputRef={vi.fn()}
                onKeyDown={vi.fn()}
                onFocusNext={vi.fn()}
                currentPage={1}
                pageSize={25}
                onPageChange={vi.fn()}
                onPageSizeChange={vi.fn()}
            />
        );

        expect(container.textContent).toContain('Aucun collaborateur trouvé');
        expect(container.textContent).toContain('Aucun collaborateur ne correspond aux filtres ou à la recherche sur cette journée.');
    });

    it('DafAjustementPointageModal utilise le petit bouton partagé avec un libellé contextuel', async () => {
        attendanceService.getFicheEmploye.mockResolvedValue({
            pointagesData: [
                {
                    dateComplete: '2026-09-15',
                    datePointage: '2026-09-15',
                    statut: 'PRESENT',
                    heureEntreeMatin: '08:00',
                    heureSortieMatin: '12:00',
                    heureEntreeAm: '13:00',
                    heureSortieAm: '17:00',
                    totalHeures: 8,
                    minutesRetard: 0,
                    typeJour: 'OUVRABLE',
                    horaireContinu: false,
                    canCorrect: true,
                    indemnites: {}
                }
            ],
            availableActions: { CORRECT_DAY: true }
        });

        const employeMock = {
            idEmploye: 42,
            idPeriodePaie: 1,
            nom: 'Rakoto',
            prenom: 'Jean',
            matricule: 'EMP042'
        };

        await render(
            <DafAjustementPointageModal
                isOpen={true}
                onClose={vi.fn()}
                employe={employeMock}
                selectedDate=""
                onAjustementSaved={vi.fn()}
            />
        );

        await act(async () => {
            await new Promise((r) => setTimeout(r, 50));
        });

        const rectifierBtn = document.querySelector('[role="dialog"] button[aria-label="Rectifier la journée du 15/09/2026"]');
        expect(rectifierBtn).not.toBeNull();
        expect(rectifierBtn.className).toContain('min-h-[26px]');
        expect(rectifierBtn.className).not.toContain('min-h-[44px]');
    });
});
