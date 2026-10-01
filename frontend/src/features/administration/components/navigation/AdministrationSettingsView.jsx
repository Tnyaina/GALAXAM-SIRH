import Button from '@/components/ui/forms/Button';
import Select from '@/components/ui/forms/Select';
import Tabs from '@/components/ui/navigation/Tabs';

/** Navigation locale : onglets pour une liste courte, sections pour un domaine dense. */
export default function AdministrationSettingsView({ groups, activeId, onChange, children, vertical = false, contained = false, scrollContent = true }) {
    const items = groups.flatMap(group => group.items);
    const choices = groups.flatMap(group => group.items.map(item => ({
        value: item.id,
        label: vertical ? `${group.label} · ${item.label}` : item.label,
    })));
    return (
        <div className={contained ? 'flex min-h-0 flex-1 flex-col space-y-3 lg:space-y-4 overflow-hidden' : 'space-y-3 lg:space-y-4'}>
            <div className="lg:hidden">
                <Select
                    label={vertical ? 'Page de paie' : 'Domaine de configuration'}
                    value={activeId}
                    options={choices}
                    onChange={onChange}
                    aria-label={vertical ? 'Choisir une page de paie' : 'Choisir un domaine de configuration'}
                />
            </div>
            {!vertical && <nav aria-label="Sections de paramétrage" className="hidden lg:block">
                <Tabs tabs={items} activeTab={activeId} onChange={onChange} className="flex-wrap" />
            </nav>}
            <div className={`${contained ? 'flex-1 min-h-0 overflow-hidden' : ''} ${vertical ? 'grid h-full min-h-0 grid-rows-[minmax(0,1fr)] grid-cols-1 lg:grid-cols-[210px_minmax(0,1fr)] gap-5' : contained ? 'flex min-w-0 min-h-0 flex-col' : 'min-w-0'}`}>
                {vertical && <nav aria-label="Sections de paramétrage" className="hidden lg:block min-h-0 space-y-3 border-r border-[var(--color-border-subtle)] pr-3">
                    {groups.map(group => (
                        <div key={group.id} className="space-y-0.5">
                            <h2 className="px-2 pb-1 text-xs font-semibold text-[var(--color-fg-muted)] border-b border-[var(--color-border-subtle)]">{group.label}</h2>
                            {group.items.map(item => {
                                const isActive = activeId === item.id;
                                return (
                                    <Button
                                        key={item.id}
                                        variant="ghost"
                                        size="sm"
                                        aria-current={isActive ? 'page' : undefined}
                                        onClick={() => onChange(item.id)}
                                        className={`w-full min-h-9 max-w-full justify-start text-left !normal-case !tracking-normal !text-xs [&>svg]:shrink-0 ${isActive ? 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)] border-l-2 border-l-[var(--color-brand-500)] font-bold' : 'text-[var(--color-fg)] border-l-2 border-l-transparent'}`}
                                        wrapLabel
                                        iconLeft={item.icon}
                                    >
                                        {item.label}
                                    </Button>
                                );
                            })}
                        </div>
                    ))}
                </nav>}
                <div
                    role={contained ? 'region' : undefined}
                    aria-label={contained ? 'Contenu du paramétrage' : undefined}
                    tabIndex={contained ? 0 : undefined}
                    className={contained
                        ? `min-w-0 min-h-0 flex-1 ${scrollContent ? 'overflow-y-auto overscroll-contain pr-1' : 'overflow-hidden'}`
                        : 'min-w-0'}
                >
                    {children}
                </div>
            </div>
        </div>
    );
}
