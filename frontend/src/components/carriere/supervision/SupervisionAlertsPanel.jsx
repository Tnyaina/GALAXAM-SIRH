import { memo } from 'react';
import { AlertTriangle, Layers, Clock, Calendar, ArrowRight } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';

function SupervisionAlertsPanel({ alertes = [], onActionClick, onVerifyDoublons }) {
    if (!alertes || alertes.length === 0) return null;

    const totalUrgent = alertes.filter(a => a.variant === 'danger' || a.type === 'DOUBLON').length;

    return (
        <div className="bg-[var(--color-surface)] border border-amber-500/30 shadow-sm overflow-hidden">
            {/* Header d'Alerte Prioritaire */}
            <div className="px-5 py-3.5 bg-gradient-to-r from-amber-500/15 via-amber-500/5 to-transparent border-b border-amber-500/20 flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                    <div className="w-6 h-6 rounded-none bg-amber-500 text-white flex items-center justify-center shadow-xs">
                        <AlertTriangle size={14} strokeWidth={2.5} />
                    </div>
                    <h2 className="text-xs font-extrabold uppercase tracking-wider text-amber-950 dark:text-amber-200">
                        À traiter en priorité
                    </h2>
                </div>
                <div className="flex items-center gap-2">
                    {totalUrgent > 0 && (
                        <Badge variant="danger" size="xs">
                            {totalUrgent} Critique{totalUrgent > 1 ? 's' : ''}
                        </Badge>
                    )}
                    <Badge variant="warning" size="xs">
                        {alertes.length} Action{alertes.length > 1 ? 's' : ''} Requise{alertes.length > 1 ? 's' : ''}
                    </Badge>
                </div>
            </div>

            {/* Liste des alertes avec distinction visuelle par criticité */}
            <div className="divide-y divide-[var(--color-border-subtle)]">
                {alertes.map((al, idx) => {
                    const isDanger = al.variant === 'danger' || al.type === 'DOUBLON';
                    const isWarning = al.variant === 'warning';

                    return (
                        <div
                            key={idx}
                            className={`flex flex-col sm:flex-row sm:items-center justify-between gap-4 p-4 transition-colors ${
                                isDanger
                                    ? 'border-l-4 border-l-red-500 bg-red-500/[0.04] hover:bg-red-500/[0.08]'
                                    : isWarning
                                        ? 'border-l-4 border-l-amber-500 bg-amber-500/[0.03] hover:bg-amber-500/[0.06]'
                                        : 'border-l-4 border-l-[var(--color-brand-500)] bg-[var(--color-brand-500)]/[0.02] hover:bg-[var(--color-brand-500)]/[0.05]'
                            }`}
                        >
                            <div className="flex items-start gap-3.5 min-w-0">
                                <div
                                    className={`w-9 h-9 rounded-none flex items-center justify-center shrink-0 border ${
                                        isDanger
                                            ? 'bg-red-500/15 border-red-500/30 text-red-700 dark:text-red-300'
                                            : isWarning
                                                ? 'bg-amber-500/15 border-amber-500/30 text-amber-700 dark:text-amber-300'
                                                : 'bg-[var(--color-brand-500)]/15 border-[var(--color-brand-500)]/30 text-[var(--color-brand-600)]'
                                    }`}
                                >
                                    {isDanger ? <Layers size={17} /> : isWarning ? <Clock size={17} /> : <Calendar size={17} />}
                                </div>
                                <div className="min-w-0 space-y-1">
                                    <div className="flex items-center gap-2 flex-wrap">
                                        <Badge variant={isDanger ? 'danger' : isWarning ? 'warning' : 'primary'} size="xs">
                                            {isDanger ? 'URGENT' : isWarning ? 'ÉCHÉANCE' : 'RAPPEL'}
                                        </Badge>
                                        <span className="text-xs font-bold text-[var(--color-fg)]">
                                            {al.titre}
                                        </span>
                                    </div>
                                    <div
                                        className="text-xs text-[var(--color-fg-muted)] leading-relaxed"
                                        dangerouslySetInnerHTML={{ __html: al.description }}
                                    />
                                </div>
                            </div>

                            <div className="shrink-0 pl-12 sm:pl-0">
                                {al.type === 'DOUBLON' ? (
                                    <Button
                                        variant="danger"
                                        size="xs"
                                        iconRight={<ArrowRight size={13} />}
                                        onClick={onVerifyDoublons}
                                    >
                                        Filtrer les doublons
                                    </Button>
                                ) : (
                                    <Button
                                        variant={isDanger ? 'primary' : 'outline'}
                                        size="xs"
                                        iconRight={<ArrowRight size={13} />}
                                        onClick={() => onActionClick(al.actionUrl)}
                                    >
                                        Voir le dossier
                                    </Button>
                                )}
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}

export default memo(SupervisionAlertsPanel);
