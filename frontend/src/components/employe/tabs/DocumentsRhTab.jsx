import { Folder, Upload, Download, Trash2, Plus, AlertTriangle } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Loading from '@/components/ui/feedback/Loading';

const fmtDate = (d) => {
    if (!d) return '-';
    try { return new Date(d).toLocaleDateString('fr-FR'); } catch { return '-'; }
};

const formatBytes = (bytes) => {
    if (!bytes || bytes === 0) return '0 Octet';
    const k = 1024;
    const sizes = ['Octets', 'Ko', 'Mo', 'Go'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

export default function DocumentsRhTab({
    documentsData = { documents: [], obligatoiresManquants: [] },
    loadingDocs = false,
    canWrite = false,
    onOpenUploadModal,
    onDownloadDoc,
    onDeleteDoc,
}) {
    return (
        <div className="space-y-6">
            {/* Bannière Alerte Documents Obligatoires Manquants */}
            {canWrite && documentsData.obligatoiresManquants && documentsData.obligatoiresManquants.length > 0 && (
                <div className="bg-warning-500/10 border border-warning-500/30 p-4 rounded-sm flex items-start gap-3">
                    <AlertTriangle size={20} className="text-warning-600 flex-shrink-0 mt-0.5" />
                    <div className="space-y-1">
                        <h4 className="text-xs font-bold uppercase tracking-wider text-warning-700">
                            Documents Obligatoires Manquants ({documentsData.obligatoiresManquants.length})
                        </h4>
                        <p className="text-xs text-warning-600">
                            Veuillez verser au dossier les pièces obligatoires manquantes pour compléter la conformité RH de cet employé :
                        </p>
                        <div className="flex flex-wrap gap-2 pt-1">
                            {documentsData.obligatoiresManquants.map(m => (
                                <button
                                    key={m.idTypeDocument}
                                    type="button"
                                    onClick={() => onOpenUploadModal(m.idTypeDocument)}
                                    className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-bold bg-white dark:bg-ink-900 border border-warning-500/40 text-warning-700 hover:bg-warning-500/20 rounded-sm transition-all cursor-pointer"
                                >
                                    <Plus size={12} />
                                    {m.libelle}
                                </button>
                            ))}
                        </div>
                    </div>
                </div>
            )}

            {/* Entête & Bouton Upload */}
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 pb-2 border-b border-ink-150 dark:border-ink-700">
                <div>
                    <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 flex items-center gap-2">
                        <Folder size={14} /> Documents RH Déposés ({documentsData.documents.length})
                    </h3>
                    <p className="text-xs text-ink-400">Consultez, téléchargez ou ajoutez les pièces justificatives de l'employé</p>
                </div>
                {canWrite && (
                    <Button
                        onClick={() => onOpenUploadModal()}
                        variant="primary"
                        size="sm"
                        iconLeft={<Upload size={16} />}
                    >
                        Téléverser un Document
                    </Button>
                )}
            </div>

            {/* Grille/Tableau des Documents */}
            {loadingDocs ? (
                <div className="py-12 flex justify-center">
                    <Loading size="md" label="Chargement des documents..." />
                </div>
            ) : documentsData.documents.length === 0 ? (
                <div className="p-10 text-center bg-ink-50 dark:bg-ink-950 border border-dashed border-ink-200 dark:border-ink-700 space-y-2">
                    <Folder size={36} className="mx-auto text-ink-300" />
                    <p className="text-sm font-bold text-ink-800 dark:text-white">Aucun document RH déposé</p>
                    <p className="text-xs text-ink-400">Cliquez sur Téléverser un Document pour en ajouter un.</p>
                </div>
            ) : (
                <div className="overflow-x-auto border border-ink-150 dark:border-ink-700 rounded-sm">
                    <table className="w-full text-sm text-left">
                        <thead>
                            <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 text-[10px] font-bold uppercase tracking-widest">
                                <th className="px-4 py-3">Document & Nom</th>
                                <th className="px-4 py-3">Type & Catégorie</th>
                                <th className="px-4 py-3">Taille</th>
                                <th className="px-4 py-3">Date Document</th>
                                <th className="px-4 py-3">Versé par</th>
                                <th className="px-4 py-3 text-right">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                            {documentsData.documents.map((doc) => (
                                <tr key={doc.idDocument} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                    <td className="px-4 py-3">
                                        <div className="flex items-center gap-3">
                                            <div className="w-8 h-8 bg-brand-500/10 text-brand-500 flex items-center justify-center rounded-sm font-bold text-xs">
                                                {doc.extension ? doc.extension.toUpperCase() : 'DOC'}
                                            </div>
                                            <div>
                                                <p className="font-semibold text-ink-900 dark:text-white">{doc.nomOriginal}</p>
                                                {doc.commentaire && <p className="text-xs text-ink-400">{doc.commentaire}</p>}
                                            </div>
                                        </div>
                                    </td>
                                    <td className="px-4 py-3">
                                        <div className="flex items-center gap-2">
                                            <Badge variant="primary" size="sm">{doc.typeDocumentLibelle}</Badge>
                                            {doc.typeDocumentObligatoire && (
                                                <Badge variant="warning" size="sm">Obligatoire</Badge>
                                            )}
                                        </div>
                                    </td>
                                    <td className="px-4 py-3 text-xs font-mono text-ink-500">
                                        {formatBytes(doc.tailleOctet)}
                                    </td>
                                    <td className="px-4 py-3 text-xs font-mono">
                                        {fmtDate(doc.dateDocument)}
                                    </td>
                                    <td className="px-4 py-3 text-xs text-ink-500">
                                        {doc.uploadedByIdentifiant || 'Admin'}
                                    </td>
                                    <td className="px-4 py-3 text-right">
                                        <div className="flex justify-end gap-2">
                                            <button
                                                onClick={() => onDownloadDoc(doc.idDocument, doc.nomOriginal)}
                                                className="p-1.5 text-brand-500 hover:bg-brand-500/10 rounded-sm transition-all cursor-pointer"
                                                title="Visualiser / Télécharger"
                                            >
                                                <Download size={16} />
                                            </button>
                                            {canWrite && (
                                                <button
                                                    onClick={() => onDeleteDoc(doc.idDocument, doc.nomOriginal)}
                                                    className="p-1.5 text-danger-500 hover:bg-danger-500/10 rounded-sm transition-all cursor-pointer"
                                                    title="Supprimer"
                                                >
                                                    <Trash2 size={16} />
                                                </button>
                                            )}
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}
