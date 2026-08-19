import { Upload, ShieldAlert } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea, Select } from '@/components/ui/forms/Input';

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
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
            <div className="bg-white dark:bg-ink-900 rounded-none border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white flex items-center gap-2">
                    <Upload size={18} className="text-brand-500" />
                    Téléverser un Document RH
                </div>
                <form onSubmit={onSubmit}>
                    <div className="p-6 space-y-4">
                        {docErrorMsg && (
                            <div className="bg-danger-500/10 text-danger-600 border border-danger-500/20 rounded-none p-3 text-xs flex items-center gap-1.5">
                                <ShieldAlert size={14} />
                                {docErrorMsg}
                            </div>
                        )}
                        <Select
                            label="Type de Document"
                            required
                            value={selectedTypeId}
                            onChange={(e) => setSelectedTypeId(e.target.value)}
                        >
                            <option value="">-- Sélectionner --</option>
                            {typesDoc.map(t => (
                                <option key={t.idTypeDocument} value={t.idTypeDocument}>
                                    {t.libelle} {t.obligatoire ? '(Obligatoire)' : ''} — [{t.categorie}]
                                </option>
                            ))}
                        </Select>

                        <div className="space-y-1">
                            <label className="text-xs font-bold uppercase tracking-wider text-ink-800 dark:text-ink-50">
                                Fichier (PDF, JPG, PNG) <span className="text-danger-500">*</span>
                            </label>
                            <input
                                type="file"
                                required
                                onChange={(e) => setSelectedFile(e.target.files?.[0] || null)}
                                className="w-full text-xs text-ink-700 dark:text-ink-300 border border-ink-200 dark:border-ink-700 p-2 rounded-none bg-ink-50 dark:bg-ink-950"
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
                    </div>
                    <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">
                            Annuler
                        </Button>
                        <Button type="submit" variant="primary" size="sm" loading={uploadingDoc}>
                            Téléverser
                        </Button>
                    </div>
                </form>
            </div>
        </div>
    );
}
