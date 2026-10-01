import { useState, useEffect } from 'react';
import { FileText } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Alert from '@/components/ui/feedback/Alert';
import FormError from '@/components/ui/feedback/FormError';
import Modal from '@/components/ui/overlays/Modal';
import HasPermission from '@/features/auth/components/HasPermission';
import { DocumentFrame } from '@/features/employees/components/carriere/ContractDocument';
import UploadDocumentModal from '@/features/employees/components/modals/UploadDocumentModal';
import documentService from '@/api/documentService';
import documentRhService from '@/features/employees/api/documentRhService';
import contratService from '@/features/employees/api/contratService';
import { contractDocumentLabel } from '@/features/employees/utils/contractDocumentLabels';
import { getErrorMessage } from '@/api/apiClient';
import { fmtDateInput, fmtDateFr, getTodayDateString } from '@/utils/dateUtils';

export default function ContratTravailModal({ isOpen, onClose, employe, contrat, onEdit, onDocumentUploaded, onContractUpdated }) {
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
    const [editingSignatureDate, setEditingSignatureDate] = useState(false);
    const [signatureDate, setSignatureDate] = useState('');
    const [savingSignatureDate, setSavingSignatureDate] = useState(false);

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
        setEditingSignatureDate(false);
        setSignatureDate(fmtDateInput(contrat?.dateSignature));
        documentService.getContratData(idContrat).then(result => {
            if (!cancelled) setData(result);
        }).catch(err => { if (!cancelled) setLoadError(getErrorMessage(err, "Impossible de préparer le contrat.")); });
        documentRhService.getDocumentsByEmploye(idEmploye).then(result => {
            if (!cancelled) setDocuments(result.documents || []);
        }).catch(err => { if (!cancelled) setLoadError(getErrorMessage(err, "Impossible de consulter les documents signés.")); });
        return () => { cancelled = true; };
    }, [isOpen, idContrat, idEmploye, contrat?.dateSignature]);

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

    const handleSaveSignatureDate = async event => {
        event.preventDefault();
        if (!signatureDate) return;
        setSavingSignatureDate(true);
        setActionError('');
        try {
            const updated = await contratService.renseignerDateSignature(idEmploye, idContrat, signatureDate);
            setSignatureDate(fmtDateInput(updated?.dateSignature) || signatureDate);
            setEditingSignatureDate(false);
            await onContractUpdated?.(updated);
        } catch (err) {
            setActionError(getErrorMessage(err, "Impossible d'enregistrer la date de signature."));
        } finally {
            setSavingSignatureDate(false);
        }
    };

    if (!isOpen) return null;

    const modalFooter = (
        <div className="flex items-center justify-between w-full gap-2">
            <span className="inline-flex border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] px-2.5 py-1 text-xs font-semibold">
                {data?.statut || contrat?.codeStatutContrat || 'Statut inconnu'}
            </span>
            <Button variant="ghost" size="sm" onClick={onClose} disabled={uploading}>
                Fermer
            </Button>
        </div>
    );

    return (
        <>
            <Modal
                isOpen={isOpen}
                onClose={onClose}
                title={`${data?.codeTypeContrat || contrat?.libelleTypeContrat || 'Contrat'} · ${contrat?.numeroContrat || ''}`}
                icon={<FileText size={18} />}
                size="2xl"
                footer={modalFooter}
            >
                <div className="space-y-4">
                    {loadError && <Alert type="error" message={loadError} />}
                    {actionError && <FormError message={actionError} />}
                    {data && (
                        <>
                            <div className="flex items-center justify-between gap-2 pb-2">
                                <div className="flex items-center gap-2">
                                    <span className="text-sm font-semibold text-[var(--color-fg)]">
                                        {data?.codeTypeContrat || contrat?.libelleTypeContrat || 'Contrat'}
                                    </span>
                                    {contrat?.numeroContrat && (
                                        <span className="text-xs text-[var(--color-fg-muted)]">
                                            ({contrat.numeroContrat})
                                        </span>
                                    )}
                                </div>
                                <span className="inline-flex border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] px-2 py-0.5 text-xs font-medium text-[var(--color-fg-muted)]">
                                    {data?.statut || contrat?.codeStatutContrat || 'BROUILLON'}
                                </span>
                            </div>
                            <dl className="grid grid-cols-1 gap-x-8 gap-y-3 border-y border-[var(--color-border-subtle)] py-4 text-sm sm:grid-cols-2">
                                <div><dt className="text-xs text-[var(--color-fg-muted)]">Employé</dt><dd className="mt-0.5 font-medium">{value('EMPLOYE_NOM_COMPLET')}</dd></div>
                                <div><dt className="text-xs text-[var(--color-fg-muted)]">Poste · Site</dt><dd className="mt-0.5 font-medium">{value('POSTE_FONCTION')} · {value('POSTE_SITE')}</dd></div>
                                <div><dt className="text-xs text-[var(--color-fg-muted)]">Rémunération</dt><dd className="mt-0.5 font-medium">{value('REMUNERATION_BASE')} · {value('REMUNERATION_MODE')}</dd></div>
                                <div><dt className="text-xs text-[var(--color-fg-muted)]">Période d'effet</dt><dd className="mt-0.5 font-medium">{value('CONTRAT_DATE_DEBUT')}{value('CONTRAT_DATE_FIN') !== 'Non renseigné' ? ` → ${value('CONTRAT_DATE_FIN')}` : ' (Indéterminée)'}</dd></div>
                                <div>
                                    <dt className="text-xs text-[var(--color-fg-muted)]">Date de signature</dt>
                                    <dd className="mt-0.5 font-medium">{signatureDate ? fmtDateFr(signatureDate) : 'Non renseignée'}</dd>
                                </div>
                            </dl>
                            {(data?.statut || contrat?.codeStatutContrat) !== 'BROUILLON' &&
                                (data?.statut || contrat?.codeStatutContrat) !== 'ANNULE' && (
                                <HasPermission authority="EMPLOYE_WRITE">
                                    {editingSignatureDate ? (
                                        <form className="grid grid-cols-1 gap-2 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-start" onSubmit={handleSaveSignatureDate}>
                                            <Input
                                                label="Date de signature"
                                                type="date"
                                                value={signatureDate}
                                                onChange={event => setSignatureDate(event.target.value)}
                                                required
                                                max={getTodayDateString()}
                                                hint={signatureDate
                                                    ? 'Date figurant sur le contrat signé.'
                                                    : 'Choisissez une date pour pouvoir enregistrer.'}
                                            />
                                            <div className="flex gap-2 sm:pt-5">
                                                <Button type="submit" size="sm" disabled={savingSignatureDate}>
                                                    {savingSignatureDate ? 'Enregistrement…' : 'Enregistrer'}
                                                </Button>
                                                <Button type="button" variant="ghost" size="sm" onClick={() => { setEditingSignatureDate(false); setSignatureDate(fmtDateInput(contrat?.dateSignature)); }} disabled={savingSignatureDate}>
                                                    Annuler
                                                </Button>
                                            </div>
                                        </form>
                                    ) : (
                                        <Button variant="outline" size="sm" onClick={() => setEditingSignatureDate(true)}>
                                            {signatureDate ? 'Modifier la date de signature' : 'Renseigner la date de signature'}
                                        </Button>
                                    )}
                                </HasPermission>
                            )}
                            {data.donneesManquantes?.length > 0 && (
                                <Alert type="warning" message={`DOCX indisponible : données obligatoires manquantes (${data.donneesManquantes.map(contractDocumentLabel).join(', ')}).`} />
                            )}
                            {data.avertissements?.length > 0 && (
                                <p className="text-xs text-[var(--color-fg-muted)]">
                                    À compléter ou vérifier dans le DOCX : {data.avertissements.map(contractDocumentLabel).join(', ')}.
                                </p>
                            )}
                        </>
                    )}

                    <div className="flex gap-2 flex-wrap">
                        {onEdit && (data?.statut || contrat?.codeStatutContrat) === 'BROUILLON' && (
                            <HasPermission authority="EMPLOYE_WRITE">
                                <Button variant="outline" size="sm" onClick={onEdit}>Modifier</Button>
                            </HasPermission>
                        )}
                        <Button variant="outline" size="sm" onClick={() => setPreview(current => !current)} disabled={!data}>
                            {preview ? 'Masquer l’aperçu' : 'Aperçu du contrat'}
                        </Button>
                        <HasPermission all={['EMPLOYE_READ', 'PAIE_READ']}>
                            <Button
                                variant="primary"
                                size="sm"
                                onClick={() => handleAction(() => documentService.downloadContratDocx(idContrat, `Contrat_${contrat.numeroContrat}.docx`))}
                                disabled={!data?.generationPossible || busy}
                            >
                                Télécharger le DOCX
                            </Button>
                        </HasPermission>
                    </div>

                    <p className="text-xs text-[var(--color-fg-muted)]">
                        Le DOCX téléchargé peut être modifié par la DAF/RH avant impression et signature.
                    </p>

                    <section className="space-y-3 border-t border-[var(--color-border-subtle)] pt-4" aria-label="Document signé">
                        <div className="flex items-center justify-between gap-3">
                            <h4 className="font-semibold text-sm text-[var(--color-fg)]">Contrat signé</h4>
                            {signed.length > 0 && (
                                <span className="border border-[var(--color-success-500)]/30 bg-[var(--color-success-500)]/10 px-2.5 py-1 text-xs font-semibold text-[var(--color-success-500)]">
                                    {signed.length} document{signed.length > 1 ? 's' : ''} déposé{signed.length > 1 ? 's' : ''}
                                </span>
                            )}
                        </div>
                        {signed.length > 0 ? (
                            <div className="space-y-2">
                                {signed.map(doc => (
                                    <div key={doc.idDocument} className="flex flex-col gap-2 border border-[var(--color-border-subtle)] p-3 sm:flex-row sm:items-center sm:justify-between">
                                        <span className="min-w-0 break-all text-sm">{doc.nomAffichage || doc.nomOriginal}</span>
                                        <div className="flex shrink-0 gap-2">
                                            <Button size="xs" variant="outline" onClick={() => handleAction(() => documentRhService.viewDocument(doc.idDocument))}>Voir</Button>
                                            <Button size="xs" variant="outline" onClick={() => handleAction(() => documentRhService.downloadDocument(doc.idDocument, doc.nomAffichage || doc.nomOriginal))}>Télécharger</Button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        ) : (
                            <p className="text-sm text-[var(--color-fg-muted)]">Aucun contrat signé déposé.</p>
                        )}
                        <HasPermission authority="EMPLOYE_WRITE">
                            <Button variant="outline" size="sm" onClick={handleOpenUpload} disabled={busy || !idContrat || !idEmploye}>
                                {signed.length > 0 ? 'Déposer une nouvelle version signée' : 'Déposer le contrat signé'}
                            </Button>
                        </HasPermission>
                    </section>

                    {preview && data && (
                        <DocumentFrame watermark="">
                            <div className="space-y-2">
                                {data.paragraphes.map((text, index) => (
                                    <p key={index} className="whitespace-pre-wrap">{text}</p>
                                ))}
                            </div>
                        </DocumentFrame>
                    )}
                </div>
            </Modal>

            <UploadDocumentModal
                isOpen={uploadOpen}
                onClose={() => { if (!uploading) setUploadOpen(false); }}
                onSubmit={handleSubmit}
                typesDoc={types}
                selectedTypeId={contractType?.idTypeDocument || ''}
                typeLocked
                contractReference={contrat?.numeroContrat}
                setSelectedTypeId={() => {}}
                setSelectedFile={setFile}
                docDate={date}
                setDocDate={setDate}
                docCommentaire={commentaire}
                setDocCommentaire={setCommentaire}
                uploadingDoc={uploading}
                docErrorMsg={uploadError}
            />
        </>
    );
}
