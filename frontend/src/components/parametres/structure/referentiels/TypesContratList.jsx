import { Plus, Edit2, FileText } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';

export default function TypesContratList({
    typesContrat = [],
    onOpenCreate,
    onOpenEdit,
}) {
    return (
        <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm p-6 space-y-4">
            <div className="flex justify-between items-center pb-3 border-b border-[var(--color-border-subtle)]">
                <div>
                    <h3 className="font-bold text-[var(--color-fg)] text-base flex items-center gap-2">
                        <FileText size={18} className="text-[var(--color-brand-500)]" />
                        Types de Contrat ({typesContrat.length})
                    </h3>
                    <p className="text-xs text-[var(--color-fg-muted)]">CDI, CDD, Stage, Intérim, Prestation, etc.</p>
                </div>
                <Button onClick={onOpenCreate} variant="primary" size="sm" iconLeft={<Plus size={16} />}>
                    Nouveau Type
                </Button>
            </div>

            <Table>
                <Thead>
                    <Th>Code</Th>
                    <Th>Libellé</Th>
                    <Th className="text-center">Exige Date Fin</Th>
                    <Th className="text-right">Actions</Th>
                </Thead>
                <Tbody>
                    {typesContrat.length > 0 ? (
                        typesContrat.map(tc => (
                            <Tr key={tc.idTypeContrat}>
                                <Td isData className="font-bold text-[var(--color-brand-500)]">{tc.code}</Td>
                                <Td className="font-semibold text-[var(--color-fg)]">{tc.libelle}</Td>
                                <Td className="text-center">
                                    <Badge variant={tc.exigeDateFin ? 'warning' : 'default'} size="sm">
                                        {tc.exigeDateFin ? "Oui (CDD)" : "Non (CDI)"}
                                    </Badge>
                                </Td>
                                <Td className="text-right">
                                    <button
                                        onClick={() => onOpenEdit(tc)}
                                        className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none  cursor-pointer"
                                        title="Modifier"
                                    >
                                        <Edit2 size={16} />
                                    </button>
                                </Td>
                            </Tr>
                        ))
                    ) : (
                        <Tr>
                            <Td colSpan={4} className="py-8 text-center text-[var(--color-fg-muted)] text-xs">
                                Aucun type de contrat configuré.
                            </Td>
                        </Tr>
                    )}
                </Tbody>
            </Table>
        </div>
    );
}