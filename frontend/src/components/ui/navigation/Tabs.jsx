import { isValidElement, useEffect, useRef, useState } from 'react';
import Button from '@/components/ui/forms/Button';
import { hasHorizontalOverflow } from './tabsOverflow';

/**
 * Tabs — Composant réutilisable d'onglets pour le Design System Galaxam.
 * Conforme WCAG 2.1 AA (WAI-ARIA Tabs pattern) :
 * - role="tablist", aria-label et orientation
 * - role="tab", aria-selected et roving tabindex (tabindex 0 sur l'actif, -1 sur les autres)
 * - Navigation complète aux touches fléchées (Gauche/Droite), Début (Home) et Fin (End).
 */
export function Tabs({
    tabs = [],
    activeTab,
    onChange,
    variant = 'underline',
    bg = 'surface', // 'surface' pour blanc, 'subtle' pour le gris léger
    className = '',
    ariaLabel = 'Navigation par onglets',
    showScrollHint = false,
}) {
    const tabListRef = useRef(null);
    const [hasOverflow, setHasOverflow] = useState(false);

    useEffect(() => {
        if (!showScrollHint || variant !== 'underline') {
            setHasOverflow(false);
            return undefined;
        }

        const tabList = tabListRef.current;
        if (!tabList) return undefined;

        const updateOverflow = () => setHasOverflow(hasHorizontalOverflow(tabList));
        updateOverflow();

        const resizeObserver = typeof ResizeObserver !== 'undefined'
            ? new ResizeObserver(updateOverflow)
            : null;
        resizeObserver?.observe(tabList);
        window.addEventListener('resize', updateOverflow);

        return () => {
            resizeObserver?.disconnect();
            window.removeEventListener('resize', updateOverflow);
        };
    }, [showScrollHint, tabs.length, variant]);

    // Choix dynamique de la couleur de fond
    const bgClass = bg === 'surface' ? 'bg-[var(--color-surface)]' : 'bg-[var(--color-bg-subtle)]';

    const renderIcon = (icon) => {
        if (!icon) return null;
        if (isValidElement(icon)) return icon;
        if (typeof icon === 'function' || (typeof icon === 'object' && icon !== null)) {
            const IconComp = icon;
            return <IconComp size={15} />;
        }
        return null;
    };

    const handleKeyDown = (e) => {
        if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(e.key)) return;
        const enabledTabs = tabs.filter((t) => !t.disabled);
        const currentIndex = enabledTabs.findIndex((t) => t.id === activeTab);
        if (currentIndex === -1) return;

        let nextIndex = currentIndex;
        if (e.key === 'ArrowRight') {
            nextIndex = (currentIndex + 1) % enabledTabs.length;
        } else if (e.key === 'ArrowLeft') {
            nextIndex = (currentIndex - 1 + enabledTabs.length) % enabledTabs.length;
        } else if (e.key === 'Home') {
            nextIndex = 0;
        } else if (e.key === 'End') {
            nextIndex = enabledTabs.length - 1;
        }

        e.preventDefault();
        const targetTab = enabledTabs[nextIndex];
        onChange(targetTab.id);

        // Déplacer le focus DOM vers le nouvel onglet
        const targetButton = tabListRef.current?.querySelector(`[data-tab-id="${targetTab.id}"]`);
        targetButton?.focus();
    };

    if (variant === 'underline') {
        return (
            <div className="min-w-0">
                <div
                    ref={tabListRef}
                    role="tablist"
                    aria-label={ariaLabel}
                    aria-orientation="horizontal"
                    onKeyDown={handleKeyDown}
                    className={`flex border-b border-[var(--color-border-subtle)] ${bgClass} px-3 overflow-x-auto gap-0.5 ${className}`}
                >
                    {tabs.map((tab) => {
                        const isActive = activeTab === tab.id;
                        const badgeVal = tab.count !== undefined ? tab.count : tab.badge;
                        return (
                            <button
                                key={tab.id}
                                id={`tab-${tab.id}`}
                                data-tab-id={tab.id}
                                role="tab"
                                aria-selected={isActive}
                                aria-controls={`panel-${tab.id}`}
                                tabIndex={isActive ? 0 : -1}
                                type="button"
                                disabled={tab.disabled}
                                onClick={() => onChange(tab.id)}
                                className={`
                                flex items-center gap-1.5 py-2 px-3 border-b-2 text-xs font-semibold cursor-pointer whitespace-nowrap rounded-none
                                focus-visible:outline-2 focus-visible:outline-[var(--color-focus-ring)] focus-visible:outline-offset-[-2px]
                                ${isActive
                                ? 'border-[var(--color-brand-600)] text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] font-bold'
                                    : 'border-transparent text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                                }
                                ${tab.disabled ? 'opacity-50 cursor-not-allowed' : ''}
                                `}
                            >
                                {renderIcon(tab.icon)}
                                <span>{tab.label}</span>
                                {badgeVal !== undefined && (
                                    <span className={`px-1.5 py-0.5 text-[10.5px] font-data font-bold rounded-none ${
                                        isActive
                                            ? 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)]'
                                            : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]'
                                    }`}>
                                        {badgeVal}
                                    </span>
                                )}
                            </button>
                        );
                    })}
                </div>
                {showScrollHint && hasOverflow && (
                    <p role="status" aria-live="polite" className="px-3 pt-1 text-[11px] text-[var(--color-fg-muted)]">
                        Faites défiler horizontalement pour voir tous les onglets.
                    </p>
                )}
            </div>
        );
    }

    // Variant 'pills'
    return (
        <div
            ref={tabListRef}
            role="tablist"
            aria-label={ariaLabel}
            aria-orientation="horizontal"
            onKeyDown={handleKeyDown}
            className={`flex flex-wrap border-b border-[var(--color-border-subtle)] ${bgClass} rounded-none p-0 gap-2 shadow-none overflow-x-auto ${className}`}
        >
            {tabs.map((tab) => {
                const isActive = activeTab === tab.id;
                return (
                    <Button
                        key={tab.id}
                        id={`tab-${tab.id}`}
                        data-tab-id={tab.id}
                        role="tab"
                        aria-selected={isActive}
                        aria-controls={`panel-${tab.id}`}
                        tabIndex={isActive ? 0 : -1}
                        type="button"
                        disabled={tab.disabled}
                        onClick={() => onChange(tab.id)}
                        variant={isActive ? 'outline' : 'ghost'}
                        size="sm"
                        iconLeft={tab.icon}
                        className="whitespace-nowrap cursor-pointer"
                    >
                        <span>{tab.label}</span>
                        {tab.count !== undefined && (
                            <span className={`ml-1.5 px-1.5 py-0.5 text-[10px] font-data font-bold rounded-none ${
                                isActive
                                    ? 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)]'
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
