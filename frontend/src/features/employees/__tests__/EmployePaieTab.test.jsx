// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import EmployePaieTab from '../components/tabs/EmployePaieTab';
import paieService from '@/features/payroll/api/paieService';
import stcService from '@/features/payroll/api/stcService';
vi.mock('@/features/payroll/api/paieService', () => ({ default: { getSynthesePaieEmploye: vi.fn(), listerBulletinsEmploye: vi.fn(), listerAjustements: vi.fn(), listerTypesMouvements: vi.fn(), creerAjustement: vi.fn() } }));
vi.mock('@/features/payroll/api/stcService', () => ({ default: { listerStcEmploye: vi.fn() } }));
vi.mock('@/features/payroll/components/views/BulletinPaieDrawer', () => ({ default: () => null }));
vi.mock('@/features/payroll/components/modals/stc/StcPreparationModal', () => ({ default: () => null }));
vi.mock('@/features/payroll/components/modals/stc/StcRecuModal', () => ({ default: () => null }));
vi.mock('@/features/payroll/hooks/usePayrollCampaign', () => ({ usePayrollCampaign: () => ({
    periodes: [{ idPeriodePaie: 9, libelle: 'Septembre 2026' }], selectedPeriodeId: 9,
    context: { availableActions: { ADD_VARIABLE: { allowed: true } } }, selectPeriode: vi.fn(), loading: false, error: null,
}) }));
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let root, host;
beforeEach(() => {
    host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host);
    paieService.getSynthesePaieEmploye.mockResolvedValue({ salaireBaseContractuel: 370000, dernierBulletinId: 12, dernierePeriodeCode: '2026-09', dernierNet: 310000, dernierBrut: 370000, dernierStatut: 'CALCULE', stcActionRequise: false, totalAvancesEnCours: 0, nbAvancesEnCours: 0 });
    paieService.listerBulletinsEmploye.mockResolvedValue([{ idBulletin: 12, codePeriode: '2026-09', statut: 'CALCULE', netAPayer: 310000 }]);
    paieService.listerAjustements.mockResolvedValue([]); stcService.listerStcEmploye.mockResolvedValue([]);
    paieService.listerTypesMouvements.mockResolvedValue([{ code: 'ACOMPTE_15', libelle: 'Acompte sur salaire', categorie: 'ACOMPTE' }, { code: 'PRIME', libelle: 'Prime', categorie: 'PRIME' }]);
    paieService.creerAjustement.mockResolvedValue({ statut: 'EN_ATTENTE' });
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); vi.clearAllMocks(); });
const render = async () => { await act(async () => root.render(<EmployePaieTab idEmploye={7} canWrite />)); };
describe('Onglet Paie employé', () => {
    it('présente le dernier bulletin sans le déclarer payé et replie les paramètres', async () => {
        await render(); expect(host.textContent).toContain('Net du dernier bulletin'); expect(host.textContent).not.toContain('Net payé');
        expect(host.textContent).toContain('CALCULE'); expect(host.querySelector('#paie-parametres').getAttribute('aria-expanded')).toBe('false');
        expect(host.querySelector('#paie-bulletins').getAttribute('aria-expanded')).toBe('true');
    });
    it('suit la décision STC du backend', async () => {
        paieService.getSynthesePaieEmploye.mockResolvedValue({ stcActionRequise: true, codeStatutContrat: 'ACTIF' });
        await render(); expect(host.textContent).toContain('Solde de Tout Compte à établir');
    });
    it('sépare les soldes d’acomptes et d’avances fournis par le backend', async () => {
        paieService.getSynthesePaieEmploye.mockResolvedValue({ acomptesRestants: 25000, avancesRestantes: 80000, demandesAcompteAvanceEnAttente: 1 });
        await render();
        expect(host.textContent).toContain('Acomptes à imputer');
        expect(host.textContent).toContain('Avances en cours');
        expect(host.textContent).toContain('1 demande(s) en attente d’approbation');
    });
    it('distingue une panne bulletins d’un historique vide', async () => {
        paieService.listerBulletinsEmploye.mockRejectedValue(new Error('Service indisponible'));
        await render(); expect(host.textContent).toContain('Service indisponible'); expect(host.textContent).not.toContain('Aucun bulletin de paie');
    });
    it('crée une demande d’acompte pour le salarié et la période du backend', async () => {
        await render();
        await act(async () => [...host.querySelectorAll('button')].find(item => item.textContent.includes('Demander un acompte')).click());
        expect(document.body.textContent).toContain('en attente d’approbation');
        expect(document.body.textContent).toContain('Septembre 2026');
        expect([...document.body.querySelectorAll('button')].some(item => item.textContent.includes('Rechercher une période'))).toBe(false);
        const selects = [...document.body.querySelectorAll('select')];
        expect(selects.at(-1).textContent).not.toContain('Prime');
        await act(async () => { selects.at(-1).value = 'ACOMPTE_15'; selects.at(-1).dispatchEvent(new Event('change', { bubbles: true })); });
        const amount = document.body.querySelector('input[type="number"]');
        const motif = document.body.querySelector('input[placeholder="Ex. Dépense imprévue à couvrir"]');
        expect(amount.placeholder).toBe('Ex. 50000');
        const setValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
        await act(async () => { setValue.call(amount, '50000'); amount.dispatchEvent(new Event('input', { bubbles: true })); setValue.call(motif, 'Acompte demandé'); motif.dispatchEvent(new Event('input', { bubbles: true })); });
        await act(async () => document.body.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
        expect(paieService.creerAjustement).toHaveBeenCalledWith(expect.objectContaining({ idEmploye: 7, idPeriodePaie: 9, typeMouvement: 'ACOMPTE_15', montant: 50000, motif: 'Acompte demandé' }));
        expect(paieService.creerAjustement.mock.calls[0][0]).not.toHaveProperty('statut');
    });
});
