import { Plus, Edit2, Calendar } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { fmtDate } from '@/utils/paieUtils';

export default function JoursFeriesList({
    feries = [],
    selectedYear,
    search,
    onOpenCreate,
    onOpenEdit,
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

            <Table>
                <Thead>
                    <Th>Date</Th>
                    <Th>Libellé de la Fête / Commémoration</Th>
                    <Th className="text-center">Chômé & Payé</Th>
                    <Th className="text-center">Portée</Th>
                    <Th className="text-right">Actions</Th>
                </Thead>
                <Tbody>
                    {feries.length === 0 ? (
                        <Tr>
                            <Td colSpan={5} className="py-8 text-center text-[var(--color-fg-muted)] text-xs">
                                Aucun jour férié trouvé pour {selectedYear} {search ? `correspondant à "${search}"` : ''}.
                            </Td>
                        </Tr>
                    ) : (
                        feries.map((f) => (
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
                                    <button
                                        onClick={() => onOpenEdit(f)}
                                        className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer"
                                        title="Modifier"
                                    >
                                        <Edit2 size={15} />
                                    </button>
                                </Td>
                            </Tr>
                        ))
                    )}
                </Tbody>
            </Table>
        </div>
    );
}