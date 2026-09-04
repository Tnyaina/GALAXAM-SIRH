import { useState } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, AlertTriangle, ArrowRight, ChevronDown, ChevronRight } from 'lucide-react';

export default function PointageAnomaliesSection({
    totalAnomalies = 0
}) {
    const hasAnomalies = totalAnomalies > 0;
    // Auto-ouvrir si des anomalies existent
    const [open, setOpen] = useState(hasAnomalies);

    return (
        <div className={`bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs overflow-hidden ${hasAnomalies ? 'border-l-2 border-l-amber-500' : ''}`}>
            {/* Header cliquable */}
            <button
                type="button"
                onClick={() => setOpen(p => !p)}
                className="w-full flex items-center justify-between px-4 py-3 text-left hover:bg-[var(--color-bg-subtle)]/60 transition-colors cursor-pointer"
            >
                <div className="flex items-center gap-2.5">
                    {hasAnomalies ? (
                        <AlertTriangle size={14} className="text-[var(--color-warning-600)] shrink-0" />
                    ) : (
                        <CheckCircle2 size={14} className="text-[var(--color-success-600)] shrink-0" />
                    )}
                    <span className="text-[12.5px] font-bold text-[var(--color-fg)]">
                        Contrôle des anomalies
                    </span>
                    <span className={`text-[11px] font-mono px-1.5 py-0.5 font-bold ${hasAnomalies ? 'bg-[var(--color-warning-500)]/10 text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)]' : 'bg-[var(--color-success-500)]/10 text-[var(--color-success-700)] dark:text-[var(--color-success-300)]'}`}>
                        {totalAnomalies} anomalie{totalAnomalies !== 1 ? 's' : ''}
                    </span>
                </div>
                {open
                    ? <ChevronDown size={14} className="text-[var(--color-fg-muted)] shrink-0" />
                    : <ChevronRight size={14} className="text-[var(--color-fg-muted)] shrink-0" />
                }
            </button>

            {/* Corps (collapsible) */}
            {open && (
                <div className="px-4 pb-4 border-t border-[var(--color-border-subtle)]">
                    <div className="flex items-center justify-between mt-3">
                        <p className="text-[12.5px] text-[var(--color-fg-muted)] leading-relaxed max-w-xl">
                            {hasAnomalies ? (
                                <span className="text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] font-medium">
                                    Des anomalies ont été détectées sur la période. Régularisez-les avant de lancer la synthèse hebdomadaire.
                                </span>
                            ) : (
                                <>Rien à corriger. Si une anomalie apparaît après une saisie, elle sera listée ici avec l'employé et le jour concernés.</>
                            )}
                        </p>
                        <Link
                            to="/pointages/anomalies"
                            className="text-[12px] font-bold text-brand-600 dark:text-brand-400 hover:underline flex items-center gap-1 shrink-0 ml-4"
                        >
                            <span>Gérer les anomalies</span>
                            <ArrowRight size={12} />
                        </Link>
                    </div>
                </div>
            )}
        </div>
    );
}
