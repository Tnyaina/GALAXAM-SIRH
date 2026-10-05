import { Lock, CheckCircle2, ShieldCheck } from 'lucide-react';

/**
 * WorkflowStepper — Composant de navigation lineaire pour les workflows sequentiels.
 *
 * Props :
 *   steps[]     : { id, label, sublabel?, status: 'active'|'done'|'pending'|'locked'|'daf-only', lockReason? }
 *   currentStep : id de l'etape active
 *   onStepClick : (stepId) => void
 *   activePeriode : objet periode (pour afficher le statut en haut a droite)
 */
export default function WorkflowStepper({ steps = [], currentStep, onStepClick, activePeriode = null }) {
    const stCode = activePeriode?.statutCode || activePeriode?.statut_code;
    const isTransmis = stCode === 'A_VALIDER' || stCode === 'TRANSMIS_DAF';
    const isValidee = stCode === 'VALIDEE';
    const isCloturee = stCode === 'CLOTUREE';

    return (
        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs px-3 py-2 flex items-center justify-between gap-4 flex-wrap">
            {/* Stepper */}
            <div className="flex items-center gap-2 overflow-x-auto">
                {steps.map((step, idx) => {
                    const isActive = step.id === currentStep;
                    const isClickable = step.status === 'active' || step.status === 'done' || step.status === 'pending';
                    const isLocked = step.status === 'locked' || step.status === 'daf-only';
                    const isDone = step.status === 'done';

                    return (
                        <div key={step.id} className="flex items-center">
                            {/* Etape */}
                            <button
                                type="button"
                                disabled={isLocked}
                                onClick={() => isClickable && onStepClick(step.id)}
                                title={isLocked ? (step.lockReason || 'Acces restreint') : step.label}
                                className={`
                                    flex items-center gap-2 px-3 py-1.5 text-[11px] font-semibold
                                    border whitespace-nowrap transition-all
                                    ${isLocked
                                        ? 'opacity-40 cursor-not-allowed border-transparent text-[var(--color-fg-muted)]'
                                        : isClickable
                                            ? 'cursor-pointer'
                                            : 'cursor-default'
                                    }
                                    ${isActive
                                        ? 'bg-[var(--color-brand-500)]/[0.08] text-[var(--color-brand-800)] dark:text-[var(--color-brand-200)] border-[var(--color-brand-500)]/50 shadow-none'
                                        : isDone
                                            ? 'bg-[var(--color-success-500)]/10 text-[var(--color-success-600)] dark:text-[var(--color-success-400)] border-[var(--color-success-500)]/40'
                                            : isLocked
                                                ? 'bg-transparent border-transparent text-[var(--color-fg-muted)]'
                                                : 'bg-transparent border-[var(--color-border-subtle)] text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)] hover:border-[var(--color-border)] hover:text-[var(--color-fg)]'
                                    }
                                `}
                            >
                                {/* Icone de statut */}
                                {isDone && !isActive ? (
                                    <CheckCircle2 size={13} className="text-[var(--color-success-600)] dark:text-[var(--color-success-400)] shrink-0" />
                                ) : isLocked && step.status === 'daf-only' ? (
                                    <ShieldCheck size={13} className="shrink-0" />
                                ) : isLocked ? (
                                    <Lock size={11} className="shrink-0" />
                                ) : (
                                    <span className={`flex items-center justify-center w-4 h-4 text-[9px] font-black border rounded-full shrink-0 ${
                                        isActive
                                            ? 'bg-[var(--color-brand-500)]/15 border-[var(--color-brand-500)]/35 text-[var(--color-brand-800)] dark:text-[var(--color-brand-200)]'
                                            : isDone
                                                ? 'bg-[var(--color-success-500)]/20 border-[var(--color-success-500)]/40 text-[var(--color-success-600)]'
                                                : 'bg-[var(--color-bg-subtle)] border-[var(--color-border)] text-[var(--color-fg-muted)]'
                                    }`}>
                                        {idx + 1}
                                    </span>
                                )}

                                {/* Label */}
                                <span>{step.label}</span>

                                {/* Sublabel (ex: progression) */}
                                {step.sublabel && isActive && (
                                    <span className={`text-[9px] font-bold px-1.5 py-0.5 border ${
                                        isActive
                                            ? 'bg-[var(--color-surface)]/10 border-[var(--color-brand-500)]/35 text-[var(--color-brand-800)] dark:text-[var(--color-brand-200)]'
                                            : 'bg-[var(--color-bg-subtle)] border-[var(--color-border)] text-[var(--color-fg-muted)]'
                                    }`}>
                                        {step.sublabel}
                                    </span>
                                )}

                                {/* Badge étape restreinte */}
                                {step.status === 'daf-only' && (
                                    <span className="text-[8px] font-bold px-1 border border-[var(--color-border)] text-[var(--color-fg-muted)]">
                                        Arbitrage
                                    </span>
                                )}
                            </button>
                        </div>
                    );
                })}
            </div>

            {/* Statut periode */}
            {activePeriode && (
                <div className="flex items-center gap-2 shrink-0 text-xs">
                    <span className="text-[11px] text-[var(--color-fg-muted)]">
                        Periode : <strong className="font-mono text-[var(--color-fg)]">{activePeriode.libelle || activePeriode.code}</strong>
                    </span>
                    <span className={`px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider border ${
                        isCloturee
                            ? 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border-[var(--color-border)]'
                            : isValidee
                                ? 'bg-[var(--color-success-500)]/10 text-[var(--color-success-700)] dark:text-[var(--color-success-400)] border-[var(--color-success-500)]/30'
                                : isTransmis
                                    ? 'bg-[var(--color-warning-500)]/10 text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] border-[var(--color-warning-500)]/30'
                                    : 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] border-[var(--color-brand-500)]/30'
                    }`}>
                        {activePeriode.statutLibelle || activePeriode.statutCode || 'En cours'}
                    </span>
                </div>
            )}
        </div>
    );
}
