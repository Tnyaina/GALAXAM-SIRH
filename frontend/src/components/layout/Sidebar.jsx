import { useState, useEffect, createElement } from 'react';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { X, ChevronDown, ChevronLeft, ChevronRight, LogOut } from 'lucide-react';
import { getMenuIcon } from '@/utils/iconRegistry';
import { useAuth } from '@/context/AuthContext';
import { Avatar } from '@/components/ui/data-display/Avatar';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';

const COLLAPSE_STORAGE_KEY = 'galaxam-sidebar-collapsed';

function sortByRang(items) {
    return [...items].sort((a, b) => (a.rang ?? 0) - (b.rang ?? 0));
}

const isUrlMatch = (href, location) => {
    if (!href || href === '#') return false;
    const fullPath = location.pathname + location.search;
    if (href.includes('?')) {
        return fullPath === href;
    }
    return location.pathname === href;
};

const isChildActive = (node, location) => {
    if (!node.sousMenus) return false;
    return node.sousMenus.some(child => (child.href && isUrlMatch(child.href, location)) || isChildActive(child, location));
};

function NavNode({ item, onClose, depth = 0, collapsed, onRequestExpandSidebar }) {
    const location = useLocation();
    const hasChildren = item.sousMenus?.length > 0;
    const paddingLeft = `${16 + depth * 16}px`;

    const [expanded, setExpanded] = useState(() => hasChildren && isChildActive(item, location));

    useEffect(() => {
        if (hasChildren && isChildActive(item, location)) {
            setExpanded(prev => (prev ? prev : true));
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [location.pathname, location.search, hasChildren, item]);

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
                        transition-colors duration-150 cursor-pointer
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

    const isActive = isUrlMatch(item.href, location);

    return (
        <li>
            <NavLink
                to={item.href || '#'}
                end={true}
                onClick={onClose}
                title={collapsed ? item.libelle : undefined}
                style={{
                    paddingLeft,
                    backgroundColor: isActive ? 'var(--color-sidebar-active-bg)' : 'transparent',
                    color: isActive ? 'var(--color-sidebar-fg-active)' : 'var(--color-sidebar-fg)',
                    borderColor: isActive ? 'var(--color-sidebar-brand)' : 'transparent'
                }}
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
    const { user, logout } = useAuth();
    const navigate = useNavigate();

    const [collapsed, setCollapsed] = useState(() => {
        if (typeof window === 'undefined') return false;
        return window.localStorage.getItem(COLLAPSE_STORAGE_KEY) === '1';
    });

    useEffect(() => {
        window.localStorage.setItem(COLLAPSE_STORAGE_KEY, collapsed ? '1' : '0');
    }, [collapsed]);

    const handleLogout = () => {
        logout();
        navigate('/');
    };

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
                        hidden lg:flex absolute top-5 -right-3 z-10
                        w-6 h-6 items-center justify-center rounded-none
                        border border-[var(--color-sidebar-border)]
                        hover:text-[var(--color-sidebar-fg-hover)] hover:border-[var(--color-sidebar-brand)]
                        shadow-sm transition-colors duration-150 cursor-pointer
                    "
                >
                    {collapsed ? <ChevronRight size={14} /> : <ChevronLeft size={14} />}
                </button>

                {/* Header Brand avec Avatar carré */}
                <div className={`flex items-center h-16 border-b border-[var(--color-sidebar-border)] shrink-0 px-4 ${collapsed ? 'lg:justify-center lg:px-0' : 'justify-between'}`}>
                    <div className="flex items-center gap-3 min-w-0">
                        <Avatar name="Galaxam SIRH" size="sm" shape="square" />
                        <span
                            style={{ color: 'var(--color-sidebar-fg-active)' }}
                            className={`text-base font-bold tracking-tight truncate ${collapsed ? 'lg:hidden' : ''}`}
                        >
                            Galaxam SIRH
                        </span>
                    </div>
                    <Button
                        variant="ghost"
                        size="xs"
                        onClick={onClose}
                        className="lg:hidden !p-1 !text-[var(--color-sidebar-fg)] hover:!text-white hover:!bg-white/10"
                        aria-label="Fermer le menu"
                        iconLeft={<X size={18} />}
                    />
                </div>

                {/* Éléments de navigation */}
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

                {/* Pied de Sidebar : Profil utilisateur avec Avatar square, Badge haute lisibilité & Bouton déconnexion */}
                {user && (
                    <div className={`border-t border-[var(--color-sidebar-border)] p-3 shrink-0 bg-[var(--color-sidebar-bg)] ${collapsed ? 'lg:flex lg:flex-col lg:items-center' : ''}`}>
                        <div className="flex items-center justify-between gap-2 w-full">
                            <div className="flex items-center gap-3 min-w-0">
                                <Avatar name={user?.identifiant || 'Utilisateur'} size="sm" shape="square" status="online" />
                                {!collapsed && (
                                    <div className="min-w-0 flex-1">
                                        <p className="text-xs font-bold truncate text-[var(--color-sidebar-fg-active)]">
                                            {user?.identifiant}
                                        </p>
                                        {user?.role && (
                                            <Badge size="sm" className="mt-0.5 !text-[9px] !px-1.5 !py-0 font-bold !text-sky-200 !bg-sky-500/20 !border-sky-400/30">
                                                {user.role}
                                            </Badge>
                                        )}
                                    </div>
                                )}
                            </div>

                            {!collapsed ? (
                                <Button
                                    variant="ghost"
                                    size="xs"
                                    onClick={handleLogout}
                                    title="Déconnexion"
                                    className="!p-1.5 !text-[var(--color-sidebar-fg)] hover:!text-red-400 hover:!bg-white/10 shrink-0"
                                    iconLeft={<LogOut size={16} />}
                                />
                            ) : (
                                <Button
                                    variant="ghost"
                                    size="xs"
                                    onClick={handleLogout}
                                    title="Déconnexion"
                                    className="hidden lg:flex mt-2 !p-1.5 !text-[var(--color-sidebar-fg)] hover:!text-red-400 hover:!bg-white/10 shrink-0"
                                    iconLeft={<LogOut size={16} />}
                                />
                            )}
                        </div>
                    </div>
                )}
            </aside>
        </>
    );
}