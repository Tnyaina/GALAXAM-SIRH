import { Plus, Power, Check, MapPin, Info } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';

export default function SiteCotisationsList({
    siteCotisations = [],
    onOpenCreate,
    onToggleStatus,
}) {
    return (
        <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm overflow-hidden space-y-4">
            <div className="p-4 flex justify-between items-center border-b border-[var(--color-border-subtle)]">
                <div>
                    <h3 className="font-bold text-[var(--color-fg)] text-base flex items-center gap-2">
                        <MapPin size={18} className="text-[var(--color-brand-500)]" />
                        Cotisations Rattachées aux Sites d'Exploitation ({siteCotisations.length})
                    </h3>
                    <p className="text-xs text-[var(--color-fg-muted)]">
                        CNaPS et organisme sanitaire (OSTIE / OMSI) — affectés par site géographique.
                    </p>
                </div>
                <Button onClick={onOpenCreate} variant="primary" size="sm" iconLeft={<Plus size={16} />}>
                    Associer un Site
                </Button>
            </div>


            {siteCotisations.length === 0 ? (
                <div className="p-8">
                    <EmptyState
                        icon={MapPin}
                        title="Aucune association"
                        description="Aucune association site - cotisation n'a été enregistrée."
                        actionLabel="Associer un Site"
                        onAction={onOpenCreate}
                    />
                </div>
            ) : (
                <Table>
                    <Thead>
                        <Th>Nom du Site</Th>
                        <Th>Cotisation Rattachée</Th>
                        <Th>Statut</Th>
                        <Th className="text-right">Actions</Th>
                    </Thead>
                    <Tbody>
                        {siteCotisations.map((sc) => (
                            <Tr key={sc.idSiteCotisation}>
                                <Td className="font-bold text-[var(--color-fg)]">
                                    {sc.siteLibelle} {sc.siteCode ? `(${sc.siteCode})` : ''}
                                </Td>
                                <Td isData className="font-semibold text-[var(--color-brand-500)]">
                                    {sc.cotisationCode} - {sc.cotisationLibelle}
                                </Td>
                                <Td>
                                    <Badge
                                        variant={sc.estActif ? 'success' : 'default'}
                                        size="sm"
                                        dot
                                    >
                                        {sc.estActif ? 'Actif' : 'Désactivé'}
                                    </Badge>
                                </Td>
                                <Td className="text-right">
                                    {sc.estActif ? (
                                        <button
                                            onClick={() => onToggleStatus(sc)}
                                            className="p-1.5 text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10 rounded-none  cursor-pointer"
                                            title="Désactiver l'association"
                                        >
                                            <Power size={16} />
                                        </button>
                                    ) : (
                                        <button
                                            onClick={() => onToggleStatus(sc)}
                                            className="p-1.5 text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10 rounded-none  cursor-pointer"
                                            title="Réactiver l'association"
                                        >
                                            <Check size={16} />
                                        </button>
                                    )}
                                </Td>
                            </Tr>
                        ))}
                    </Tbody>
                </Table>
            )}
        </div>
    );
}