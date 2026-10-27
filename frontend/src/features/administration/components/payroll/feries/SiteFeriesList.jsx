import SharedButton from '@/components/ui/forms/Button';
import { useState } from 'react';
import { Plus, Trash2, MapPin } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { fmtDate } from '@/utils/dateUtils';
import EmptyState from '@/components/ui/data-display/EmptyState';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';

export default function SiteFeriesList({
    siteFeries = [],
    selectedYear,
    onOpenCreate,
    onDelete,
}) {
    const [deleteTarget, setDeleteTarget] = useState(null);

    return (
        <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm p-6 space-y-4">
            <div className="flex justify-between items-center pb-3 border-b border-[var(--color-border-subtle)]">
                <div>
                    <h3 className="font-bold text-[var(--color-fg)] text-base flex items-center gap-2">
                        <MapPin size={18} className="text-[var(--color-brand-500)]" />
                        Jours Fériés Régionaux par Site ({siteFeries.length})
                    </h3>
                    <p className="text-xs text-[var(--color-fg-muted)]">
                        Jours fériés spécifiques aux fêtes patronales ou régionales par site d'exploitation
                    </p>
                </div>
                {siteFeries.length > 0 && (
                    <Button onClick={onOpenCreate} variant="primary" size="sm" iconLeft={<Plus size={16} />}>
                        Affecter Férié à un Site
                    </Button>
                )}
            </div>

            {siteFeries.length === 0 ? (
                <div className="p-8">
                    <EmptyState
                        icon={MapPin}
                        title="Aucun jour férié régional"
                        description={`Aucune association de jour férié spécifique par site pour l'année ${selectedYear}.`}
                        actionLabel="Affecter Férié à un Site"
                        onAction={onOpenCreate}
                    />
                </div>
            ) : (
                <Table bordered={false}>
                    <Thead>
                        <Th>Site d'Exploitation</Th>
                        <Th>Date Férié</Th>
                        <Th>Fête / Événement</Th>
                        <Th className="text-center">Chômé</Th>
                        <Th className="text-right">Actions</Th>
                    </Thead>
                    <Tbody>
                        {siteFeries.map((sf) => (
                            <Tr key={sf.idSiteFerie}>
                                <Td className="font-bold text-[var(--color-fg)] flex items-center gap-2">
                                    <MapPin size={14} className="text-[var(--color-brand-500)]" />
                                    {sf.siteLibelle || `Site #${sf.idSite}`}
                                </Td>
                                <Td isData className="font-bold text-[var(--color-brand-500)]">
                                    {fmtDate(sf.ferieDate)}
                                </Td>
                                <Td className="font-medium text-[var(--color-fg)]">
                                    {sf.ferieLibelle || '-'}
                                </Td>
                                <Td className="text-center">
                                    <Badge variant={sf.estChome !== false ? 'success' : 'default'} size="sm">
                                        {sf.estChome !== false ? "Oui" : "Non"}
                                    </Badge>
                                </Td>
                                <Td className="text-right">
                                    <SharedButton plain
                                        onClick={() => setDeleteTarget(sf)}
                                        className="ui-button-icon-xs inline-flex items-center justify-center p-1.5 text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10 rounded-none cursor-pointer transition-colors"
                                        title="Supprimer l'association"
                                        aria-label={`Supprimer association ${sf.siteLibelle || sf.idSite} et ${sf.ferieLibelle || sf.ferieDate}`}
                                    >
                                        <Trash2 size={15} />
                                    </SharedButton>
                                </Td>
                            </Tr>
                        ))}
                    </Tbody>
                </Table>
            )}

            <ConfirmDialog
                isOpen={Boolean(deleteTarget)}
                onClose={() => setDeleteTarget(null)}
                onConfirm={() => {
                    if (deleteTarget) {
                        onDelete(deleteTarget.idSiteFerie);
                        setDeleteTarget(null);
                    }
                }}
                title="Supprimer l'association du jour férié ?"
                message={deleteTarget ? `Voulez-vous vraiment retirer le jour férié "${deleteTarget.ferieLibelle || deleteTarget.ferieDate}" pour le site "${deleteTarget.siteLibelle || deleteTarget.idSite}" ?` : ''}
                confirmLabel="Supprimer"
                cancelLabel="Annuler"
                variant="danger"
            />
        </div>
    );
}