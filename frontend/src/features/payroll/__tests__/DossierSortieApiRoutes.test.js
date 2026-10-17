// @vitest-environment jsdom
import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import dossierSortieService from '../api/dossierSortieService';
import apiClient from '@/api/apiClient';

vi.mock('@/api/apiClient', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
let click;
beforeEach(() => {
    apiClient.get.mockResolvedValue({ data: new Blob(['document'], { type: 'application/pdf' }) });
    URL.createObjectURL = vi.fn(() => 'blob:test');
    URL.revokeObjectURL = vi.fn();
    click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
});
afterEach(() => { click.mockRestore(); vi.clearAllMocks(); });

it('utilise les six routes documentaires du dossier sous le chemin API canonique', async () => {
    const cases = [
        ['decompte', 'decompte/pdf'], ['recuProjet', 'recu-stc/pdf'],
        ['certificatPdf', 'certificat/pdf'], ['certificatDocx', 'certificat/docx'],
        ['bordereau', 'bordereau-remise/pdf'], ['quittance', 'quittance/8/pdf', 8],
    ];
    for (const [kind, path, paymentId] of cases) {
        await dossierSortieService.telechargerDocument(12, kind, paymentId);
        expect(apiClient.get).toHaveBeenLastCalledWith(`paie/dossier-sortie/12/documents/${path}`, { responseType: 'blob' });
    }
    expect(click).toHaveBeenCalledTimes(6);
    expect(apiClient.post).not.toHaveBeenCalled();
});

it('utilise les routes de règlement, annulation et clôture du dossier', async () => {
    apiClient.post.mockResolvedValue({ data: { idDossierSortie: 12 } });
    await dossierSortieService.enregistrerReglement(12, { montantRegle: 200 }, 'idem-12');
    expect(apiClient.post).toHaveBeenCalledWith('paie/dossier-sortie/12/reglement',
        { montantRegle: 200 }, { headers: { 'X-Idempotency-Key': 'idem-12' } });
    await dossierSortieService.annulerReglement(12, 8, 'Erreur bancaire');
    expect(apiClient.post).toHaveBeenCalledWith('paie/dossier-sortie/12/reglement/8/annuler',
        { motifAnnulation: 'Erreur bancaire' });
    await dossierSortieService.cloturerDossier(12, { confirmationFormalites: true });
    expect(apiClient.post).toHaveBeenCalledWith('paie/dossier-sortie/12/cloturer', { confirmationFormalites: true });
});

it('recherche les salariés du dossier avec la permission paie et une réponse limitée', async () => {
    apiClient.get.mockResolvedValue({ data: [{ idEmploye: 7, nom: 'Rakoto', prenom: 'Jean', matricule: 'E7' }] });
    const employes = await dossierSortieService.rechercherEmployes('Rakoto');
    expect(apiClient.get).toHaveBeenCalledWith('paie/dossier-sortie/employes', { params: { q: 'Rakoto' } });
    expect(employes).toHaveLength(1);
});
