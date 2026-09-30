// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ValidationModals from '../components/validation/ValidationModals';
import congeService from '../api/congeService';

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

vi.mock('../api/congeService', () => ({ default: {
  getSoldeEmploye: vi.fn(),
  simulerDemande: vi.fn(),
} }));

let host;
let root;

beforeEach(() => {
  host = document.createElement('div');
  document.body.appendChild(host);
  root = createRoot(host);
  vi.clearAllMocks();
  congeService.getSoldeEmploye.mockResolvedValue({ soldeRestant: 4, tauxJournalierConge: 8223.5 });
  congeService.simulerDemande.mockResolvedValue({ joursDebites: 3, soldeApres: 1 });
});

afterEach(async () => {
  await act(async () => root.unmount());
  host.remove();
});

describe('Détail de demande de congé', () => {
  it('présente le commentaire utilisateur et le calcul du solde sans préfixe technique', async () => {
    const demande = {
      idDemandeConge: 1,
      idEmploye: 7,
      prenom: 'Y TNR',
      nom: 'AGENT',
      matricule: '199',
      libelleTypeConge: 'Congé annuel payé',
      codeTypeConge: 'ANNUEL',
      codeStatutDemande: 'VALIDE',
      libelleStatutDemande: 'Validée',
      dateDebut: '2026-08-07',
      dateFin: '2026-08-09',
      dateDemande: '2026-09-29',
      nbJoursDecomptes: 3,
      commentaire: '[REGULARISATION_POINTAGE] Congé lery',
      justificatifFourni: false,
      estPaye: true,
      availableActions: { CANCEL: { allowed: true } },
    };

    await act(async () => {
      root.render(<ValidationModals
        selectedDemande={demande}
        showDetailModal
        onCloseDetail={vi.fn()}
        onOpenAnnuler={vi.fn()}
        showValiderModal={false}
        onCloseValider={vi.fn()}
        onConfirmValider={vi.fn()}
        showRefusModal={false}
        onCloseRefus={vi.fn()}
        onConfirmRefus={vi.fn()}
        motifRefus=""
        setMotifRefus={vi.fn()}
        showAnnulerModal={false}
        onCloseAnnuler={vi.fn()}
        onConfirmAnnuler={vi.fn()}
        motifAnnulation=""
        setMotifAnnulation={vi.fn()}
        actionLoading={false}
        actionError=""
      />);
    });
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });

    expect(host.textContent).toContain('Demande de congé #1');
    expect(host.textContent).toContain('Régularisation de pointage');
    expect(host.textContent).toContain('Congé lery');
    expect(host.textContent).not.toContain('[REGULARISATION_POINTAGE]');
    expect(host.textContent).toContain('Jours décomptés');
    expect(host.textContent).toContain('Vérification du solde');
    expect(host.textContent).toContain('4.00 j');
    expect(host.textContent).toContain('3.00 j');
    expect(host.textContent).toContain('1.00 j');
    expect(host.textContent).toContain('Annuler ce congé');
  });
});
