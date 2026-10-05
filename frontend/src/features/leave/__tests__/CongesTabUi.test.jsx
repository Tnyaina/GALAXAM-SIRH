// @vitest-environment jsdom
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import SoldeCongeWidget from '../components/SoldeCongeWidget';
import CongesTab from '@/features/employees/components/tabs/CongesTab';
import congeService from '../api/congeService';

// Mock congeService
vi.mock('../api/congeService', () => ({
  default: {
    getSoldeEmploye: vi.fn(),
    getSituationEmploye: vi.fn(),
    getMouvementsEmploye: vi.fn(),
    getDemandesEmploye: vi.fn(),
    getConsommationPool: vi.fn(),
    getAllReferentiels: vi.fn(),
    annulerDemande: vi.fn(),
    getExamenExpiration: vi.fn(),
    getValorisationLots: vi.fn(),
    corrigerReliquatCutover: vi.fn(),
  }
}));

describe('CongesTab & SoldeCongeWidget - UI / UX enhancements', () => {
  let container, root;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    vi.clearAllMocks();
    congeService.getExamenExpiration.mockResolvedValue({ idEmploye: 1, delaiPrescriptionMois: 36,
      dateReference: '2026-09-28', joursAExaminer: 0, droitsCandidats: [] });
    congeService.getValorisationLots.mockResolvedValue({ lots: [
      { origine: 'REPRISE_CUTOVER', jours: 10, valeur: 90000 }
    ] });
  });

  afterEach(async () => {
    if (root) {
      await act(async () => root.unmount());
    }
    if (container) {
      container.remove();
    }
  });

  it('met le solde et le taux en premier et replie les autres compteurs', async () => {
    const soldeData = { soldeActuel: 14.5, totalAcquis: 25.0, totalPris: 10.5, totalBonification: 0, totalExpire: 0, tauxJournalierConge: 50000 };
    const situationData = {
      soldeJours: 14.5,
      totalAcquis: 25.0,
      totalPris: 10.5,
      tauxApplicable: { montant: 50000, source: 'CALCUL_PERIODE_REFERENCE' },
      periodeReference: { dateDebut: '01/01/2026', dateFin: '31/12/2026', openingComplete: true, modeReprise: false }
    };
    const employe = {
      dateEmbauche: '2024-01-01',
      ancienneteEffectiveMois: 30,
      ancienneteEffectiveTexte: '2 ans et 6 mois'
    };

    await act(async () => {
      root.render(
        <SoldeCongeWidget
          soldeData={soldeData}
          situationData={situationData}
          consommationPool={[{ joursConsommes: 2, plafond: 10, joursEnAttente: 0, joursEngages: 2 }]}
          employe={employe}
        />
      );
    });

    expect(container.textContent).toContain('Solde disponible');
    expect(container.textContent).toContain('+14.50 j');
    expect(container.querySelector('details')?.open).toBe(false);
    expect(container.textContent).toContain('Droits acquis');
    expect(container.textContent).toContain('25 j');
    expect(container.textContent).toContain('Congés pris');
    expect(container.textContent).toContain('Événements familiaux');

    // Le contexte provient du backend, sans calcul d'éligibilité dans React.
    expect(container.textContent).toContain('2 ans et 6 mois');
    expect(container.textContent).toContain('Taux TCONGE applicable');
    expect(container.textContent).toContain('Cycle de référence :');
  });

  it('présente l’ancienneté backend sans seuil local ni dérogation inventée', async () => {
    const soldeData = { soldeActuel: 5.0, totalAcquis: 5.0, totalPris: 0, totalBonification: 0, totalExpire: 0 };
    const employe = {
      dateEmbauche: '2026-06-01',
      ancienneteEffectiveMois: 4,
      ancienneteEffectiveTexte: '4 mois'
    };

    await act(async () => {
      root.render(
        <SoldeCongeWidget
          soldeData={soldeData}
          consommationPool={[]}
          employe={employe}
        />
      );
    });

    expect(container.textContent).toContain('Ancienneté : 4 mois');
    expect(container.textContent).not.toContain('Dérogation requise');
  });

  it('affiche les filtres de statut, le CTA primaire et les demi-journées dans CongesTab', async () => {
    await import('../components/CongeExpirationReviewCard');
    congeService.getSoldeEmploye.mockResolvedValue({ soldeActuel: 10 });
    congeService.getSituationEmploye.mockResolvedValue({ soldeJours: 10 });
    congeService.getMouvementsEmploye.mockResolvedValue([]);
    congeService.getConsommationPool.mockResolvedValue([]);
    congeService.getAllReferentiels.mockResolvedValue({});
    congeService.getDemandesEmploye.mockResolvedValue({
      totalElements: 2,
      content: [
        {
          idDemandeConge: 101,
          codeTypeConge: 'ANNUEL',
          libelleTypeConge: 'Congé Annuel',
          codeStatutDemande: 'VALIDE',
          libelleStatutDemande: 'Validé',
          dateDebut: '2026-04-10',
          dateFin: '2026-04-10',
          nbJoursDecomptes: 0.5,
          estDemiJournee: true,
          momentDemiJournee: 'MATIN',
          availableActions: {}
        },
        {
          idDemandeConge: 102,
          codeTypeConge: 'EXCEPTIONNEL',
          libelleTypeConge: 'Événement Familial',
          codeStatutDemande: 'EN_ATTENTE',
          libelleStatutDemande: 'En attente',
          dateDebut: '2026-05-01',
          dateFin: '2026-05-03',
          nbJoursDecomptes: 2,
          estDemiJournee: false,
          availableActions: { EDIT: { allowed: true } }
        }
      ]
    });

    await act(async () => {
      root.render(
        <MemoryRouter>
          <CongesTab idEmploye={1} employe={{ idEmploye: 1, dateEmbauche: '2024-01-01' }} canWrite={true} canValidateConges={true} />
        </MemoryRouter>
      );
    });

    // CTA primaire présent
    expect(container.textContent).toContain('Poser un congé');
    expect(container.textContent).toContain('Module Congés');

    // Filtres présents
    expect(container.textContent).toContain('Toutes');
    expect(container.textContent).toContain('En attente');
    expect(container.textContent).toContain('Validées');

    // Bouton Export CSV pour les demandes
    expect(container.textContent).toContain('Exporter la page');

    // Demi-journée identifiée
    expect(container.textContent).toContain('Matinée');

    // Bouton détails présent
    expect(container.textContent).toContain('Détails');

    // Le filtre passe au backend avant la pagination ; aucun décompte local de la page.
    const validated = [...container.querySelectorAll('button')].find(button => button.textContent === 'Validées');
    await act(async () => validated.click());
    expect(congeService.getDemandesEmploye).toHaveBeenLastCalledWith(1, { page: 1, size: 10, statut: 'VALIDE' });
    expect(container.textContent).toContain('Autres actions');
    await vi.waitFor(() => expect(container.textContent).toContain('Examen des droits de congé'));
    expect(container.textContent).toContain('Aucun droit à examiner');
  });

  it('affiche les soldes historiques fournis par le backend et l’export CSV du registre', async () => {
    const { default: MouvementsSoldeTable } = await import('../components/MouvementsSoldeTable');

    const fakeMouvements = [
      {
        idMouvement: 3, soldeApres: 10.5,
        typeMouvement: 'PRISE',
        nbJours: -2.0,
        dateMouvement: '2026-03-10',
        commentaire: 'Congé annuel',
        createdBy: 1,
      },
      {
        idMouvement: 2, soldeApres: 12.5,
        typeMouvement: 'ACQUISITION',
        nbJours: 2.5,
        dateMouvement: '2026-02-28',
        commentaire: 'Acquisition mensuelle',
        createdBy: null,
      },
      {
        idMouvement: 1, soldeApres: 10,
        typeMouvement: 'AJUSTEMENT',
        nbJours: 10.0,
        dateMouvement: '2026-01-01',
        commentaire: '[SOLDE_INITIAL] Reprise solde',
        createdBy: 1,
      }
    ];

    // Les soldes de chaque mouvement sont ceux fournis par le backend.
    await act(async () => {
      root.render(
        <MemoryRouter><MouvementsSoldeTable
          idEmploye={1}
          mouvements={fakeMouvements}
          soldeActuel={10.5}
          canWrite={true}
          canValidate={true}
          matricule="EMP-001"
        /></MemoryRouter>
      );
    });

    // En-tête et actions
    expect(container.textContent).toContain('Registre des Mouvements & Crédits de Solde (3)');
    expect(container.textContent).toContain('Export CSV');
    expect(container.textContent).toContain('Ajustement RH');
    expect(container.textContent).toContain('Corriger la reprise');

    expect(container.textContent).toContain('Solde actuel :');

    // Colonne Solde Cumulé
    expect(container.textContent).toContain('Solde Cumulé');
    expect(container.textContent).toContain('+10.50 j'); // après mvt 3
    expect(container.textContent).toContain('+12.50 j'); // après mvt 2
    expect(container.textContent).toContain('+10.00 j'); // après mvt 1
    await act(async () => {
      [...container.querySelectorAll('button')].find(b => b.textContent.includes('Corriger la reprise')).click();
    });
    expect(congeService.getValorisationLots).toHaveBeenCalledWith(1);
    expect(document.body.textContent).toContain('Jours restants après correction');
  });
});
