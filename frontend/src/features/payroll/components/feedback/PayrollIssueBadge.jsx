import { AlertCircle, AlertTriangle, Info } from 'lucide-react';

const SEVERITY_CONFIG = {
    BLOCKER: {
        icon: AlertCircle,
        label: 'Bloquant',
        badgeClass: 'bg-[var(--color-danger-500)]/15 text-[var(--color-danger-700)] dark:text-[var(--color-danger-300)] border-[var(--color-danger-500)]/30',
    },
    WARNING: {
        icon: AlertTriangle,
        label: 'Attention',
        badgeClass: 'bg-[var(--color-warning-500)]/15 text-[var(--color-warning-800)] dark:text-[var(--color-warning-200)] border-[var(--color-warning-500)]/30',
    },
    INFO: {
        icon: Info,
        label: 'Info',
        badgeClass: 'bg-[var(--color-brand-500)]/15 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] border-[var(--color-brand-500)]/30',
    },
};

/**
 * PayrollIssueBadge — Badge compact pour signaler un statut d'anomalie / bloqueur de paie.
 *
 * @prop {'BLOCKER'|'WARNING'|'INFO'} [severity='INFO']
 * @prop {string|number} [count]
 * @prop {string} [label]
 * @prop {string} [className]
 */
export default function PayrollIssueBadge({
    severity = 'INFO',
    count,
    label,
    className = '',
}) {
    const config = SEVERITY_CONFIG[severity] || SEVERITY_CONFIG.INFO;
    const Icon = config.icon;

    return (
        <span
            className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-none border text-[11px] font-semibold ${config.badgeClass} ${className}`}
        >
            <Icon size={12} className="shrink-0" aria-hidden="true" />
            {count !== undefined && count !== null && (
                <span className="font-mono font-bold">{count}</span>
            )}
            <span>{label || config.label}</span>
        </span>
    );
}
