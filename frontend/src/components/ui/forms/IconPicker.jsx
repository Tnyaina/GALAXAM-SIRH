import { useState, useMemo, useRef, useEffect, createElement } from 'react';
import { Search, X, ChevronDown, Check } from 'lucide-react';
import {
    getMenuIcon,
    AVAILABLE_ICON_NAMES,
    ICON_CATEGORIES,
    ICON_KEYWORDS
} from '@/utils/iconRegistry';

export default function IconPicker({
    label = 'Icone Graphique',
    value = 'Circle',
    onChange,
    disabled = false,
    className = ''
}) {
    const [isOpen, setIsOpen] = useState(false);
    const [search, setSearch] = useState('');
    const [selectedCategory, setSelectedCategory] = useState('ALL');
    const [hoveredIcon, setHoveredIcon] = useState(null);
    const containerRef = useRef(null);
    const searchInputRef = useRef(null);

    // Fermeture lors d'un clic exterieur
    useEffect(() => {
        const handleClickOutside = (event) => {
            if (containerRef.current && !containerRef.current.contains(event.target)) {
                setIsOpen(false);
            }
        };
        if (isOpen) {
            document.addEventListener('mousedown', handleClickOutside);
            setTimeout(() => {
                searchInputRef.current?.focus();
            }, 50);
        }
        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
        };
    }, [isOpen]);

    // Filtrage dynamique selon la recherche et la categorie
    const filteredIcons = useMemo(() => {
        let list = AVAILABLE_ICON_NAMES;

        // Filtre categorie
        if (selectedCategory !== 'ALL') {
            const catObj = ICON_CATEGORIES.find(c => c.id === selectedCategory);
            if (catObj && catObj.icons) {
                const catSet = new Set(catObj.icons);
                list = list.filter(ico => catSet.has(ico));
            }
        }

        // Filtre texte / mots-cles
        if (search.trim() !== '') {
            const term = search.toLowerCase().trim();
            list = list.filter(ico => {
                const nameMatch = ico.toLowerCase().includes(term);
                const keywords = (ICON_KEYWORDS[ico] || '').toLowerCase();
                const keywordMatch = keywords.includes(term);
                return nameMatch || keywordMatch;
            });
        }

        return list;
    }, [search, selectedCategory]);

    const handleSelect = (iconName) => {
        if (onChange) {
            onChange(iconName);
        }
        setIsOpen(false);
        setSearch('');
    };

    const CurrentIconComponent = getMenuIcon(value);
    const ActiveDisplayIcon = hoveredIcon ? getMenuIcon(hoveredIcon) : CurrentIconComponent;
    const activeDisplayName = hoveredIcon || value || 'Circle';

    return (
        <div className={`space-y-1.5 relative ${className}`} ref={containerRef}>
            {label && (
                <label className="block text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                    {label}
                </label>
            )}

            {/* Declencheur visuel */}
            <div
                role="button"
                tabIndex={0}
                aria-haspopup="dialog"
                aria-expanded={isOpen}
                onClick={() => !disabled && setIsOpen(!isOpen)}
                onKeyDown={(e) => {
                    if ((e.key === 'Enter' || e.key === ' ') && !disabled) {
                        e.preventDefault();
                        setIsOpen(!isOpen);
                    }
                }}
                className={`
                    flex items-center justify-between gap-3 p-2.5 bg-[var(--color-surface)]
                    border border-[var(--color-border)] rounded-none cursor-pointer transition-colors
                    hover:border-[var(--color-brand-500)]
                    ${disabled ? 'opacity-50 cursor-not-allowed' : ''}
                    ${isOpen ? 'ring-1 ring-[var(--color-brand-500)] border-[var(--color-brand-500)]' : ''}
                `}
            >
                <div className="flex items-center gap-3 min-w-0">
                    <div className="w-8 h-8 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] flex items-center justify-center shrink-0">
                        {createElement(CurrentIconComponent, { size: 18, className: 'text-[var(--color-brand-500)]' })}
                    </div>
                    <div className="min-w-0">
                        <div className="font-mono text-xs font-bold text-[var(--color-fg)] truncate">
                            {value || 'Non selectionne'}
                        </div>
                        <div className="text-[10px] text-[var(--color-fg-muted)]">
                            Cliquez pour changer d'icone
                        </div>
                    </div>
                </div>

                <div className="flex items-center gap-1.5 text-[var(--color-fg-muted)] shrink-0 pr-1">
                    <span className="text-xs uppercase font-medium hidden sm:inline">Choisir</span>
                    <ChevronDown size={15} className={`transition-transform duration-200 ${isOpen ? 'rotate-180 text-[var(--color-brand-500)]' : ''}`} />
                </div>
            </div>

            {/* Panneau de selection popover */}
            {isOpen && (
                <div className="absolute left-0 right-0 z-50 mt-1 bg-[var(--color-surface)] border border-[var(--color-border)] shadow-2xl rounded-none p-3 space-y-3 animate-in fade-in zoom-in-95">
                    {/* Barre de recherche */}
                    <div className="relative">
                        <input
                            ref={searchInputRef}
                            type="text"
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            placeholder="Rechercher par nom ou mot-cle (ex: paie, conge, user, temps)..."
                            className="w-full bg-[var(--color-bg-subtle)] border border-[var(--color-border)] rounded-none text-xs text-[var(--color-fg)] pl-8 pr-8 py-2 outline-none focus:border-[var(--color-brand-500)] focus:bg-[var(--color-surface)]"
                        />
                        <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)] pointer-events-none" />
                        {search && (
                            <button
                                type="button"
                                onClick={() => setSearch('')}
                                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] cursor-pointer"
                                aria-label="Effacer recherche"
                            >
                                <X size={13} />
                            </button>
                        )}
                    </div>

                    {/* Onglets de categories */}
                    <div className="flex items-center gap-1 overflow-x-auto pb-1 scrollbar-thin">
                        {ICON_CATEGORIES.map(cat => {
                            const isSelected = selectedCategory === cat.id;
                            return (
                                <button
                                    key={cat.id}
                                    type="button"
                                    onClick={() => setSelectedCategory(cat.id)}
                                    className={`
                                        px-2 py-1 text-[10px] font-bold uppercase tracking-wider whitespace-nowrap border transition-colors cursor-pointer rounded-none
                                        ${isSelected
                                            ? 'bg-[var(--color-brand-500)] text-white border-[var(--color-brand-500)]'
                                            : 'bg-[var(--color-surface)] text-[var(--color-fg-muted)] border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)]'
                                        }
                                    `}
                                >
                                    {cat.label}
                                </button>
                            );
                        })}
                    </div>

                    {/* Grille d'icones */}
                    <div className="max-h-48 overflow-y-auto p-1.5 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/30">
                        {filteredIcons.length === 0 ? (
                            <div className="py-8 text-center text-xs text-[var(--color-fg-muted)]">
                                Aucune icone ne correspond a "{search}".
                            </div>
                        ) : (
                            <div className="grid grid-cols-7 sm:grid-cols-9 gap-1.5">
                                {filteredIcons.map(icoName => {
                                    const IcoComponent = getMenuIcon(icoName);
                                    const isCurrent = icoName === value;
                                    return (
                                        <button
                                            key={icoName}
                                            type="button"
                                            title={icoName}
                                            onClick={() => handleSelect(icoName)}
                                            onMouseEnter={() => setHoveredIcon(icoName)}
                                            onMouseLeave={() => setHoveredIcon(null)}
                                            className={`
                                                relative w-9 h-9 flex items-center justify-center border transition-all cursor-pointer rounded-none
                                                ${isCurrent
                                                    ? 'bg-[var(--color-brand-500)] text-white border-[var(--color-brand-500)] shadow-sm'
                                                    : 'bg-[var(--color-surface)] text-[var(--color-fg)] border-[var(--color-border-subtle)] hover:border-[var(--color-brand-500)] hover:text-[var(--color-brand-500)]'
                                                }
                                            `}
                                            aria-label={`Choisir icone ${icoName}`}
                                        >
                                            {createElement(IcoComponent, { size: 17 })}
                                            {isCurrent && (
                                                <div className="absolute -top-1 -right-1 w-3.5 h-3.5 bg-white text-[var(--color-brand-500)] rounded-full flex items-center justify-center border border-[var(--color-brand-500)]">
                                                    <Check size={9} strokeWidth={3} />
                                                </div>
                                            )}
                                        </button>
                                    );
                                })}
                            </div>
                        )}
                    </div>

                    {/* Pied d'information avec apercu live */}
                    <div className="flex items-center justify-between text-[11px] pt-1 border-t border-[var(--color-border-subtle)] text-[var(--color-fg-muted)]">
                        <div className="flex items-center gap-2 min-w-0">
                            <span className="shrink-0 text-[10px] uppercase font-bold text-[var(--color-brand-500)]">Apercu :</span>
                            <div className="w-5 h-5 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] flex items-center justify-center shrink-0">
                                {createElement(ActiveDisplayIcon, { size: 13, className: 'text-[var(--color-brand-500)]' })}
                            </div>
                            <span className="font-mono font-bold text-[var(--color-fg)] truncate">
                                {activeDisplayName}
                            </span>
                        </div>
                        <div className="text-[10px] shrink-0">
                            {filteredIcons.length} disponible{filteredIcons.length > 1 ? 's' : ''}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
