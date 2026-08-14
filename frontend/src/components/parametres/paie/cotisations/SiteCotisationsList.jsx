import { Plus, Power, Check, MapPin } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';

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
                        Associez des règles de cotisation spécifiques selon les sites géographiques.
                    </p>
                </div>
                <Button onClick={onOpenCreate} variant="primary" size="sm" iconLeft={<Plus size={16} />}>
                    Associer un Site
                </Button>
            </div>

            <Table>
                <Thead>
                    <Th>Nom du Site</Th>
                    <Th>Filiale</Th>
                    <Th>Cotisation Rattachée</Th>
                    <Th>Statut</Th>
                    <Th className="text-right">Actions</Th>
                </Thead>
                <Tbody>
                    {siteCotisations.length === 0 ? (
                        <Tr>
                            <Td colSpan={5} className="py-8 text-center text-[var(--color-fg-muted)] text-xs">
                                Aucune association site - cotisation enregistrée.
                            </Td>
                        </Tr>
                    ) : (
                        siteCotisations.map((sc) => (
                            <Tr key={sc.idSiteCotisation}>
                                <Td className="font-bold text-[var(--color-fg)]">
                                    {sc.siteLibelle} {sc.siteCode ? `(${sc.siteCode})` : ''}
                                </Td>
                                <Td muted className="text-xs font-semibold">
                                    {sc.filialeRaisonSociale || '-'}
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
                                            className="p-1.5 text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10 rounded-none transition-all cursor-pointer"
                                            title="Désactiver l'association"
                                        >
                                            <Power size={16} />
                                        </button>
                                    ) : (
                                        <button
                                            onClick={() => onToggleStatus(sc)}
                                            className="p-1.5 text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10 rounded-none transition-all cursor-pointer"
                                            title="Réactiver l'association"
                                        >
                                            <Check size={16} />
                                        </button>
                                    )}
                                </Td>
                            </Tr>
                        ))
                    )}
                </Tbody>
            </Table>
        </div>
    );
}