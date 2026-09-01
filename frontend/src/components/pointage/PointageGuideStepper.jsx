import { Calendar, AlertTriangle } from 'lucide-react';

export default function PointageGuideStepper({
    periodeInfo = null,
    totalWeeks = 4,
    currentStep = 1,
}) {
    const isCloturee = periodeInfo?.statut_code === 'CLOTUREE' || periodeInfo?.statut_code === 'VALIDEE';
    const isEnValidation = periodeInfo?.statut_code === 'A_VALIDER';
    const noPeriode = !periodeInfo;

    const periodeLibelle = periodeInfo
        ? (periodeInfo.libelle || periodeInfo.code || 'Période active').toUpperCase()
        : 'AUCUNE PÉRIODE DE PAIE ACTIVE';

    const statusDot = noPeriode
        ? 'bg-amber-500'
        : isCloturee
            ? 'bg-[var(--color-fg-muted)]'
            : isEnValidation
                ? 'bg-amber-500'
                : 'bg-emerald-500';

    const statusLabel = noPeriode
        ? 'Saisie verrouillée'
        : isCloturee
            ? 'Clôturée'
            : isEnValidation
                ? 'À valider DAF'
                : 'Saisie en cours';

    const formatDateFr = (dStr) => {
        if (!dStr) return '';
        try {
            return new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short' }).format(new Date(dStr));
        } catch { return dStr; }
    };

    const stepLabels = ['Saisie', 'Anomalies', 'Synthèse', 'Transmission'];

    return (
        <div className={`border px-3.5 py-1.5 flex items-center justify-between gap-4 ${noPeriode ? 'bg-amber-500/[0.08] border-amber-500/30' : 'bg-[var(--color-bg-subtle)] border-[var(--color-border-subtle)]'}`}>
            {/* Gauche : info période */}
            <div className="flex items-center gap-2 min-w-0">
                {noPeriode
                    ? <AlertTriangle size={12} className="text-amber-600 shrink-0" />
                    : <Calendar size={12} className="text-[var(--color-fg-muted)] shrink-0" />
                }
                <span className={`text-[11px] font-mono font-bold tracking-wide truncate ${noPeriode ? 'text-amber-700 dark:text-amber-400' : 'text-[var(--color-fg)]'}`}>
                    {periodeLibelle}
                </span>
                {periodeInfo?.date_debut && periodeInfo?.date_fin && (
                    <span className="text-[10.5px] font-mono text-[var(--color-fg-muted)] hidden sm:inline">
                        · {formatDateFr(periodeInfo.date_debut)} – {formatDateFr(periodeInfo.date_fin)} ({totalWeeks} sem.)
                    </span>
                )}
                <span className="flex items-center gap-1.5 shrink-0">
                    <span className={`w-1.5 h-1.5 rounded-full ${statusDot} ${!isCloturee ? 'animate-pulse' : ''}`} />
                    <span className={`text-[10.5px] font-semibold ${noPeriode ? 'text-amber-600' : 'text-[var(--color-fg-muted)]'}`}>
                        {statusLabel}
                    </span>
                </span>
            </div>

            {/* Droite : progression 4 étapes */}
            <div className="flex items-center gap-1 shrink-0">
                {stepLabels.map((label, i) => {
                    const stepNum = i + 1;
                    const isDone = stepNum < currentStep;
                    const isActive = stepNum === currentStep;
                    return (
                        <div key={stepNum} className="flex items-center gap-1">
                            <div
                                title={label}
                                className={`
                                    w-5 h-5 flex items-center justify-center text-[9px] font-bold transition-colors
                                    ${isActive
                                        ? 'bg-brand-600 text-white'
                                        : isDone
                                            ? 'bg-emerald-500 text-white'
                                            : 'bg-[var(--color-surface)] border border-[var(--color-border)] text-[var(--color-fg-muted)] opacity-50'
                                    }
                                `}
                            >
                                {isDone ? '✓' : stepNum}
                            </div>
                            <span className={`text-[9.5px] font-semibold hidden lg:inline ${isActive ? 'text-brand-600' : 'text-[var(--color-fg-muted)] opacity-60'}`}>
                                {label}
                            </span>
                            {i < stepLabels.length - 1 && (
                                <div className="hidden lg:block w-4 h-px bg-[var(--color-border-subtle)] mx-0.5" />
                            )}
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
