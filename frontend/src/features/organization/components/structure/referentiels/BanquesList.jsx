import { Plus, Edit2, Power, Landmark } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';

export default function BanquesList({
    banques = [],
    onOpenCreate,
    onOpenEdit,
    onToggleStatus,
}) {
    return (
        <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm p-6 space-y-4">
            <div className="flex justify-between items-center pb-3 border-b border-[var(--color-border-subtle)]">
                <div>
                    <h3 className="font-bold text-[var(--color-fg)] text-base flex items-center gap-2">
                        <Landmark size={18} className="text-[var(--color-brand-500)]" />
                        Établissements Bancaires ({banques.length})
                    </h3>
                    <p className="text-xs text-[var(--color-fg-muted)]">BOA, BNI, BMOI, Société Générale, AccèsBanque...</p>
                </div>
                <Button onClick={onOpenCreate} variant="primary" size="sm" iconLeft={<Plus size={16} />}>
                    Nouvelle Banque
                </Button>
            </div>

            <Table>
                <Thead>
                    <Th>Code Banque</Th>
                    <Th>Nom de la Banque</Th>
                    <Th>Code SWIFT / BIC</Th>
                    <Th>Statut</Th>
                    <Th className="text-right">Actions</Th>
                </Thead>
                <Tbody>
                    {banques.length > 0 ? (
                        banques.map(b => (
                            <Tr key={b.idBanque}>
                                <Td isData className="font-bold text-[var(--color-brand-500)]">{b.codeBanque}</Td>
                                <Td className="font-semibold text-[var(--color-fg)]">{b.nomBanque}</Td>
                                <Td isData muted className="text-xs">{b.codeSwift || '—'}</Td>
                                <Td>
                                    <Badge variant={b.estActif !== false ? 'success' : 'default'} size="sm" dot>
                                        {b.estActif !== false ? "Actif" : "Désactivé"}
                                    </Badge>
                                </Td>
                                <Td className="text-right">
                                    <div className="flex justify-end items-center gap-1.5">
                                        <button
                                            onClick={() => onOpenEdit(b)}
                                            className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                            title="Modifier"
                                            aria-label={`Modifier ${b.nomBanque}`}
                                        >
                                            <Edit2 size={15} />
                                        </button>
                                        {onToggleStatus && (
                                            <button
                                                onClick={() => onToggleStatus(b)}
                                                className={`p-1.5 rounded-none cursor-pointer transition-colors ${
                                                    b.estActif !== false
                                                        ? 'text-[var(--color-fg-muted)] hover:text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10'
                                                        : 'text-[var(--color-fg-muted)] hover:text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10'
                                                }`}
                                                title={b.estActif !== false ? "Désactiver" : "Réactiver"}
                                                aria-label={b.estActif !== false ? `Désactiver ${b.nomBanque}` : `Activer ${b.nomBanque}`}
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
                                <EmptyState title="Aucune banque" description="Aucun établissement bancaire configuré." />
                            </Td>
                        </Tr>
                    )}
                </Tbody>
            </Table>
        </div>
    );
}