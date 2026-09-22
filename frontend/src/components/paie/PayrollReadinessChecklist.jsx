import { CheckCircle2, AlertTriangle, XCircle, MinusCircle } from 'lucide-react';

const STATUS = {
    PASS: { icon: CheckCircle2, label: 'Prêt', color: 'success' },
    WARNING: { icon: AlertTriangle, label: 'À vérifier', color: 'warning' },
    BLOCK: { icon: XCircle, label: 'Bloquant', color: 'danger' },
    BLOCKING: { icon: XCircle, label: 'Bloquant', color: 'danger' },
    NOT_APPLICABLE: { icon: MinusCircle, label: 'Non applicable', color: 'fg-subtle' },
};

export default function PayrollReadinessChecklist({ checks = [] }) {
    if (!checks.length) return null;

    return (
        <section className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)]" aria-labelledby="payroll-readiness-title">
            <div className="border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] px-4 py-3">
                <h2 id="payroll-readiness-title" className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">Prérequis de calcul</h2>
            </div>
            <ul className="divide-y divide-[var(--color-border-subtle)]">
                {checks.map((check) => {
                    const config = STATUS[check.status] || STATUS.WARNING;
                    const Icon = config.icon;
                    return (
                        <li key={check.code} className="flex items-start gap-3 px-4 py-3">
                            <Icon size={16} className="mt-0.5 shrink-0 text-[var(--color-fg-muted)]" aria-hidden="true" />
                            <div className="min-w-0 flex-1">
                                <p className="text-xs font-semibold text-[var(--color-fg)]">{check.label || check.code}</p>
                                <p className="mt-0.5 text-[11px] text-[var(--color-fg-muted)]">{check.message}</p>
                            </div>
                            <span className="text-[10px] font-bold uppercase text-[var(--color-fg-muted)]">{config.label}</span>
                        </li>
                    );
                })}
            </ul>
        </section>
    );
}
