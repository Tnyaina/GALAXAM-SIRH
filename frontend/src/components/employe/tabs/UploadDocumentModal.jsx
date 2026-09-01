import { Upload } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea, Select } from '@/components/ui/forms/Input';
import Alert from '@/components/ui/feedback/Alert';

export default function UploadDocumentModal({
    isOpen,
    onClose,
    onSubmit,
    typesDoc = [],
    selectedTypeId,
    setSelectedTypeId,
    setSelectedFile,
    docDate,
    setDocDate,
    docCommentaire,
    setDocCommentaire,
    uploadingDoc,
    docErrorMsg,
}) {
    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title="Téléverser un Document RH"
            icon={<Upload size={18} />}
            size="md"
            footer={
                <>
                    <Button 
                        type="button" 
                        onClick={onClose} 
                        variant="ghost" 
                        size="sm" 
                        disabled={uploadingDoc}
                    >
                        Annuler
                    </Button>
                    <Button 
                        type="submit" 
                        form="upload-doc-form" 
                        variant="primary" 
                        size="sm" 
                        loading={uploadingDoc}
                    >
                        Téléverser le document
                    </Button>
                </>
            }
        >
            <form id="upload-doc-form" onSubmit={onSubmit} className="space-y-4">
                {docErrorMsg && (
                    <Alert type="error" message={docErrorMsg} />
                )}

                <Select
                    label="Type de Document"
                    required
                    value={selectedTypeId}
                    onChange={(e) => setSelectedTypeId(e.target.value)}
                >
                    <option value="">-- Sélectionner un type --</option>
                    {typesDoc.map(t => (
                        <option key={t.idTypeDocument} value={t.idTypeDocument}>
                            {t.libelle} {t.obligatoire ? '(Obligatoire)' : ''} — [{t.categorie}]
                        </option>
                    ))}
                </Select>

                <div className="space-y-1">
                    <label className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                        Fichier (PDF, JPG, PNG) <span className="text-[var(--color-danger-500)]">*</span>
                    </label>
                    <input
                        type="file"
                        required
                        accept=".pdf,image/png,image/jpeg,image/webp"
                        onChange={(e) => setSelectedFile(e.target.files?.[0] || null)}
                        className="w-full text-xs text-[var(--color-fg)] border border-[var(--color-border)] p-2 rounded-none bg-[var(--color-bg-subtle)] focus:outline-none focus:border-[var(--color-brand-500)]"
                    />
                </div>

                <Input
                    label="Date du Document"
                    type="date"
                    value={docDate}
                    onChange={(e) => setDocDate(e.target.value)}
                />

                <Textarea
                    label="Commentaire / Remarque"
                    rows={2}
                    value={docCommentaire}
                    onChange={(e) => setDocCommentaire(e.target.value)}
                    placeholder="Remarque éventuelle (numéro de diplôme, version, etc.)"
                />
            </form>
        </Modal>
    );
}
