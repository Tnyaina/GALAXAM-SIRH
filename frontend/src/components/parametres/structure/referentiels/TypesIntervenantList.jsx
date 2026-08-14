import { Plus, Edit2, UserCheck } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';

export default function TypesIntervenantList({
    typesIntervenant = [],
    onOpenCreate,
    onOpenEdit,
}) {
    return (
        <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm p-6 space-y-4">
            <div className="flex justify-between items-center pb-3 border-b border-[var(--color-border-subtle)]">
                <div>
                    <h3 className="font-bold text-[var(--color-fg)] text-base flex items-center gap-2">
                        <UserCheck size={18} className="text-[var(--color-brand-500)]" />
                        Types d'Intervenant ({typesIntervenant.length})
                    </h3>
                    <p className="text-xs text-[var(--color-fg-muted)]">Salarié national, Expatrié, Consultant, Stagiaire...</p>
                </div>
                <Button onClick={onOpenCreate} variant="primary" size="sm" iconLeft={<Plus size={16} />}>
                    Nouveau Type
                </Button>
            </div>

            <Table>
                <Thead>
                    <Th>Code</Th>
                    <Th>Libellé</Th>
                    <Th className="text-center">IRSA</Th>
                    <Th className="text-center">ISI 5%</Th>
                    <Th className="text-center">CNaPS</Th>
                    <Th className="text-center">Santé</Th>
                    <Th className="text-right">Actions</Th>
                </Thead>
                <Tbody>
                    {typesIntervenant.length > 0 ? (
                        typesIntervenant.map(ti => (
                            <Tr key={ti.idTypeIntervenant}>
                                <Td isData className="font-bold text-[var(--color-brand-500)]">{ti.code}</Td>
                                <Td className="font-semibold text-[var(--color-fg)]">{ti.libelle}</Td>
                                <Td className="text-center">
                                    <Badge variant={ti.soumisIrsa ? 'success' : 'default'} size="sm">
                                        {ti.soumisIrsa ? 'Oui' : 'Non'}
                                    </Badge>
                                </Td>
                                <Td className="text-center">
                                    <Badge variant={ti.soumisIsi5 ? 'warning' : 'default'} size="sm">
                                        {ti.soumisIsi5 ? 'Oui' : 'Non'}
                                    </Badge>
                                </Td>
                                <Td className="text-center">
                                    <Badge variant={ti.soumisCnaps ? 'success' : 'default'} size="sm">
                                        {ti.soumisCnaps ? 'Oui' : 'Non'}
                                    </Badge>
                                </Td>
                                <Td className="text-center">
                                    <Badge variant={ti.soumisSante ? 'success' : 'default'} size="sm">
                                        {ti.soumisSante ? 'Oui' : 'Non'}
                                    </Badge>
                                </Td>
                                <Td className="text-right">
                                    <button
                                        onClick={() => onOpenEdit(ti)}
                                        className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none transition-all cursor-pointer"
                                        title="Modifier"
                                    >
                                        <Edit2 size={16} />
                                    </button>
                                </Td>
                            </Tr>
                        ))
                    ) : (
                        <Tr>
                            <Td colSpan={7} className="py-8 text-center text-[var(--color-fg-muted)] text-xs">
                                Aucun type d'intervenant configuré.
                            </Td>
                        </Tr>
                    )}
                </Tbody>
            </Table>
        </div>
    );
}