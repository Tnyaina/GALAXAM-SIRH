import { Plus, Edit2, ShieldCheck } from 'lucide-react';
import Button from '@/components/ui/Button';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/Table';
import { fmtDate } from '@/utils/paieUtils';

export default function CotisationsList({
    cotisations = [],
    onOpenCreate,
    onOpenEdit,
}) {
    return (
        <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm overflow-hidden space-y-4">
            <div className="p-4 flex justify-between items-center border-b border-[var(--color-border-subtle)]">
                <div>
                    <h3 className="font-bold text-[var(--color-fg)] text-base flex items-center gap-2">
                        <ShieldCheck size={18} className="text-[var(--color-brand-500)]" />
                        Règles & Cotisations Sociales ({cotisations.length})
                    </h3>
                    <p className="text-xs text-[var(--color-fg-muted)]">
                        Taux salariaux et patronaux applicables pour CNaPS, Santé et autres organismes.
                    </p>
                </div>
                <Button onClick={onOpenCreate} variant="primary" size="sm" iconLeft={<Plus size={16} />}>
                    Nouvelle Cotisation
                </Button>
            </div>

            <Table>
                <Thead>
                    <Th>Code</Th>
                    <Th>Libellé</Th>
                    <Th className="text-right">Taux Salarial</Th>
                    <Th className="text-right">Taux Patronal</Th>
                    <Th className="text-right">Taux Total</Th>
                    <Th>Période d'Application</Th>
                    <Th className="text-right">Actions</Th>
                </Thead>
                <Tbody>
                    {cotisations.length === 0 ? (
                        <Tr>
                            <Td colSpan={7} className="py-8 text-center text-[var(--color-fg-muted)] text-xs">
                                Aucune cotisation enregistrée.
                            </Td>
                        </Tr>
                    ) : (
                        cotisations.map((c) => (
                            <Tr key={c.idTypeCotisation}>
                                <Td isData className="font-bold text-[var(--color-brand-500)]">
                                    {c.code}
                                </Td>
                                <Td className="font-semibold text-[var(--color-fg)]">
                                    {c.libelle}
                                </Td>
                                <Td isData className="text-right font-bold text-sky-500">
                                    {c.tauxSalarial}%
                                </Td>
                                <Td isData className="text-right font-bold text-amber-500">
                                    {c.tauxPatronal}%
                                </Td>
                                <Td isData className="text-right font-bold text-[var(--color-brand-500)]">
                                    {(Number(c.tauxSalarial || 0) + Number(c.tauxPatronal || 0)).toFixed(2)}%
                                </Td>
                                <Td isData muted className="text-xs">
                                    Du {fmtDate(c.dateDebut)} {c.dateFin ? `au ${fmtDate(c.dateFin)}` : '(Toujours actif)'}
                                </Td>
                                <Td className="text-right">
                                    <button
                                        onClick={() => onOpenEdit(c)}
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