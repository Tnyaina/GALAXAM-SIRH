import { useState, useEffect, useRef, useMemo } from 'react';
import { ChevronLeft, ChevronRight, Calendar, Search, X, Check, ChevronDown } from 'lucide-react';
import { fmtDateFr } from '@/utils/dateUtils';

/**
 * PeriodeNavigator — Sélecteur de période de paie avec recherche et navigation séquentielle.
 * Conçu pour un alignement parfait au pixel avec PeriodNavigator et les barres d'outils SIRH.
 */
export default function PeriodeNavigator({
    periodesList = [],
    selectedPeriodeId = null,
    onSelectPeriode,
    className = '',
}) {
    const [isOpen, setIsOpen] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const containerRef = useRef(null);
    const inputRef = useRef(null);

    // Index courant dans la liste (triée DESC : 0 = plus récente)
    const currIndex = periodesList.findIndex((p) => p.idPeriodePaie === selectedPeriodeId);
    const selectedPeriode = periodesList[currIndex] || periodesList[0] || null;

    // Fermeture lors d'un clic extérieur
    useEffect(() => {
        const handleClickOutside = (e) => {
            if (containerRef.current && !containerRef.current.contains(e.target)) {
                setIsOpen(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    // Focus automatique sur le champ de recherche à l'ouverture
    useEffect(() => {
        if (isOpen) {
            const timer = setTimeout(() => inputRef.current?.focus(), 30);
            return () => clearTimeout(timer);
        }
        setSearchQuery('');
    }, [isOpen]);

    // Navigation précédent / suivant (liste DESC : -1 va vers le plus ancien => index + 1)
    const handleNavigate = (delta) => {
        if (!periodesList || periodesList.length === 0) return;
        if (currIndex === -1) return;
        const newIndex = currIndex - delta;
        if (newIndex >= 0 && newIndex < periodesList.length) {
            const target = periodesList[newIndex];
            if (onSelectPeriode && target) {
                onSelectPeriode(target.idPeriodePaie, target);
            }
        }
    };

    // Préparation des options avec formatage propre
    const formattedOptions = useMemo(() => {
        return (periodesList || []).map((p) => {
            const rawLibelle = p.libelle || '';
            const cleanLibelle = rawLibelle.includes('(') ? rawLibelle.split('(')[0].trim() : rawLibelle;
            const annee = p.code?.split('-')?.[0] || p.dateDebut?.split('-')?.[0] || '';
            const datesLabel = p.dateDebut && p.dateFin ? `${fmtDateFr(p.dateDebut)} au ${fmtDateFr(p.dateFin)}` : '';
            const statut = p.statutLibelle || p.statutCode || '';
            const isEnCours = p.statutCode === 'EN_COURS';
            const isCloture = p.statutCode === 'CLOTURE';

            return {
                id: p.idPeriodePaie,
                periode: p,
                code: p.code,
                cleanLibelle,
                displayLabel: [p.code, cleanLibelle].filter(Boolean).join(' — ') || `Période #${p.idPeriodePaie}`,
                datesLabel,
                group: annee ? `Année ${annee}` : 'Toutes les périodes',
                statut,
                isEnCours,
                isCloture,
            };
        });
    }, [periodesList]);

    // Filtrage textuel de la recherche
    const filteredOptions = useMemo(() => {
        const q = searchQuery.toLowerCase().trim();
        if (!q) return formattedOptions;
        return formattedOptions.filter(
            (opt) =>
                opt.displayLabel.toLowerCase().includes(q) ||
                opt.datesLabel.toLowerCase().includes(q) ||
                opt.group.toLowerCase().includes(q) ||
                opt.statut.toLowerCase().includes(q)
        );
    }, [formattedOptions, searchQuery]);

    // Formatage du déclencheur principal
    const triggerLabel = useMemo(() => {
        if (!selectedPeriode) return 'Sélectionner une période…';
        const rawLibelle = selectedPeriode.libelle || '';
        const cleanLibelle = rawLibelle.includes('(') ? rawLibelle.split('(')[0].trim() : rawLibelle;
        return [selectedPeriode.code, cleanLibelle].filter(Boolean).join(' — ') || `Période #${selectedPeriode.idPeriodePaie}`;
    }, [selectedPeriode]);

    const isEnCoursSelected = selectedPeriode?.statutCode === 'EN_COURS';
    const isClotureSelected = selectedPeriode?.statutCode === 'CLOTURE';
    const selectedBadge = selectedPeriode?.statutLibelle || selectedPeriode?.statutCode || null;

    return (
        <div ref={containerRef} className={`relative inline-flex items-stretch h-9 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs ${className}`}>
            {/* Bouton Période Précédente */}
            <button
                type="button"
                onClick={() => handleNavigate(-1)}
                disabled={currIndex < 0 || currIndex >= periodesList.length - 1}
                className={`px-2.5 flex items-center justify-center text-[var(--color-fg)] transition-colors cursor-pointer ${
                    currIndex < 0 || currIndex >= periodesList.length - 1
                        ? 'opacity-30 cursor-not-allowed'
                        : 'hover:bg-[var(--color-bg-subtle)]'
                }`}
                title="Période précédente"
            >
                <ChevronLeft size={15} />
            </button>

            {/* Séparateur vertical */}
            <span className="w-[1px] bg-[var(--color-border-subtle)] my-1.5 shrink-0" />

            {/* Bouton Déclencheur du Dropdown */}
            <button
                type="button"
                onClick={() => setIsOpen((prev) => !prev)}
                onKeyDown={(e) => {
                    if (e.key === 'Escape') setIsOpen(false);
                    if (e.key === 'ArrowDown' || e.key === 'Enter') setIsOpen(true);
                }}
                className="flex items-center gap-2.5 px-3 hover:bg-[var(--color-bg-subtle)]/70 transition-colors cursor-pointer text-left select-none outline-none min-w-0"
                aria-haspopup="listbox"
                aria-expanded={isOpen}
            >
                <Calendar size={15} className="text-[var(--color-brand-500)] shrink-0" />
                <span className="text-xs sm:text-[13px] font-bold text-[var(--color-fg)] tracking-tight truncate max-w-[240px] sm:max-w-[320px]">
                    {triggerLabel}
                </span>

                {selectedBadge && (
                    <span
                        className={`text-[9px] uppercase font-bold px-2 py-0.5 rounded-full shrink-0 hidden md:inline tracking-wider ${
                            isEnCoursSelected
                                ? 'bg-[var(--color-success-500)]/10 text-[var(--color-success-700)]'
                                : isClotureSelected
                                ? 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]'
                                : 'bg-[var(--color-warning-500)]/10 text-[var(--color-warning-700)]'
                        }`}
                    >
                        {selectedBadge}
                    </span>
                )}

                <ChevronDown
                    size={14}
                    className={`text-[var(--color-fg-muted)] shrink-0 transition-transform duration-150 ${
                        isOpen ? 'rotate-180' : ''
                    }`}
                />
            </button>

            {/* Séparateur vertical */}
            <span className="w-[1px] bg-[var(--color-border-subtle)] my-1.5 shrink-0" />

            {/* Bouton Période Suivante */}
            <button
                type="button"
                onClick={() => handleNavigate(1)}
                disabled={currIndex <= 0}
                className={`px-2.5 flex items-center justify-center text-[var(--color-fg)] transition-colors cursor-pointer ${
                    currIndex <= 0 ? 'opacity-30 cursor-not-allowed' : 'hover:bg-[var(--color-bg-subtle)]'
                }`}
                title="Période suivante"
            >
                <ChevronRight size={15} />
            </button>

            {/* Menu Déroulant / Popover avec Recherche */}
            {isOpen && (
                <div className="absolute left-0 top-full mt-1.5 min-w-[360px] sm:min-w-[420px] max-w-[500px] bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-2xl z-[70] flex flex-col font-sans max-h-80 overflow-hidden">
                    {/* Barre de recherche */}
                    <div className="p-2.5 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] flex items-center gap-2 shrink-0">
                        <Search size={14} className="text-[var(--color-fg-muted)] shrink-0" />
                        <input
                            ref={inputRef}
                            type="text"
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            onKeyDown={(e) => {
                                if (e.key === 'Escape') setIsOpen(false);
                            }}
                            placeholder="Rechercher une période (ex: 2025, Janvier...)"
                            className="w-full bg-transparent text-xs sm:text-sm text-[var(--color-fg)] placeholder:text-[var(--color-fg-subtle)] outline-none"
                        />
                        {searchQuery && (
                            <button
                                type="button"
                                onClick={() => {
                                    setSearchQuery('');
                                    inputRef.current?.focus();
                                }}
                                className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] p-0.5 cursor-pointer"
                                title="Effacer"
                            >
                                <X size={13} />
                            </button>
                        )}
                    </div>

                    {/* Liste des périodes groupées */}
                    <div role="listbox" className="overflow-y-auto max-h-64 divide-y divide-[var(--color-border-subtle)]">
                        {filteredOptions.length === 0 ? (
                            <div className="p-4 text-xs text-[var(--color-fg-muted)] text-center">
                                Aucune période trouvée pour "{searchQuery}"
                            </div>
                        ) : (
                            filteredOptions.map((opt, idx) => {
                                const isSelected = opt.id === selectedPeriodeId;
                                const showGroupHeader = opt.group && (idx === 0 || filteredOptions[idx - 1]?.group !== opt.group);

                                return (
                                    <div key={opt.id}>
                                        {showGroupHeader && (
                                            <div className="px-3.5 py-1.5 bg-[var(--color-bg-subtle)] text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] border-b border-[var(--color-border-subtle)] sticky top-0 z-10 select-none">
                                                {opt.group}
                                            </div>
                                        )}
                                        <button
                                            type="button"
                                            role="option"
                                            aria-selected={isSelected}
                                            onClick={() => {
                                                if (onSelectPeriode) onSelectPeriode(opt.id, opt.periode);
                                                setIsOpen(false);
                                            }}
                                            className={`w-full text-left px-3.5 py-2.5 text-xs sm:text-sm flex items-center justify-between transition-colors cursor-pointer ${
                                                isSelected
                                                    ? 'bg-[var(--color-brand-500)]/10 font-bold'
                                                    : 'hover:bg-[var(--color-bg-subtle)]'
                                            }`}
                                        >
                                            <div className="flex flex-col min-w-0 pr-3">
                                                <div className="flex items-center gap-1.5">
                                                    {isSelected && (
                                                        <Check size={13} className="text-[var(--color-brand-500)] shrink-0" />
                                                    )}
                                                    <span className="text-[var(--color-fg)] truncate">
                                                        {opt.displayLabel}
                                                    </span>
                                                </div>
                                                {opt.datesLabel && (
                                                    <span className="text-[11px] text-[var(--color-fg-muted)] font-normal mt-0.5">
                                                        {opt.datesLabel}
                                                    </span>
                                                )}
                                            </div>

                                            {opt.statut && (
                                                <span
                                                    className={`text-[9px] uppercase font-bold px-2 py-0.5 rounded-full shrink-0 tracking-wider ${
                                                        opt.isEnCours
                                                            ? 'bg-[var(--color-success-500)]/10 text-[var(--color-success-700)]'
                                                            : opt.isCloture
                                                            ? 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]'
                                                            : 'bg-[var(--color-warning-500)]/10 text-[var(--color-warning-700)]'
                                                    }`}
                                                >
                                                    {opt.statut}
                                                </span>
                                            )}
                                        </button>
                                    </div>
                                );
                            })
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
