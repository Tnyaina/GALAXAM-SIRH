import { AlertCircle, AlertTriangle, Info } from 'lucide-react';
import Button from '@/components/ui/forms/Button';

const CONFIG = {
    BLOCKER: { icon: AlertCircle, label: 'Bloquant', tone: 'danger' },
    WARNING: { icon: AlertTriangle, label: 'Attention', tone: 'warning' },
    INFO: { icon: Info, label: 'Information', tone: 'brand' },
};

export default function PayrollIssuePanel({ severity = 'INFO', title, message, source, code, action }) {
    const config = CONFIG[severity] || CONFIG.INFO;
    const Icon = config.icon;

    return (
        <section
            role={severity === 'BLOCKER' ? 'alert' : 'status'}
            className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-3"
        >
            <div className="flex items-start gap-3">
                <Icon size={16} className="mt-0.5 shrink-0 text-[var(--color-fg-muted)]" aria-hidden="true" />
                <div className="min-w-0 flex-1">
                    <p className="text-xs font-bold text-[var(--color-fg)]">{title || config.label}</p>
                    {message && <p className="mt-1 text-[11px] text-[var(--color-fg-muted)]">{message}</p>}
                    {(source || code) && (
                        <p className="mt-1 text-[10px] text-[var(--color-fg-subtle)]">
                            {source && <>Source : {source}</>}
                            {source && code && <span aria-hidden="true"> · </span>}
                            {code && <>Code : <code>{code}</code></>}
                        </p>
                    )}
                </div>
                {action && <Button variant="outline" size="xs" onClick={action.onClick}>{action.label}</Button>}
            </div>
        </section>
    );
}
