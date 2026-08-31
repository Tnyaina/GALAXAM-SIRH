import { isValidElement } from 'react';
import Button from '@/components/ui/forms/Button';

/**
 * Composant réutilisable d'onglets pour le Design System Galaxam.
 */
export function Tabs({
    tabs = [],
    activeTab,
    onChange,
    variant = 'underline',
    bg = 'surface', // 'surface' pour blanc, 'subtle' pour le gris léger
    className = '',
}) {
    // Choix dynamique de la couleur de fond
    const bgClass = bg === 'surface' ? 'bg-[var(--color-surface)]' : 'bg-[var(--color-bg-subtle)]';

    const renderIcon = (icon) => {
        if (!icon) return null;
        if (isValidElement(icon)) return icon;
        if (typeof icon === 'function' || (typeof icon === 'object' && icon !== null)) {
            const IconComp = icon;
            return <IconComp size={16} />;
        }
        return null;
    };

    if (variant === 'underline') {
        return (
            <div className={`flex border-b border-[var(--color-border-subtle)] ${bgClass} px-3 overflow-x-auto gap-0.5 ${className}`}>
                {tabs.map((tab) => {
                    const isActive = activeTab === tab.id;
                    const badgeVal = tab.count !== undefined ? tab.count : tab.badge;
                    return (
                        <button
                            key={tab.id}
                            type="button"
                            onClick={() => onChange(tab.id)}
                            className={`
                                flex items-center gap-1.5 py-2.5 px-3 border-b-2 text-xs sm:text-[13px] font-semibold cursor-pointer whitespace-nowrap rounded-none
                                ${isActive
                                    ? 'border-[var(--color-brand-500)] text-[var(--color-brand-500)] font-bold'
                                    : 'border-transparent text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                                }
                            `}
                        >
                            {renderIcon(tab.icon)}
                            <span>{tab.label}</span>
                            {badgeVal !== undefined && (
                                <span className={`px-1.5 py-0.5 text-[10.5px] font-data font-bold rounded-none ${
                                    isActive
                                        ? 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-500)]'
                                        : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]'
                                }`}>
                                    {badgeVal}
                                </span>
                            )}
                        </button>
                    );
                })}
            </div>
        );
    }

    // Variant 'pills'
    return (
        <div className={`flex border-b border-[var(--color-border-subtle)] ${bgClass} rounded-none p-2 gap-2 shadow-sm overflow-x-auto ${className}`}>
            {tabs.map((tab) => {
                const isActive = activeTab === tab.id;
                return (
                    <Button
                        key={tab.id}
                        type="button"
                        onClick={() => onChange(tab.id)}
                        variant={isActive ? 'primary' : 'ghost'}
                        size="sm"
                        iconLeft={tab.icon}
                        className="whitespace-nowrap cursor-pointer"
                    >
                        <span>{tab.label}</span>
                        {tab.count !== undefined && (
                            <span className={`ml-1.5 px-1.5 py-0.5 text-[10px] font-data font-bold rounded-none ${
                                isActive
                                    ? 'bg-white/20 text-white'
                                    : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]'
                            }`}>
                                {tab.count}
                            </span>
                        )}
                    </Button>
                );
            })}
        </div>
    );
}

export default Tabs;