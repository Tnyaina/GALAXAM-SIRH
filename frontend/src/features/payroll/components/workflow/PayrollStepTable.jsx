import SharedButton from '@/components/ui/forms/Button';
import { default as SharedInput } from '@/components/ui/forms/Input';
import { Search, XCircle } from 'lucide-react';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Pagination from '@/components/ui/navigation/Pagination';
import { Table, Thead, Th, Tbody, Tr } from '@/components/ui/data-display/Table';
import Badge from '@/components/ui/data-display/Badge';
import { formatRoundedMoneyAr } from '@/utils/moneyUtils';

/**
 * PayrollStepTable — Composant unifié pour les tables du workflow de paie (Population, Calcul, Contrôle).
 * Construit sur les composants officiels du Design System Galaxam (Table, Thead, Th, Tbody, Tr).
 */
export default function PayrollStepTable({
    title,
    badgeCount,
    subtitle,
    // Recherche
    searchValue = '',
    onSearchChange,
    searchPlaceholder = 'Rechercher par nom, matricule…',
    // Slots complémentaires
    filterSlot,
    actionsSlot,
    // Configuration des colonnes : [{ key, label, align = 'left'|'right'|'center', width, className }]
    columns = [],
    // Données
    loading = false,
    emptyMessage = 'Aucun élément trouvé.',
    skeletonRows = 6,
    children,
    // Pagination
    pagination, // { currentPage, pageSize, totalItems, onPageChange, onPageSizeChange, pageSizeOptions }
    ariaLabel = 'Table de paie',
}) {
    const colSpan = columns.length || 1;

    return (
        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] overflow-hidden shadow-none">
            {/* Barre supérieure : Titre + Filtres + Recherche unifiée */}
            {(title || filterSlot || onSearchChange || actionsSlot) && (
                <div className="px-4 py-3 bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] flex flex-wrap items-center justify-between gap-3">
                    <div className="flex items-center gap-2.5 flex-wrap min-w-0">
                        {title && (
                            <div className="flex items-center gap-2">
                                <span className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                                    {title}
                                </span>
                                {badgeCount !== undefined && badgeCount !== null && (
                                    <Badge variant="neutral" size="xs">
                                        {badgeCount}
                                    </Badge>
                                )}
                            </div>
                        )}
                        {subtitle && (
                            <span className="text-xs text-[var(--color-fg-muted)] hidden md:inline">
                                · {subtitle}
                            </span>
                        )}
                        {filterSlot}
                    </div>

                    <div className="flex items-center gap-2 shrink-0 flex-wrap">
                        {actionsSlot}
                        {onSearchChange && (
                            <div className="relative">
                                <Search
                                    size={13}
                                    className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)] pointer-events-none"
                                />
                                <SharedInput plain
                                    type="text"
                                    placeholder={searchPlaceholder}
                                    value={searchValue}
                                    onChange={(e) => onSearchChange(e.target.value)}
                                    className="pl-8 pr-7 py-1.5 text-xs border border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-fg)] placeholder:text-[var(--color-fg-subtle)] focus:outline-none focus:ring-1 focus:ring-[var(--color-brand-500)] w-48 sm:w-56"
                                    aria-label={searchPlaceholder}
                                />
                                {searchValue && (
                                    <SharedButton plain
                                        type="button"
                                        onClick={() => onSearchChange('')}
                                        className="absolute right-2 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] cursor-pointer"
                                        aria-label="Effacer la recherche"
                                    >
                                        <XCircle size={13} />
                                    </SharedButton>
                                )}
                            </div>
                        )}
                    </div>
                </div>
            )}

            {/* Corps du tableau utilisant Table du Design System */}
            {loading ? (
                <div className="p-4">
                    <SkeletonTable rows={skeletonRows} cols={colSpan} bordered={false} />
                </div>
            ) : (
                <Table bordered={false} density="compact" rowHover aria-label={ariaLabel}>
                    <Thead>
                        <Tr>
                            {columns.map((col, idx) => {
                                const alignClass = col.align === 'right' ? 'text-right' : col.align === 'center' ? 'text-center' : 'text-left';
                                return (
                                    <Th
                                        key={col.key || idx}
                                        className={`${alignClass} ${col.width || ''} ${col.className || ''}`}
                                    >
                                        {col.label}
                                    </Th>
                                );
                            })}
                        </Tr>
                    </Thead>
                    <Tbody>
                        {children}
                    </Tbody>
                </Table>
            )}

            {/* État vide si aucun enfant ou résultat */}
            {!loading && (!children || (Array.isArray(children) && children.length === 0)) && (
                <div className="p-8 text-center text-sm text-[var(--color-fg-muted)]">
                    {emptyMessage}
                </div>
            )}

            {/* Pagination unifiée */}
            {!loading && pagination && pagination.totalItems > 0 && (
                <Pagination
                    currentPage={pagination.currentPage}
                    totalItems={pagination.totalItems}
                    pageSize={pagination.pageSize || 10}
                    pageSizeOptions={pagination.pageSizeOptions || [10, 25, 50]}
                    onPageChange={pagination.onPageChange}
                    onPageSizeChange={pagination.onPageSizeChange}
                />
            )}
        </div>
    );
}

/** Cellule standard pour afficher un salarié (Nom + matricule pill + sous-titre affectation/site) */
export function PayrollEmployeeCell({ name, matricule, subtitle }) {
    return (
        <div className="py-0.5">
            <div className="font-semibold text-[var(--color-fg)] text-xs leading-snug">
                {name || '–'}
            </div>
            <div className="flex items-center gap-1.5 mt-1 text-[11px] text-[var(--color-fg-muted)]">
                {matricule && (
                    <span className="font-data bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] px-1.5 py-0.2 text-[10px] border border-[var(--color-border-subtle)] font-medium">
                        {matricule}
                    </span>
                )}
                {subtitle && <span className="truncate">{subtitle}</span>}
            </div>
        </div>
    );
}

/** Cellule standardisée pour montants financiers (base, brut, déduction rouge, net vert) */
export function PayrollMoneyCell({ value, type = 'normal' }) {
    if (type === 'net') {
        return (
            <span className="font-data font-bold text-xs tabular-nums whitespace-nowrap text-[var(--color-success-700)] dark:text-[var(--color-success-300)] bg-[var(--color-success-50)] dark:bg-[var(--color-success-950)] border border-[var(--color-success-300)]/60 dark:border-[var(--color-success-800)] px-2 py-0.5 inline-block">
                {formatRoundedMoneyAr(value)}
            </span>
        );
    }
    if (type === 'deduction') {
        const num = Number(value || 0);
        return (
            <span className="font-data text-xs tabular-nums whitespace-nowrap text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)] font-medium">
                {num > 0 ? `−${formatRoundedMoneyAr(num)}` : '—'}
            </span>
        );
    }
    if (type === 'muted') {
        return (
            <span className="font-data text-xs tabular-nums whitespace-nowrap text-[var(--color-fg-muted)]">
                {value != null ? formatRoundedMoneyAr(value) : '—'}
            </span>
        );
    }
    return (
        <span className="font-data text-xs tabular-nums whitespace-nowrap font-medium text-[var(--color-fg)]">
            {value != null ? formatRoundedMoneyAr(value) : '—'}
        </span>
    );
}
