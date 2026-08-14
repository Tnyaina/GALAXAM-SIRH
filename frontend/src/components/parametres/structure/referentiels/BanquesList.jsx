import { Plus, Edit2, Landmark } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';

export default function BanquesList({
    banques = [],
    onOpenCreate,
    onOpenEdit,
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
                    <Th className="text-right">Actions</Th>
                </Thead>
                <Tbody>
                    {banques.length > 0 ? (
                        banques.map(b => (
                            <Tr key={b.idBanque}>
                                <Td isData className="font-bold text-[var(--color-brand-500)]">{b.codeBanque}</Td>
                                <Td className="font-semibold text-[var(--color-fg)]">{b.nomBanque}</Td>
                                <Td isData muted className="text-xs">{b.codeSwift || '—'}</Td>
                                <Td className="text-right">
                                    <button
                                        onClick={() => onOpenEdit(b)}
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
                            <Td colSpan={4} className="py-8 text-center text-[var(--color-fg-muted)] text-xs">
                                Aucun établissement bancaire configuré.
                            </Td>
                        </Tr>
                    )}
                </Tbody>
            </Table>
        </div>
    );
}