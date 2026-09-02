import { NavLink, useSearchParams } from 'react-router-dom';
import { CalendarDays, AlertTriangle, FileSpreadsheet, CalendarRange } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';

export default function PointageWorkflowNav() {
    const { hasPermission } = useAuth();
    const canViewPeriodes = hasPermission('POINTAGE_VALIDATE');

    const steps = [
        {
            to: '/pointages?tab=saisie',
            tab: 'saisie',
            label: '1. Saisie Quotidienne (Agent)',
            icon: CalendarDays,
            step: 1
        },
        {
            to: '/pointages?tab=anomalies',
            tab: 'anomalies',
            label: '2. Suivi Anomalies & Incidents',
            icon: AlertTriangle,
            step: 2
        },
        {
            to: '/pointages?tab=synthese',
            tab: 'synthese',
            label: '3. Consolidation Hebdo & Clôture DAF',
            icon: FileSpreadsheet,
            step: 3
        }
    ];

    if (canViewPeriodes) {
        steps.push({
            to: '/pointages?tab=periodes',
            tab: 'periodes',
            label: '4. Calendrier des Périodes',
            icon: CalendarRange,
            step: 4
        });
    }

    const [searchParams] = useSearchParams();
    const currentTab = searchParams.get('tab') || 'vue-globale';

    return (
        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs flex items-stretch overflow-x-auto">
            {steps.map((step) => {
                const Icon = step.icon;
                const isActive = currentTab === step.tab;
                return (
                    <NavLink
                        key={step.to}
                        to={step.to}
                        className={`
                            flex items-center gap-2 px-4 py-2 text-[11.5px] font-semibold border-r border-[var(--color-border-subtle)] whitespace-nowrap transition-colors shrink-0 last:border-r-0
                            ${isActive
                                ? 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-500)] font-bold border-b-2 border-b-[var(--color-brand-500)]'
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
