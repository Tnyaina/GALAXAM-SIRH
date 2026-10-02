// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import GestionCongesPage from '../pages/GestionCongesPage';
import ValidationTable from '../components/validation/ValidationTable';
import ValidationFilters from '../components/validation/ValidationFilters';
import congeService from '../api/congeService';

vi.mock('../api/congeService', () => ({ default: {
  getAllDemandes: vi.fn(),
  getAllReferentiels: vi.fn(),
  telechargerGrandRecapCongesExcel: vi.fn(),
} }));
vi.mock('@/components/system/SEO', () => ({ default: () => null }));
vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ hasPermission: () => true }) }));
vi.mock('../components/SoldesOverviewTab', () => ({ default: () => <div>Planning chargé</div> }));
vi.mock('../components/DemandesValidationTab', () => ({ default: () => <div>Demandes chargées</div> }));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let host;
let root;

beforeEach(() => {
  host = document.createElement('div');
  document.body.appendChild(host);
  root = createRoot(host);
  vi.clearAllMocks();
});

afterEach(async () => {
  await act(async () => root.unmount());
  host.remove();
});

describe('Supervision des congés', () => {
  it('télécharge le grand récapitulatif à la date choisie', async () => {
    congeService.getAllDemandes.mockResolvedValue({ totalElements: 0, content: [] });
    congeService.getAllReferentiels.mockResolvedValue({});
    congeService.telechargerGrandRecapCongesExcel.mockResolvedValue();
    await act(async () => root.render(<MemoryRouter><GestionCongesPage /></MemoryRouter>));
    await act(async () => [...host.querySelectorAll('button')]
      .find(button => button.textContent.includes('Grand récapitulatif Excel')).click());
    await act(async () => {
    const input = host.querySelectorAll('input[type="date"]')[1];
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
      setter.call(input, '2026-09-01');
      input.dispatchEvent(new Event('input', { bubbles: true }));
      input.dispatchEvent(new Event('change', { bubbles: true }));
    });
    await act(async () => [...host.querySelectorAll('button')]
      .find(button => button.textContent.includes('Télécharger Excel')).click());
    expect(congeService.telechargerGrandRecapCongesExcel).toHaveBeenCalledWith('2026-01-01', '2026-09-01');
  });

  it('nomme le planning et prend le nombre de demandes en attente dans le total backend', async () => {
    congeService.getAllDemandes.mockResolvedValue({ totalElements: 12, content: [{ idDemandeConge: 1 }] });
    congeService.getAllReferentiels.mockResolvedValue({ statutsDemande: [] });
    await act(async () => root.render(<MemoryRouter initialEntries={['/conges?tab=soldes']}><GestionCongesPage /></MemoryRouter>));
    expect(congeService.getAllDemandes).toHaveBeenCalledWith({ statut: 'EN_ATTENTE', page: 1, size: 1 });
    expect(host.textContent).toContain('Planning des absences');
    expect(host.textContent).toContain('12');
    for (let attempt = 0; attempt < 20 && !host.textContent.includes('Planning chargé'); attempt += 1) {
      await act(async () => { await new Promise(resolve => setTimeout(resolve, 10)); });
    }
    expect(host.textContent).toContain('Planning chargé');
  });

  it('présente la durée backend et guide vers un seul examen de la demande', async () => {
    const onOpenDetail = vi.fn();
    const demande = {
      idDemandeConge: 42, idEmploye: 7, prenom: 'Jeanne', nom: 'Rakoto', matricule: 'M007',
      libelleTypeConge: 'Congé annuel', codeTypeConge: 'ANNUEL', codeStatutDemande: 'EN_ATTENTE',
      dateDebut: '2026-10-05', dateFin: '2026-10-05', nbJoursDecomptes: 0.5,
      estDemiJournee: true, momentDemiJournee: 'MATIN', estPaye: true,
      availableActions: { APPROVE: { allowed: true }, REJECT: { allowed: true } }
    };
    await act(async () => root.render(<MemoryRouter><ValidationTable
      demandes={[demande]} activeStatut="EN_ATTENTE" displayTotal={1} searchTerm=""
      currentPage={1} pageSize={10} setCurrentPage={vi.fn()} setPageSize={vi.fn()}
      canValidate onOpenDetail={onOpenDetail} /></MemoryRouter>));
    expect(host.textContent).toContain('0.5 jour décompté');
    expect(host.textContent).not.toContain('j ouvrés');
    expect(host.textContent).toContain('Examiner');
    expect(host.textContent).not.toContain('Refuser');
    await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent.includes('Examiner')).click());
    expect(onOpenDetail).toHaveBeenCalledWith(demande);
  });

  it('affiche les statuts et types transmis par les référentiels', async () => {
    await act(async () => root.render(<ValidationFilters
      activeStatut="EN_ATTENTE" setActiveStatut={vi.fn()}
      selectedTypeConge="" setSelectedTypeConge={vi.fn()}
      referentiels={{
        statutsDemande: [{ code: 'EN_ATTENTE', libelle: 'À décider' }, { code: 'VALIDE', libelle: 'Approuvée' }],
        typesConge: [{ idTypeConge: 9, libelleTypeConge: 'Congé spécial' }]
      }}
      displayTotal={4} searchRaw="" setSearchRaw={vi.fn()} loading={false}
      onRefresh={vi.fn()} onNewDemande={vi.fn()} />));
    expect(host.textContent).toContain('À décider');
    expect(host.textContent).toContain('Approuvée');
    const trigger = [...host.querySelectorAll('button')].find(b => b.textContent.includes("Tous les types d'absence"));
    expect(trigger).toBeTruthy();
    await act(async () => trigger.click());
    expect(host.textContent).toContain('Congé spécial');
  });
});
