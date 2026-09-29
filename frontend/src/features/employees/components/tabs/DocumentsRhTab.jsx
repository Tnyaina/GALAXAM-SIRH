import { useState, useMemo, useEffect } from 'react';
import { FileText, Upload, Download, Trash2, AlertTriangle, Search } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { fmtDate } from '@/utils/dateUtils';

const formatBytes = (bytes) => {
    if (!bytes || bytes === 0) return '0 Octet';
    const k = 1024;
    const sizes = ['Octets', 'Ko', 'Mo', 'Go'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
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

    // Recherche et filtrage
    const [searchQuery, setSearchQuery] = useState('');
    const [debouncedSearch, setDebouncedSearch] = useState('');
    const [selectedCategory, setSelectedCategory] = useState('ALL');

    useEffect(() => {
        const timer = setTimeout(() => setDebouncedSearch(searchQuery.trim().toLowerCase()), 300);
        return () => clearTimeout(timer);
    }, [searchQuery]);

    const countContrats = useMemo(() => documents.filter(d => Boolean(d.idContrat)).length, [documents]);
    const countObligatoires = useMemo(() => documents.filter(d => Boolean(d.typeDocumentObligatoire)).length, [documents]);
    const isConforme = obligatoiresManquants.length === 0;

    const filteredDocuments = useMemo(() => {
        return documents.filter(doc => {
            if (selectedCategory === 'OBLIGATOIRES' && !doc.typeDocumentObligatoire) return false;
            if (selectedCategory === 'CONTRATS' && !doc.idContrat) return false;
            if (debouncedSearch) {
                const matchNom = doc.nomOriginal?.toLowerCase().includes(debouncedSearch);
                const matchComment = doc.commentaire?.toLowerCase().includes(debouncedSearch);
                const matchType = doc.typeDocumentLibelle?.toLowerCase().includes(debouncedSearch);
                const matchContrat = doc.numeroContrat?.toLowerCase().includes(debouncedSearch);
                if (!matchNom && !matchComment && !matchType && !matchContrat) return false;
            }
            return true;
        });
    }, [documents, selectedCategory, debouncedSearch]);

    return (
        <div className="space-y-4 font-sans">
            {/* 1. Bandeau de conformité compact (si pièces obligatoires manquantes) */}
            {canWrite && obligatoiresManquants.length > 0 && (
                <div className="py-2.5 px-3.5 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 flex flex-col sm:flex-row sm:items-center justify-between gap-2.5 text-xs text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)]">
                    <div className="flex items-center gap-2 min-w-0">
                        <AlertTriangle size={15} className="text-[var(--color-warning-500)] shrink-0" />
                        <span className="font-bold uppercase tracking-wider text-[11px]">Conformité RH :</span>
                        <span className="text-[var(--color-fg)]">
                            {obligatoiresManquants.length} pièce{obligatoiresManquants.length > 1 ? 's' : ''} obligatoire{obligatoiresManquants.length > 1 ? 's' : ''} attendue{obligatoiresManquants.length > 1 ? 's' : ''}
                        </span>
                    </div>
                    <div className="flex flex-wrap items-center gap-1.5 shrink-0">
                        {obligatoiresManquants.map(m => (
                            <Button
                                key={m.idTypeDocument}
                                type="button"
                                variant="outline"
                                size="xs"
                                className="h-6 px-2 text-[11px] bg-[var(--color-surface)] border-[var(--color-warning-500)]/40 hover:bg-[var(--color-warning-500)]/10"
                                onClick={() => onOpenUploadModal(m.idTypeDocument)}
                                iconLeft={<Upload size={11} />}
                            >
                                Verser {m.libelle}
                            </Button>
                        ))}
                    </div>
                </div>
            )}

            {/* 2. Carte Principale des Documents RH */}
            <Card shadow="sm">
                <CardHeader className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3">
                    <div>
                        <div className="flex items-center gap-2.5 flex-wrap">
                            <CardTitle size="sm" className="uppercase tracking-wider flex items-center gap-2">
                                <FileText size={15} className="text-[var(--color-brand-500)]" />
                                Pièces Justificatives &amp; Documents RH ({documents.length})
                            </CardTitle>
                            <Badge variant={isConforme ? "success" : "warning"} size="xs" dot>
                                {isConforme ? "Dossier conforme" : `${obligatoiresManquants.length} manquant(s)`}
                            </Badge>
                        </div>
                        <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                            Pièces administratives, contrats et justificatifs légaux du collaborateur
                        </p>
                    </div>

                    {canWrite && (
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

                {/* Barre de Recherche & Filtres essentiels */}
                {documents.length > 0 && (
                    <div className="px-5 py-2.5 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/40 flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
                        <div className="relative flex-1 max-w-xs">
                            <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)] pointer-events-none" />
                            <input
                                type="text"
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                                placeholder="Filtrer un document, contrat..."
                                className="w-full text-xs pl-7 pr-3 py-1 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] focus:border-[var(--color-brand-500)] focus:outline-none transition-colors rounded-none placeholder:text-[var(--color-fg-subtle)]"
                            />
                        </div>

                        <div className="flex items-center gap-1.5">
                            <button
                                type="button"
                                onClick={() => setSelectedCategory('ALL')}
                                className={`px-2 py-0.5 text-xs font-semibold rounded-none transition-colors border ${
                                    selectedCategory === 'ALL'
                                        ? 'bg-[var(--color-brand-500)] text-white border-[var(--color-brand-500)]'
                                        : 'bg-[var(--color-surface)] text-[var(--color-fg-muted)] border-[var(--color-border-subtle)] hover:text-[var(--color-fg)]'
                                }`}
                            >
                                Tous ({documents.length})
                            </button>
                            {countObligatoires > 0 && (
                                <button
                                    type="button"
                                    onClick={() => setSelectedCategory('OBLIGATOIRES')}
                                    className={`px-2 py-0.5 text-xs font-semibold rounded-none transition-colors border ${
                                        selectedCategory === 'OBLIGATOIRES'
                                            ? 'bg-[var(--color-brand-500)] text-white border-[var(--color-brand-500)]'
                                            : 'bg-[var(--color-surface)] text-[var(--color-fg-muted)] border-[var(--color-border-subtle)] hover:text-[var(--color-fg)]'
                                    }`}
                                >
                                    Obligatoires ({countObligatoires})
                                </button>
                            )}
                            {countContrats > 0 && (
                                <button
                                    type="button"
                                    onClick={() => setSelectedCategory('CONTRATS')}
                                    className={`px-2 py-0.5 text-xs font-semibold rounded-none transition-colors border ${
                                        selectedCategory === 'CONTRATS'
                                            ? 'bg-[var(--color-brand-500)] text-white border-[var(--color-brand-500)]'
                                            : 'bg-[var(--color-surface)] text-[var(--color-fg-muted)] border-[var(--color-border-subtle)] hover:text-[var(--color-fg)]'
                                    }`}
                                >
                                    Contrats ({countContrats})
                                </button>
                            )}
                        </div>
                    </div>
                )}

                <CardBody className="p-0">
                    {loadingDocs ? (
                        <div className="p-5">
                            <SkeletonTable rows={4} columns={5} />
                        </div>
                    ) : documents.length === 0 ? (
                        <div className="p-5">
                            <EmptyState
                                icon={FileText}
                                title="Aucun document RH déposé"
                                description="Ajoutez les pièces justificatives ou contrats du collaborateur pour compléter son dossier."
                                actionLabel={canWrite ? "Téléverser un Document" : undefined}
                                onAction={canWrite ? () => onOpenUploadModal() : undefined}
                                actionIcon={<Upload size={14} />}
                                compact
                            />
                        </div>
                    ) : filteredDocuments.length === 0 ? (
                        <div className="p-5">
                            <EmptyState
                                icon={Search}
                                title="Aucun résultat pour cette recherche"
                                description="Aucun document ne correspond à vos critères."
                                actionLabel="Réinitialiser"
                                onAction={() => { setSearchQuery(''); setSelectedCategory('ALL'); }}
                                compact
                            />
                        </div>
                    ) : (
                        <Table bordered={false}>
                            <Thead>
                                <Tr>
                                    <Th>Document &amp; Intitulé</Th>
                                    <Th>Catégorie &amp; Statut</Th>
                                    <Th>Date &amp; Taille</Th>
                                    <Th align="right">Actions</Th>
                                </Tr>
                            </Thead>
                            <Tbody>
                                {filteredDocuments.map((doc) => (
                                    <Tr key={doc.idDocument} className="hover:bg-[var(--color-bg-subtle)]/40 transition-colors">
                                        <Td>
                                            <div className="flex items-center gap-2.5">
                                                <div className="w-7 h-7 bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] flex items-center justify-center shrink-0">
                                                    <FileText size={15} />
                                                </div>
                                                <div className="min-w-0">
                                                    <p className="font-bold text-xs text-[var(--color-fg)] truncate max-w-sm" title={doc.nomOriginal}>
                                                        {doc.nomOriginal}
                                                    </p>
                                                    <p className="text-[11px] text-[var(--color-fg-muted)] truncate">
                                                        {doc.typeDocumentLibelle}
                                                        {doc.numeroContrat && ` · ${doc.numeroContrat}`}
                                                        {doc.codeTypeContrat && ` · ${doc.codeTypeContrat}`}
                                                    </p>
                                                </div>
                                            </div>
                                        </Td>
                                        <Td>
                                            <div className="flex items-center gap-1.5 flex-wrap">
                                                {doc.typeDocumentObligatoire ? (
                                                    <Badge variant="warning" size="xs">Obligatoire</Badge>
                                                ) : (
                                                    <Badge variant="default" size="xs">{doc.categorie || 'Dossier RH'}</Badge>
                                                )}
                                                {doc.contratSigne && (
                                                    <Badge variant="success" size="xs">Signé ✓</Badge>
                                                )}
                                            </div>
                                        </Td>
                                        <Td isData className="text-xs">
                                            <span className="font-mono text-[var(--color-fg)]">{fmtDate(doc.dateDocument)}</span>
                                            <span className="text-[11px] text-[var(--color-fg-muted)] block font-mono">
                                                {formatBytes(doc.tailleOctet)}
                                            </span>
                                        </Td>
                                        <Td align="right">
                                            <div className="flex justify-end items-center gap-1">
                                                <Button
                                                    type="button"
                                                    variant="ghost"
                                                    size="xs"
                                                    onClick={() => onDownloadDoc(doc.idDocument, doc.nomOriginal)}
                                                    iconLeft={<Download size={13} className="text-[var(--color-brand-600)]" />}
                                                    title="Visualiser / Télécharger"
                                                >
                                                    Ouvrir
                                                </Button>
                                                {canWrite && (
                                                    <Button
                                                        type="button"
                                                        variant="ghost"
                                                        size="xs"
                                                        onClick={() => onDeleteDoc(doc.idDocument, doc.nomOriginal)}
                                                        iconLeft={<Trash2 size={13} className="text-[var(--color-danger-500)]" />}
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
