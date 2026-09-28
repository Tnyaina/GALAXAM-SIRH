// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import apiClient from '@/api/apiClient';
import documentService from '@/api/documentService';
import documentRhService from '../api/documentRhService';
vi.mock('@/api/apiClient', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
beforeEach(() => vi.clearAllMocks());
describe('Clients documentaires contrat', () => {
    it('transmet les liens facultatifs via multipart', async () => {
        apiClient.post.mockResolvedValue({ data: { idDocument: 18, idContrat: 42 } });
        const file=new File(['scan'],'scan.pdf');
        const result=await documentRhService.uploadDocument(7,file,66,'2026-09-27','Signé',{ idContrat: 42, idWorkflow: 8 });
        const [url,form]=apiClient.post.mock.calls[0];
        expect(url).toBe('/employes/7/documents'); expect(form.get('idContrat')).toBe('42'); expect(form.get('idWorkflow')).toBe('8'); expect(form.get('idTypeDocument')).toBe('66'); expect(result.idContrat).toBe(42);
    });
    it('garde compatibles les dépôts génériques', async () => {
        apiClient.post.mockResolvedValue({ data: {} }); await documentRhService.uploadDocument(7,new File(['x'],'x.pdf'),1);
        const form=apiClient.post.mock.calls[0][1]; expect(form.has('idContrat')).toBe(false); expect(form.has('idWorkflow')).toBe(false);
    });
    it('appelle les routes aperçu et DOCX puis libère URL temporaire', async () => {
        apiClient.get.mockResolvedValueOnce({ data: { idContrat: 42 } }); expect((await documentService.getContratData(42)).idContrat).toBe(42);
        const blob=new Blob(['docx']); apiClient.get.mockResolvedValueOnce({ data: blob });
        URL.createObjectURL=vi.fn().mockReturnValue('blob:contract'); URL.revokeObjectURL=vi.fn(); vi.spyOn(HTMLAnchorElement.prototype,'click').mockImplementation(()=>{});
        await documentService.downloadContratDocx(42,'contrat.docx');
        expect(apiClient.get).toHaveBeenLastCalledWith('documents/contrat/42/docx',{ responseType: 'blob' }); expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:contract');
    });
    it('décode une erreur métier reçue sous forme de blob DOCX', async () => {
        const data=new Blob(); data.text=vi.fn().mockResolvedValue('{"code":"CONTRACT_DOCUMENT_DATA_MISSING","message":"Adresse absente"}');
        const error={ response: { data } }; apiClient.get.mockRejectedValue(error);
        await expect(documentService.downloadContratDocx(42)).rejects.toMatchObject({ response: { data: { code: 'CONTRACT_DOCUMENT_DATA_MISSING' } } });
    });
});
