// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest';
import ContratTravailModal from '../components/carriere/modals/workflow/ContratTravailModal';
import CarriereTimeline from '../components/carriere/CarriereTimeline';
import HistoriqueContratsTable from '../components/carriere/timeline/HistoriqueContratsTable';
import DocumentsRhTab from '../components/tabs/DocumentsRhTab';
import documentService from '@/api/documentService';
import documentRhService from '../api/documentRhService';
import contratService from '../api/contratService';
vi.mock('@/api/documentService', () => ({ default: { getContratData: vi.fn(), downloadContratDocx: vi.fn() } }));
vi.mock('../api/documentRhService', () => ({ default: { getDocumentsByEmploye: vi.fn(), getTypesDocument: vi.fn(), uploadDocument: vi.fn(), downloadDocument: vi.fn(), viewDocument: vi.fn() } }));
vi.mock('@/features/auth/components/HasPermission', () => ({ default: ({ children }) => children }));
vi.mock('@/components/ui/overlays/Modal', () => ({ default: ({ isOpen, children, footer }) => isOpen ? <div>{children}{footer}</div> : null }));
vi.mock('../api/contratService', () => ({ default: { getContratsParEmploye: vi.fn().mockResolvedValue([]), renseignerDateSignature: vi.fn() } }));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let root, host;
const contrat = { idContrat: 42, idEmploye: 7, numeroContrat: 'CTR-2026-0042', codeStatutContrat: 'BROUILLON' };
const prepared = { codeTypeContrat: 'CDD', statut: 'BROUILLON', generationPossible: true, donneesManquantes: [], avertissements: [], variables: { '{{EMPLOYE_NOM_COMPLET}}': 'Rakoto Jean', '{{POSTE_FONCTION}}': 'Poste historique' }, paragraphes: ['Contrat de travail', '[Clause à modifier par la DAF/RH]'] };
const signed = { idDocument: 18, idContrat: 42, contratSigne: true, numeroContrat: 'CTR-2026-0042', codeTypeContrat: 'CDD', nomOriginal: 'signe.pdf', typeDocumentLibelle: 'Contrat de travail' };
const button = text => [...document.querySelectorAll('button')].find(element => element.textContent === text);
const click = async text => { await act(async () => { button(text).click(); }); };
const render = async props => { await act(async () => { root.render(<ContratTravailModal isOpen onClose={vi.fn()} contrat={contrat} {...props} />); }); };
beforeEach(() => {
    vi.clearAllMocks(); host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host);
    documentService.getContratData.mockResolvedValue(prepared);
    documentRhService.getDocumentsByEmploye.mockResolvedValue({ documents: [] });
    documentRhService.getTypesDocument.mockResolvedValue([{ idTypeDocument: 66, libelle: 'Contrat de travail', contratTravail: true, actif: true }]);
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); });
describe('Contrats : génération et archivage', () => {
    it('permet génération et modification au brouillon, puis télécharge le contrat choisi', async () => {
        const edit = vi.fn(); await render({ onEdit: edit });
        expect(button('Télécharger le DOCX').disabled).toBe(false);
        await click('Modifier'); expect(edit).toHaveBeenCalled();
        await click('Télécharger le DOCX'); expect(documentService.downloadContratDocx).toHaveBeenCalledWith(42, 'Contrat_CTR-2026-0042.docx');
        await click('Aperçu du contrat'); expect(document.body.textContent).toContain('[Clause à modifier par la DAF/RH]');
    });
    it('préremplit et verrouille le type, puis transmet employé et contrat sans ressaisie', async () => {
        documentRhService.uploadDocument.mockResolvedValue(signed); await render(); await click('Déposer le contrat signé');
        const select = document.querySelector('select'); expect(select.disabled).toBe(true); expect(select.value).toBe('66');
        const file = new File(['scan'], 'signe.pdf', { type: 'application/pdf' });
        const input = document.querySelector('input[type=file]'); Object.defineProperty(input, 'files', { value: [file] });
        await act(async () => { input.dispatchEvent(new Event('change', { bubbles: true })); });
        await act(async () => { document.querySelector('#upload-doc-form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });
        expect(documentRhService.uploadDocument).toHaveBeenCalledWith(7, file, 66, '', '', { idContrat: 42 });
        expect(document.body.textContent).toContain('1 document déposé');
    });
    it('utilise les mêmes actions et le même archivage pour TEMPORAIRE horaire', async () => {
        documentService.getContratData.mockResolvedValue({ ...prepared, codeTypeContrat: 'TEMPORAIRE', variables: { ...prepared.variables, '{{REMUNERATION_BASE}}': '12 500,50 Ar par heure', '{{REMUNERATION_MODE}}': 'Taux horaire' } });
        documentRhService.getDocumentsByEmploye.mockResolvedValue({ documents: [{ ...signed, codeTypeContrat: 'TEMPORAIRE' }] });
        await render(); expect(document.body.textContent).toContain('TEMPORAIRE'); expect(document.body.textContent).toContain('12 500,50 Ar par heure');
        expect(button('Aperçu du contrat').disabled).toBe(false); expect(button('Télécharger le DOCX').disabled).toBe(false);
        expect(button('Déposer une nouvelle version signée').disabled).toBe(false); expect(document.body.textContent).toContain('1 document déposé');
        await click('Télécharger le DOCX'); expect(documentService.downloadContratDocx).toHaveBeenCalledWith(42, 'Contrat_CTR-2026-0042.docx');
    });
    it('affiche les fichiers signés uniquement pour le contrat demandé et permet consultation', async () => {
        documentRhService.getDocumentsByEmploye.mockResolvedValue({ documents: [signed, { ...signed, idContrat: 99, nomOriginal: 'autre.pdf' }] });
        await render(); expect(document.body.textContent).toContain('signe.pdf'); expect(document.body.textContent).not.toContain('autre.pdf');
        await click('Voir'); expect(documentRhService.viewDocument).toHaveBeenCalledWith(18);
        await click('Télécharger'); expect(documentRhService.downloadDocument).toHaveBeenCalledWith(18, 'signe.pdf');
    });
    it('affiche une erreur backend et permet toujours le dépôt du scan', async () => {
        documentService.getContratData.mockRejectedValue(new Error('CONTRACT_ASSIGNMENT_MISSING'));
        await render(); expect(button('Télécharger le DOCX').disabled).toBe(true); expect(button('Déposer le contrat signé').disabled).toBe(false);
        expect(document.body.textContent).toContain('CONTRACT_ASSIGNMENT_MISSING');
    });
    it('bloque DOCX pour des données manquantes sans bloquer aperçu ou archivage', async () => {
        documentService.getContratData.mockResolvedValue({ ...prepared, generationPossible: false, donneesManquantes: ['{{ENTREPRISE_ADRESSE}}'], statut: 'ACTIF' });
        await render({ onEdit: vi.fn() }); expect(button('Modifier')).toBeUndefined(); expect(button('Télécharger le DOCX').disabled).toBe(true); expect(button('Aperçu du contrat').disabled).toBe(false);
        expect(document.body.textContent).toContain('Adresse de l’entreprise');
    });
    it('avertit des champs à compléter manuellement sans désactiver le canevas DOCX', async () => {
        documentService.getContratData.mockResolvedValue({ ...prepared, avertissements: ['{{SIGNATAIRE_NOM}}'] });
        await render();
        expect(button('Télécharger le DOCX').disabled).toBe(false);
        expect(document.body.textContent).toContain('À compléter ou vérifier dans le DOCX');
    });
    it('permet l’accès direct aux documents du contrat actif', async () => {
        const open=vi.fn();
        await act(async () => root.render(<CarriereTimeline contratActif={{ ...contrat, codeStatutContrat: 'ACTIF', isActif: true }} affectationActive={{ libelleFonction: 'Assistant', libelleSite: 'Agence' }} contratsList={[]} workflowsList={[]} onOpenContrat={open} />));
        expect(document.body.textContent).toContain('Assistant · Agence');
        const sections=[...document.querySelectorAll('details')];
        expect(sections).toHaveLength(2);
        const contractDetails = sections.find(section => section.querySelector("summary")?.textContent.includes("Détails du contrat"));
        expect(contractDetails.open).toBe(true);
        await click('Documents');
        await click('Contrat et signature');
        expect(open).toHaveBeenCalledWith(expect.objectContaining({ idContrat: 42 }));
        const history=sections.find(section => section.querySelector('summary').textContent.includes('Historique'));
        expect(history?.open).toBe(false);
        await act(async () => history.querySelector('summary').click()); expect(history.open).toBe(true);
    });
    it('sépare la transition de la fin de contrat et ferme les menus avec Échap', async () => {
        const transition = vi.fn(), rupture = vi.fn();
        await act(async () => root.render(<CarriereTimeline contratActif={contrat} canWrite onOpenTransition={transition} onOpenRupture={rupture} onOpenContrat={vi.fn()} />));
        await click('Faire évoluer la situation'); expect(transition).toHaveBeenCalledOnce();
        const more=document.querySelector('[aria-label="Autres actions"]');
        await act(async () => more.click());
        expect(button('Mettre fin au contrat')).toBeDefined();
        await act(async () => more.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })));
        expect(button('Mettre fin au contrat')).toBeUndefined(); expect(document.activeElement).toBe(more);
        await act(async () => more.click()); await click('Mettre fin au contrat'); expect(rupture).toHaveBeenCalledOnce();
    });
    it('prépare le brouillon et masque les nouvelles transitions pendant un changement en cours', async () => {
        const open=vi.fn(), draft={ ...contrat, idContrat: 43 };
        await act(async () => root.render(<CarriereTimeline contratActif={contrat} contratsList={[draft]} brouillonWf={{ idContratCible: 43, codeTypeEvenement: 'EMBAUCHE_INITIALE' }} canWrite onOpenContrat={open} />));
        expect(button('Faire évoluer la situation')).toBeUndefined();
        expect(document.querySelector('[aria-label="Autres actions"]')).toBeNull();
        expect(document.querySelector('[aria-label="Changements en cours"]')).not.toBeNull();
        await click('Préparer le contrat'); expect(open).toHaveBeenCalledWith(draft);
    });
    it('masque la préparation du contrat pour un brouillon de rupture', async () => {
        const open=vi.fn(), draft={ ...contrat, idContrat: 43 };
        await act(async () => root.render(<CarriereTimeline contratActif={contrat} contratsList={[draft]} brouillonWf={{ idContratCible: 43, codeTypeEvenement: 'RUPTURE_CONVENTIONNELLE' }} canWrite onOpenContrat={open} />));
        expect(button('Préparer le contrat')).toBeUndefined();
        expect(button('Relecture et validation')).toBeDefined();
        expect(open).not.toHaveBeenCalled();
    });
    it('ne propose STC et certificat que pour le contrat qui clôt la succession', async () => {
        const contratStage = { idContrat: 51, numeroContrat: 'CTR-STAGE', dateDebut: '2020-01-01', dateFin: '2020-03-01', codeStatutContrat: 'CLOTURE', isCloture: true };
        const contratCdi = { idContrat: 52, numeroContrat: 'CTR-CDI', dateDebut: '2020-03-02', dateFin: '2022-01-30', codeStatutContrat: 'ROMPU', isRompu: true };
        const openStc = vi.fn(), openCertificat = vi.fn();
        await act(async () => root.render(<HistoriqueContratsTable
            displayContrats={[contratStage, contratCdi]}
            workflowsList={[{ codeTypeEvenement: 'EMBAUCHE_APRES_STAGE', codeStatutWorkflow: 'HISTORISE', idContratSource: 51, idContratCible: 52 }]}
            onOpenStc={openStc}
            onOpenCertificat={openCertificat}
        />));
        expect(document.body.textContent).toContain('Terminé · transition');
        expect(document.body.textContent).toContain('Contrat remplacé');
        expect([...document.querySelectorAll('button')].filter(element => element.textContent.trim() === 'STC')).toHaveLength(1);
        expect([...document.querySelectorAll('button')].filter(element => element.textContent.trim() === 'Certificat')).toHaveLength(1);
        expect(openStc).not.toHaveBeenCalled();
        expect(openCertificat).not.toHaveBeenCalled();
    });
    it('donne un accès visible à la saisie de la date de signature', async () => {
        const activeContract = { idContrat: 61, numeroContrat: 'CTR-ACTIF', dateDebut: '2026-01-01', codeStatutContrat: 'ACTIF', isActif: true };
        const openContract = vi.fn();
        await act(async () => root.render(<HistoriqueContratsTable
            displayContrats={[activeContract]}
            contratActif={activeContract}
            onOpenContrat={openContract}
        />));
        await click('Renseigner');
        expect(openContract).toHaveBeenCalledWith(activeContract);
    });
    it('enregistre la date de signature saisie et confirme la valeur affichée', async () => {
        const activeContract = { idContrat: 62, idEmploye: 7, numeroContrat: 'CTR-SIGNE', codeStatutContrat: 'ACTIF' };
        contratService.renseignerDateSignature.mockResolvedValue({ dateSignature: '2026-09-30' });
        documentService.getContratData.mockResolvedValue({ ...prepared, statut: 'ACTIF' });
        await render({ contrat: activeContract });
        await click('Renseigner la date de signature');
        const dateInput = document.querySelector('input[type="date"]');
        const setValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
        await act(async () => {
            setValue.call(dateInput, '2026-09-30');
            dateInput.dispatchEvent(new Event('input', { bubbles: true }));
            dateInput.dispatchEvent(new Event('change', { bubbles: true }));
        });
        await click('Enregistrer');
        expect(contratService.renseignerDateSignature).toHaveBeenCalledWith(7, 62, '2026-09-30');
        expect(document.body.textContent).toContain('30/09/2026');
    });
    it('n’expose pas les actions de modification en lecture seule et ferme au clic extérieur', async () => {
        await act(async () => root.render(<CarriereTimeline contratActif={contrat} canWrite={false} onOpenContrat={vi.fn()} />));
        expect(button('Faire évoluer la situation')).toBeUndefined();
        expect(document.querySelector('[aria-label="Autres actions"]')).toBeNull();
        await click('Documents');
        await act(async () => document.body.dispatchEvent(new Event('pointerdown', { bubbles: true })));
        expect(button('Documents').getAttribute('aria-expanded')).toBe('false');
    });
    it('affiche la référence, le type et la signature dans Documents RH', async () => {
        await act(async () => { root.render(<DocumentsRhTab documentsData={{ documents: [signed], obligatoiresManquants: [] }} />); });
        expect(document.body.textContent).toContain('CTR-2026-0042'); expect(document.body.textContent).toContain('CDD'); expect(document.body.textContent).toContain('Signé ✓');
    });
});
