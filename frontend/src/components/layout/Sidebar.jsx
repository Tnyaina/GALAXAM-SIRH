import { useState, useEffect, createElement } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { X, ChevronDown, ChevronLeft, ChevronRight } from 'lucide-react';
import { getMenuIcon } from '@/utils/iconRegistry';

const COLLAPSE_STORAGE_KEY = 'galaxam-sidebar-collapsed';

function sortByRang(items) {
    return [...items].sort((a, b) => (a.rang ?? 0) - (b.rang ?? 0));
}

const isChildActive = (node, currentPath) => {
    if (!node.sousMenus) return false;
    return node.sousMenus.some(child => (child.href && currentPath === child.href) || isChildActive(child, currentPath));
};

function NavNode({ item, onClose, depth = 0, collapsed, onRequestExpandSidebar }) {
    const location = useLocation();
    const hasChildren = item.sousMenus?.length > 0;
    const paddingLeft = `${16 + depth * 16}px`;

    const [expanded, setExpanded] = useState(() => hasChildren && isChildActive(item, location.pathname));

    useEffect(() => {
        if (hasChildren && isChildActive(item, location.pathname)) {
            setExpanded(prev => (prev ? prev : true));
        }
    }, [location.pathname, hasChildren, item]);

    const handleParentClick = () => {
        if (collapsed) {
            onRequestExpandSidebar?.();
            setExpanded(true);
            return;
        }
        setExpanded((p) => !p);
    };

    if (hasChildren) {
        return (
            <li>
                <button
                    onClick={handleParentClick}
                    style={{
                        paddingLeft,
                        color: expanded ? 'var(--color-sidebar-fg-active)' : 'var(--color-sidebar-fg)'
                    }}
                    title={collapsed ? item.libelle : undefined}
                    className="
                        w-full flex items-center gap-3 pr-4 py-2.5
                        text-sm font-semibold rounded-none
                        transition-colors duration-150
                        hover:bg-[var(--color-sidebar-hover-bg)] hover:text-[var(--color-sidebar-fg-hover)]
                    "
                >
                    {createElement(getMenuIcon(item.icone), {
                        size: 17,
                        className: 'shrink-0',
                        strokeWidth: 1.9,
                    })}
                    <span className={`flex-1 text-left truncate ${collapsed ? 'lg:hidden' : ''}`}>
                        {item.libelle}
                    </span>
                    <ChevronDown
                        size={14}
                        className={`shrink-0 transition-transform duration-200 ${
                            expanded ? 'rotate-180 opacity-100' : 'opacity-60'
                        } ${collapsed ? 'lg:hidden' : ''}`}
                    />
                </button>

                <div
                    className={`grid transition-all duration-200 ${
                        expanded && !collapsed ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'
                    } ${collapsed ? 'lg:hidden' : ''}`}
                >
                    <ul className="overflow-hidden space-y-0.5 py-0.5">
                        {sortByRang(item.sousMenus).map((child) => (
                            <NavNode
                                key={child.idMenu}
                                item={child}
                                onClose={onClose}
                                depth={depth + 1}
                                collapsed={collapsed}
                                onRequestExpandSidebar={onRequestExpandSidebar}
                            />
                        ))}
                    </ul>
                </div>
            </li>
        );
    }

    return (
        <li>
            <NavLink
                to={item.href || '#'}
                end={true}
                onClick={onClose}
                title={collapsed ? item.libelle : undefined}
                style={({ isActive }) => ({
                    paddingLeft,
                    backgroundColor: isActive ? 'var(--color-sidebar-active-bg)' : 'transparent',
                    color: isActive ? 'var(--color-sidebar-fg-active)' : 'var(--color-sidebar-fg)',
                    borderColor: isActive ? 'var(--color-sidebar-brand)' : 'transparent'
                })}
                className="
                    group relative flex items-center gap-3 pr-4 py-2.5 text-sm font-medium
                    border-l-2 rounded-none transition-colors duration-150
                    hover:bg-[var(--color-sidebar-hover-bg)] hover:text-[var(--color-sidebar-fg-hover)]
                "
            >
                {createElement(getMenuIcon(item.icone), {
                    size: 17,
                    className: 'shrink-0 opacity-90 group-hover:opacity-100',
                    strokeWidth: 1.9,
                })}
                <span className={`truncate ${collapsed ? 'lg:hidden' : ''}`}>{item.libelle}</span>
            </NavLink>
        </li>
    );
}

export default function Sidebar({ open, onClose, menu = [], loading, error }) {
    const sorted = sortByRang(menu);

    const [collapsed, setCollapsed] = useState(() => {
        if (typeof window === 'undefined') return false;
        return window.localStorage.getItem(COLLAPSE_STORAGE_KEY) === '1';
    });

    useEffect(() => {
        window.localStorage.setItem(COLLAPSE_STORAGE_KEY, collapsed ? '1' : '0');
    }, [collapsed]);

    return (
        <>
            {open && (
                <div
                    className="fixed inset-0 bg-black/60 backdrop-blur-[2px] z-40 lg:hidden"
                    onClick={onClose}
                />
            )}

            <aside
                className={`
                    fixed inset-y-0 left-0 z-50 flex flex-col rounded-none
                    bg-[var(--color-sidebar-bg)] border-r border-[var(--color-sidebar-border)] shadow-lg
                    transition-all duration-300 ease-in-out
                    lg:static lg:translate-x-0 lg:shadow-none
                    w-[260px] ${collapsed ? 'lg:w-[76px]' : 'lg:w-[260px]'}
                    ${open ? 'translate-x-0' : '-translate-x-full'}
                `}
            >
                <button
                    onClick={() => setCollapsed((v) => !v)}
                    aria-label={collapsed ? 'Déplier le menu' : 'Replier le menu'}
                    style={{
                        backgroundColor: 'var(--color-sidebar-bg)',
                        color: 'var(--color-sidebar-fg)'
                    }}
                    className="
                        hidden lg:flex absolute top-6 -right-3 z-10
                        w-6 h-6 items-center justify-center rounded-none
                        border border-[var(--color-sidebar-border)]
                        hover:text-[var(--color-sidebar-fg-hover)] hover:border-[var(--color-sidebar-brand)]
                        shadow-sm transition-colors duration-150
                    "
                >
                    {collapsed ? <ChevronRight size={14} /> : <ChevronLeft size={14} />}
                </button>

                <div className={`flex items-center h-16 border-b border-[var(--color-sidebar-border)] shrink-0 px-5 ${collapsed ? 'lg:justify-center lg:px-0' : 'justify-between'}`}>
                    <div className="flex items-center gap-3 min-w-0">
                        <div className="w-8 h-8 flex items-center justify-center bg-[var(--color-sidebar-brand)] text-white font-bold text-sm shrink-0 shadow-sm rounded-none">
                            G
                        </div>
                        <span
                            style={{ color: 'var(--color-sidebar-fg-active)' }}
                            className={`text-base font-bold tracking-tight truncate ${collapsed ? 'lg:hidden' : ''}`}
                        >
                            Galaxam SIRH
                        </span>
                    </div>
                    <button
                        onClick={onClose}
                        style={{ color: 'var(--color-sidebar-fg)' }}
                        className="lg:hidden p-1 hover:text-[var(--color-sidebar-fg-hover)] transition-colors rounded-none"
                    >
                        <X size={18} />
                    </button>
                </div>

                <nav className="flex-1 overflow-y-auto overflow-x-hidden py-4">
                    {loading && (
                        <div className="px-4 space-y-2">
                            {[...Array(5)].map((_, i) => (
                                <div key={i} className="h-8 bg-[var(--color-sidebar-hover-bg)] animate-pulse rounded-none" />
                            ))}
                        </div>
                    )}
                    {error && (
                        <p className={`px-4 text-xs text-[var(--color-danger-500)] ${collapsed ? 'lg:hidden' : ''}`}>
                            Impossible de charger le menu.
                        </p>
                    )}
                    {!loading && !error && (
                        <ul className="space-y-0.5">
                            {sorted.map((item) => (
                                <NavNode
                                    key={item.idMenu}
                                    item={item}
                                    onClose={onClose}
                                    collapsed={collapsed}
                                    onRequestExpandSidebar={() => setCollapsed(false)}
                                />
                            ))}
                        </ul>
                    )}
                </nav>
            </aside>
        </>
    );
}