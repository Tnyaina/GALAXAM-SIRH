import { TrendingUp, TrendingDown } from 'lucide-react';

export default function StatCard({ title, value, change, changeLabel, icon, color = 'brand' }) {
  const isPositive = change >= 0;

  const colors = {
    brand:   'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-500)] border-[var(--color-brand-500)]/20',
    info:    'bg-[var(--color-info-500)]/10 text-[var(--color-info-500)] border-[var(--color-info-500)]/20',
    success: 'bg-[var(--color-success-500)]/10 text-[var(--color-success-500)] border-[var(--color-success-500)]/20',
    warning: 'bg-[var(--color-warning-500)]/10 text-[var(--color-warning-500)] border-[var(--color-warning-500)]/20',
    danger:  'bg-[var(--color-danger-500)]/10 text-[var(--color-danger-500)] border-[var(--color-danger-500)]/20',
  };

  return (
    <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm p-6 hover:border-[var(--color-brand-500)]  ">
      <div className="flex items-start justify-between mb-4">
        <div>
          <p className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">{title}</p>
          <p className="text-3xl font-bold text-[var(--color-fg)] mt-1 font-data">
            {value}
          </p>
        </div>
        {icon && (
          <div className={`w-11 h-11 border rounded-none ${colors[color] ?? colors.brand} flex items-center justify-center shrink-0`}>
            {icon}
          </div>
        )}
      </div>

      {change !== undefined && (
        <div className={`flex items-center gap-1.5 text-xs font-semibold font-data ${isPositive ? 'text-[var(--color-success-500)]' : 'text-[var(--color-danger-500)]'}`}>
          {isPositive ? <TrendingUp size={13} /> : <TrendingDown size={13} />}
          <span>{isPositive ? '+' : ''}{change}%</span>
          {changeLabel && <span className="text-[var(--color-fg-subtle)] font-normal ml-1">{changeLabel}</span>}
        </div>
      )}
    </div>
  );
}