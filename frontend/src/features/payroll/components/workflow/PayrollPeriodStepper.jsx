import SharedButton from '@/components/ui/forms/Button';
import { CheckCircle2, Lock, AlertTriangle, Loader2 } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useMemo, useState } from 'react';
import PayrollIssuePanel from '@/features/payroll/components/feedback/PayrollIssuePanel';

/**
 * PayrollPeriodStepper — Stepper horizontal backend-driven pour une campagne de paie.
 *
 * @prop {Array}  steps       - Liste des étapes issue du backend (PayrollCampaignStep[])
 * @prop {string} currentStep - Code de l'étape courante (ex: "VARIABLES")
 * @prop {string} activeCode  - Code de l'étape actuellement affichée (la page actuelle)
 * @prop {boolean} loading    - Affiche un indicateur de chargement
 */
export default function PayrollPeriodStepper({
    steps = [],
    currentStep = null,
    activeCode = null,
    loading = false,
}) {
    // Contexte et navigation
    const navigate = useNavigate();

    // État local : détail de l'étape bloquée ouverte par l'utilisateur.
    const [blockedStep, setBlockedStep] = useState(null);

    // Données dérivées : apparence de chaque état fourni par le backend.
    const stateConfig = useMemo(() => ({
        COMPLETED: {
            icon: (_n) => <CheckCircle2 size={15} className="text-[var(--color-success-500)] shrink-0" aria-label="Complété" />,
            label: 'text-[var(--color-fg-muted)]',
            connector: 'bg-[var(--color-success-500)]',
            ring: 'ring-[var(--color-success-500)]/20',
            dot: 'bg-[var(--color-success-500)]',
        },
        CURRENT: {
            icon: (n) => (
                <span className="w-[15px] h-[15px] rounded-none flex items-center justify-center
                    bg-[var(--color-brand-500)] text-[var(--color-on-color)] text-[9px] font-bold shrink-0"
                    aria-label="Étape courante">
                    {n}
                </span>
            ),
            label: 'text-[var(--color-brand-600)] dark:text-[var(--color-brand-300)] font-semibold',
            connector: 'bg-[var(--color-border)]',
            ring: 'ring-[var(--color-brand-500)]/20',
            dot: 'bg-[var(--color-brand-500)]',
        },
        AVAILABLE: {
            icon: (n) => (
                <span className="w-[15px] h-[15px] rounded-none border-2 border-[var(--color-border)] flex items-center justify-center
                    text-[9px] font-bold text-[var(--color-fg-subtle)] shrink-0" aria-label="Disponible">
                    {n}
                </span>
            ),
            label: 'text-[var(--color-fg-muted)]',
            connector: 'bg-[var(--color-border)]',
            ring: '',
            dot: 'bg-[var(--color-border)]',
        },
        WARNING: {
            icon: (_n) => <AlertTriangle size={14} className="text-[var(--color-warning-500)] shrink-0" aria-label="Avertissement" />,
            label: 'text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] font-semibold',
            connector: 'bg-[var(--color-border)]',
            ring: 'ring-[var(--color-warning-500)]/20',
            dot: 'bg-[var(--color-warning-500)]',
        },
        STALE: {
            icon: (_n) => <AlertTriangle size={14} className="text-[var(--color-warning-500)] shrink-0" aria-label="Calcul obsolète" />,
            label: 'text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] font-semibold',
            connector: 'bg-[var(--color-border)]',
            ring: 'ring-[var(--color-warning-500)]/20',
            dot: 'bg-[var(--color-warning-500)]',
        },
        BLOCKED: {
            icon: (n) => (
                <span className="w-[15px] h-[15px] rounded-none border border-dashed border-[var(--color-border)] flex items-center justify-center
                    text-[9px] text-[var(--color-fg-subtle)] opacity-60 shrink-0" aria-label="Bloqué">
                    {n}
                </span>
            ),
            label: 'text-[var(--color-fg-muted)]',
            connector: 'bg-[var(--color-border-subtle)]',
            ring: '',
            dot: 'bg-[var(--color-border-subtle)]',
        },
        LOCKED: {
            icon: (_n) => <Lock size={12} className="text-[var(--color-fg-subtle)] shrink-0" aria-label="Verrouillé" />,
            label: 'text-[var(--color-fg-muted)]',
            connector: 'bg-[var(--color-success-500)]',
            ring: '',
            dot: 'bg-[var(--color-fg-subtle)]',
        },
    }), []);

    // Rendu : gardes d'affichage.
    if (loading) {
        return (
            <div className="flex items-center gap-2 p-3 text-xs text-[var(--color-fg-muted)]">
                <Loader2 size={14} className="animate-spin shrink-0" />
                <span>Chargement du workflow…</span>
            </div>
        );
    }

    if (!steps || steps.length === 0) return null;

    // Rendu : étapes et explication éventuelle d'un blocage.
    return (
        <nav
            role="navigation"
            aria-label="Workflow de campagne de paie"
            className="relative bg-[var(--color-surface)] border border-[var(--color-border-subtle)]"
        >
            <p className="border-b border-[var(--color-border-subtle)] px-3 py-2 text-xs text-[var(--color-fg-muted)] sm:hidden">
                Faites défiler les étapes horizontalement pour parcourir la campagne.
            </p>
            <ol className="relative z-10 grid w-full max-w-full grid-flow-col auto-cols-[minmax(10rem,1fr)] snap-x snap-mandatory overflow-x-auto overscroll-x-contain" aria-label="Étapes de la campagne">
                {steps.map((step, index) => {
                    const conf = stateConfig[step.state] || stateConfig.BLOCKED;
                    const isActive = step.code === activeCode;
                    const _isCurrentBusiness = step.code === currentStep;
                    const accessible = step.accessible ?? step.state !== 'BLOCKED';
                    const num = index + 1;

                    return (
                        <li
                            key={step.code}
                            className={`
                                min-w-0 snap-start flex items-stretch group border-r border-[var(--color-border-subtle)]
                                ${isActive ? 'bg-[var(--color-brand-500)]/5' : ''}
                            `}
                        >
                            <SharedButton plain
                                type="button"
                                aria-disabled={!accessible}
                                onClick={() => accessible ? navigate(step.destination) : setBlockedStep(step)}
                                title={step.issues?.length > 0 ? step.issues[0] : step.label}
                                aria-current={isActive ? 'step' : undefined}
                                className={`
                                    w-full min-h-[68px] min-w-0 flex items-start gap-2.5 px-3 py-3 text-left transition-colors duration-150
                                    ${accessible ? 'cursor-pointer hover:bg-[var(--color-bg-subtle)]' : 'cursor-not-allowed opacity-80'}
                                    ${isActive ? 'bg-[var(--color-brand-500)]/8' : ''}
                                    focus:outline-none focus:ring-1 focus:ring-inset focus:ring-[var(--color-brand-500)]/40
                                `}
                            >
                                {/* Icône d'état */}
                                {conf.icon(num)}

                                {/* Label & sous-texte — conforme V1 Full V3 */}
                                <span className="flex min-w-0 flex-1 flex-col items-start text-left">
                                    <span className={`break-words text-xs font-semibold leading-snug ${conf.label}`}>
                                        {step.label}
                                        {isActive && (
                                            <span className="sr-only"> (page actuelle)</span>
                                        )}
                                    </span>
                                    {/* Sous-texte contextuel (subtitle ou issue résumé) — V1 Full V3 */}
                                    {step.subtitle && !step.warnings?.length && !step.issues?.length && (
                                        <span className="break-words text-xs text-[var(--color-fg-muted)] leading-snug mt-0.5">
                                            {step.subtitle}
                                        </span>
                                    )}
                                    {step.warnings?.length > 0 && (
                                        <span className="break-words text-[11px] text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] leading-snug mt-1">
                                            ⚠ {step.warnings[0]}
                                        </span>
                                    )}
                                    {step.issues?.length > 0 && step.state !== 'COMPLETED' && step.state !== 'LOCKED' && (
                                        <span className="break-words text-[11px] text-[var(--color-fg-muted)] leading-snug mt-1">
                                            {step.issues[0]}
                                        </span>
                                    )}
                                </span>
                            </SharedButton>
                        </li>
                    );
                })}
            </ol>
            {blockedStep && (
                <div className="border-t border-[var(--color-border-subtle)] p-2">
                    <PayrollIssuePanel
                        severity="BLOCKER"
                        title={`${blockedStep.label} indisponible`}
                        message={blockedStep.issues?.[0] || 'Cette étape n’est pas encore accessible dans l’état actuel de la campagne.'}
                        code={blockedStep.code}
                    />
                </div>
            )}
        </nav>
    );
}
