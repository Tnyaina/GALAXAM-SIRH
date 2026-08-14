import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react';
import Button from '@/components/ui/forms/Button';

/**
 * Pagination — Composant de pagination de table et listes volumineuses.
 * Structure anguleuse, typographie .font-data, sélecteur de taille de page.
 */
export default function Pagination({
    currentPage = 1,
    totalItems = 0,
    pageSize = 10,
    pageSizeOptions = [10, 25, 50, 100],
    onPageChange,
    onPageSizeChange,
    className = '',
}) {
    const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
    const startItem = totalItems === 0 ? 0 : (currentPage - 1) * pageSize + 1;
    const endItem = Math.min(currentPage * pageSize, totalItems);

    // Génération des numéros de page avec ellipsis
    const getPageNumbers = () => {
        const pages = [];
        const maxVisible = 5;

        if (totalPages <= maxVisible) {
            for (let i = 1; i <= totalPages; i++) pages.push(i);
        } else {
            if (currentPage <= 3) {
                pages.push(1, 2, 3, 4, '...', totalPages);
            } else if (currentPage >= totalPages - 2) {
                pages.push(1, '...', totalPages - 3, totalPages - 2, totalPages - 1, totalPages);
            } else {
                pages.push(1, '...', currentPage - 1, currentPage, currentPage + 1, '...', totalPages);
            }
        }
        return pages;
    };

    return (
        <div
            className={`
        flex flex-col sm:flex-row items-center justify-between gap-4 px-4 py-3
        bg-[var(--color-surface)] border-t border-[var(--color-border-subtle)] text-xs text-[var(--color-fg-muted)]
        ${className}
      `}
        >
            {/* Informations d'affichage */}
            <div className="flex items-center gap-3">
                <span>
                    Affichage de <span className="font-data font-bold text-[var(--color-fg)]">{startItem}</span> à{' '}
                    <span className="font-data font-bold text-[var(--color-fg)]">{endItem}</span> sur{' '}
                    <span className="font-data font-bold text-[var(--color-fg)]">{totalItems}</span> éléments
                </span>

                {onPageSizeChange && pageSizeOptions && (
                    <div className="flex items-center gap-1.5 ml-2">
                        <span className="text-[var(--color-fg-subtle)]">Afficher :</span>
                        <select
                            value={pageSize}
                            onChange={(e) => onPageSizeChange(Number(e.target.value))}
                            className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-[var(--color-fg)] font-data font-semibold px-2 py-1 outline-none rounded-none cursor-pointer focus:border-[var(--color-brand-500)]"
                        >
                            {pageSizeOptions.map((opt) => (
                                <option key={opt} value={opt}>
                                    {opt}
                                </option>
                            ))}
                        </select>
                    </div>
                )}
            </div>

            {/* Contrôles de navigation */}
            <div className="flex items-center gap-1">
                {/* Première page */}
                <Button
                    type="button"
                    variant="outline"
                    size="xs"
                    disabled={currentPage === 1}
                    onClick={() => onPageChange(1)}
                    icon={<ChevronsLeft size={14} />}
                    className="!min-h-0 !p-1.5"
                    title="Première page"
                />

                {/* Page précédente */}
                <Button
                    type="button"
                    variant="outline"
                    size="xs"
                    disabled={currentPage === 1}
                    onClick={() => onPageChange(currentPage - 1)}
                    icon={<ChevronLeft size={14} />}
                    className="!min-h-0 !p-1.5"
                    title="Page précédente"
                />

                {/* Numéros de page */}
                <div className="flex items-center gap-1 mx-1">
                    {getPageNumbers().map((p, idx) => {
                        if (p === '...') {
                            return (
                                <span key={`dots-${idx}`} className="px-2 text-[var(--color-fg-subtle)]">
                                    ...
                                </span>
                            );
                        }
                        const isActive = p === currentPage;
                        return (
                            <button
                                key={`page-${p}`}
                                type="button"
                                onClick={() => onPageChange(p)}
                                className={`
                  min-w-[28px] h-7 px-2 font-data text-xs font-semibold rounded-none border transition-colors cursor-pointer
                  ${isActive
                                        ? 'bg-[var(--color-brand-500)] border-[var(--color-brand-500)] text-white'
                                        : 'bg-[var(--color-surface)] border-[var(--color-border-subtle)] text-[var(--color-fg)] hover:bg-[var(--color-surface-hover)]'
                                    }
                `}
                            >
                                {p}
                            </button>
                        );
                    })}
                </div>

                {/* Page suivante */}
                <Button
                    type="button"
                    variant="outline"
                    size="xs"
                    disabled={currentPage === totalPages}
                    onClick={() => onPageChange(currentPage + 1)}
                    icon={<ChevronRight size={14} />}
                    className="!min-h-0 !p-1.5"
                    title="Page suivante"
                />

                {/* Dernière page */}
                <Button
                    type="button"
                    variant="outline"
                    size="xs"
                    disabled={currentPage === totalPages}
                    onClick={() => onPageChange(totalPages)}
                    icon={<ChevronsRight size={14} />}
                    className="!min-h-0 !p-1.5"
                    title="Dernière page"
                />
            </div>
        </div>
    );
}