import SharedButton from '@/components/ui/forms/Button';
import { Plus, Edit2, Power, Check, Calendar } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { fmtDate } from '@/utils/dateUtils';
import EmptyState from '@/components/ui/data-display/EmptyState';

export default function JoursFeriesList({
    feries = [],
    selectedYear,
    search,
    onOpenCreate,
    onOpenEdit,
    onToggleStatus,
    _onDelete,
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
                {feries.length > 0 && (
                    <Button onClick={onOpenCreate} variant="primary" size="sm" iconLeft={<Plus size={16} />}>
                        Ajouter un Jour Férié
                    </Button>
                )}
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
                <Table bordered={false}>
                    <Thead>
                        <Th>Date</Th>
                        <Th>Libellé de la Fête / Commémoration</Th>
                        <Th className="text-center">Chômé & Payé</Th>
                        <Th className="text-center">Portée</Th>
                        <Th className="text-center">Statut</Th>
                        <Th className="text-right">Actions</Th>
                    </Thead>
                    <Tbody>
                        {feries.map((f) => {
                            const isActif = f.estActif !== false;
                            return (
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
                                    <Td className="text-center">
                                        <Badge variant={isActif ? 'success' : 'default'} size="sm" dot>
                                            {isActif ? "Actif" : "Désactivé"}
                                        </Badge>
                                    </Td>
                                    <Td className="text-right">
                                        <div className="flex items-center justify-end gap-1">
                                            <SharedButton plain
                                                onClick={() => onOpenEdit(f)}
                                                className="ui-button-icon-xs inline-flex items-center justify-center p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                                title="Modifier"
                                                aria-label={`Modifier ${f.libelle}`}
                                            >
                                                <Edit2 size={15} />
                                            </SharedButton>
                                            {onToggleStatus && (
                                                isActif ? (
                                                    <SharedButton plain
                                                        onClick={() => onToggleStatus(f)}
                                                        className="ui-button-icon-xs inline-flex items-center justify-center p-1.5 text-[var(--color-danger-500)] hover:text-[var(--color-danger-600)] hover:bg-[var(--color-danger-500)]/10 rounded-none cursor-pointer transition-colors"
                                                        title="Désactiver ce jour férié"
                                                        aria-label={`Désactiver ${f.libelle}`}
                                                    >
                                                        <Power size={15} />
                                                    </SharedButton>
                                                ) : (
                                                    <SharedButton plain
                                                        onClick={() => onToggleStatus(f)}
                                                        className="ui-button-icon-xs inline-flex items-center justify-center p-1.5 text-[var(--color-success-600)] hover:text-[var(--color-success-700)] hover:bg-[var(--color-success-600)]/10 rounded-none cursor-pointer transition-colors"
                                                        title="Réactiver ce jour férié"
                                                        aria-label={`Réactiver ${f.libelle}`}
                                                    >
                                                        <Check size={15} />
                                                    </SharedButton>
                                                )
                                            )}
                                        </div>
                                    </Td>
                                </Tr>
                            );
                        })}
                    </Tbody>
                </Table>
            )}
        </div>
    );
}