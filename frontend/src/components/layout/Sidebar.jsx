import { useState, useEffect, createElement } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { X, ChevronDown, ChevronLeft, ChevronRight } from 'lucide-react';
import { getMenuIcon } from '@/utils/iconRegistry';

const COLLAPSE_STORAGE_KEY = 'galaxam-sidebar-collapsed';

function sortByRang(items) {
    return [...items].sort((a, b) => (a.rang ?? 0) - (b.rang ?? 0));
}

function NavNode({ item, onClose, depth = 0, collapsed, onRequestExpandSidebar }) {
    const location = useLocation();
    const hasChildren = item.sousMenus?.length > 0;
    const paddingLeft = `${16 + depth * 16}px`;

    // Vérifie si un des sous-menus est actuellement actif dans l'URL
    const isChildActive = (node) => {
        if (!node.sousMenus) return false;
        return node.sousMenus.some(child => (child.href && location.pathname === child.href) || isChildActive(child));
    };

    const [expanded, setExpanded] = useState(() => hasChildren && isChildActive(item));

    // Mettre à jour l'ouverture du menu parent quand l'URL change
    useEffect(() => {
        if (hasChildren && isChildActive(item)) {
            setExpanded(true);
        }
    }, [location.pathname]);

    const handleParentClick = () => {
        // En mode replié (desktop), on redéplie d'abord la sidebar plutôt
        // que d'ouvrir un sous-menu invisible.
        if (collapsed) {
            onRequestExpandSidebar?.();
            setExpanded(true);
            return;
        }
        setExpanded((p) => !p);
    };

    // Un item avec des enfants se comporte comme un en-tête de section,
    // en un peu plus appuyé (semibold) qu'un lien classique — sans tomber
    // dans le tout-majuscule "vieux portail intranet".
    if (hasChildren) {
        return (
            <li>
                <button
                    onClick={handleParentClick}
                    style={{ paddingLeft }}
                    title={collapsed ? item.libelle : undefined}
                    className={`
                        w-full flex items-center gap-3 pr-4 py-2.5
                        text-sm font-semibold
                        transition-colors duration-150
                        ${expanded ? 'text-white' : 'text-ink-300 hover:text-white'}
                        hover:bg-white/5
                    `}
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
                        className={`shrink-0 text-ink-500 transition-transform duration-200 ${
                            expanded ? 'rotate-180 text-ink-300' : ''
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
                className={({ isActive }) =>
                    `group relative flex items-center gap-3 pr-4 py-2.5 text-sm font-medium
                     border-l-2 transition-colors duration-150 ${
                        isActive
                            ? 'bg-white/10 text-white border-brand-500'
                            : 'text-ink-300 border-transparent hover:bg-white/5 hover:text-white'
                    }`
                }
                style={{ paddingLeft }}
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

/**
 * Sidebar — reçoit le menu déjà chargé par Layout (via useMenu) plutôt que
 * de le fetcher lui-même, pour éviter un double appel réseau.
 *
 * Desktop (lg+) : colonne statique dans le flux flex, repliable en mode
 * "icônes seules" via le bouton latéral sur la bordure droite.
 * Mobile        : tiroir en position fixed, toujours en largeur pleine,
 * ouvert/fermé via `open`.
 */
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
                    fixed inset-y-0 left-0 z-50 flex flex-col
                    bg-ink-800 border-r border-ink-700 shadow-lg
                    transition-all duration-300 ease-in-out
                    lg:static lg:translate-x-0 lg:shadow-none
                    w-[260px] ${collapsed ? 'lg:w-[76px]' : 'lg:w-[260px]'}
                    ${open ? 'translate-x-0' : '-translate-x-full'}
                `}
            >
                {/* Bouton latéral — replie/déplie la sidebar (desktop uniquement) */}
                <button
                    onClick={() => setCollapsed((v) => !v)}
                    aria-label={collapsed ? 'Déplier le menu' : 'Replier le menu'}
                    className="
                        hidden lg:flex absolute top-6 -right-3 z-10
                        w-6 h-6 items-center justify-center
                        bg-ink-800 border border-ink-600 text-ink-300
                        hover:text-white hover:border-brand-500
                        shadow-sm transition-colors duration-150
                    "
                >
                    {collapsed ? <ChevronRight size={14} /> : <ChevronLeft size={14} />}
                </button>

                {/* En-tête (marque) — ne scrolle pas */}
                <div className={`flex items-center h-16 border-b border-ink-700 shrink-0 px-5 ${collapsed ? 'lg:justify-center lg:px-0' : 'justify-between'}`}>
                    <div className="flex items-center gap-3 min-w-0">
                        <div className="w-8 h-8 flex items-center justify-center bg-brand-500 text-white font-bold text-sm shrink-0 shadow-sm">
                            G
                        </div>
                        <span className={`text-base font-bold text-white tracking-tight truncate ${collapsed ? 'lg:hidden' : ''}`}>
                            Galaxam SIRH
                        </span>
                    </div>
                    <button
                        onClick={onClose}
                        className="lg:hidden p-1 text-ink-400 hover:text-white transition-colors"
                    >
                        <X size={18} />
                    </button>
                </div>

                {/* Navigation — seule zone qui scrolle */}
                <nav className="flex-1 overflow-y-auto overflow-x-hidden py-4">
                    {loading && (
                        <div className="px-4 space-y-2">
                            {[...Array(5)].map((_, i) => (
                                <div key={i} className="h-8 bg-white/5 animate-pulse" />
                            ))}
                        </div>
                    )}
                    {error && (
                        <p className={`px-4 text-xs text-danger-500 ${collapsed ? 'lg:hidden' : ''}`}>
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