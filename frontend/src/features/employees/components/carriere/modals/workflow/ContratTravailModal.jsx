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
            <section role="dialog" aria-modal="true" aria-label="Contrat de travail" className="w-full max-w-4xl max-h-[92vh] overflow-y-auto bg-[var(--color-surface)] p-6 space-y-4 print:max-h-none print:overflow-visible">
                <div className="flex justify-between gap-3 print:hidden">
                    <h3 className="font-bold">{data?.codeTypeContrat || contrat?.libelleTypeContrat} — {contrat?.numeroContrat}</h3>
                    <Button onClick={onClose} disabled={uploading}>Fermer</Button>
                </div>
                {loadError && <Alert type="error" message={loadError} />}
                {actionError && <FormError message={actionError} />}
                <p>Statut : {data?.statut || contrat?.codeStatutContrat}</p>
                {data && <>
                    <dl className="grid grid-cols-2 gap-2 text-sm print:hidden">
                        <dt>Employé</dt><dd>{value('EMPLOYE_NOM_COMPLET')}</dd>
                        <dt>Poste / site</dt><dd>{value('POSTE_FONCTION')} / {value('POSTE_SITE')}</dd>
                        <dt>Rémunération</dt><dd>{value('REMUNERATION_BASE')} · {value('REMUNERATION_MODE')}</dd>
                        <dt>Début / fin</dt><dd>{value('CONTRAT_DATE_DEBUT')} / {value('CONTRAT_DATE_FIN')}</dd>
                    </dl>
                    <Alert type="warning" message="Canevas interne : les clauses et informations doivent être validées avant signature." />
                    {data.donneesManquantes?.length > 0 && <Alert type="warning" message={`DOCX indisponible : données obligatoires manquantes (${data.donneesManquantes.map(contractDocumentLabel).join(', ')}).`} />}
                    {data.avertissements?.length > 0 && <Alert type="warning" message={`À vérifier ou compléter avant signature : ${data.avertissements.map(contractDocumentLabel).join(', ')}.`} />}
                </>}
                <div className="flex gap-2 flex-wrap print:hidden">
                    {onEdit && (data?.statut || contrat?.codeStatutContrat) === 'BROUILLON' && <HasPermission authority="EMPLOYE_WRITE"><Button onClick={onEdit}>Modifier</Button></HasPermission>}
                    <Button onClick={() => setPreview(current => !current)} disabled={!data}>Aperçu</Button>
                    <HasPermission all={['EMPLOYE_READ', 'PAIE_READ']}>
                        <Button onClick={() => handleAction(() => documentService.downloadContratDocx(idContrat, `Contrat_${contrat.numeroContrat}.docx`))} disabled={!data?.generationPossible || busy}>Télécharger DOCX</Button>
                    </HasPermission>
                    {preview && <Button onClick={() => window.print()}>Imprimer / PDF</Button>}
                    <HasPermission authority="EMPLOYE_WRITE"><Button onClick={handleOpenUpload} disabled={busy || !idContrat || !idEmploye}>Déposer le contrat signé</Button></HasPermission>
                </div>
                {signed.length > 0 && <div className="space-y-2 print:hidden"><p className="font-semibold">Contrat signé ✓</p>{signed.map(doc => <div key={doc.idDocument} className="flex gap-2 items-center"><span>{doc.nomOriginal}</span><Button onClick={() => handleAction(() => documentRhService.viewDocument(doc.idDocument))}>Voir le document</Button><Button onClick={() => handleAction(() => documentRhService.downloadDocument(doc.idDocument, doc.nomOriginal))}>Télécharger</Button></div>)}</div>}
                {preview && data && <DocumentFrame watermark="Canevas interne"><div className="space-y-2">{data.paragraphes.map((text, index) => <p key={index} className="whitespace-pre-wrap">{text}</p>)}</div></DocumentFrame>}
            </section>
        </div>
        <UploadDocumentModal isOpen={uploadOpen} onClose={() => { if (!uploading) setUploadOpen(false); }} onSubmit={handleSubmit}
            typesDoc={types} selectedTypeId={contractType?.idTypeDocument || ''} typeLocked contractReference={contrat?.numeroContrat}
            setSelectedTypeId={() => {}} setSelectedFile={setFile} docDate={date} setDocDate={setDate}
            docCommentaire={commentaire} setDocCommentaire={setCommentaire} uploadingDoc={uploading} docErrorMsg={uploadError} />
    </>, document.body);
}
