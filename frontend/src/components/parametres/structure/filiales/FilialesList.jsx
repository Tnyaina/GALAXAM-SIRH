import { Edit2 } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';
export default function FilialesList({
    filiales = [],
    onOpenEdit,
    onToggleStatus,
}) {
    return (
        <Table>
            <Thead>
                <Th>Code</Th>
                <Th>Raison Sociale</Th>
                <Th>NIF</Th>
                <Th>STAT</Th>
                <Th>Seuil Alerte Effectif</Th>
                <Th>Statut</Th>
                <Th className="text-right">Actions</Th>
            </Thead>
            <Tbody>
                {filiales.length > 0 ? (
                    filiales.map((f) => (
                        <Tr key={f.idFiliale}>
                            <Td isData className="font-bold text-[var(--color-brand-500)]">{f.codeFiliale}</Td>
                            <Td className="font-bold text-[var(--color-fg)]">{f.raisonSociale}</Td>
                            <Td isData muted className="text-xs">{f.nif || '-'}</Td>
                            <Td isData muted className="text-xs">{f.stat || '-'}</Td>
                            <Td>
                                <span className="px-2 py-0.5 bg-[var(--color-bg-subtle)] text-[var(--color-fg)] font-data text-xs font-semibold rounded-none">
                                    {f.seuilEffectifAlerte ?? 19} pers.
                                </span>
                            </Td>
                            <Td>
                                <Badge variant={f.estActif ? 'success' : 'default'} size="sm" dot>
                                    {f.estActif ? "Actif" : "Désactivé"}
                                </Badge>
                            </Td>
                            <Td className="text-right">
                                <div className="flex justify-end items-center gap-2">
                                    <button
                                        onClick={() => onOpenEdit(f)}
                                        className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none  cursor-pointer"
                                        title="Modifier"
                                    >
                                        <Edit2 size={16} />
                                    </button>
                                    <button
                                        onClick={() => onToggleStatus(f)}
                                        className={`px-2 py-1 text-xs font-bold rounded-none  cursor-pointer uppercase ${
                                            f.estActif
                                                ? 'text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10'
                                                : 'text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10'
                                        }`}
                                    >
                                        {f.estActif ? "Désactiver" : "Activer"}
                                    </button>
                                </div>
                            </Td>
                        </Tr>
                    ))
                ) : (
                        <Tr>
                            <Td colSpan={7} className="p-0">
                                <EmptyState title="Aucune filiale" description="Aucune filiale enregistrée." />
                            </Td>
                        </Tr>
                )}
            </Tbody>
        </Table>
    );
}