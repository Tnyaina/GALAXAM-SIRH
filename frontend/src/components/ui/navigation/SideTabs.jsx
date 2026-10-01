import { isValidElement } from 'react';

/** Navigation latérale pour les pages qui ont un troisième niveau d'onglets. */
export default function SideTabs({ tabs = [], activeTab, onChange, children, className = '', title = 'Rubriques' }) {
    return (
        <div className={`grid grid-cols-1 gap-5 md:grid-cols-[260px_minmax(0,1fr)] ${className}`}>
            <nav aria-label={title} className="h-fit overflow-hidden rounded-sm border border-[var(--color-border-subtle)] bg-[var(--color-surface)] shadow-sm">
                <div className="border-b border-[var(--color-border-subtle)] px-4 py-3">
                    <h2 className="text-sm font-bold text-[var(--color-fg)]">{title}</h2>
                    <p className="mt-0.5 text-xs text-[var(--color-fg-muted)]">Choisissez une rubrique à configurer.</p>
                </div>
                <div className="space-y-1 p-2">
                    {tabs.map((tab) => {
                        const isActive = activeTab === tab.id;
                        const Icon = tab.icon;
                        const icon = isValidElement(Icon) ? Icon : Icon ? <Icon size={17} /> : null;
                        return (
                            <button
                                key={tab.id}
                                type="button"
                                onClick={() => onChange(tab.id)}
                                aria-current={isActive ? 'page' : undefined}
                                className={`flex min-h-12 w-full items-center gap-3 border-l-[3px] px-3 py-2.5 text-left text-sm font-semibold leading-5 transition-colors ${isActive
                                    ? 'border-[var(--color-brand-500)] bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)]'
                                    : 'border-transparent text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)] hover:text-[var(--color-fg)]'
                                }`}
                            >
                                <span className="shrink-0">{icon}</span>
                                <span className="min-w-0 flex-1">{tab.label}</span>
                                {tab.count !== undefined && <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-bold ${isActive ? 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)]' : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]'}`}>{tab.count}</span>}
                            </button>
                        );
                    })}
                </div>
            </nav>
            <div className="min-w-0">{children}</div>
        </div>
    );
}
