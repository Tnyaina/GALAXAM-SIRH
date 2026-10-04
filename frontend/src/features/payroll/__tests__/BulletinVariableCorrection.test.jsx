// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import BulletinVariableCorrection from '../components/views/BulletinVariableCorrection';
import paieService from '../api/paieService';

vi.mock('../api/paieService', () => ({ default: {
    listerAjustements: vi.fn(),
    listerTypesMouvements: vi.fn(),
    getWorkflowContext: vi.fn(),
    getBulletinCorrectionStatus: vi.fn(),
    executerCommandeAjustement: vi.fn(),
    calculerBulletinEmploye: vi.fn(),
} }));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let host;
let root;

beforeEach(() => {
    host = document.createElement('div');
    document.body.appendChild(host);
    root = createRoot(host);
});

afterEach(() => {
    act(() => root.unmount());
    host.remove();
    vi.clearAllMocks();
});

describe('Correction depuis le bulletin', () => {
    it('masque les variables annulées par défaut et permet de consulter leur historique', async () => {
        paieService.listerAjustements.mockResolvedValue([{
            idAvance: 11, nature: 'VARIABLE_DAF', provenance: 'VARIABLE_DAF',
            typeLibelle: 'Prime de performance', typeMouvement: 'PRIME_PERFORMANCE',
            sens: 'GAIN', montant: 100000, statut: 'ANNULE', version: 2,
        }]);
        paieService.listerTypesMouvements.mockResolvedValue([]);
        paieService.getWorkflowContext.mockResolvedValue({ availableActions: { ADD_VARIABLE: { allowed: true } } });
        paieService.getBulletinCorrectionStatus.mockResolvedValue({ pendingCount: 0, recalculationRequired: false });

        await act(async () => root.render(<BulletinVariableCorrection bulletin={{
            idBulletin: 3, idPeriodePaie: 1, idEmploye: 2,
        }} />));

        expect(host.textContent).not.toContain('Prime de performance');
        const historyButton = [...host.querySelectorAll('button')].find(button => button.textContent.includes('Afficher les annulées'));
        expect(historyButton).toBeDefined();
        await act(async () => historyButton.click());
        expect(host.textContent).toContain('Prime de performance');
    });

    it('affiche la retenue nette manuelle imputée et permet de demander son annulation', async () => {
        paieService.listerAjustements.mockResolvedValue([{
            idAvance: 12, idBulletin: 3, nature: 'RETENUE_NETTE', provenance: 'VARIABLE_DAF',
            typeLibelle: 'Retenue téléphonie personnelle', typeMouvement: 'CALL_PERSO',
            sens: 'RETENUE', montant: 1010.5, statut: 'IMPUTE_PAIE', version: 2,
        }]);
        paieService.listerTypesMouvements.mockResolvedValue([{
            code: 'CALL_PERSO', libelle: 'Retenue téléphonie personnelle',
            nature: 'RETENUE_NETTE', sens: 'RETENUE',
        }]);
        paieService.getWorkflowContext.mockResolvedValue({ availableActions: {
            ADD_VARIABLE: { allowed: true }, CALCULATE: { allowed: true },
        } });
        paieService.getBulletinCorrectionStatus.mockResolvedValue({ pendingCount: 0, recalculationRequired: false });

        await act(async () => root.render(<BulletinVariableCorrection bulletin={{
            idBulletin: 3, idPeriodePaie: 1, idEmploye: 2,
        }} />));

        expect(host.textContent).toContain('Retenue téléphonie personnelle');
        expect(host.textContent.replace(/\s/g, ' ')).toContain('1 010,5 Ar');
        expect([...host.querySelectorAll('button')].some(button => button.textContent.includes('Annuler'))).toBe(true);
    });

    it('attend l’approbation puis une simulation avant le recalcul individuel', async () => {
        let status = 'EN_ATTENTE';
        paieService.listerAjustements.mockImplementation(async () => [{
            idAvance: 9, idBulletin: null, nature: 'VARIABLE_DAF', provenance: 'VARIABLE_DAF', typeLibelle: 'Prime exceptionnelle',
            typeMouvement: 'PRIME_EXCEPTIONNELLE', sens: 'GAIN', montant: 10000,
            motif: 'Prime justifiée', statut: status, version: 1, updatedAt: '2026-10-01T08:00:00Z',
        }]);
        paieService.listerTypesMouvements.mockResolvedValue([{ code: 'PRIME_EXCEPTIONNELLE', libelle: 'Prime exceptionnelle', sens: 'GAIN' }]);
        paieService.getWorkflowContext.mockImplementation(async () => ({ availableActions: {
            ADD_VARIABLE: { allowed: true }, CALCULATE: { allowed: status === 'APPROUVE' },
        } }));
        paieService.getBulletinCorrectionStatus.mockImplementation(async () => ({
            pendingCount: status === 'EN_ATTENTE' ? 1 : 0,
            recalculationRequired: true,
        }));
        paieService.executerCommandeAjustement.mockImplementation(async () => { status = 'APPROUVE'; });
        paieService.calculerBulletinEmploye.mockResolvedValue({ bulletin: {
            totalGainsBruts: 110000, totalCotisationsSalariales: 1000, irsaNet: 5000, netAPayer: 104000,
        } });

        await act(async () => root.render(<BulletinVariableCorrection bulletin={{
            idBulletin: 3, idPeriodePaie: 1, idEmploye: 2, updatedAt: '2026-10-01T07:00:00Z',
            totalGainsBruts: 100000, totalCotisationsSalariales: 1000, irsaNet: 5000, netAPayer: 94000,
        }} />));

        const button = label => [...host.querySelectorAll('button')].find(item => item.textContent.includes(label));
        expect(button('Recalculer').disabled).toBe(true);
        await act(async () => button('Approuver').click());
        expect(button('Recalculer').disabled).toBe(true);
        await act(async () => button('Simuler').click());
        expect(paieService.calculerBulletinEmploye).toHaveBeenCalledWith(2, 1, true);
        expect(button('Recalculer').disabled).toBe(false);
        expect(host.textContent.replace(/\s/g, ' ')).toContain('104 000 Ar');
        await act(async () => button('Recalculer').click());
        expect(paieService.calculerBulletinEmploye).toHaveBeenCalledWith(2, 1, false);
    });
});
