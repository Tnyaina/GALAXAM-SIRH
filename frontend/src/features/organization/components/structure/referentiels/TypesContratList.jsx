import { Edit2, Power, FileText } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';

export default function TypesContratList({
    typesContrat = [],
    onOpenEdit,
    onToggleStatus,
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
            </div>

            <Table>
                <Thead>
                    <Th>Code</Th>
                    <Th>Libellé</Th>
                    <Th className="text-center">Exige Date Fin</Th>
                    <Th>Statut</Th>
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
                                <Td>
                                    <Badge variant={tc.estActif !== false ? 'success' : 'default'} size="sm" dot>
                                        {tc.estActif !== false ? "Actif" : "Désactivé"}
                                    </Badge>
                                </Td>
                                <Td className="text-right">
                                    <div className="flex justify-end items-center gap-1.5">
                                        <button
                                            onClick={() => onOpenEdit(tc)}
                                            className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                            title="Modifier"
                                            aria-label={`Modifier ${tc.libelle}`}
                                        >
                                            <Edit2 size={15} />
                                        </button>
                                        {onToggleStatus && (
                                            <button
                                                onClick={() => onToggleStatus(tc)}
                                                className={`p-1.5 rounded-none cursor-pointer transition-colors ${
                                                    tc.estActif !== false
                                                        ? 'text-[var(--color-fg-muted)] hover:text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10'
                                                        : 'text-[var(--color-fg-muted)] hover:text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10'
                                                }`}
                                                title={tc.estActif !== false ? "Désactiver" : "Réactiver"}
                                                aria-label={tc.estActif !== false ? `Désactiver ${tc.libelle}` : `Activer ${tc.libelle}`}
                                            >
                                                <Power size={15} />
                                            </button>
                                        )}
                                    </div>
                                </Td>
                            </Tr>
                        ))
                    ) : (
                        <Tr>
                            <Td colSpan={5} className="p-0">
                                <EmptyState title="Aucun type de contrat" description="Aucun type de contrat configuré." />
                            </Td>
                        </Tr>
                    )}
                </Tbody>
            </Table>
        </div>
    );
}