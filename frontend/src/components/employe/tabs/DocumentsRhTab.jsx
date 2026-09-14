import { Folder, Upload, Download, Trash2, AlertTriangle } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';

const fmtDate = (d) => {
    if (!d) return '—';
    try { return new Date(d).toLocaleDateString('fr-FR'); } catch { return '—'; }
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
    const obligatoiresManquants = documentsData.obligatoiresManquants || [];
    const documents = documentsData.documents || [];

    return (
        <div className="space-y-5">
            {/* Bannière Conformité RH — Documents Obligatoires Manquants */}
            {canWrite && obligatoiresManquants.length > 0 && (
                <Card padding className="border-l-4 border-l-amber-500 bg-[var(--color-bg-subtle)]/80 space-y-3">
                    <div className="flex items-center justify-between gap-2 flex-wrap">
                        <div className="flex items-center gap-2">
                            <AlertTriangle size={16} className="text-amber-500 shrink-0" />
                            <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                                Conformité RH — Pièces Obligatoires Attendues
                            </h4>
                        </div>
                        <Badge variant="warning" size="xs" dot>
                            {obligatoiresManquants.length} document{obligatoiresManquants.length > 1 ? 's' : ''} manquant{obligatoiresManquants.length > 1 ? 's' : ''}
                        </Badge>
                    </div>
                    <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                        Pour finaliser le dossier administratif et respecter la réglementation sociale, veuillez verser les pièces justificatives suivantes :
                    </p>
                    <div className="flex flex-wrap gap-2 pt-0.5">
                        {obligatoiresManquants.map(m => (
                            <Button
                                key={m.idTypeDocument}
                                type="button"
                                onClick={() => onOpenUploadModal(m.idTypeDocument)}
                                variant="outline"
                                size="xs"
                                iconLeft={<Upload size={12} />}
                                title={`Verser ${m.libelle}`}
                            >
                                {m.libelle}
                            </Button>
                        ))}
                    </div>
                </Card>
            )}

            {/* Carte Principale des Documents RH */}
            <Card shadow="sm">
                <CardHeader className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3">
                    <div>
                        <CardTitle size="sm" className="uppercase tracking-wider flex items-center gap-2">
                            <Folder size={15} className="text-brand-500" />
                            Documents RH Déposés ({documents.length})
                        </CardTitle>
                        <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                            Consultez, téléchargez ou ajoutez les pièces justificatives de l'employé
                        </p>
                    </div>
                    {canWrite && documents.length > 0 && (
                        <Button
                            onClick={() => onOpenUploadModal()}
                            variant="primary"
                            size="sm"
                            iconLeft={<Upload size={14} />}
                        >
                            Téléverser un Document
                        </Button>
                    )}
                </CardHeader>
                <CardBody className="p-0">
                    {loadingDocs ? (
                        <div className="p-5">
                            <SkeletonTable rows={5} columns={6} />
                        </div>
                    ) : documents.length === 0 ? (
                        <div className="p-5">
                            <EmptyState
                                icon={Folder}
                                title="Aucun document RH déposé"
                                description="Cliquez sur Téléverser un Document pour ajouter les pièces justificatives du collaborateur."
                                actionLabel={canWrite ? "Téléverser un Document" : undefined}
                                onAction={canWrite ? () => onOpenUploadModal() : undefined}
                                actionIcon={<Upload size={14} />}
                            />
                        </div>
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
                                {documents.map((doc) => (
                                    <Tr key={doc.idDocument} className="group">
                                        <Td>
                                            <div className="flex items-center gap-3">
                                                <div className="w-8 h-8 bg-[var(--color-brand-500)]/10 text-[var(--color-brand-500)] flex items-center justify-center font-bold text-xs">
                                                    {doc.extension ? doc.extension.toUpperCase() : 'DOC'}
                                                </div>
                                                <div>
                                                    <p className="font-semibold text-[var(--color-fg)] text-xs sm:text-[13px]">{doc.nomOriginal}</p>
                                                    {doc.commentaire && <p className="text-[11px] text-[var(--color-fg-muted)]">{doc.commentaire}</p>}
                                                </div>
                                            </div>
                                        </Td>
                                        <Td>
                                            <div className="flex items-center gap-1.5 flex-wrap">
                                                <Badge variant="brand" size="xs">{doc.typeDocumentLibelle}</Badge>
                                                {doc.typeDocumentObligatoire && (
                                                    <Badge variant="warning" size="xs">Obligatoire</Badge>
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
                                            <div className="flex justify-end items-center gap-1">
                                                <Button
                                                    type="button"
                                                    variant="ghost"
                                                    size="xs"
                                                    onClick={() => onDownloadDoc(doc.idDocument, doc.nomOriginal)}
                                                    iconLeft={<Download size={14} className="text-[var(--color-brand-500)]" />}
                                                    title="Visualiser / Télécharger"
                                                    aria-label="Visualiser / Télécharger"
                                                >
                                                    Ouvrir
                                                </Button>
                                                {canWrite && (
                                                    <Button
                                                        type="button"
                                                        variant="ghost"
                                                        size="xs"
                                                        onClick={() => onDeleteDoc(doc.idDocument, doc.nomOriginal)}
                                                        iconLeft={<Trash2 size={14} className="text-[var(--color-danger-500)]" />}
                                                        title="Supprimer"
                                                        aria-label="Supprimer le document"
                                                    />
                                                )}
                                            </div>
                                        </Td>
                                    </Tr>
                                ))}
                            </Tbody>
                        </Table>
                    )}
                </CardBody>
            </Card>
        </div>
    );
}
