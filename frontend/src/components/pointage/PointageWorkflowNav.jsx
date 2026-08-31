import { NavLink } from 'react-router-dom';
import { CalendarDays, AlertTriangle, FileSpreadsheet, CalendarRange, ArrowRight } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';

export default function PointageWorkflowNav() {
    const { user } = useAuth();
    const isAgent = user?.roles?.includes('ROLE_AGENT_POINTAGE') && !user?.roles?.includes('ROLE_ADMIN');

    const steps = [
        {
            to: '/pointages/saisie',
            label: 'Saisie Quotidienne',
            desc: 'Pointage, heures & indemnités',
            icon: CalendarDays
        },
        {
            to: '/pointages/anomalies',
            label: 'Contrôle des Anomalies',
            desc: 'Saisies incomplets & retards',
            icon: AlertTriangle
        },
        {
            to: '/pointages/synthese',
            label: 'Synthèse & Compensations',
            desc: 'Compensations retards & heures sup',
            icon: FileSpreadsheet
        }
    ];

    if (!isAgent) {
        steps.push({
            to: '/pointages/periodes',
            label: 'Périodes de Paie',
            desc: 'Transmission DAF & Clôture',
            icon: CalendarRange
        });
    }

    return (
        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-2 shadow-xs mb-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2">
                {steps.map((step, index) => {
                    const Icon = step.icon;
                    return (
                        <NavLink
                            key={step.to}
                            to={step.to}
                            className={({ isActive }) => `
                                flex items-center justify-between p-3 border transition-all
                                ${isActive
                                    ? 'bg-[var(--color-brand-500)]/10 border-[var(--color-brand-500)] text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] shadow-xs'
                                    : 'bg-[var(--color-bg-subtle)] border-transparent hover:border-[var(--color-border-subtle)] text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                                }
                            `}
                        >
                            <div className="flex items-center gap-3">
                                <div className="p-2 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shrink-0">
                                    <Icon size={16} />
                                </div>
                                <div className="text-left">
                                    <p className="text-xs font-bold leading-tight">{step.label}</p>
                                    <p className="text-[10px] text-[var(--color-fg-muted)] leading-tight mt-0.5">{step.desc}</p>
                                </div>
                            </div>
                            {index < steps.length - 1 && (
                                <ArrowRight size={13} className="hidden lg:block text-[var(--color-border-subtle)]" />
                            )}
                        </NavLink>
                    );
                })}
            </div>
        </div>
    );
}
