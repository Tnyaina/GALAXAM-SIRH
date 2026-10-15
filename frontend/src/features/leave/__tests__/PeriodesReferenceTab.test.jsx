// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import PeriodesReferenceTab from '../components/PeriodesReferenceTab';
import congeService from '../api/congeService';

vi.mock('../api/congeService', () => ({
  default: {
    getPeriodesReferenceEmploye: vi.fn(),
    getValorisationLots: vi.fn(),
    telechargerEtatCongesExcel: vi.fn(),
    getMoisReference: vi.fn(),
    getCorrectionsValorisation: vi.fn(),
    corrigerValorisationMois: vi.fn(),
    getSituationEmploye: vi.fn(),
    activerTauxCalculeReference: vi.fn(),
  }
}));

vi.mock('@/components/ui/feedback/Toast', () => ({
  useToast: () => ({
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  })
}));

vi.mock('@/hooks/useConfirm', () => ({
  useConfirm: () => ({
    confirm: vi.fn(({ onConfirm }) => onConfirm && onConfirm()),
    ConfirmModal: null,
  }),
  default: () => ({
    confirm: vi.fn(({ onConfirm }) => onConfirm && onConfirm()),
    ConfirmModal: null,
  })
}));

vi.mock('@/components/ui/forms/EmployeSearchSelect', () => ({
  default: ({ value, onChange, label }) => (
    <div data-testid="employe-search-select">
      <label>{label}</label>
      <input
        data-testid="emp-input"
        value={value || ''}
        onChange={(e) => onChange(e.target.value)}
      />
    </div>
  )
}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let host;
let root;

beforeEach(() => {
  host = document.createElement('div');
  document.body.appendChild(host);
  root = createRoot(host);
  vi.clearAllMocks();
  congeService.getValorisationLots.mockResolvedValue({ soldeJours: 0, soldeValorisation: 0 });
  congeService.getSituationEmploye.mockResolvedValue({ tauxApplicable: {
    montant: 40000, dateEffet: '2025-01-01', source: 'CUTOVER',
  } });
});

afterEach(async () => {
  await act(async () => root.unmount());
  host.remove();
});

describe('PeriodesReferenceTab', () => {
  it('affiche un état vide si aucun employé n’est sélectionné', async () => {
    await act(async () => {
      root.render(<PeriodesReferenceTab />);
    });
    expect(host.textContent).toContain('Choisissez un collaborateur');
  });

  it('charge et affiche les cycles d’un employé sélectionné', async () => {
    const mockPeriodes = [
      {
        idPeriodeReference: 1,
        dateDebut: '2025-01-01',
        dateFin: '2025-12-31',
        statut: 'CLOTUREE',
        cumulDroitsCloture: 30.00,
        cumulValorisationCloture: 1500000.00,
        tauxCalculeCloture: 50000.00,
      },
      {
        idPeriodeReference: 2,
        dateDebut: '2026-01-01',
        dateFin: '2026-12-31',
        statut: 'OUVERTE',
        cumulDroitsCourant: 2.5,
        cumulValorisationCourante: 25000,
        cumulDroitsCloture: 0,
        cumulValorisationCloture: 0,
        tauxCalculeCloture: 0,
      }
    ];

    congeService.getPeriodesReferenceEmploye.mockResolvedValue(mockPeriodes);

    await act(async () => {
      root.render(<PeriodesReferenceTab initialIdEmploye={42} />);
    });

    expect(congeService.getPeriodesReferenceEmploye).toHaveBeenCalledWith(42);

    expect(host.textContent).toContain('Références et acquisitions');
    expect(host.textContent).toContain("Disponible aujourd'hui");
    expect(host.textContent).toContain('OUVERTE');
    expect(host.textContent).toContain('CLOTUREE');
    expect(host.textContent).toContain('50 000');
    expect(host.textContent).toContain('2.50 j');
    const rows = [...host.querySelectorAll('tr')];
    const openRow = rows.find(row => row.textContent.includes('2026') && row.textContent.includes('OUVERTE'));
    const closedRow = rows.find(row => row.textContent.includes('2025') && row.textContent.includes('CLOTUREE'));
    expect(openRow.textContent).toContain('Vérifier les mois');
    expect(closedRow.textContent).toContain('Excel');
    expect(host.textContent).not.toContain('Cycle suivant');
    expect(host.textContent).toContain('À activer');
    expect(host.textContent).not.toContain('Activer le taux');
  });

  it('guide la DAF pour activer le taux de 2025 au 01/01/2026', async () => {
    congeService.getPeriodesReferenceEmploye.mockResolvedValue([{
      idPeriodeReference: 1, dateDebut: '2025-01-01', dateFin: '2025-12-31',
      statut: 'CLOTUREE', tauxCalculeCloture: 50000,
    }]);
    congeService.activerTauxCalculeReference.mockResolvedValue({ idTauxApplicable: 7 });
    await act(async () => root.render(<PeriodesReferenceTab initialIdEmploye={42} canValidate />));
    const activer = [...host.querySelectorAll('button')].find(b => b.textContent === 'Activer le taux');
    expect(activer).toBeDefined();
    await act(async () => activer.click());
    const date = document.querySelector('[role="dialog"] input[type="date"]');
    expect(date.value).toBe('2026-01-01');
    expect(date.min).toBe('2026-01-01');
    expect(congeService.getSituationEmploye).toHaveBeenCalledWith(42, '2026-01-01');
    expect(document.body.textContent).toContain('40 000');
    expect(document.body.textContent).toContain('50 000');
    const confirmer = [...document.querySelectorAll('[role="dialog"] button')].find(b => b.textContent === "Confirmer l'activation");
    expect(confirmer.disabled).toBe(true);
    await act(async () => {
      const caseConfirmation = document.querySelector('[role="dialog"] input[type="checkbox"]');
      caseConfirmation.click();
    });
    expect(confirmer.disabled).toBe(false);
    await act(async () => confirmer.click());
    expect(congeService.activerTauxCalculeReference).toHaveBeenCalledWith(1, '2026-01-01');
  });

  it('affiche la date d’effet déjà activée sans proposer une deuxième activation', async () => {
    congeService.getPeriodesReferenceEmploye.mockResolvedValue([{
      idPeriodeReference: 1, dateDebut: '2025-01-01', dateFin: '2025-12-31',
      statut: 'CLOTUREE', tauxCalculeCloture: 50000,
      dateEffetActivation: '2026-01-01', tauxActive: 50000,
    }]);
    await act(async () => root.render(<PeriodesReferenceTab initialIdEmploye={42} canValidate />));
    expect(host.textContent).toContain('Activé le 01/01/2026');
    expect(host.textContent).not.toContain('Activer le taux');
  });

  it('propose le lendemain de la dernière acquisition si janvier est déjà clôturé', async () => {
    congeService.getPeriodesReferenceEmploye.mockResolvedValue([{
      idPeriodeReference: 1, dateDebut: '2025-01-01', dateFin: '2025-12-31',
      statut: 'CLOTUREE', tauxCalculeCloture: 50000,
      derniereAcquisition: '2026-01-25',
    }]);
    await act(async () => root.render(<PeriodesReferenceTab initialIdEmploye={42} canValidate />));
    await act(async () => [...host.querySelectorAll('button')]
      .find(b => b.textContent === 'Activer le taux').click());
    const date = document.querySelector('[role="dialog"] input[type="date"]');
    expect(date.value).toBe('2026-01-26');
    expect(date.min).toBe('2026-01-26');
    expect(document.body.textContent).toContain('25/01/2026');
  });

  it('permet de déclencher le téléchargement Excel officiel d’une période', async () => {
    const mockPeriodes = [
      {
        idPeriodeReference: 10,
        dateDebut: '2026-01-01',
        dateFin: '2026-12-31',
        statut: 'OUVERTE',
      }
    ];
    congeService.getPeriodesReferenceEmploye.mockResolvedValue(mockPeriodes);

    await act(async () => {
      root.render(<PeriodesReferenceTab initialIdEmploye={12} />);
    });

    const excelBtn = [...host.querySelectorAll('button')].find(b => b.textContent.includes('Excel'));
    expect(excelBtn).toBeDefined();

    await act(async () => {
      excelBtn.click();
    });

    expect(congeService.telechargerEtatCongesExcel).toHaveBeenCalledWith(12, 10);
  });

  it('n’offre pas de clôture pour un cycle ouvert sans acquisition', async () => {
    congeService.getPeriodesReferenceEmploye.mockResolvedValue([{
      idPeriodeReference: 11,
      dateDebut: '2025-01-01',
      dateFin: '2025-12-31',
      statut: 'OUVERTE',
      cumulDroitsCourant: 0,
      cumulValorisationCourante: 0,
    }]);
    await act(async () => root.render(<PeriodesReferenceTab initialIdEmploye={12} />));
    const row = [...host.querySelectorAll('tr')].find(element => element.textContent.includes('OUVERTE'));
    expect(row.textContent).toContain('0.00 j');
    expect(row.textContent).not.toContain('Clôturer');
    expect(row.textContent).not.toContain('Cycle suivant');
    expect(host.textContent).toContain('Cycle antérieur encore ouvert');
    expect(host.textContent).toContain('Vérifiez la clôture de la paie de décembre');
  });

  it('présente au DAF la valeur acquise chaque mois et son historique', async () => {
    congeService.getPeriodesReferenceEmploye.mockResolvedValue([{
      idPeriodeReference: 13, dateDebut: '2026-01-01', dateFin: '2026-12-31',
      statut: 'OUVERTE', cumulDroitsCourant: 2.5, cumulValorisationCourante: 27000,
    }]);
    congeService.getMoisReference.mockResolvedValue([{
      idJournal: 7, annee: 2026, mois: 1, jours: 2.5, valeur: 27000,
      cumul: 27000, statutReference: 'OUVERTE',
    }]);
    congeService.getCorrectionsValorisation.mockResolvedValue([{
      idCorrection: 1, idJournal: 7, ancienneValeur: 25000,
      nouvelleValeur: 27000, motif: 'Rapprochement Excel', corrigePar: 1,
    }]);
    await act(async () => root.render(<PeriodesReferenceTab initialIdEmploye={12} />));
    const bouton = [...host.querySelectorAll('button')].find(b => b.textContent.trim() === 'Vérifier les mois');
    await act(async () => bouton.click());
    expect(congeService.getMoisReference).toHaveBeenCalledWith(13);
    expect(document.body.textContent).toContain('01/2026');
    expect(document.body.textContent).toContain('Rapprochement Excel');
  });

  it('réserve la rectification mensuelle à la permission de validation', async () => {
    congeService.getPeriodesReferenceEmploye.mockResolvedValue([{
      idPeriodeReference: 14, dateDebut: '2026-01-01', dateFin: '2026-12-31', statut: 'OUVERTE',
    }]);
    congeService.getMoisReference.mockResolvedValue([{
      idJournal: 8, annee: 2026, mois: 1, jours: 2.5, valeur: 25000,
      cumul: 25000, statutReference: 'OUVERTE',
    }]);
    congeService.getCorrectionsValorisation.mockResolvedValue([]);
    await act(async () => root.render(<PeriodesReferenceTab initialIdEmploye={12} canValidate />));
    const controle = [...host.querySelectorAll('button')].find(b => b.textContent.trim() === 'Vérifier les mois');
    await act(async () => controle.click());
    expect([...document.querySelectorAll('[role="dialog"] button')].some(b => b.textContent.trim() === 'Rectifier')).toBe(true);
  });
});
