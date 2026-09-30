import Badge from '@/components/ui/data-display/Badge';

/** Affiche les statuts métier calculés par le backend, sans déduire la Paie du code de période. */
export default function PayrollWorkflowStatusBadges({ statuses, size = 'sm' }) {
    if (!statuses) return null;
    return (
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5 text-xs text-[var(--color-fg-muted)]">
            {statuses.pointage && (
                <span className="inline-flex items-center gap-1.5">
                    Pointage <Badge variant={statuses.pointage.tone} size={size}>{statuses.pointage.label}</Badge>
                </span>
            )}
            {statuses.paie && (
                <span className="inline-flex items-center gap-1.5">
                    Paie <Badge variant={statuses.paie.tone} size={size}>{statuses.paie.label}</Badge>
                </span>
            )}
        </div>
    );
}
