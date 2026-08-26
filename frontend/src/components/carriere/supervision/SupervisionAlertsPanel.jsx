import { memo } from 'react';
import { AlertTriangle, Layers, Clock, Calendar } from 'lucide-react';
import Button from '@/components/ui/forms/Button';

function SupervisionAlertsPanel({ alertes = [], onActionClick, onVerifyDoublons }) {
    if (!alertes || alertes.length === 0) return null;

    return (
        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">
            <div className="px-5 py-3.5 border-b border-[var(--color-border-subtle)] flex items-center gap-2">
                <AlertTriangle size={15} className="text-[var(--color-fg)]" />
                <h2 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                    À traiter en priorité
                </h2>
            </div>

            <div className="divide-y divide-[var(--color-border-subtle)]">
                {alertes.map((al, idx) => {
                    const isDanger = al.variant === 'danger';
                    const isWarning = al.variant === 'warning';

                    return (
                        <div key={idx} className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 p-4 hover:bg-[var(--color-bg-subtle)]/50 transition-colors">
                            <div className="flex items-start gap-3.5 min-w-0">
                                <div className={`w-8 h-8 rounded-none flex items-center justify-center shrink-0 ${
                                    isDanger 
                                        ? 'bg-danger-500/10 text-danger-600 dark:text-danger-400' 
                                        : isWarning 
                                            ? 'bg-warning-500/10 text-warning-600 dark:text-warning-400'
                                            : 'bg-[var(--color-brand-50)] text-[var(--color-brand-600)]'
                                }`}>
                                    {isDanger ? <Layers size={16} /> : isWarning ? <Clock size={16} /> : <Calendar size={16} />}
                                </div>
                                <div className="min-w-0">
                                    <div className="text-xs font-bold text-[var(--color-fg)]">
                                        {al.titre}
                                    </div>
                                    <div 
                                        className="text-xs text-[var(--color-fg-muted)] mt-0.5"
                                        dangerouslySetInnerHTML={{ __html: al.description }}
                                    />
                                </div>
                            </div>

                            <div className="shrink-0 pl-11 sm:pl-0">
                                {al.type === 'DOUBLON' ? (
                                    <Button
                                        variant="outline"
                                        size="xs"
                                        onClick={onVerifyDoublons}
                                    >
                                        Filtrer les doublons
                                    </Button>
                                ) : (
                                    <Button
                                        variant="outline"
                                        size="xs"
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
