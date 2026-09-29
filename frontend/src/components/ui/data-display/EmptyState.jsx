import { isValidElement } from 'react';
import { Inbox } from 'lucide-react';
import Button from '@/components/ui/forms/Button';

/**
 * EmptyState — État vide standardisé pour listes, tableaux et recherches.
 */
export default function EmptyState({
    icon: Icon = Inbox,
    title = 'Aucune donnée disponible',
    description = 'Il n’y a aucun élément correspondant à vos critères pour le moment.',
    actionLabel,
    onAction,
    actionIcon,
    compact = false,
    bordered = true,
    className = '',
}) {
    if (compact) {
        return (
            <div className={`flex flex-col items-center justify-center py-6 px-4 text-center text-[var(--color-fg-muted)] space-y-2 ${className}`}>
                <div className="w-9 h-9 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] flex items-center justify-center text-[var(--color-fg-muted)] rounded-none shrink-0 shadow-sm">
                    {isValidElement(Icon) ? Icon : (Icon ? <Icon size={18} /> : <Inbox size={18} />)}
                </div>
                <div className="max-w-xs space-y-0.5">
                    {title && <h5 className="text-xs font-bold text-[var(--color-fg)]">{title}</h5>}
                    {description && (
                        <p className="text-[11px] text-[var(--color-fg-muted)] leading-relaxed">{description}</p>
                    )}
                </div>
                {actionLabel && onAction && (
                    <div className="pt-1">
                        <Button
                            type="button"
                            variant="primary"
                            size="xs"
                            onClick={onAction}
                            iconLeft={actionIcon}
                        >
                            {actionLabel}
                        </Button>
                    </div>
                )}
            </div>
        );
    }

    return (
        <div
            className={`
        flex flex-col items-center justify-center p-5 sm:p-6 text-center bg-[var(--color-surface)]
        ${bordered ? 'border border-[var(--color-border-subtle)]' : 'border border-transparent'} rounded-none space-y-2.5 ${className}
      `}
        >
            <div className="w-10 h-10 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] flex items-center justify-center text-[var(--color-fg-muted)] rounded-none shrink-0 shadow-xs">
                {isValidElement(Icon) ? Icon : (Icon ? <Icon size={20} /> : <Inbox size={20} />)}
            </div>

            <div className="max-w-md space-y-0.5">
                <h4 className="text-xs sm:text-sm font-bold text-[var(--color-fg)]">{title}</h4>
                {description && (
                    <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">{description}</p>
                )}
            </div>

            {actionLabel && onAction && (
                <div className="pt-2">
                    <Button
                        type="button"
                        variant="primary"
                        size="sm"
                        onClick={onAction}
                        iconLeft={actionIcon}
                    >
                        {actionLabel}
                    </Button>
                </div>
            )}
        </div>
    );
}
