import { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import FormError from '@/components/ui/feedback/FormError';
import HasPermission from '@/features/auth/components/HasPermission';
import { DocumentFrame } from '@/features/employees/components/carriere/ContractDocument';
import UploadDocumentModal from '@/features/employees/components/tabs/UploadDocumentModal';
import documentService from '@/api/documentService';
import documentRhService from '@/features/employees/api/documentRhService';
import { contractDocumentLabel } from '@/features/employees/utils/contractDocumentLabels';
import { getErrorMessage } from '@/api/apiClient';

export default function ContratTravailModal({ isOpen, onClose, employe, contrat, onEdit, onDocumentUploaded }) {
    // État local
    const [data, setData] = useState(null);
    const [loadError, setLoadError] = useState('');
    const [actionError, setActionError] = useState('');
    const [busy, setBusy] = useState(false);
    const [preview, setPreview] = useState(false);
    const [documents, setDocuments] = useState([]);
    const [types, setTypes] = useState([]);
    const [uploadOpen, setUploadOpen] = useState(false);
    const [file, setFile] = useState(null);
    const [date, setDate] = useState('');
    const [commentaire, setCommentaire] = useState('');
    const [uploadError, setUploadError] = useState('');
    const [uploading, setUploading] = useState(false);
    // Données dérivées
    const idContrat = contrat?.idContrat;
    const idEmploye = contrat?.idEmploye || employe?.idEmploye;

    const signed = documents.filter(doc => Number(doc.idContrat) === Number(idContrat) && doc.contratSigne);
    const contractType = types.find(type => type.contratTravail && type.actif !== false);
    const value = key => data?.variables?.[`{{${key}}}`] || 'Non renseigné';

    // Effets
    useEffect(() => {
        if (!isOpen || !idContrat) return;
        let cancelled = false;
        setData(null); setLoadError(''); setActionError(''); setDocuments([]); setPreview(false); setUploadOpen(false);
        documentService.getContratData(idContrat).then(result => {
            if (!cancelled) setData(result);
        }).catch(err => { if (!cancelled) setLoadError(getErrorMessage(err, "Impossible de préparer le contrat.")); });
        documentRhService.getDocumentsByEmploye(idEmploye).then(result => {
            if (!cancelled) setDocuments(result.documents || []);
        }).catch(err => { if (!cancelled) setLoadError(getErrorMessage(err, "Impossible de consulter les documents signés.")); });
        return () => { cancelled = true; };
    }, [isOpen, idContrat, idEmploye]);

    useEffect(() => {
        if (!isOpen) return;
        const handler = e => { if (e.key === 'Escape' && !uploadOpen && !uploading) onClose(); };
        window.addEventListener('keydown', handler);
        return () => window.removeEventListener('keydown', handler);
    }, [isOpen, onClose, uploadOpen, uploading]);

    // Actions
    const handleAction = async operation => {
        setBusy(true); setActionError('');
        try { await operation(); } catch (err) { setActionError(getErrorMessage(err, "L'opération a échoué.")); }
        finally { setBusy(false); }
    };
    const handleOpenUpload = () => handleAction(async () => {
        const result = await documentRhService.getTypesDocument(true);
        if (!result.some(type => type.contratTravail && type.actif !== false)) throw new Error('Le type Contrat de travail est absent ou inactif.');
        setTypes(result); setFile(null); setDate(''); setCommentaire(''); setUploadError(''); setUploadOpen(true);
    });
    const handleSubmit = async event => {
        event.preventDefault();
        if (!file || !contractType) { setUploadError('Sélectionnez un fichier.'); return; }
        setUploading(true); setUploadError('');
        try {
            const created = await documentRhService.uploadDocument(idEmploye, file, contractType.idTypeDocument, date, commentaire, { idContrat });
            setDocuments(previous => [created, ...previous]); setUploadOpen(false);
            onDocumentUploaded?.();
            window.dispatchEvent(new CustomEvent('documents-rh-updated', { detail: { idEmploye } }));
        } catch (err) { setUploadError(getErrorMessage(err, "Impossible de déposer le contrat signé.")); }
        finally { setUploading(false); }
    };
    // Rendu
    if (!isOpen) return null;
    return createPortal(<>
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[var(--color-overlay)]/60 print:static print:bg-[var(--color-surface)] print:p-0">
            <section role="dialog" aria-modal="true" aria-label="Contrat de travail" className="w-full max-w-3xl max-h-[92vh] overflow-y-auto bg-[var(--color-surface)] p-6 space-y-5 print:max-h-none print:overflow-visible">
                <div className="flex items-start justify-between gap-4 print:hidden">
                    <div className="space-y-2">
                        <h3 className="font-bold">{data?.codeTypeContrat || contrat?.libelleTypeContrat} — {contrat?.numeroContrat}</h3>
                        <span className="inline-flex rounded-full border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] px-2.5 py-1 text-xs font-semibold">
                            {data?.statut || contrat?.codeStatutContrat || 'Statut inconnu'}
                        </span>
                    </div>
                    <Button onClick={onClose} disabled={uploading}>Fermer</Button>
                </div>
                {loadError && <Alert type="error" message={loadError} />}
                {actionError && <FormError message={actionError} />}
                {data && <>
                    <dl className="grid grid-cols-1 gap-x-8 gap-y-3 border-y border-[var(--color-border-subtle)] py-4 text-sm sm:grid-cols-2 print:hidden">
                        <div><dt className="text-xs text-[var(--color-fg-muted)]">Employé</dt><dd className="mt-0.5 font-medium">{value('EMPLOYE_NOM_COMPLET')}</dd></div>
                        <div><dt className="text-xs text-[var(--color-fg-muted)]">Poste / site</dt><dd className="mt-0.5 font-medium">{value('POSTE_FONCTION')} / {value('POSTE_SITE')}</dd></div>
                        <div><dt className="text-xs text-[var(--color-fg-muted)]">Rémunération</dt><dd className="mt-0.5 font-medium">{value('REMUNERATION_BASE')} · {value('REMUNERATION_MODE')}</dd></div>
                        <div><dt className="text-xs text-[var(--color-fg-muted)]">Début / fin</dt><dd className="mt-0.5 font-medium">{value('CONTRAT_DATE_DEBUT')} / {value('CONTRAT_DATE_FIN')}</dd></div>
                    </dl>
                    {data.donneesManquantes?.length > 0 && <Alert type="warning" message={`DOCX indisponible : données obligatoires manquantes (${data.donneesManquantes.map(contractDocumentLabel).join(', ')}).`} />}
                    {data.avertissements?.length > 0 && <p className="text-xs text-[var(--color-fg-muted)] print:hidden">À compléter ou vérifier dans le DOCX : {data.avertissements.map(contractDocumentLabel).join(', ')}.</p>}
                </>}
                <div className="flex gap-2 flex-wrap print:hidden">
                    {onEdit && (data?.statut || contrat?.codeStatutContrat) === 'BROUILLON' && <HasPermission authority="EMPLOYE_WRITE"><Button onClick={onEdit}>Modifier</Button></HasPermission>}
                    <Button onClick={() => setPreview(current => !current)} disabled={!data}>{preview ? 'Masquer l’aperçu' : 'Aperçu du contrat'}</Button>
                    <HasPermission all={['EMPLOYE_READ', 'PAIE_READ']}>
                        <Button onClick={() => handleAction(() => documentService.downloadContratDocx(idContrat, `Contrat_${contrat.numeroContrat}.docx`))} disabled={!data?.generationPossible || busy}>Télécharger le DOCX</Button>
                    </HasPermission>
                    {preview && <Button onClick={() => window.print()}>Imprimer / PDF</Button>}
                </div>
                <p className="-mt-3 text-xs text-[var(--color-fg-muted)] print:hidden">Le DOCX téléchargé peut être modifié par la DAF/RH avant impression et signature.</p>
                <section className="space-y-3 border-t border-[var(--color-border-subtle)] pt-4 print:hidden" aria-label="Document signé">
                    <div className="flex items-center justify-between gap-3">
                        <h4 className="font-semibold">Contrat signé</h4>
                        {signed.length > 0 && <span className="rounded-full border border-[var(--color-success-500)]/30 bg-[var(--color-success-500)]/10 px-2.5 py-1 text-xs font-semibold text-[var(--color-success-500)]">{signed.length} document{signed.length > 1 ? 's' : ''} déposé{signed.length > 1 ? 's' : ''}</span>}
                    </div>
                    {signed.length > 0 ? <div className="space-y-2">{signed.map(doc => <div key={doc.idDocument} className="flex flex-col gap-2 rounded border border-[var(--color-border-subtle)] p-3 sm:flex-row sm:items-center sm:justify-between"><span className="min-w-0 break-all text-sm">{doc.nomAffichage || doc.nomOriginal}</span><div className="flex shrink-0 gap-2"><Button onClick={() => handleAction(() => documentRhService.viewDocument(doc.idDocument))}>Voir</Button><Button variant="outline" onClick={() => handleAction(() => documentRhService.downloadDocument(doc.idDocument, doc.nomAffichage || doc.nomOriginal))}>Télécharger</Button></div></div>)}</div> : <p className="text-sm text-[var(--color-fg-muted)]">Aucun contrat signé déposé.</p>}
                    <HasPermission authority="EMPLOYE_WRITE"><Button onClick={handleOpenUpload} disabled={busy || !idContrat || !idEmploye}>{signed.length > 0 ? 'Déposer une nouvelle version signée' : 'Déposer le contrat signé'}</Button></HasPermission>
                </section>
                {preview && data && <DocumentFrame watermark=""><div className="space-y-2">{data.paragraphes.map((text, index) => <p key={index} className="whitespace-pre-wrap">{text}</p>)}</div></DocumentFrame>}
            </section>
        </div>
        <UploadDocumentModal isOpen={uploadOpen} onClose={() => { if (!uploading) setUploadOpen(false); }} onSubmit={handleSubmit}
            typesDoc={types} selectedTypeId={contractType?.idTypeDocument || ''} typeLocked contractReference={contrat?.numeroContrat}
            setSelectedTypeId={() => {}} setSelectedFile={setFile} docDate={date} setDocDate={setDate}
            docCommentaire={commentaire} setDocCommentaire={setCommentaire} uploadingDoc={uploading} docErrorMsg={uploadError} />
    </>, document.body);
}
