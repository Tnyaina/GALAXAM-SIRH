// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import StcPreparationModal from '../components/modals/stc/StcPreparationModal';
import stcService from '../api/stcService';
import contratService from '@/features/employees/api/contratService';

vi.mock('../api/stcService', () => ({ default: {
    preparerStc: vi.fn(), simulerStc: vi.fn(), validerStc: vi.fn(), enregistrerReglement: vi.fn(),
} }));
vi.mock('@/features/employees/api/contratService', () => ({ default: { getMotifsSortie: vi.fn() } }));
vi.mock('@/components/ui/overlays/Modal', () => ({ default: ({ isOpen, children, footer }) => isOpen ? <div>{children}{footer}</div> : null }));
vi.mock('@/components/ui/overlays/ConfirmDialog', () => ({ default: ({ isOpen, onConfirm }) => isOpen ? <button onClick={onConfirm}>Confirmer la validation</button> : null }));
vi.mock('../components/modals/stc/StcRecuModal', () => ({ default: () => null }));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let host, root;
const prep = {
    idEmploye: 7, nomComplet: 'Rakoto Jean', matricule: 'E7', dateDepart: '2026-10-10',
    motifRupture: 'DEMISSION', joursTravaillesEstimes: 10, moisPreavisSuggere: 0,
    salaireBase: 300000, tauxHoraire: 1730, soldeCongesRestant: 0,
};
const result = { idStc: 15, numeroRecu: 'STC-2026-0015', statut: 'SIMULATION',
    salaireProrata: 100000, netAPayerStc: 100000, totalBrutStc: 100000 };
const click = async label => act(async () => {
    const button = [...host.querySelectorAll('button')].find(item => item.textContent.includes(label));
    expect(button).toBeDefined();
    button.click();
});

beforeEach(() => {
    host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host);
    contratService.getMotifsSortie.mockResolvedValue([{ code: 'DEMISSION', libelle: 'Démission', actif: true }]);
    stcService.preparerStc.mockResolvedValue(prep);
    stcService.simulerStc.mockResolvedValue(result);
    stcService.validerStc.mockResolvedValue({ ...result, statut: 'VALIDE' });
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); vi.clearAllMocks(); });

describe('Parcours STC', () => {
    it('rapproche la dernière paie sans ressaisir les jours de salaire', async () => {
        stcService.preparerStc.mockResolvedValue({ ...prep, idBulletinMensuel: 12,
            statutBulletinMensuel: 'VALIDE', netBulletinMensuel: 300000 });
        await act(async () => root.render(<StcPreparationModal isOpen idEmploye={7}
            dateDepartInitiale="2026-10-10" onClose={vi.fn()} />));
        expect(host.textContent).toContain('Rapprochement de la dernière paie');
        expect(host.textContent).not.toContain('Jours sous contrat à payer');
        await click('Simuler le STC');
        expect(stcService.simulerStc).toHaveBeenCalledWith(expect.objectContaining({
            montantBulletinDejaPaye: 0,
        }));
    });
    it('termine la simulation sans proposer la validation', async () => {
        await act(async () => root.render(<StcPreparationModal isOpen idEmploye={7} simulationOnly
            dateDepartInitiale="2026-10-10" onClose={vi.fn()} />));
        await click('Calculer la simulation');
        expect(stcService.simulerStc).toHaveBeenCalledOnce();
        expect(stcService.validerStc).not.toHaveBeenCalled();
        expect(host.textContent).toContain('rien n’est enregistré');
        expect(host.textContent).not.toContain('Valider le calcul et le bulletin');
    });

    it('guide application, validation puis règlement séparé', async () => {
        stcService.preparerStc.mockResolvedValueOnce({ ...prep, controlesApplication: [
            'Pointage final non gelé : vérifier les absences, heures et indemnités avant application.',
        ] });
        await act(async () => root.render(<StcPreparationModal isOpen idEmploye={7}
            dateDepartInitiale="2026-10-10" onClose={vi.fn()} />));
        expect(host.textContent).toContain('Pointage final non gelé');
        await click('Simuler le STC');
        expect(stcService.validerStc).not.toHaveBeenCalled();
        await click('Valider le calcul et le bulletin');
        await click('Confirmer la validation');
        expect(stcService.validerStc).toHaveBeenCalledOnce();
        expect(stcService.enregistrerReglement).not.toHaveBeenCalled();
        expect(host.textContent).toContain('Constater le paiement réellement effectué');
    });
});
