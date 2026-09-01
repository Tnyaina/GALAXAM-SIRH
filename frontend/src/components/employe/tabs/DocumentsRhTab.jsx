import { Folder, Upload, Download, Trash2, AlertTriangle } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';

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
            {/* Bannière Conformité RH — Documents Obligatoires Manquants */}
            {canWrite && documentsData.obligatoiresManquants && documentsData.obligatoiresManquants.length > 0 && (
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] border-l-4 border-l-amber-500 p-4 rounded-none shadow-2xs space-y-2.5">
                    <div className="flex items-center justify-between gap-2 flex-wrap">
                        <div className="flex items-center gap-2">
                            <AlertTriangle size={16} className="text-amber-500 shrink-0" />
                            <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                                Conformité RH — Pièces Obligatoires Attendues
                            </h4>
                        </div>
                        <Badge variant="warning" size="xs" dot>
                            {documentsData.obligatoiresManquants.length} document{documentsData.obligatoiresManquants.length > 1 ? 's' : ''} manquant{documentsData.obligatoiresManquants.length > 1 ? 's' : ''}
                        </Badge>
                    </div>
                    <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                        Pour finaliser le dossier administratif et respecter la réglementation sociale, veuillez verser les pièces justificatives suivantes :
                    </p>
                    <div className="flex flex-wrap gap-2 pt-0.5">
                        {documentsData.obligatoiresManquants.map(m => (
                            <button
                                key={m.idTypeDocument}
                                type="button"
                                onClick={() => onOpenUploadModal(m.idTypeDocument)}
                                className="group inline-flex items-center gap-1.5 px-3 py-1 text-xs font-semibold bg-[var(--color-bg-subtle)] border border-[var(--color-border)] hover:border-[var(--color-brand-500)] hover:text-[var(--color-brand-500)] text-[var(--color-fg)] transition-all rounded-none cursor-pointer shadow-2xs"
                                title={`Verser ${m.libelle}`}
                            >
                                <Upload size={12} className="text-[var(--color-fg-muted)] group-hover:text-[var(--color-brand-500)] transition-colors" />
                                <span>{m.libelle}</span>
                            </button>
                        ))}
                    </div>
                </div>
            )}

            {/* Entête & Bouton Upload (affiché uniquement s'il y a déjà des documents pour éviter le doublon avec l'EmptyState) */}
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 pb-2 border-b border-[var(--color-border-subtle)]">
                <div>
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-500)] flex items-center gap-2">
                        <Folder size={14} /> Documents RH Déposés ({documentsData.documents.length})
                    </h3>
                    <p className="text-xs text-[var(--color-fg-muted)]">Consultez, téléchargez ou ajoutez les pièces justificatives de l'employé</p>
                </div>
                {canWrite && documentsData.documents.length > 0 && (
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
                <div className="p-4">
                    <SkeletonTable rows={5} columns={6} />
                </div>
            ) : documentsData.documents.length === 0 ? (
                <EmptyState
                    icon={Folder}
                    title="Aucun document RH déposé"
                    description="Cliquez sur Téléverser un Document pour ajouter les pièces justificatives du collaborateur."
                    actionLabel={canWrite ? "Téléverser un Document" : undefined}
                    onAction={canWrite ? () => onOpenUploadModal() : undefined}
                />
            ) : (
                <Table>
                    <Thead>
                        <Tr>
                            <Th>Document & Nom</Th>
                            <Th>Type & Catégorie</Th>
                            <Th>Taille</Th>
                            <Th>Date Document</Th>
                            <Th>Versé par</Th>
                            <Th className="text-right">Actions</Th>
                        </Tr>
                    </Thead>
                    <Tbody>
                        {documentsData.documents.map((doc) => (
                            <Tr key={doc.idDocument} className="group">
                                <Td>
                                    <div className="flex items-center gap-3">
                                        <div className="w-8 h-8 bg-[var(--color-brand-500)]/10 text-[var(--color-brand-500)] flex items-center justify-center rounded-none font-bold text-xs">
                                            {doc.extension ? doc.extension.toUpperCase() : 'DOC'}
                                        </div>
                                        <div>
                                            <p className="font-semibold text-[var(--color-fg)]">{doc.nomOriginal}</p>
                                            {doc.commentaire && <p className="text-xs text-[var(--color-fg-muted)]">{doc.commentaire}</p>}
                                        </div>
                                    </div>
                                </Td>
                                <Td>
                                    <div className="flex items-center gap-2">
                                        <Badge variant="brand" size="sm">{doc.typeDocumentLibelle}</Badge>
                                        {doc.typeDocumentObligatoire && (
                                            <Badge variant="warning" size="sm">Obligatoire</Badge>
                                        )}
                                    </div>
                                </Td>
                                <Td isData className="text-xs font-mono text-[var(--color-fg-muted)]">
                                    {formatBytes(doc.tailleOctet)}
                                </Td>
                                <Td isData className="text-xs font-mono text-[var(--color-fg-muted)]">
                                    {fmtDate(doc.dateDocument)}
                                </Td>
                                <Td className="text-xs text-[var(--color-fg-muted)]">
                                    {doc.uploadedByIdentifiant || 'Admin'}
                                </Td>
                                <Td className="text-right whitespace-nowrap">
                                    <div className="flex justify-end gap-1">
                                        <button
                                            type="button"
                                            onClick={() => onDownloadDoc(doc.idDocument, doc.nomOriginal)}
                                            className="p-1.5 text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer"
                                            title="Visualiser / Télécharger"
                                            aria-label="Visualiser / Télécharger"
                                        >
                                            <Download size={16} />
                                        </button>
                                        {canWrite && (
                                            <button
                                                type="button"
                                                onClick={() => onDeleteDoc(doc.idDocument, doc.nomOriginal)}
                                                className="p-1.5 text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10 rounded-none cursor-pointer"
                                                title="Supprimer"
                                                aria-label="Supprimer le document"
                                            >
                                                <Trash2 size={16} />
                                            </button>
                                        )}
                                    </div>
                                </Td>
                            </Tr>
                        ))}
                    </Tbody>
                </Table>
            )}
        </div>
    );
}
