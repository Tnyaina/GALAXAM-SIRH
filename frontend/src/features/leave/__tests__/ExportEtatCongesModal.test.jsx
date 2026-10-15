// @vitest-environment jsdom
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import ExportEtatCongesModal from '../components/ExportEtatCongesModal';
import congeService from '../api/congeService';

vi.mock('../api/congeService', () => ({
  default: {
    getPeriodesReferenceEmploye: vi.fn(),
    telechargerEtatCongesExcel: vi.fn()
  }
}));

vi.mock('@/components/ui/feedback/Toast', () => ({
  useToast: () => ({
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn()
  })
}));

describe('ExportEtatCongesModal', () => {
  let container, root;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    vi.clearAllMocks();
  });

  afterEach(async () => {
    await act(async () => {
      root.unmount();
    });
    container.remove();
    document.body.innerHTML = '';
  });

  it('charge et liste les périodes de référence avec sélection par défaut', async () => {
    const mockPeriodes = [
      {
        idPeriodeReference: 10,
        dateDebut: '2026-01-01',
        dateFin: '2026-12-31',
        statut: 'OUVERTE',
        modeReprise: 'FULL_HISTORY',
        tauxCalculeCloture: 65000
      },
      {
        idPeriodeReference: 9,
        dateDebut: '2025-01-01',
        dateFin: '2025-12-31',
        statut: 'CLOTUREE',
        modeReprise: 'AGGREGATED_OPENING',
        tauxCalculeCloture: 60000
      }
    ];

    congeService.getPeriodesReferenceEmploye.mockResolvedValue(mockPeriodes);

    await act(async () => {
      root.render(
        <ExportEtatCongesModal
          isOpen={true}
          onClose={vi.fn()}
          idEmploye={42}
          employe={{ nom: 'ANDRIA', prenom: 'Tojo', matricule: 'EMP-042' }}
        />
      );
    });

    expect(congeService.getPeriodesReferenceEmploye).toHaveBeenCalledWith(42);
    expect(document.body.textContent).toContain('Bilan annuel des congés');
    expect(document.body.textContent).toContain('ANDRIA Tojo');
    expect(document.body.textContent).toContain('2 période(s) disponible(s)');
    expect(document.body.textContent).toContain('En cours');
    expect(document.body.textContent).toContain('Clôturée');
    expect(document.body.textContent).toContain('Feuille 1 (État des congés)');
    expect(document.body.textContent).toContain('Feuille 2 (Mouvements)');
  });

  it('déclenche le téléchargement du classeur avec la période sélectionnée', async () => {
    const mockPeriodes = [
      {
        idPeriodeReference: 10,
        dateDebut: '2026-01-01',
        dateFin: '2026-12-31',
        statut: 'OUVERTE',
        modeReprise: 'FULL_HISTORY'
      }
    ];

    congeService.getPeriodesReferenceEmploye.mockResolvedValue(mockPeriodes);
    congeService.telechargerEtatCongesExcel.mockResolvedValue(undefined);
    const onClose = vi.fn();

    await act(async () => {
      root.render(
        <ExportEtatCongesModal
          isOpen={true}
          onClose={onClose}
          idEmploye={42}
          employe={{ nom: 'ANDRIA', prenom: 'Tojo' }}
        />
      );
    });

    // Trouver le bouton Télécharger
    const downloadBtn = Array.from(document.body.querySelectorAll('button')).find((b) =>
      b.textContent.includes('Télécharger')
    );
    expect(downloadBtn).toBeTruthy();

    await act(async () => {
      downloadBtn.click();
    });

    expect(congeService.telechargerEtatCongesExcel).toHaveBeenCalledWith(42, 10);
    expect(onClose).toHaveBeenCalled();
  });

  it('guide vers la création de la période quand aucune n’est disponible', async () => {
    congeService.getPeriodesReferenceEmploye.mockResolvedValue([]);
    await act(async () => {
      root.render(<ExportEtatCongesModal isOpen onClose={vi.fn()} idEmploye={42}
        employe={{ nom: 'ANDRIA', prenom: 'Tojo' }} />);
    });
    expect(document.body.textContent).toContain('Créez ou régularisez sa période');
    const download = [...document.body.querySelectorAll('button')].find(button => button.textContent.includes('Télécharger'));
    expect(download.disabled).toBe(true);
  });
});
