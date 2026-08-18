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
    className = '',
}) {
    return (
        <div
            className={`
        flex flex-col items-center justify-center p-12 text-center bg-[var(--color-surface)]
        border border-[var(--color-border-subtle)] rounded-none space-y-4 ${className}
      `}
        >
            <div className="w-14 h-14 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] flex items-center justify-center text-[var(--color-fg-muted)] rounded-none shrink-0 shadow-sm">
                <Icon size={26} />
            </div>

            <div className="max-w-md space-y-1">
                <h4 className="text-base font-bold text-[var(--color-fg)]">{title}</h4>
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