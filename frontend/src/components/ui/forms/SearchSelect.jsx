import { useState, useEffect, useRef, useId } from 'react';
import { createPortal } from 'react-dom';
import { Search, ChevronDown, X } from 'lucide-react';
import FieldError from './FieldError';

export default function SearchSelect({
  id,
  label,
  required = false,
  options = [],
  value,
  onChange,
  placeholder = 'Rechercher ou sélectionner…',
  error = null,
  className = '',
  disabled = false,
  hint = null,
  borderless = false,
  clearable = true,
  buttonClassName = '',
  size = 'md',
  showSelectedSublabel = true,
}) {
  // État local et références DOM.
  const [searchQuery, setSearchQuery] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const [highlightedIndex, setHighlightedIndex] = useState(-1);
  const dropdownRef = useRef(null);
  const buttonRef = useRef(null);
  const inputRef = useRef(null);
  const listRef = useRef(null);
  const menuRef = useRef(null);
  const [menuLayout, setMenuLayout] = useState(null);
  const reactId = useId();
  const fieldId = id || `search-select-${reactId}`;
  const feedbackId = `${fieldId}-feedback`;

  // Données dérivées : sélection et options visibles.
  const selectedOption = options.find((opt) => String(opt.value) === String(value));

  // Effet : fermeture lors d'un clic extérieur.
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target) && !menuRef.current?.contains(event.target)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Render the menu in a portal so scroll containers inside modals cannot clip it.
  useEffect(() => {
    if (!isOpen || !buttonRef.current) return undefined;
    const updateMenuLayout = () => {
      const trigger = buttonRef.current;
      if (!trigger) return;
      const triggerRect = trigger.getBoundingClientRect();
      // The menu is portaled above the modal layer; size it against the viewport
      // so opening a select never changes the modal's dimensions.
      const bounds = { top: 8, bottom: window.innerHeight - 8 };
      const gap = 6;
      const below = Math.max(0, bounds.bottom - triggerRect.bottom - gap);
      const above = Math.max(0, triggerRect.top - bounds.top - gap);
      const openUp = below < 140 && above > below;
      const available = openUp ? above : below;
      const maxHeight = Math.max(96, Math.min(288, available));
      setMenuLayout({
        left: triggerRect.left,
        top: openUp ? undefined : triggerRect.bottom + gap,
        bottom: openUp ? window.innerHeight - triggerRect.top + gap : undefined,
        width: triggerRect.width,
        maxHeight,
      });
    };
    const layoutFrame = requestAnimationFrame(updateMenuLayout);
    window.addEventListener('resize', updateMenuLayout);
    window.addEventListener('scroll', updateMenuLayout, true);
    return () => {
      cancelAnimationFrame(layoutFrame);
      window.removeEventListener('resize', updateMenuLayout);
      window.removeEventListener('scroll', updateMenuLayout, true);
    };
  }, [isOpen]);

  // Effet : gestion du focus et réinitialisation à l'ouverture
  useEffect(() => {
    if (isOpen) {
      setHighlightedIndex(0);
      const timer = setTimeout(() => {
        inputRef.current?.focus();
      }, 30);
      return () => clearTimeout(timer);
    } else {
      setSearchQuery('');
      setHighlightedIndex(-1);
    }
  }, [isOpen]);

  // Actions utilisateur
  const handleSelect = (opt) => {
    setIsOpen(false);
    setSearchQuery('');
    setHighlightedIndex(-1);
    if (onChange) {
      onChange(opt ? opt.value : '', opt);
    }
    buttonRef.current?.focus();
  };

  const handleClear = (e) => {
    e.stopPropagation();
    setIsOpen(false);
    setSearchQuery('');
    setHighlightedIndex(-1);
    if (onChange) {
      onChange('', null);
    }
  };

  const filteredOptions = options.filter((opt) => {
    if (opt.estActif === false || opt.actif === false) {
      if (String(opt.value) !== String(value)) return false;
    }
    const query = searchQuery.toLowerCase().trim();
    if (!query) return true;
    const optLabel = (opt.label || '').toLowerCase();
    const optSublabel = (opt.sublabel || '').toLowerCase();
    const optGroup = (opt.group || '').toLowerCase();
    return optLabel.includes(query) || optSublabel.includes(query) || optGroup.includes(query);
  });

  const handleInputKeyDown = (e) => {
    if (e.key === 'Enter') {
      // Éviter impérativement la soumission inopinée du formulaire parent
      e.preventDefault();
      e.stopPropagation();
      const targetOpt = filteredOptions[highlightedIndex >= 0 ? highlightedIndex : 0];
      if (targetOpt) {
        handleSelect(targetOpt);
      }
    } else if (e.key === 'Escape') {
      // Ferme uniquement le menu déroulant sans fermer la modale parente
      e.preventDefault();
      e.stopPropagation();
      setIsOpen(false);
      buttonRef.current?.focus();
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      setHighlightedIndex((prev) => {
        const next = prev < filteredOptions.length - 1 ? prev + 1 : prev;
        scrollOptionIntoView(next);
        return next;
      });
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlightedIndex((prev) => {
        const next = prev > 0 ? prev - 1 : 0;
        scrollOptionIntoView(next);
        return next;
      });
    }
  };

  const scrollOptionIntoView = (index) => {
    if (!listRef.current) return;
    const items = listRef.current.querySelectorAll('[role="option"]');
    if (items[index]) {
      items[index].scrollIntoView({ block: 'nearest' });
    }
  };

  const handleButtonKeyDown = (e) => {
    if (e.key === 'ArrowDown' || e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      if (!disabled) {
        setIsOpen(true);
      }
    }
  };

  const displayLabel = typeof label === 'string' && required ? label.trim().replace(/\s*\*+$/, '') : label;

  // Rendu
  return (
    <div className={`flex flex-col gap-1 relative ${className}`} ref={dropdownRef}>
      {label && (
        <label htmlFor={fieldId} className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg)]">
          {displayLabel}
          {required && <span className="text-[var(--color-danger-500)] ml-1">*</span>}
        </label>
      )}

      <div className="relative">
        <button
          ref={buttonRef}
          id={fieldId}
          type="button"
          aria-invalid={!!error}
          aria-required={required}
          aria-describedby={error ? feedbackId : undefined}
          aria-haspopup="listbox"
          aria-expanded={isOpen}
          disabled={disabled}
          onClick={() => !disabled && setIsOpen((v) => !v)}
          onKeyDown={handleButtonKeyDown}
          className={`w-full text-left flex items-center justify-between outline-none cursor-pointer transition-colors ${
            size === 'sm'
              ? 'h-[32px] px-2.5 text-xs'
              : borderless
                ? 'px-2 py-1.5 text-xs sm:text-sm'
                : 'px-3 py-2 text-xs sm:text-sm'
          } ${
            borderless
              ? 'border-0 bg-transparent hover:bg-[var(--color-bg-subtle)]/60 focus:bg-[var(--color-bg-subtle)]'
              : `bg-[var(--color-surface)] border ${
                  error
                    ? 'border-[var(--color-danger-500)]'
                    : 'border-[var(--color-border)] hover:border-[var(--color-fg-muted)] focus:border-[var(--color-brand-500)]'
                }`
          } ${disabled ? 'opacity-60 cursor-not-allowed' : ''} ${buttonClassName}`}
        >
          {selectedOption ? (
            <div className="flex items-center min-w-0 truncate">
              <span className="font-bold text-[var(--color-fg)] truncate">
                {selectedOption.label}
              </span>
              {showSelectedSublabel && selectedOption.sublabel && (
                <span className="text-xs text-[var(--color-fg-muted)] font-normal ml-2 truncate hidden sm:inline">
                  ({selectedOption.sublabel})
                </span>
              )}
              {selectedOption.badge && (
                <span className={`text-[10px] uppercase font-semibold px-2 py-0.5 rounded-none ml-2 shrink-0 hidden md:inline ${selectedOption.badgeClassName || 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]'}`}>
                  {selectedOption.badge}
                </span>
              )}
            </div>
          ) : (
            <span className="text-[var(--color-fg-muted)] truncate">{placeholder}</span>
          )}

          <div className="flex items-center gap-1.5 shrink-0 ml-2">
            {clearable && selectedOption && !disabled && (
              <span
                role="button"
                tabIndex={-1}
                onClick={handleClear}
                className="p-0.5 hover:bg-[var(--color-danger-500)]/10 text-[var(--color-danger-500)] hover:text-[var(--color-danger-700)] transition-colors cursor-pointer"
                title="Effacer la sélection"
              >
                <X className="w-3.5 h-3.5" />
              </span>
            )}
            <ChevronDown
              className={`w-3.5 h-3.5 text-[var(--color-fg-muted)] transition-transform ${
                isOpen ? 'rotate-180' : ''
              }`}
            />
          </div>
        </button>

        {isOpen && !disabled && menuLayout && createPortal(
          <div ref={menuRef} style={{ position: 'fixed', left: menuLayout.left, top: menuLayout.top, bottom: menuLayout.bottom, width: menuLayout.width, minWidth: menuLayout.width, maxWidth: `min(${menuLayout.width}px, calc(100vw - 1rem))`, maxHeight: menuLayout.maxHeight }} className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-2xl z-[100] overflow-hidden flex flex-col font-sans">
            {/* Barre de recherche dans le menu déroulant */}
            <div className="p-2.5 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] flex items-center gap-2 shrink-0">
              <Search className="w-4 h-4 text-[var(--color-fg-muted)] shrink-0" />
              <input
                ref={inputRef}
                type="text"
                value={searchQuery}
                onChange={(e) => {
                  setSearchQuery(e.target.value);
                  setHighlightedIndex(0);
                }}
                onKeyDown={handleInputKeyDown}
                placeholder="Filtrer..."
                className="w-full bg-transparent text-sm text-[var(--color-fg)] placeholder:text-[var(--color-fg-subtle)] outline-none"
              />
              {searchQuery && (
                <button
                  type="button"
                  onClick={() => {
                    setSearchQuery('');
                    inputRef.current?.focus();
                  }}
                  className="text-xs text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] p-0.5"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
            </div>

            {/* Liste filtrée des options */}
            <div ref={listRef} role="listbox" style={{ maxHeight: Math.max(48, menuLayout.maxHeight - 56) }} className="overflow-y-auto divide-y divide-[var(--color-border-subtle)]">
              {filteredOptions.length === 0 ? (
                <div className="p-3.5 text-xs text-[var(--color-fg-muted)] text-center">
                  Aucun résultat pour "{searchQuery}"
                </div>
              ) : (
                filteredOptions.map((opt, idx) => {
                  const isSelected = String(opt.value) === String(value);
                  const isHighlighted = idx === highlightedIndex;
                  const showGroupHeader = opt.group && (idx === 0 || filteredOptions[idx - 1]?.group !== opt.group);
                  return (
                    <div key={opt.value}>
                      {showGroupHeader && (
                        <div className="px-3.5 py-1.5 bg-[var(--color-bg-subtle)] text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] border-b border-[var(--color-border-subtle)] sticky top-0 z-10 backdrop-blur-xs select-none">
                          {opt.group}
                        </div>
                      )}
                      <button
                        role="option"
                        aria-selected={isSelected}
                        type="button"
                        onMouseEnter={() => setHighlightedIndex(idx)}
                        onClick={() => handleSelect(opt)}
                        className={`w-full text-left px-3.5 py-2.5 text-sm flex items-center justify-between transition-colors cursor-pointer ${
                          isSelected
                            ? 'bg-[var(--color-brand-500)]/15 font-bold'
                            : isHighlighted
                            ? 'bg-[var(--color-brand-500)]/10 font-medium'
                            : 'hover:bg-[var(--color-bg-subtle)]'
                        }`}
                      >
                        <div className="flex flex-col min-w-0 pr-2">
                          <span className="text-[var(--color-fg)] truncate font-medium">{opt.label}</span>
                          {opt.sublabel && (
                            <span className="text-xs text-[var(--color-fg-muted)] font-normal truncate mt-0.5">{opt.sublabel}</span>
                          )}
                        </div>
                        {opt.badge && (
                          <span className={`text-[10px] uppercase font-semibold px-2 py-0.5 rounded-none shrink-0 ${opt.badgeClassName || 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]'}`}>
                            {opt.badge}
                          </span>
                        )}
                      </button>
                    </div>
                  );
                })
              )}
            </div>
          </div>,
          document.body,
        )}
      </div>

      {error && <FieldError id={feedbackId}>{error}</FieldError>}
      {hint && !error && <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">{hint}</p>}
    </div>
  );
}
