import { useState } from 'react';
import { NavLink } from 'react-router-dom';
import { X, ChevronDown } from 'lucide-react';
import { getMenuIcon } from '@/utils/iconRegistry';

function sortByRang(items) {
    return [...items].sort((a, b) => (a.rang ?? 0) - (b.rang ?? 0));
}

function NavNode({ item, onClose, depth = 0 }) {
    const [expanded, setExpanded] = useState(false);
    const Icon = getMenuIcon(item.icone);
    const hasChildren = item.sousMenus?.length > 0;
    const paddingLeft = `${16 + depth * 16}px`;

    // Un item avec des enfants se comporte visuellement comme un en-tête
    // de section (majuscules, plus discret) plutôt qu'un lien classique.
    if (hasChildren) {
        return (
            <li>
                <button
                    onClick={() => setExpanded((p) => !p)}
                    style={{ paddingLeft }}
                    className={`
                        w-full flex items-center gap-3 pr-4 py-2.5
                        text-[13px] font-semibold uppercase tracking-wide
                        transition-colors duration-150
                        ${expanded ? 'text-white' : 'text-ink-400 hover:text-white'}
                        hover:bg-white/5
                    `}
                >
                    <Icon size={16} className="shrink-0" strokeWidth={2} />
                    <span className="flex-1 text-left truncate">{item.libelle}</span>
                    <ChevronDown
                        size={14}
                        className={`shrink-0 text-ink-500 transition-transform duration-200 ${
                            expanded ? 'rotate-180 text-ink-300' : ''
                        }`}
                    />
                </button>

                <div
                    className={`grid transition-all duration-200 ${
                        expanded ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'
                    }`}
                >
                    <ul className="overflow-hidden space-y-0.5 py-0.5">
                        {sortByRang(item.sousMenus).map((child) => (
                            <NavNode key={child.idMenu} item={child} onClose={onClose} depth={depth + 1} />
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
                end={item.href === '/'}
                onClick={onClose}
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
                <Icon size={16} className="shrink-0 opacity-90 group-hover:opacity-100" strokeWidth={2} />
                <span className="truncate">{item.libelle}</span>
            </NavLink>
        </li>
    );
}

/**
 * Sidebar — reçoit le menu déjà chargé par Layout (via useMenu) plutôt que
 * de le fetcher lui-même, pour éviter un double appel réseau.
 *
 * Desktop (lg+) : colonne statique dans le flux flex (hauteur héritée du parent).
 * Mobile        : tiroir en position fixed, ouvert/fermé via `open`.
 */
export default function Sidebar({ open, onClose, menu = [], loading, error }) {
    const sorted = sortByRang(menu);

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
                    fixed inset-y-0 left-0 z-50 w-[260px] flex flex-col
                    bg-ink-800 border-r border-ink-700 shadow-lg
                    transform transition-transform duration-300
                    lg:static lg:translate-x-0 lg:shadow-none
                    ${open ? 'translate-x-0' : '-translate-x-full'}
                `}
            >
                {/* En-tête (marque) — ne scrolle pas */}
                <div className="flex items-center justify-between px-5 h-16 border-b border-ink-700 shrink-0">
                    <div className="flex items-center gap-3 min-w-0">
                        <div className="w-8 h-8 flex items-center justify-center bg-brand-500 text-white font-bold text-sm shrink-0 shadow-sm">
                            G
                        </div>
                        <span className="text-base font-bold text-white tracking-tight truncate">
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
                <nav className="flex-1 overflow-y-auto py-4">
                    {loading && (
                        <div className="px-4 space-y-2">
                            {[...Array(5)].map((_, i) => (
                                <div key={i} className="h-8 bg-white/5 animate-pulse" />
                            ))}
                        </div>
                    )}
                    {error && (
                        <p className="px-4 text-xs text-danger-500">
                            Impossible de charger le menu.
                        </p>
                    )}
                    {!loading && !error && (
                        <ul className="space-y-0.5">
                            {sorted.map((item) => (
                                <NavNode key={item.idMenu} item={item} onClose={onClose} />
                            ))}
                        </ul>
                    )}
                </nav>
            </aside>
        </>
    );
}