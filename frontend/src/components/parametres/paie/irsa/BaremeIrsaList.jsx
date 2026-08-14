import { Plus, Edit2, Percent } from 'lucide-react';
import Button from '@/components/ui/Button';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/Table';
import { fmtDate, fmtMoney } from '@/utils/paieUtils';

export default function BaremeIrsaList({
    tranchesIrsa = [],
    onOpenCreate,
    onOpenEdit,
}) {
    return (
        <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm overflow-hidden space-y-4">
            <div className="p-4 flex justify-between items-center border-b border-[var(--color-border-subtle)]">
                <div>
                    <h3 className="font-bold text-[var(--color-fg)] text-base flex items-center gap-2">
                        <Percent size={18} className="text-[var(--color-brand-500)]" />
                        Barème IRSA (Impôt sur les Revenus Salariaux & Assimilés)
                    </h3>
                    <p className="text-xs text-[var(--color-fg-muted)]">
                        Tranches d'imposition progressives selon la législation fiscale malagasy.
                    </p>
                </div>
                <Button onClick={onOpenCreate} variant="primary" size="sm" iconLeft={<Plus size={16} />}>
                    Nouvelle Tranche IRSA
                </Button>
            </div>

            <Table>
                <Thead>
                    <Th>Tranche N°</Th>
                    <Th className="text-right">Seuil Minimum (Ar)</Th>
                    <Th className="text-right">Seuil Maximum (Ar)</Th>
                    <Th className="text-right">Taux d'imposition</Th>
                    <Th>Validité</Th>
                    <Th className="text-right">Actions</Th>
                </Thead>
                <Tbody>
                    {tranchesIrsa.length === 0 ? (
                        <Tr>
                            <Td colSpan={6} className="py-8 text-center text-[var(--color-fg-muted)] text-xs">
                                Aucune tranche IRSA configurée.
                            </Td>
                        </Tr>
                    ) : (
                        tranchesIrsa.map((t) => (
                            <Tr key={t.idTranche}>
                                <Td isData className="font-bold text-[var(--color-brand-500)]">
                                    Tranche {t.numeroTranche}
                                </Td>
                                <Td isData className="text-right font-semibold">
                                    {fmtMoney(t.trancheMin || 0)}
                                </Td>
                                <Td isData className="text-right font-semibold">
                                    {t.trancheMax ? fmtMoney(t.trancheMax) : 'Illimité'}
                                </Td>
                                <Td isData className="text-right font-bold text-[var(--color-danger-500)]">
                                    {t.taux}%
                                </Td>
                                <Td isData muted className="text-xs">
                                    Depuis le {fmtDate(t.dateDebut)} {t.dateFin ? `jusqu'au ${fmtDate(t.dateFin)}` : ''}
                                </Td>
                                <Td className="text-right">
                                    <button
                                        onClick={() => onOpenEdit(t)}
                                        className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none transition-all cursor-pointer"
                                        title="Modifier"
                                    >
                                        <Edit2 size={16} />
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