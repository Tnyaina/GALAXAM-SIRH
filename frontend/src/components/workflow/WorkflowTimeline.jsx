import { CheckCircle2, Circle, Clock, AlertCircle, XCircle } from 'lucide-react';

const STATUS_CONFIG = {
    COMPLETED: {
        icon: CheckCircle2,
        dotClass: 'bg-[var(--color-success-500)] text-[var(--color-on-color)]',
        lineClass: 'bg-[var(--color-success-500)]',
        labelClass: 'text-[var(--color-fg)] font-semibold',
    },
    CURRENT: {
        icon: Clock,
        dotClass: 'bg-[var(--color-brand-500)] text-[var(--color-on-color)] ring-4 ring-[var(--color-brand-500)]/20 animate-pulse',
        lineClass: 'bg-[var(--color-border)]',
        labelClass: 'text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] font-bold',
    },
    PENDING: {
        icon: Circle,
        dotClass: 'border-2 border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-fg-muted)]',
        lineClass: 'bg-[var(--color-border)]',
        labelClass: 'text-[var(--color-fg-muted)]',
    },
    WARNING: {
        icon: AlertCircle,
        dotClass: 'bg-[var(--color-warning-500)] text-[var(--color-on-color)]',
        lineClass: 'bg-[var(--color-border)]',
        labelClass: 'text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] font-medium',
    },
    REJECTED: {
        icon: XCircle,
        dotClass: 'bg-[var(--color-danger-500)] text-[var(--color-on-color)]',
        lineClass: 'bg-[var(--color-border)]',
        labelClass: 'text-[var(--color-danger-700)] dark:text-[var(--color-danger-300)] font-semibold',
    },
};

/**
 * WorkflowTimeline — Composant générique et léger pour l'historique et le cycle de vie d'un dossier.
 *
 * Réservé exclusivement aux détails de dossiers (avances, acomptes, prêts, déclarations, STC, contrats).
 * Ne doit PAS être utilisé pour les 7 grandes étapes de la campagne de paie (réservées à PayrollPeriodStepper).
 *
 * @prop {Array} events - Liste d'événements ordonnés chronologiquement :
 *   [{ id, label, date, description, status: 'COMPLETED'|'CURRENT'|'PENDING'|'WARNING'|'REJECTED', author?: string }]
 * @prop {string} [className]
 */
export default function WorkflowTimeline({ events = [], className = '' }) {
    if (!Array.isArray(events) || events.length === 0) {
        return null;
    }

    return (
        <div className={`flow-root ${className}`}>
            <ul className="-mb-8">
                {events.map((event, idx) => {
                    const isLast = idx === events.length - 1;
                    const config = STATUS_CONFIG[event.status] || STATUS_CONFIG.PENDING;
                    const Icon = config.icon;

                    return (
                        <li key={event.id || idx} className="relative pb-8">
                            {!isLast && (
                                <span
                                    className={`absolute left-4 top-4 -ml-px h-full w-0.5 ${config.lineClass}`}
                                    aria-hidden="true"
                                />
                            )}
                            <div className="relative flex items-start space-x-3">
                                <div className="relative">
                                    <div
                                        className={`flex h-8 w-8 items-center justify-center rounded-full ${config.dotClass}`}
                                    >
                                        <Icon size={16} aria-hidden="true" />
                                    </div>
                                </div>
                                <div className="min-w-0 flex-1 pt-1.5">
                                    <div className="flex justify-between items-baseline gap-2">
                                        <p className={`text-xs ${config.labelClass}`}>
                                            {event.label}
                                        </p>
                                        {event.date && (
                                            <span className="text-[10px] text-[var(--color-fg-subtle)] whitespace-nowrap">
                                                {event.date}
                                            </span>
                                        )}
                                    </div>
                                    {event.description && (
                                        <p className="mt-0.5 text-[11px] text-[var(--color-fg-muted)]">
                                            {event.description}
                                        </p>
                                    )}
                                    {event.author && (
                                        <p className="mt-0.5 text-[10px] text-[var(--color-fg-subtle)]">
                                            Par : <span className="font-medium">{event.author}</span>
                                        </p>
                                    )}
                                </div>
                            </div>
                        </li>
                    );
                })}
            </ul>
        </div>
    );
}
