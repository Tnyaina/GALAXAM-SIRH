import { Lock, Calendar } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';

export default function PointageGuideStepper({
    periodeInfo = null,
    totalWeeks = 4,
    currentStep = 1
}) {
    const isCloturee = periodeInfo?.statut_code === 'CLOTUREE' || periodeInfo?.statut_code === 'VALIDEE';
    const isEnValidation = periodeInfo?.statut_code === 'A_VALIDER';

    const periodeLibelle = (periodeInfo?.libelle || periodeInfo?.code || 'Période active').toUpperCase();

    const formatDateFr = (dStr) => {
        if (!dStr) return '';
        try {
            const date = new Date(dStr);
            return new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' }).format(date);
        } catch {
            return dStr;
        }
    };

    return (
        <section className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs">
            {/* 1. Header du Cycle de Pointage - Fortement Distinguable */}
            <div className="bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] px-5 py-3.5 flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                    <div className="w-8 h-8 bg-brand-500/10 text-brand-600 dark:text-brand-400 flex items-center justify-center font-bold">
                        <Calendar size={16} />
                    </div>
                    <div>
                        <div className="flex items-center gap-2">
                            <span className="text-[12px] font-extrabold tracking-wider text-brand-700 dark:text-brand-300 uppercase font-mono">
                                CYCLE DE POINTAGE · {periodeLibelle}
                            </span>
                            <Badge
                                variant={isCloturee ? 'secondary' : isEnValidation ? 'warning' : 'success'}
                                size="xs"
                                dot
                            >
                                {periodeInfo?.statut_libelle || (isEnValidation ? 'À valider DAF' : isCloturee ? 'Clôturée' : 'Saisie en cours')}
                            </Badge>
                        </div>
                        <p className="text-[11.5px] font-mono text-[var(--color-fg-muted)] mt-0.5">
                            {periodeInfo?.date_debut && periodeInfo?.date_fin ? (
                                <>Du {formatDateFr(periodeInfo.date_debut)} au {formatDateFr(periodeInfo.date_fin)} · {totalWeeks} semaines</>
                            ) : (
                                <>Période opérationnelle active</>
                            )}
                        </p>
                    </div>
                </div>

                <div className="text-[11px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider bg-[var(--color-surface)] px-2.5 py-1 border border-[var(--color-border-subtle)]">
                    Étape {currentStep} / 4 : Saisie
                </div>
            </div>

            {/* 2. Stepper Visuel en 4 Étapes */}
            <div className="p-5">
                <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
                    {/* Étape 1 : Saisie Quotidienne (Active) */}
                    <div className="flex items-center gap-3 shrink-0">
                        <div className="w-[32px] h-[32px] flex items-center justify-center font-bold text-[13px] bg-brand-600 text-white shadow-xs">
                            1
                        </div>
                        <div>
                            <p className="text-[13px] font-bold text-[var(--color-fg)]">Saisie quotidienne</p>
                            <p className="text-[11.5px] text-[var(--color-fg-muted)]">Horaires &amp; indemnités du jour</p>
                        </div>
                    </div>

                    <div className="hidden lg:block h-[1px] flex-1 bg-[var(--color-border-subtle)] mx-3" />

                    {/* Étape 2 : Contrôle des anomalies */}
                    <div className="flex items-center gap-3 shrink-0 opacity-70">
                        <div className="w-[32px] h-[32px] flex items-center justify-center font-bold text-[12px] bg-[var(--color-surface)] border border-[var(--color-border)] text-[var(--color-fg-muted)]">
                            2
                        </div>
                        <div>
                            <p className="text-[13px] font-bold text-[var(--color-fg)]">Contrôle des anomalies</p>
                            <p className="text-[11.5px] text-[var(--color-fg-muted)]">Saisies incomplets</p>
                        </div>
                    </div>

                    <div className="hidden lg:block h-[1px] flex-1 bg-[var(--color-border-subtle)] mx-3" />

                    {/* Étape 3 : Synthèse hebdomadaire */}
                    <div className="flex items-center gap-3 shrink-0 opacity-70">
                        <div className="w-[32px] h-[32px] flex items-center justify-center font-bold text-[12px] bg-[var(--color-surface)] border border-[var(--color-border)] text-[var(--color-fg-muted)]">
                            3
                        </div>
                        <div>
                            <p className="text-[13px] font-bold text-[var(--color-fg)]">Synthèse hebdomadaire</p>
                            <p className="text-[11.5px] text-[var(--color-fg-muted)]">HS 30/50 % &amp; retards</p>
                        </div>
                    </div>

                    <div className="hidden lg:block h-[1px] flex-1 bg-[var(--color-border-subtle)] mx-3" />

                    {/* Étape 4 : Transmission DAF */}
                    <div className="flex items-center gap-3 shrink-0 opacity-50">
                        <div className="w-[32px] h-[32px] flex items-center justify-center font-bold text-[12px] bg-[var(--color-surface)] border border-[var(--color-border)] text-[var(--color-fg-muted)]">
                            <Lock size={13} />
                        </div>
                        <div>
                            <p className="text-[13px] font-bold text-[var(--color-fg)]">Transmission DAF</p>
                            <p className="text-[11.5px] text-[var(--color-fg-muted)]">Clôture de la période</p>
                        </div>
                    </div>
                </div>
            </div>
        </section>
    );
}
