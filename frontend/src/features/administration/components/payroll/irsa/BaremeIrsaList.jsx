import { useState, useEffect, useMemo } from 'react';
import { Plus, Edit2, Percent, CheckCircle2, Clock, Archive, ChevronDown, ChevronRight, TrendingUp } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { fmtDate } from '@/utils/dateUtils';
import { fmtMoney } from '@/utils/moneyUtils';
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
    PROGRAMME: { label: '◆ Programmé', cls: 'text-[var(--color-brand-500)] bg-[var(--color-brand-500)]/10 border-[var(--color-brand-500)]/25' },
    ARCHIVE: { label: '○ Archivé', cls: 'text-[var(--color-fg-muted)] bg-[var(--color-bg-subtle)] border-[var(--color-border-subtle)]' },
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

export default function BaremeIrsaList({ tranchesIrsa = [], onOpenCreate, onOpenEdit, onOpenRevaloriser }) {
    const groups = useMemo(() => groupByExercice(tranchesIrsa), [tranchesIrsa]);

    // Initialisation de l'accordéon :
    // Les groupes EN_VIGUEUR et PROGRAMME sont ouverts par défaut, les ARCHIVÉS sont repliés
    const [openGroups, setOpenGroups] = useState(new Set());

    useEffect(() => {
        const initial = new Set();
        groups.forEach(g => {
            const statut = getStatutGroup(g.dateDebut, g.dateFin);
            if (statut === 'EN_VIGUEUR' || statut === 'PROGRAMME') {
                initial.add(`${g.dateDebut || ''}|${g.dateFin || ''}`);
            }
        });
        if (initial.size === 0 && groups.length > 0) {
            initial.add(`${groups[0].dateDebut || ''}|${groups[0].dateFin || ''}`);
        }
        setOpenGroups(initial);
    }, [groups]);

    const toggleGroup = (key) => {
        setOpenGroups(prev => {
            const next = new Set(prev);
            if (next.has(key)) {
                next.delete(key);
            } else {
                next.add(key);
            }
            return next;
        });
    };

    return (
        <div className="space-y-4">
            {/* En-tête global */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-sm p-4 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3">
                <div>
                    <h3 className="font-bold text-[var(--color-fg)] text-base flex items-center gap-2">
                        <Percent size={18} className="text-[var(--color-brand-500)]" />
                        Barème IRSA (Impôt sur les Revenus Salariaux &amp; Assimilés)
                    </h3>
                    <p className="text-xs text-[var(--color-fg-muted)]">
                        Tranches d'imposition progressives groupées par exercice fiscal (Loi de Finances).
                    </p>
                </div>
                <div className="flex items-center gap-2 w-full sm:w-auto justify-end">
                    <Button
                        onClick={onOpenRevaloriser}
                        variant="secondary"
                        size="sm"
                        iconLeft={<TrendingUp size={16} />}
                    >
                        Revaloriser le barème
                    </Button>
                    <Button
                        onClick={onOpenCreate}
                        variant="primary"
                        size="sm"
                        iconLeft={<Plus size={16} />}
                    >
                        Nouvelle Tranche IRSA
                    </Button>
                </div>
            </div>

            {/* Contenu avec Accordéons */}
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
                    const groupKey = `${group.dateDebut || ''}|${group.dateFin || ''}`;
                    const isOpen = openGroups.has(groupKey);
                    const statut = getStatutGroup(group.dateDebut, group.dateFin);
                    const meta = STATUT_META[statut] || STATUT_META.ARCHIVE;
                    const isArchive = statut === 'ARCHIVE';
                    const sortedTranches = [...group.tranches].sort(
                        (a, b) => (a.numeroTranche || 0) - (b.numeroTranche || 0)
                    );

                    return (
                        <div
                            key={groupKey}
                            className={`border shadow-sm overflow-hidden transition-opacity ${isArchive ? 'opacity-80 hover:opacity-100' : ''
                                } ${statut === 'EN_VIGUEUR'
                                    ? 'border-[var(--color-success-500)]/30'
                                    : statut === 'PROGRAMME'
                                        ? 'border-[var(--color-brand-500)]/25'
                                        : 'border-[var(--color-border-subtle)]'
                                }`}
                        >
                            {/* Bandeau d'accordéon (Cliquable pour déplier/replier) */}
                            <button
                                type="button"
                                onClick={() => toggleGroup(groupKey)}
                                className={`w-full px-4 py-3 flex items-center justify-between text-left transition-colors cursor-pointer select-none ${statut === 'EN_VIGUEUR'
                                    ? 'bg-[var(--color-success-500)]/8 hover:bg-[var(--color-success-500)]/12'
                                    : statut === 'PROGRAMME'
                                        ? 'bg-[var(--color-brand-500)]/8 hover:bg-[var(--color-brand-500)]/12'
                                        : 'bg-[var(--color-bg-subtle)] hover:bg-[var(--color-surface)]'
                                    }`}
                            >
                                <div className="flex items-center gap-3">
                                    <span className="text-[var(--color-fg-muted)]">
                                        {isOpen ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                                    </span>
                                    {statut === 'EN_VIGUEUR' && <CheckCircle2 size={15} className="text-[var(--color-success-600)] shrink-0" />}
                                    {statut === 'PROGRAMME' && <Clock size={15} className="text-[var(--color-brand-500)] shrink-0" />}
                                    {statut === 'ARCHIVE' && <Archive size={15} className="text-[var(--color-fg-muted)] shrink-0" />}
                                    <span className={`inline-flex items-center px-2 py-0.5 text-[10px] font-bold border uppercase tracking-wide ${meta.cls}`}>
                                        {meta.label}
                                    </span>
                                    <span className="text-xs font-bold text-[var(--color-fg)]">
                                        {fmtDate(group.dateDebut)} → {group.dateFin ? fmtDate(group.dateFin) : 'En vigueur'}
                                    </span>
                                </div>
                                <div className="flex items-center gap-3">
                                    <span className="text-xs text-[var(--color-fg-muted)]">
                                        {sortedTranches.length} tranche{sortedTranches.length > 1 ? 's' : ''}
                                    </span>
                                    <span className="text-[11px] font-medium text-[var(--color-brand-500)]">
                                        {isOpen ? 'Replier' : 'Déplier'}
                                    </span>
                                </div>
                            </button>

                            {/* Table des tranches (visible si déplié) */}
                            {isOpen && (
                                <div className="border-t border-[var(--color-border-subtle)]">
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
                                                            <span className="text-[10px] text-[var(--color-fg-muted)]  px-2">archivé</span>
                                                        )}
                                                    </Td>
                                                </Tr>
                                            ))}
                                        </Tbody>
                                    </Table>
                                </div>
                            )}
                        </div>
                    );
                })
            )}
        </div>
    );
}
