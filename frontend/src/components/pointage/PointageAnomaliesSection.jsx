import { Link } from 'react-router-dom';
import { CheckCircle2, AlertTriangle, ArrowRight } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';

export default function PointageAnomaliesSection({
    totalAnomalies = 0
}) {
    const hasAnomalies = totalAnomalies > 0;

    return (
        <section className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 shadow-xs">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                    <div className={`w-[34px] h-[34px] flex items-center justify-center font-bold text-[13px] ${hasAnomalies ? 'bg-amber-500/15 text-amber-600' : 'bg-emerald-500/15 text-emerald-600'}`}>
                        {hasAnomalies ? <AlertTriangle size={17} /> : <CheckCircle2 size={17} />}
                    </div>
                    <div>
                        <h2 className="text-[15px] font-bold text-[var(--color-fg)]">Contrôle des anomalies</h2>
                        <p className="text-[12.5px] text-[var(--color-fg-muted)]">Saisies incomplets et sorties orphelines de la période.</p>
                    </div>
                </div>

                <div className="flex items-center gap-3 self-start sm:self-auto">
                    <Badge variant={hasAnomalies ? 'warning' : 'success'} size="md" className="font-bold">
                        {totalAnomalies} anomalie{totalAnomalies > 1 ? 's' : ''}
                    </Badge>
                    <Link
                        to="/pointages/anomalies"
                        className="text-[12.5px] font-bold text-brand-600 dark:text-brand-400 hover:underline flex items-center gap-1"
                    >
                        <span>Gérer</span>
                        <ArrowRight size={13} />
                    </Link>
                </div>
            </div>

            <p className="text-[12.5px] text-[var(--color-fg-muted)] mt-3 pl-0 sm:pl-[46px] leading-relaxed">
                {hasAnomalies ? (
                    <span className="text-amber-700 dark:text-amber-400 font-medium">
                        Attention : des anomalies de pointage ont été détectées sur la période. Veuillez les régulariser avant de lancer la synthèse hebdomadaire.
                    </span>
                ) : (
                    <>Rien à corriger pour l'instant. Si une anomalie apparaît après une saisie, elle sera listée ici avec l'employé et le jour concernés, pour correction avant la synthèse hebdomadaire.</>
                )}
            </p>
        </section>
    );
}
