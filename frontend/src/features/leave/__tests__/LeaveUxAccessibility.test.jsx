// @vitest-environment jsdom
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import ParametresCongesTab from '../components/ParametresCongesTab';
import ValidationTable from '../components/validation/ValidationTable';
import CongesTab from '@/features/employees/components/tabs/CongesTab';
import congeService from '@/features/leave/api/congeService';

vi.mock('@/components/ui/feedback/Toast', () => ({
  useToast: () => ({
    toast: vi.fn(),
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn()
  })
}));

vi.mock('@/hooks/useConfirm', () => ({
  default: () => ({
    confirm: vi.fn(),
    ConfirmModal: () => null
  })
}));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({
    hasPermission: () => true,
    user: { idUtilisateur: 1, nom: 'Admin' }
  })
}));

vi.mock('react-router-dom', () => ({
  useNavigate: () => vi.fn(),
  Link: ({ children, ...props }) => <a {...props}>{children}</a>
}));

vi.mock('@/features/leave/api/congeService', () => ({
  default: {
    getTypesConge: vi.fn(),
    getMotifsFamiliaux: vi.fn(),
    getSoldeEmploye: vi.fn(),
    getSituationEmploye: vi.fn(),
    getMouvementsEmploye: vi.fn(),
    getConsommationPool: vi.fn(),
    getAllReferentiels: vi.fn(),
    getDemandesEmploye: vi.fn(),
    annulerDemande: vi.fn()
  }
}));

describe('Conges & Absences UX & Accessibilité (WCAG AA & EmptyStates)', () => {
  let container;
  let root;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.clearAllMocks();
  });

  it('ParametresCongesTab : EmptyState focal quand aucun type de conge n\'est présent', async () => {
    congeService.getTypesConge.mockResolvedValue([]);
    congeService.getMotifsFamiliaux.mockResolvedValue([]);

    await act(async () => {
      root.render(<ParametresCongesTab />);
    });

    const buttons = Array.from(container.querySelectorAll('button'));
    const newTypeBtns = buttons.filter(b => b.textContent.trim() === 'Nouveau Type');
    // Le bouton d'en-tête ne doit pas être dupliqué : seul le bouton focal EmptyState apparaît
    expect(newTypeBtns.length).toBe(1);

    const emptyAction = buttons.find(b => b.textContent.includes('Nouveau Type'));
    expect(emptyAction).toBeDefined();
  });

  it('ParametresCongesTab : boutons d\'actions conformes aux cibles tactiles WCAG AA (min 44x44px) avec aria-label explicite', async () => {
    congeService.getTypesConge.mockResolvedValue([
      {
        idTypeConge: 1,
        code: 'PAYE',
        libelle: 'Congé Payé Annuel',
        estPaye: true,
        decompteSoldeAcquis: true,
        deduireJoursFeries: false,
        estActif: true
      }
    ]);
    congeService.getMotifsFamiliaux.mockResolvedValue([]);

    await act(async () => {
      root.render(<ParametresCongesTab />);
    });

    const actionButtons = Array.from(container.querySelectorAll('td button[aria-label]'));
    expect(actionButtons.length).toBe(2);
    actionButtons.forEach(btn => {
      expect(btn.className).toContain('min-h-[44px]');
      expect(btn.className).toContain('min-w-[44px]');
      expect(btn.getAttribute('aria-label')).toBeTruthy();
    });
  });

  it('ValidationTable : boutons Examiner / Détails conformes aux cibles tactiles WCAG AA (min-h-[44px])', async () => {
    const demandeId = 99;
    const demandes = [
      {
        idDemandeConge: demandeId,
        idEmploye: 42,
        prenom: 'Jean',
        nom: 'Dupont',
        matricule: 'EMP042',
        codeStatutDemande: 'EN_ATTENTE',
        libelleTypeConge: 'Congé Payé',
        dateDebut: '2026-10-10',
        dateFin: '2026-10-15',
        nbJoursDecomptes: 5,
        availableActions: {
          APPROVE: { allowed: true }
        }
      }
    ];

    await act(async () => {
      root.render(
        <ValidationTable
          demandes={demandes}
          activeStatut="EN_ATTENTE"
          displayTotal={1}
          searchTerm=""
          currentPage={1}
          pageSize={10}
          setCurrentPage={vi.fn()}
          setPageSize={vi.fn()}
          canValidate={true}
          onOpenDetail={vi.fn()}
        />
      );
    });

    const detailButtons = Array.from(container.querySelectorAll(`button[aria-label*="demande #${demandeId}"]`));
    expect(detailButtons.length).toBeGreaterThanOrEqual(1);
    detailButtons.forEach(btn => {
      expect(btn.className).toContain('min-h-[44px]');
      expect(btn.getAttribute('aria-label')).toContain('Jean Dupont');
    });
  });

  it('CongesTab : bouton Détails de demande conforme aux cibles tactiles WCAG AA (min-h-[44px])', async () => {
    congeService.getSoldeEmploye.mockResolvedValue({
      soldeActuel: 15,
      joursAcquis: 20,
      joursPris: 5
    });
    congeService.getSituationEmploye.mockResolvedValue({
      idEmploye: 42,
      soldeRestant: 15
    });
    congeService.getMouvementsEmploye.mockResolvedValue([]);
    congeService.getConsommationPool.mockResolvedValue([]);
    congeService.getAllReferentiels.mockResolvedValue({ typesConge: [] });
    congeService.getDemandesEmploye.mockResolvedValue({
      content: [
        {
          idDemandeConge: 55,
          codeTypeConge: 'PAYE',
          libelleTypeConge: 'Congé Payé',
          dateDebut: '2026-11-01',
          dateFin: '2026-11-05',
          nbJoursDecomptes: 4,
          codeStatutDemande: 'VALIDE',
          statutLibelle: 'Validé',
          decompteSoldeAcquis: true
        }
      ],
      totalElements: 1
    });

    await act(async () => {
      root.render(<CongesTab idEmploye={42} canWrite={true} />);
    });
    await act(async () => {
      await new Promise((r) => setTimeout(r, 10));
    });

    const detailsBtn = container.querySelector('button[aria-label*="Détails de la demande #55"]');
    expect(detailsBtn).not.toBeNull();
    expect(detailsBtn.className).toContain('min-h-[44px]');
  });
});
