import { Users, CheckCircle2, AlertTriangle, XCircle } from 'lucide-react';

export default function PointageStatsCards({ kpis }) {
    const pctOf = (value) => (kpis.total > 0 ? Math.round((value / kpis.total) * 100) : 0);
    const tauxPresence = pctOf(kpis.presents);
    const tauxRetard = pctOf(kpis.retards);
    const tauxAbsence = pctOf(kpis.absences);

    return (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {/* Effectif Total */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 flex items-center justify-between shadow-sm">
                <div>
                    <p className="text-[11px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">Effectif</p>
                    <div className="mt-1 flex items-baseline gap-1.5">
                        <span className="text-xl font-extrabold text-[var(--color-fg)] font-mono">{kpis.total}</span>
                        <span className="text-[11px] text-[var(--color-fg-muted)]">employés</span>
                    </div>
                </div>
                <div className="w-8 h-8 rounded-none bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] flex items-center justify-center">
                    <Users size={16} />
                </div>
            </div>

            {/* Présents */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 flex items-center justify-between shadow-xs">
                <div>
                    <p className="text-[11px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">Présents</p>
                    <div className="mt-1 flex items-baseline gap-1.5">
                        <span className="text-xl font-extrabold text-[var(--color-success-600)] font-mono">{kpis.presents}</span>
                        <span className="text-[11px] text-[var(--color-fg-muted)]">({tauxPresence}%)</span>
                    </div>
                </div>
                <div className="w-8 h-8 rounded-none bg-[var(--color-success-500)]/10 text-[var(--color-success-600)] flex items-center justify-center">
                    <CheckCircle2 size={16} />
                </div>
            </div>

            {/* Retards */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 flex items-center justify-between shadow-xs">
                <div>
                    <p className="text-[11px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">Retards</p>
                    <div className="mt-1 flex items-baseline gap-1.5">
                        <span className="text-xl font-extrabold text-[var(--color-warning-600)] font-mono">{kpis.retards}</span>
                        <span className="text-[11px] text-[var(--color-fg-muted)]">({tauxRetard}%)</span>
                    </div>
                </div>
                <div className="w-8 h-8 rounded-none bg-[var(--color-warning-500)]/10 text-[var(--color-warning-600)] flex items-center justify-center">
                    <AlertTriangle size={16} />
                </div>
            </div>

            {/* Absences */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 flex items-center justify-between shadow-xs">
                <div>
                    <p className="text-[11px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">Absences & Congés</p>
                    <div className="mt-1 flex items-baseline gap-1.5">
                        <span className="text-xl font-extrabold text-[var(--color-danger-600)] font-mono">{kpis.absences}</span>
                        <span className="text-[11px] text-[var(--color-fg-muted)]">({tauxAbsence}%)</span>
                    </div>
                </div>
                <div className="w-8 h-8 rounded-none bg-[var(--color-danger-500)]/10 text-[var(--color-danger-600)] flex items-center justify-center">
                    <XCircle size={16} />
                </div>
            </div>
        </div>
    );
}
