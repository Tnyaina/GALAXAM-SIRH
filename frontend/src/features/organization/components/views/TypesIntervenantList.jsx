import { Edit2, Power, Check, UserCheck } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';

export default function TypesIntervenantList({
    typesIntervenant = [],
    onOpenEdit,
    onToggleStatus,
}) {
    return (
        <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm p-6 space-y-4">
            <div className="flex justify-between items-center pb-3 border-b border-[var(--color-border-subtle)]">
                <div>
                    <h3 className="font-bold text-[var(--color-fg)] text-base flex items-center gap-2">
                        <UserCheck size={18} className="text-[var(--color-brand-500)]" />
                        Régimes Fiscaux & Sociaux des Intervenants ({typesIntervenant.length})
                    </h3>
                    <p className="text-xs text-[var(--color-fg-muted)]">
                        Assujettissements des salariés. Les indépendants sont réglés sur facture.
                    </p>
                </div>
            </div>

            <Table bordered={false}>
                <Thead>
                    <Th>Code</Th>
                    <Th>Libellé</Th>
                    <Th className="text-center">IRSA</Th>
                    <Th className="text-center">CNaPS</Th>
                    <Th className="text-center">Santé</Th>
                    <Th>Statut</Th>
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
                                    <Badge variant={ti.soumisCnaps ? 'success' : 'default'} size="sm">
                                        {ti.soumisCnaps ? 'Oui' : 'Non'}
                                    </Badge>
                                </Td>
                                <Td className="text-center">
                                    <Badge variant={ti.soumisSante ? 'success' : 'default'} size="sm">
                                        {ti.soumisSante ? 'Oui' : 'Non'}
                                    </Badge>
                                </Td>
                                <Td>
                                    <Badge variant={ti.estActif !== false ? 'success' : 'default'} size="sm" dot>
                                        {ti.estActif !== false ? "Actif" : "Désactivé"}
                                    </Badge>
                                </Td>
                                <Td className="text-right">
                                    <div className="flex justify-end items-center gap-1.5">
                                        <button
                                            onClick={() => onOpenEdit(ti)}
                                            className="min-h-[44px] min-w-[44px] inline-flex items-center justify-center p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                            title="Modifier"
                                            aria-label={`Modifier ${ti.libelle}`}
                                        >
                                            <Edit2 size={15} />
                                        </button>
                                        {onToggleStatus && (
                                            <button
                                                onClick={() => onToggleStatus(ti)}
                                                className={`min-h-[44px] min-w-[44px] inline-flex items-center justify-center p-1.5 rounded-none cursor-pointer transition-colors ${
                                                    ti.estActif !== false
                                                        ? 'text-[var(--color-fg-muted)] hover:text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10'
                                                        : 'text-[var(--color-fg-muted)] hover:text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10'
                                                }`}
                                                title={ti.estActif !== false ? "Désactiver" : "Réactiver"}
                                                aria-label={ti.estActif !== false ? `Désactiver ${ti.libelle}` : `Activer ${ti.libelle}`}
                                            >
                                                {ti.estActif !== false ? <Power size={15} /> : <Check size={15} />}
                                            </button>
                                        )}
                                    </div>
                                </Td>
                            </Tr>
                        ))
                    ) : (
                        <Tr>
                            <Td colSpan={7} className="p-0">
                                <EmptyState title="Aucun type d'intervenant" description="Aucun type d'intervenant configuré." />
                            </Td>
                        </Tr>
                    )}
                </Tbody>
            </Table>
        </div>
    );
}
