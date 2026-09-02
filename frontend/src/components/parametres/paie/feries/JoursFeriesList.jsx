import { Plus, Edit2, Trash2, Calendar } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { fmtDate } from '@/utils/paieUtils';
import EmptyState from '@/components/ui/data-display/EmptyState';

export default function JoursFeriesList({
    feries = [],
    selectedYear,
    search,
    onOpenCreate,
    onOpenEdit,
    onDelete,
}) {
    return (
        <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm p-6 space-y-4">
            <div className="flex justify-between items-center pb-3 border-b border-[var(--color-border-subtle)]">
                <div>
                    <h3 className="font-bold text-[var(--color-fg)] text-base flex items-center gap-2">
                        <Calendar size={18} className="text-[var(--color-brand-500)]" />
                        Jours Fériés Officiels ({feries.length})
                    </h3>
                    <p className="text-xs text-[var(--color-fg-muted)]">
                        Jours fériés chômés et payés de l'année {selectedYear}
                    </p>
                </div>
                <Button onClick={onOpenCreate} variant="primary" size="sm" iconLeft={<Plus size={16} />}>
                    Ajouter un Jour Férié
                </Button>
            </div>

            {feries.length === 0 ? (
                <div className="p-8">
                    <EmptyState
                        icon={Calendar}
                        title="Aucun jour férié"
                        description={`Aucun jour férié trouvé pour ${selectedYear} ${search ? `correspondant à "${search}"` : ''}.`}
                        actionLabel="Ajouter un Jour Férié"
                        onAction={onOpenCreate}
                    />
                </div>
            ) : (
                <Table>
                    <Thead>
                        <Th>Date</Th>
                        <Th>Libellé de la Fête / Commémoration</Th>
                        <Th className="text-center">Chômé & Payé</Th>
                        <Th className="text-center">Portée</Th>
                        <Th className="text-right">Actions</Th>
                    </Thead>
                    <Tbody>
                        {feries.map((f) => (
                            <Tr key={f.idFerie}>
                                <Td isData className="font-bold text-[var(--color-brand-500)]">
                                    {fmtDate(f.dateFerie)}
                                </Td>
                                <Td className="font-semibold text-[var(--color-fg)]">
                                    {f.libelle}
                                </Td>
                                <Td className="text-center">
                                    <Badge variant={f.estChome ? 'success' : 'default'} size="sm">
                                        {f.estChome ? "Oui (Chômé)" : "Non (Travaillé)"}
                                    </Badge>
                                </Td>
                                <Td className="text-center">
                                    <Badge variant={f.estNational !== false ? 'primary' : 'warning'} size="sm">
                                        {f.estNational !== false ? "National (Tous sites)" : "Local / Régional"}
                                    </Badge>
                                </Td>
                                <Td className="text-right">
                                    <div className="flex items-center justify-end gap-1">
                                        <button
                                            onClick={() => onOpenEdit(f)}
                                            className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer"
                                            title="Modifier"
                                        >
                                            <Edit2 size={15} />
                                        </button>
                                        <button
                                            onClick={() => onDelete && onDelete(f)}
                                            className="p-1.5 text-[var(--color-danger-500)] hover:text-[var(--color-danger-600)] hover:bg-[var(--color-danger-500)]/10 rounded-none cursor-pointer"
                                            title="Supprimer ce jour férié"
                                        >
                                            <Trash2 size={15} />
                                        </button>
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