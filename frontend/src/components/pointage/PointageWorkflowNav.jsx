import { NavLink } from 'react-router-dom';
import { CalendarDays, AlertTriangle, FileSpreadsheet, CalendarRange } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';

export default function PointageWorkflowNav() {
    const { hasPermission } = useAuth();
    const canViewPeriodes = hasPermission('POINTAGE_VALIDATE');

    const steps = [
        {
            to: '/pointages/saisie',
            label: 'Saisie quotidienne',
            icon: CalendarDays,
            step: 1
        },
        {
            to: '/pointages/anomalies',
            label: 'Anomalies',
            icon: AlertTriangle,
            step: 2
        },
        {
            to: '/pointages/synthese',
            label: 'Synthèse & Compensations',
            icon: FileSpreadsheet,
            step: 3
        }
    ];

    if (canViewPeriodes) {
        steps.push({
            to: '/pointages/periodes',
            label: 'Périodes de Paie',
            icon: CalendarRange,
            step: 4
        });
    }

    return (
        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs flex items-stretch overflow-x-auto">
            {steps.map((step) => {
                const Icon = step.icon;
                return (
                    <NavLink
                        key={step.to}
                        to={step.to}
                        className={({ isActive }) => `
                            flex items-center gap-2 px-4 py-2 text-[11.5px] font-semibold border-r border-[var(--color-border-subtle)] whitespace-nowrap transition-colors shrink-0 last:border-r-0
                            ${isActive
                                ? 'bg-brand-600/8 text-brand-700 dark:text-brand-300 border-b-2 border-b-brand-600'
                                : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)]'
                            }
                        `}
                    >
                        <span className="flex items-center justify-center w-4 h-4 rounded-full bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-[9px] font-bold shrink-0">
                            {step.step}
                        </span>
                        <Icon size={12} className="shrink-0" />
                        <span>{step.label}</span>
                    </NavLink>
                );
            })}
        </div>
    );
}
