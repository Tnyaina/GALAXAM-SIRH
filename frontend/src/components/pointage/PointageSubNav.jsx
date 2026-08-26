import { NavLink } from 'react-router-dom';
import { Clock, BarChart2, AlertTriangle, CalendarDays } from 'lucide-react';

const NAV_ITEMS = [
    {
        to: '/pointage',
        label: 'Saisie Quotidienne',
        icon: Clock,
        end: true
    },
    {
        to: '/pointage/synthese',
        label: 'Synthèse & Export',
        icon: BarChart2
    },
    {
        to: '/pointage/anomalies',
        label: 'Anomalies de Pointage',
        icon: AlertTriangle
    },
    {
        to: '/pointage/periodes',
        label: 'Périodes de Paie',
        icon: CalendarDays
    }
];

export default function PointageSubNav({ className = '' }) {
    return (
        <div className={`border-b border-[var(--color-border-subtle)] bg-[var(--color-surface)] shadow-xs ${className}`}>
            <nav className="flex space-x-1 sm:space-x-2 px-4 sm:px-6 overflow-x-auto no-scrollbar">
                {NAV_ITEMS.map((item) => {
                    const Icon = item.icon;
                    return (
                        <NavLink
                            key={item.to}
                            to={item.to}
                            end={item.end}
                            className={({ isActive }) => `
                                flex items-center gap-2 py-3 px-3.5 border-b-2 text-xs sm:text-sm font-semibold tracking-wide transition-all whitespace-nowrap
                                ${isActive
                                    ? 'border-[var(--color-brand-500)] text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] bg-[var(--color-brand-500)]/5'
                                    : 'border-transparent text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:border-[var(--color-border)]'
                                }
                            `}
                        >
                            <Icon size={16} className="shrink-0" />
                            <span>{item.label}</span>
                        </NavLink>
                    );
                })}
            </nav>
        </div>
    );
}
