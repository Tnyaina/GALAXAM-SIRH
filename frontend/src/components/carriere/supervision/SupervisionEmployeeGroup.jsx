import { memo } from 'react';
import { ChevronDown } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import { Avatar } from '@/components/ui/data-display/Avatar';
import { fmtDate } from '@/components/dashboard/DashboardUtils';

function SupervisionEmployeeGroup({
    group,
    isCollapsed,
    onToggleCollapse,
    onOpenCompare,
    onNavigateCarriere
}) {
    const hasDuplicateContract = group.contrats.some(c => c.isDuplicate);

    return (
        <div
            className={`bg-[var(--color-surface)] border transition-all ${
                hasDuplicateContract
                    ? 'border-danger-500/80 shadow-sm'
                    : 'border-[var(--color-border-subtle)]'
            }`}
        >
            {/* En-tête de Groupe Collaborateur */}
            <div
                onClick={onToggleCollapse}
                className={`flex items-center gap-3.5 p-4 cursor-pointer select-none transition-colors ${
                    hasDuplicateContract
                        ? 'bg-danger-500/5 hover:bg-danger-500/10'
                        : 'hover:bg-[var(--color-bg-subtle)]/60'
                }`}
            >
                {/* Avatar */}
                <Avatar name={group.nomPrenom} size="md" shape="square" />

                {/* Identité & Métadonnées */}
                <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-extrabold text-sm text-[var(--color-fg)] truncate">
                            {group.nomPrenom}
                        </span>
                        <span className="font-data text-xs text-[var(--color-fg-subtle)]">
                            #{group.matricule}
                        </span>
                        {hasDuplicateContract && (
                            <Badge variant="danger" size="xs">
                                Doublon détecté
                            </Badge>
                        )}
                    </div>
                    <div className="text-xs text-[var(--color-fg-muted)] mt-0.5 truncate">
                        {group.nomSite || 'Site non défini'} · {group.nomFonction || 'Fonction non définie'}
                    </div>
                </div>

                {/* Compteur de Contrats & Chevron */}
                <div className="flex items-center gap-3 shrink-0">
                    <span className="text-xs font-bold text-[var(--color-fg-muted)]">
                        {group.contrats.length} contrat{group.contrats.length > 1 ? 's' : ''}
                    </span>
                    {hasDuplicateContract && (
                        <Button
                            variant="danger"
                            size="xs"
                            onClick={(e) => {
                                e.stopPropagation();
                                onOpenCompare(group);
                            }}
                        >
                            Comparer
                        </Button>
                    )}
                    <ChevronDown
                        size={16}
                        className={`text-[var(--color-fg-subtle)] transition-transform duration-150 ${
                            isCollapsed ? '-rotate-90' : ''
                        }`}
                    />
                </div>
            </div>

            {/* Lignes de Contrats (si déplié) */}
            {!isCollapsed && (
                <div className="border-t border-[var(--color-border-subtle)] divide-y divide-[var(--color-border-subtle)]">
                    {group.contrats.map((c, idx) => {
                        const isPending = Boolean(c.isBrouillon || c.isPlanifie || (c.isActif && new Date(c.dateDebut) > new Date()));
                        const isSoon = (c.joursRestantsFin !== null && c.joursRestantsFin <= 60 && c.joursRestantsFin >= 0) ||
                                       (c.joursRestantsEssai !== null && c.joursRestantsEssai <= 60 && c.joursRestantsEssai >= 0);
                        const isDuplicate = c.isDuplicate;

                        // Repère de bordure latérale
                        const borderClass = isDuplicate
                            ? 'flag-danger bg-[var(--color-danger-500)]/[0.02]'
                            : isPending
                                ? 'flag-brand'
                                : isSoon
                                    ? 'flag-warning'
                                    : 'border-l-4 border-l-transparent';

                        return (
                            <div
                                key={c.idContrat ? `contrat-${c.idContrat}-${idx}` : `${group.key}-${idx}`}
                                className={`p-4 pl-6 md:pl-8 grid grid-cols-1 md:grid-cols-12 gap-3 md:gap-4 items-center hover:bg-[var(--color-bg-subtle)]/40 transition-colors ${borderClass}`}
                            >
                                {/* 1. Type & N° Contrat (Cols 1-3) */}
                                <div className="md:col-span-3">
                                    <div className="font-extrabold text-xs text-[var(--color-fg)]">
                                        {c.libelleTypeContrat || c.codeTypeContrat || 'Contrat'}
                                    </div>
                                    <div className="font-data text-[11px] text-[var(--color-brand-600)] font-semibold mt-0.5">
                                        {c.numeroContrat || 'N° En attente'}
                                    </div>
                                </div>

                                {/* 2. Site / Fonction (Cols 4-5) */}
                                <div className="md:col-span-2 text-xs">
                                    <div className="text-[var(--color-fg)] truncate">
                                        {c.nomSite || '-'}
                                    </div>
                                    <div className="text-[11px] text-[var(--color-fg-muted)] truncate">
                                        {c.nomFonction || '-'}
                                    </div>
                                </div>

                                {/* 3. Dates & Échéances (Cols 6-8) */}
                                <div className="md:col-span-3 text-xs">
                                    <div className="flex items-center gap-1 font-data text-[var(--color-fg)]">
                                        <span>{fmtDate(c.dateDebut)}</span>
                                        <span className="text-[var(--color-fg-subtle)]">→</span>
                                        <span>{c.dateFin ? fmtDate(c.dateFin) : 'Indéterminée'}</span>
                                    </div>

                                    {/* Compte à rebours fin contrat ou période d'essai */}
                                    {c.dateFin && c.joursRestantsFin !== null && (
                                        <div className={`text-[10.5px] font-bold mt-0.5 ${
                                            c.joursRestantsFin <= 7
                                                ? 'text-danger-600 dark:text-danger-400'
                                                : c.joursRestantsFin <= 30
                                                    ? 'text-warning-600 dark:text-warning-400'
                                                    : 'text-[var(--color-fg-muted)]'
                                        }`}>
                                            {c.joursRestantsFin >= 0
                                                ? `Expire dans ${c.joursRestantsFin} jour(s)`
                                                : 'Contrat échu'}
                                        </div>
                                    )}

                                    {c.dateFinEssai && c.joursRestantsEssai !== null && (
                                        <div className="text-[10.5px] font-bold text-warning-700 dark:text-warning-400 mt-0.5">
                                            Essai : J-{c.joursRestantsEssai} ({fmtDate(c.dateFinEssai)})
                                        </div>
                                    )}
                                </div>

                                {/* 4. Statut Badge (Cols 9-10) */}
                                <div className="md:col-span-2">
                                    {isDuplicate ? (
                                        <Badge variant="danger" size="xs">Doublon ?</Badge>
                                    ) : (
                                        <StatusBadge 
                                            status={c.codeStatutContrat} 
                                            label={c.libelleStatutContrat} 
                                            size="xs" 
                                        />
                                    )}
                                </div>

                                {/* 5. Action (Cols 11-12) */}
                                <div className="md:col-span-2 flex items-center justify-end gap-2">
                                    {isDuplicate && (
                                        <Button
                                            variant="danger"
                                            size="xs"
                                            onClick={() => onOpenCompare(group)}
                                        >
                                            Comparer
                                        </Button>
                                    )}
                                    <Button
                                        variant="outline"
                                        size="xs"
                                        onClick={() => onNavigateCarriere(c.idEmploye)}
                                    >
                                        Fiche
                                    </Button>
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
}

export default memo(SupervisionEmployeeGroup);
