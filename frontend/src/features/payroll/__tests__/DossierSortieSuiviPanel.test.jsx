// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import DossierSortieSuiviPanel from '../components/modals/stc/DossierSortieSuiviPanel';
import dossierSortieService from '../api/dossierSortieService';

const permission = vi.hoisted(() => ({ canWrite: true }));
vi.mock('../api/dossierSortieService', () => ({ default: {
    obtenirSituationReglements: vi.fn(), enregistrerReglement: vi.fn(), obtenirDossier: vi.fn(),
    cloturerDossier: vi.fn(), annulerReglement: vi.fn(), telechargerDocument: vi.fn(),
} }));
vi.mock('@/features/auth/components/HasPermission', () => ({ default: ({ children }) => permission.canWrite ? <>{children}</> : null }));
vi.mock('@/components/ui/overlays/ConfirmDialog', () => ({ default: ({ isOpen, onConfirm, title, children }) => isOpen ?
    <div><strong>{title}</strong>{children}<button onClick={onConfirm}>Confirmer l’action</button></div> : null }));
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

const dossier = {
    idDossierSortie: 12, idStc: 4, statutDossier: 'VALIDE', pretAValider: true,
    dateDepart: '2026-10-01', dateFinContrat: '2026-10-01', statutContrat: 'ROMPU', netAPayerStc: 1000,
};
const situation = {
    netAPayerStc: 1000, totalRegle: 300, resteAPayer: 700, estIntegralementRegle: false,
    reglements: [{ idReglement: 8, dateReglement: '2026-10-02', montantRegle: 300,
        referenceReglement: 'VIR-1', modeReglement: 'VIREMENT', estAnnule: true }],
};
let host, root;
const setInput = async (input, value) => {
    if (!input) return;
    const proto = input.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype
        : input.tagName === 'SELECT' ? HTMLSelectElement.prototype
        : HTMLInputElement.prototype;
    const setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
    await act(async () => {
        setter.call(input, value);
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.dispatchEvent(new Event('change', { bubbles: true }));
    });
};
beforeEach(() => {
    sessionStorage.clear();
    permission.canWrite = true;
    host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host);
    dossierSortieService.obtenirSituationReglements.mockResolvedValue(situation);
    dossierSortieService.telechargerDocument.mockResolvedValue();
    dossierSortieService.enregistrerReglement.mockResolvedValue({ ...situation, totalRegle: 500, resteAPayer: 500 });
    dossierSortieService.obtenirDossier.mockResolvedValue(dossier);
    dossierSortieService.cloturerDossier.mockResolvedValue({ ...dossier, statutDossier: 'CLOTURE' });
    dossierSortieService.annulerReglement.mockResolvedValue(situation);
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); vi.clearAllMocks(); });

describe('suivi RH du dossier de sortie', () => {
    it('sépare les états, affiche les règlements annulés et les six documents sans mutation', async () => {
        await act(async () => root.render(<DossierSortieSuiviPanel dossier={dossier} />));
        expect(host.textContent).toContain('ANNULÉ');
        expect(host.textContent).toContain('Reçu STC — projet');
        expect(host.textContent).toContain('Certificat DOCX');
        expect(host.textContent).toContain('700');
        for (const [label, kind, reglementId] of [
            ['Décompte STC scellé', 'decompte'], ['Reçu STC — projet', 'recuProjet'],
            ['Certificat DOCX', 'certificatDocx'],
            ['Justificatif PDF', 'quittance', 8],
        ]) {
            const button = [...host.querySelectorAll('button')].find(item => item.textContent === label);
            await act(async () => button.click());
            expect(dossierSortieService.telechargerDocument).toHaveBeenCalledWith(12, kind, reglementId);
        }
        expect(dossierSortieService.enregistrerReglement).not.toHaveBeenCalled();
        expect(dossierSortieService.cloturerDossier).not.toHaveBeenCalled();
    });

    it('ne propose pas le paiement sur un dossier clôturé sans dérogation', async () => {
        await act(async () => root.render(<DossierSortieSuiviPanel dossier={{ ...dossier, statutDossier: 'CLOTURE', derogationReglement: false }} />));
        expect(host.textContent).not.toContain('Enregistrer ce règlement');
        expect(host.textContent).not.toContain('Clôturer le dossier');
    });

    it('permet un règlement après clôture dérogatoire et garde la dette visible', async () => {
        await act(async () => root.render(<DossierSortieSuiviPanel dossier={{ ...dossier, statutDossier: 'CLOTURE', derogationReglement: true,
            justificationDerogation: 'Échéancier signé', autoriteDecisionDerogation: 'DAF', referenceDecisionDerogation: 'DEC-1' }} />));
        expect(host.textContent).toContain('Échéancier signé');
        expect(host.textContent).toContain('Le solde restant reste dû');
        expect(host.textContent).toContain('Vérifier puis enregistrer');
        expect(host.textContent).not.toContain('Clôturer le dossier');
        expect(host.textContent).not.toContain('Annuler ce règlement');
    });

    it('masque les mutations sans PAIE_WRITE', async () => {
        permission.canWrite = false;
        await act(async () => root.render(<DossierSortieSuiviPanel dossier={dossier} />));
        expect(host.textContent).not.toContain('Enregistrer ce règlement');
        expect(host.textContent).not.toContain('Clôturer le dossier');
        expect(host.textContent).toContain('Décompte STC scellé');
    });

    it('présente une clôture normale lorsque le STC est intégralement réglé', async () => {
        dossierSortieService.obtenirSituationReglements.mockResolvedValue({ ...situation, totalRegle: 1000, resteAPayer: 0,
            estIntegralementRegle: true });
        await act(async () => root.render(<DossierSortieSuiviPanel dossier={dossier} />));
        expect(host.textContent).not.toContain('Dérogation formelle');
        expect(host.textContent).not.toContain('Enregistrer ce règlement');
        expect(host.textContent).toContain('Vérifier puis clôturer');
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Vérifier puis clôturer').click());
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Confirmer l’action').click());
        expect(dossierSortieService.cloturerDossier).toHaveBeenCalledWith(12,
            expect.objectContaining({ derogationReglementIncomplet: false, confirmationFormalites: true }));
    });

    it('propose une attestation explicite lorsque le net scellé est nul', async () => {
        dossierSortieService.obtenirSituationReglements.mockResolvedValue({ ...situation,
            netAPayerStc: 0, totalRegle: 0, resteAPayer: 0, estIntegralementRegle: false });
        await act(async () => root.render(<DossierSortieSuiviPanel dossier={{ ...dossier, netAPayerStc: 0 }} />));
        expect(host.textContent).toContain('Attester le STC à solde nul');
        expect(host.querySelector('[aria-label="Montant du règlement"]').value).toBe('0');
    });

    it('enregistre un paiement partiel seulement après confirmation avec clé d’idempotence', async () => {
        await act(async () => root.render(<DossierSortieSuiviPanel dossier={dossier} />));
        await setInput(host.querySelector('[aria-label="Référence du règlement"]'), 'VIR-2');
        await setInput(host.querySelector('[aria-label="Montant du règlement"]'), '200');
        await act(async () => host.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
        expect(dossierSortieService.enregistrerReglement).not.toHaveBeenCalled();
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Confirmer l’action').click());
        expect(dossierSortieService.enregistrerReglement).toHaveBeenCalledWith(12,
            expect.objectContaining({ referenceReglement: 'VIR-2', montantRegle: '200' }), expect.any(String));
    });

    it('conserve le solde du serveur lorsqu’un règlement est refusé', async () => {
        dossierSortieService.enregistrerReglement.mockRejectedValue(new Error('Règlement refusé'));
        await act(async () => root.render(<DossierSortieSuiviPanel dossier={dossier} />));
        await setInput(host.querySelector('[aria-label="Référence du règlement"]'), 'VIR-REFUS');
        await setInput(host.querySelector('[aria-label="Montant du règlement"]'), '100');
        await act(async () => host.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Confirmer l’action').click());
        expect(host.querySelector('[role="alert"]')).not.toBeNull();
        expect(host.textContent).toContain('700');
        expect(dossierSortieService.obtenirDossier).not.toHaveBeenCalled();
    });

    it('réutilise la même clé après une réponse réseau perdue et un rafraîchissement de page', async () => {
        dossierSortieService.enregistrerReglement.mockRejectedValueOnce(new Error('Réseau indisponible'));
        await act(async () => root.render(<DossierSortieSuiviPanel dossier={dossier} />));
        await setInput(host.querySelector('[aria-label="Référence du règlement"]'), 'VIR-REPRISE');
        await setInput(host.querySelector('[aria-label="Montant du règlement"]'), '200');
        await act(async () => host.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Confirmer l’action').click());
        const firstKey = dossierSortieService.enregistrerReglement.mock.calls[0][2];
        expect(sessionStorage.getItem('galaxam:stc:reglement:12')).toContain(firstKey);
        await act(async () => root.unmount());
        root = createRoot(host);
        await act(async () => root.render(<DossierSortieSuiviPanel dossier={dossier} />));
        await setInput(host.querySelector('[aria-label="Référence du règlement"]'), 'VIR-REPRISE');
        await setInput(host.querySelector('[aria-label="Montant du règlement"]'), '200');
        await act(async () => host.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Confirmer l’action').click());
        expect(dossierSortieService.enregistrerReglement.mock.calls[1][2]).toBe(firstKey);
        expect(sessionStorage.getItem('galaxam:stc:reglement:12')).toBeNull();
    });

    it('empêche une double confirmation pendant une requête de paiement en cours', async () => {
        let resolvePayment;
        dossierSortieService.enregistrerReglement.mockImplementation(() => new Promise(resolve => { resolvePayment = resolve; }));
        await act(async () => root.render(<DossierSortieSuiviPanel dossier={dossier} />));
        await setInput(host.querySelector('[aria-label="Référence du règlement"]'), 'VIR-DOUBLE');
        await setInput(host.querySelector('[aria-label="Montant du règlement"]'), '100');
        await act(async () => host.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
        await act(async () => {
            const confirm = [...host.querySelectorAll('button')].find(button => button.textContent === 'Confirmer l’action');
            confirm.click();
            confirm.click();
        });
        expect(dossierSortieService.enregistrerReglement).toHaveBeenCalledTimes(1);
        await act(async () => resolvePayment({ ...situation, totalRegle: 400, resteAPayer: 600 }));
    });

    it('emploie une nouvelle clé quand la charge utile du règlement change', async () => {
        dossierSortieService.enregistrerReglement.mockRejectedValue(new Error('Réseau indisponible'));
        await act(async () => root.render(<DossierSortieSuiviPanel dossier={dossier} />));
        await setInput(host.querySelector('[aria-label="Référence du règlement"]'), 'VIR-CHANGE');
        await setInput(host.querySelector('[aria-label="Montant du règlement"]'), '100');
        const submit = async () => {
            await act(async () => host.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
            await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Confirmer l’action').click());
        };
        await submit();
        const firstKey = dossierSortieService.enregistrerReglement.mock.calls[0][2];
        await setInput(host.querySelector('[aria-label="Montant du règlement"]'), '150');
        await submit();
        expect(dossierSortieService.enregistrerReglement.mock.calls[1][2]).not.toBe(firstKey);
    });

    it('annonce un paiement confirmé même si la lecture du dossier échoue ensuite', async () => {
        dossierSortieService.obtenirDossier.mockRejectedValueOnce(new Error('Réseau indisponible'));
        await act(async () => root.render(<DossierSortieSuiviPanel dossier={dossier} onDossierChange={vi.fn()} />));
        await setInput(host.querySelector('[aria-label="Référence du règlement"]'), 'VIR-CONFIRME');
        await setInput(host.querySelector('[aria-label="Montant du règlement"]'), '200');
        await act(async () => host.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Confirmer l’action').click());
        expect(host.querySelector('[role="alert"]').textContent).toContain('Règlement enregistré');
        expect(host.textContent).toContain('500');
        expect(host.querySelector('[aria-label="Référence du règlement"]').value).toBe('');
        expect(dossierSortieService.enregistrerReglement).toHaveBeenCalledTimes(1);
    });

    it('trace l’annulation motivée d’une écriture active', async () => {
        dossierSortieService.obtenirSituationReglements.mockResolvedValue({ ...situation,
            reglements: [{ ...situation.reglements[0], estAnnule: false }] });
        await act(async () => root.render(<DossierSortieSuiviPanel dossier={dossier} />));
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Annuler ce règlement').click());
        expect(dossierSortieService.annulerReglement).not.toHaveBeenCalled();
        await setInput(host.querySelector('[aria-label="Motif de l’annulation"]'), 'Erreur bancaire');
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Confirmer l’action').click());
        expect(dossierSortieService.annulerReglement).toHaveBeenCalledWith(12, 8, 'Erreur bancaire');
    });

    it('demande une décision complète avant une clôture dérogatoire', async () => {
        await act(async () => root.render(<DossierSortieSuiviPanel dossier={dossier} />));
        const check = host.querySelector('input[type="checkbox"]');
        await act(async () => check.click());
        await setInput(host.querySelector('[aria-label="Justification de la dérogation"]'), 'Échéancier formel');
        await setInput(host.querySelector('[aria-label="Autorité décisionnaire"]'), 'DAF');
        await setInput(host.querySelector('[aria-label="Référence de la décision"]'), 'DEC-2026-1');
        await setInput(host.querySelector('[aria-label="Date de décision"]'), '2026-10-01');
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Vérifier puis clôturer').click());
        expect(dossierSortieService.cloturerDossier).not.toHaveBeenCalled();
        await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Confirmer l’action').click());
        expect(dossierSortieService.cloturerDossier).toHaveBeenCalledWith(12,
            expect.objectContaining({ derogationReglementIncomplet: true, autoriteDecisionDerogation: 'DAF',
                referenceDecisionDerogation: 'DEC-2026-1', confirmationFormalites: true }));
    });

    it('affiche clairement les erreurs métier sans modifier localement les montants', async () => {
        dossierSortieService.obtenirSituationReglements.mockRejectedValue(new Error('Situation indisponible'));
        await act(async () => root.render(<DossierSortieSuiviPanel dossier={dossier} />));
        expect(host.querySelector('[role="alert"]')).not.toBeNull();
        expect(dossierSortieService.enregistrerReglement).not.toHaveBeenCalled();
    });
});
