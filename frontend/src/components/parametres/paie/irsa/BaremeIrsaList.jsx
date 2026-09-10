import { Plus, Edit2, Percent, CheckCircle2, Clock, Archive } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { fmtDate, fmtMoney } from '@/utils/paieUtils';
import EmptyState from '@/components/ui/data-display/EmptyState';

// ---- Helpers ----
function getStatutGroup(dateDebut, dateFin) {
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const debut = dateDebut ? new Date(dateDebut) : null;
    if (debut) debut.setHours(0, 0, 0, 0);
    const fin = dateFin ? new Date(dateFin) : null;
    if (fin) fin.setHours(23, 59, 59, 999);
    if (!debut) return 'INCONNU';
    if (debut > today) return 'PROGRAMME';
    if (!fin || fin >= today) return 'EN_VIGUEUR';
    return 'ARCHIVE';
}

const STATUT_META = {
    EN_VIGUEUR: { label: '● En vigueur', cls: 'text-[var(--color-success-600)] bg-[var(--color-success-500)]/10 border-[var(--color-success-500)]/25' },
    PROGRAMME:  { label: '◆ Programmé',  cls: 'text-[var(--color-brand-500)] bg-[var(--color-brand-500)]/10 border-[var(--color-brand-500)]/25' },
    ARCHIVE:    { label: '○ Archivé',    cls: 'text-[var(--color-fg-muted)] bg-[var(--color-bg-subtle)] border-[var(--color-border-subtle)]' },
};

function groupByExercice(tranches) {
    const map = new Map();
    for (const t of tranches) {
        const key = `${t.dateDebut || ''}|${t.dateFin || ''}`;
        if (!map.has(key)) map.set(key, { dateDebut: t.dateDebut, dateFin: t.dateFin, tranches: [] });
        map.get(key).tranches.push(t);
    }
    return [...map.values()].sort((a, b) => {
        const da = a.dateDebut ? new Date(a.dateDebut) : new Date(0);
        const db = b.dateDebut ? new Date(b.dateDebut) : new Date(0);
        return db - da; // plus récent en premier
    });
}

export default function BaremeIrsaList({ tranchesIrsa = [], onOpenCreate, onOpenEdit }) {
    const groups = groupByExercice(tranchesIrsa);

    return (
        <div className="space-y-4">
            {/* En-tête global */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-sm p-4 flex justify-between items-center">
                <div>
                    <h3 className="font-bold text-[var(--color-fg)] text-base flex items-center gap-2">
                        <Percent size={18} className="text-[var(--color-brand-500)]" />
                        Barème IRSA (Impôt sur les Revenus Salariaux &amp; Assimilés)
                    </h3>
                    <p className="text-xs text-[var(--color-fg-muted)]">
                        Tranches d'imposition progressives — groupées par exercice fiscal (Loi de Finances).
                    </p>
                </div>
                <Button onClick={onOpenCreate} variant="primary" size="sm" iconLeft={<Plus size={16} />}>
                    Nouvelle Tranche IRSA
                </Button>
            </div>

            {/* Contenu */}
            {tranchesIrsa.length === 0 ? (
                <div className="p-8 bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">
                    <EmptyState
                        icon={Percent}
                        title="Aucune tranche IRSA"
                        description="Aucune tranche IRSA n'a été configurée."
                        actionLabel="Nouvelle Tranche IRSA"
                        onAction={onOpenCreate}
                    />
                </div>
            ) : (
                groups.map((group) => {
                    const statut = getStatutGroup(group.dateDebut, group.dateFin);
                    const meta = STATUT_META[statut] || STATUT_META.ARCHIVE;
                    const isArchive = statut === 'ARCHIVE';
                    const sortedTranches = [...group.tranches].sort(
                        (a, b) => (a.numeroTranche || 0) - (b.numeroTranche || 0)
                    );

                    return (
                        <div
                            key={`${group.dateDebut}|${group.dateFin}`}
                            className={`border shadow-sm overflow-hidden ${isArchive ? 'opacity-65' : ''} ${
                                statut === 'EN_VIGUEUR'
                                    ? 'border-[var(--color-success-500)]/30'
                                    : statut === 'PROGRAMME'
                                    ? 'border-[var(--color-brand-500)]/25'
                                    : 'border-[var(--color-border-subtle)]'
                            }`}
                        >
                            {/* Bandeau de groupe */}
                            <div className={`px-4 py-2.5 flex items-center gap-3 border-b ${
                                statut === 'EN_VIGUEUR'
                                    ? 'bg-[var(--color-success-500)]/5 border-[var(--color-success-500)]/20'
                                    : statut === 'PROGRAMME'
                                    ? 'bg-[var(--color-brand-500)]/5 border-[var(--color-brand-500)]/15'
                                    : 'bg-[var(--color-bg-subtle)] border-[var(--color-border-subtle)]'
                            }`}>
                                {statut === 'EN_VIGUEUR' && <CheckCircle2 size={14} className="text-[var(--color-success-600)] shrink-0" />}
                                {statut === 'PROGRAMME'  && <Clock size={14} className="text-[var(--color-brand-500)] shrink-0" />}
                                {statut === 'ARCHIVE'    && <Archive size={14} className="text-[var(--color-fg-muted)] shrink-0" />}
                                <span className={`inline-flex items-center px-2 py-0.5 text-[10px] font-bold border uppercase tracking-wide ${meta.cls}`}>
                                    {meta.label}
                                </span>
                                <span className="text-xs font-semibold text-[var(--color-fg)]">
                                    {fmtDate(group.dateDebut)} → {group.dateFin ? fmtDate(group.dateFin) : 'En vigueur'}
                                </span>
                                <span className="ml-auto text-[10px] text-[var(--color-fg-muted)]">
                                    {sortedTranches.length} tranche{sortedTranches.length > 1 ? 's' : ''}
                                </span>
                            </div>

                            {/* Table des tranches */}
                            <Table>
                                <Thead>
                                    <Th>Tranche N°</Th>
                                    <Th className="text-right">Seuil Minimum (Ar)</Th>
                                    <Th className="text-right">Seuil Maximum (Ar)</Th>
                                    <Th className="text-right">Taux d'imposition</Th>
                                    <Th className="text-right">Actions</Th>
                                </Thead>
                                <Tbody>
                                    {sortedTranches.map((t) => (
                                        <Tr key={t.idTranche}>
                                            <Td isData className="font-bold text-[var(--color-brand-500)]">
                                                Tranche {t.numeroTranche}
                                            </Td>
                                            <Td isData className="text-right font-semibold">
                                                {fmtMoney(t.trancheMin || 0)}
                                            </Td>
                                            <Td isData className="text-right font-semibold">
                                                {t.trancheMax ? fmtMoney(t.trancheMax) : 'Sans plafond'}
                                            </Td>
                                            <Td isData className="text-right font-bold text-[var(--color-danger-500)]">
                                                {t.taux}%
                                            </Td>
                                            <Td className="text-right">
                                                {!isArchive ? (
                                                    <button
                                                        onClick={() => onOpenEdit(t)}
                                                        className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer"
                                                        title="Modifier"
                                                    >
                                                        <Edit2 size={16} />
                                                    </button>
                                                ) : (
                                                    <span className="text-[10px] text-[var(--color-fg-muted)] italic px-2">archivé</span>
                                                )}
                                            </Td>
                                        </Tr>
                                    ))}
                                </Tbody>
                            </Table>
                        </div>
                    );
                })
            )}
        </div>
    );
}