import { isValidElement, useRef } from 'react';

/** Navigation latérale pour les pages qui ont un troisième niveau d'onglets (conforme WCAG 2.1 AA). */
export default function SideTabs({ tabs = [], activeTab, onChange, children, className = '', title = 'Rubriques', badge = null, sidebarFooter = null }) {
    const listRef = useRef(null);

    const handleKeyDown = (e) => {
        if (!['ArrowUp', 'ArrowDown', 'Home', 'End'].includes(e.key)) return;
        const enabledTabs = tabs.filter((t) => !t.disabled);
        const currentIndex = enabledTabs.findIndex((t) => t.id === activeTab);
        if (currentIndex === -1) return;

        let nextIndex = currentIndex;
        if (e.key === 'ArrowDown') {
            nextIndex = (currentIndex + 1) % enabledTabs.length;
        } else if (e.key === 'ArrowUp') {
            nextIndex = (currentIndex - 1 + enabledTabs.length) % enabledTabs.length;
        } else if (e.key === 'Home') {
            nextIndex = 0;
        } else if (e.key === 'End') {
            nextIndex = enabledTabs.length - 1;
        }

        e.preventDefault();
        const targetTab = enabledTabs[nextIndex];
        onChange(targetTab.id);

        const targetButton = listRef.current?.querySelector(`[data-tab-id="${targetTab.id}"]`);
        targetButton?.focus();
    };

    return (
        <div className={`grid grid-cols-1 gap-5 md:grid-cols-[240px_minmax(0,1fr)] ${className}`}>
            <div className="flex flex-col gap-3">
                <nav aria-label={title} className="h-fit overflow-hidden rounded-none border border-[var(--color-border-subtle)] bg-[var(--color-surface)] shadow-sm">
                    <div className="border-b border-[var(--color-border-subtle)] px-4 py-3">
                        <div className="flex items-center justify-between">
                            <h2 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">{title}</h2>
                            {badge && (
                                <span className="text-[10px] font-semibold text-[var(--color-fg-muted)] bg-[var(--color-bg-subtle)] px-1.5 py-0.5 border border-[var(--color-border-subtle)] rounded-none">
                                    {badge}
                                </span>
                            )}
                        </div>
                    </div>
                    <div
                        ref={listRef}
                        role="tablist"
                        aria-orientation="vertical"
                        aria-label={title}
                        onKeyDown={handleKeyDown}
                        className="space-y-1 p-2"
                    >
                        {tabs.map((tab) => {
                            const isActive = activeTab === tab.id;
                            const Icon = tab.icon;
                            const icon = isValidElement(Icon) ? Icon : Icon ? <Icon size={17} /> : null;
                            return (
                                <button
                                    key={tab.id}
                                    id={`sidetab-${tab.id}`}
                                    data-tab-id={tab.id}
                                    role="tab"
                                    aria-selected={isActive}
                                    aria-controls={`sidetabpanel-${tab.id}`}
                                    tabIndex={isActive ? 0 : -1}
                                    type="button"
                                    disabled={tab.disabled}
                                    onClick={() => onChange(tab.id)}
                                    className={`flex min-h-12 w-full items-center gap-3 border-l-[3px] px-3 py-2.5 text-left text-sm font-semibold leading-5 transition-colors rounded-none focus-visible:outline-2 focus-visible:outline-[var(--color-focus-ring)] focus-visible:outline-offset-[-2px] ${isActive
                                        ? 'border-[var(--color-brand-500)] bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)]'
                                        : 'border-transparent text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)] hover:text-[var(--color-fg)]'
                                    } ${tab.disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
                                >
                                    <span className="shrink-0">{icon}</span>
                                    <span className="min-w-0 flex-1">{tab.label}</span>
                                    {tab.count !== undefined && <span className={`shrink-0 rounded-none px-2 py-0.5 text-xs font-bold ${isActive ? 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)]' : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]'}`}>{tab.count}</span>}
                                </button>
                            );
                        })}
                    </div>
                </nav>
                {sidebarFooter}
            </div>
            <div className="min-w-0" role="tabpanel" id={`sidetabpanel-${activeTab}`} aria-labelledby={`sidetab-${activeTab}`}>
                {children}
            </div>
        </div>
    );
}

