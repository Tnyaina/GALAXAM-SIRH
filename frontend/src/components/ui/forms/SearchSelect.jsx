import { useState, useEffect, useRef } from 'react';
import { Search, ChevronDown, X, AlertTriangle } from 'lucide-react';

export default function SearchSelect({
  label,
  required = false,
  options = [],
  value,
  onChange,
  placeholder = 'Rechercher ou sélectionner…',
  error = null,
  className = '',
  disabled = false,
}) {
  const [searchQuery, setSearchQuery] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef(null);

  // Trouver l'option sélectionnée
  const selectedOption = options.find((opt) => String(opt.value) === String(value));

  // Fermeture du dropdown lors d'un clic extérieur
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleSelect = (opt) => {
    setIsOpen(false);
    setSearchQuery('');
    if (onChange) {
      onChange(opt ? opt.value : '', opt);
    }
  };

  const handleClear = (e) => {
    e.stopPropagation();
    setIsOpen(false);
    setSearchQuery('');
    if (onChange) {
      onChange('', null);
    }
  };

  const filteredOptions = options.filter((opt) => {
    const query = searchQuery.toLowerCase().trim();
    if (!query) return true;
    const optLabel = (opt.label || '').toLowerCase();
    const optSublabel = (opt.sublabel || '').toLowerCase();
    return optLabel.includes(query) || optSublabel.includes(query);
  });

  return (
    <div className={`space-y-1.5 relative ${className}`} ref={dropdownRef}>
      {label && (
        <label className="block text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
          {label} {required && <span className="text-danger-500 ml-0.5">*</span>}
        </label>
      )}

      <div className="relative">
        <button
          type="button"
          disabled={disabled}
          onClick={() => !disabled && setIsOpen((v) => !v)}
          className={`w-full px-3 py-1.5 bg-[var(--color-bg-subtle)] border text-left text-xs sm:text-sm flex items-center justify-between outline-none cursor-pointer ${
            error
              ? 'border-danger-500'
              : 'border-[var(--color-border-subtle)] hover:border-[var(--color-fg-muted)] focus:border-[var(--color-brand-500)]'
          } ${disabled ? 'opacity-60 cursor-not-allowed' : ''}`}
        >
          {selectedOption ? (
            <span className="font-semibold text-[var(--color-fg)] truncate">
              {selectedOption.label}
              {selectedOption.sublabel && (
                <span className="text-xs text-[var(--color-fg-muted)] font-normal ml-2">
                  ({selectedOption.sublabel})
                </span>
              )}
            </span>
          ) : (
            <span className="text-[var(--color-fg-subtle)]">{placeholder}</span>
          )}

          <div className="flex items-center gap-1 shrink-0 ml-2">
            {selectedOption && !disabled && (
              <span
                onClick={handleClear}
                className="p-0.5 hover:bg-danger-500/10 text-danger-500 hover:text-danger-700 transition-colors"
                title="Effacer la sélection"
              >
                <X className="w-3.5 h-3.5" />
              </span>
            )}
            <ChevronDown
              className={`w-4 h-4 text-[var(--color-fg-muted)] transition-transform ${
                isOpen ? 'rotate-180' : ''
              }`}
            />
          </div>
        </button>

        {isOpen && !disabled && (
          <div className="absolute left-0 right-0 top-full mt-1 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-2xl z-50 max-h-60 overflow-hidden flex flex-col font-sans">
            {/* Barre de recherche dans le menu déroulant */}
            <div className="p-2 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] flex items-center gap-2">
              <Search className="w-4 h-4 text-[var(--color-fg-muted)] shrink-0" />
              <input
                type="text"
                autoFocus
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Filtrer..."
                className="w-full bg-transparent text-sm text-[var(--color-fg)] placeholder:text-[var(--color-fg-subtle)] outline-none"
              />
              {searchQuery && (
                <button
                  type="button"
                  onClick={() => setSearchQuery('')}
                  className="text-xs text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
            </div>

            {/* Liste filtrée des options */}
            <div className="overflow-y-auto max-h-48">
              {filteredOptions.length === 0 ? (
                <div className="p-3 text-xs text-[var(--color-fg-muted)] text-center">
                  Aucun résultat pour "{searchQuery}"
                </div>
              ) : (
                filteredOptions.map((opt) => (
                  <button
                    key={opt.value}
                    type="button"
                    onClick={() => handleSelect(opt)}
                    className={`w-full text-left px-4 py-2 hover:bg-[var(--color-brand-500)]/10 text-sm flex items-center justify-between border-b border-[var(--color-border-subtle)] last:border-0 transition-colors cursor-pointer ${
                      String(opt.value) === String(value) ? 'bg-[var(--color-brand-500)]/15 font-bold' : ''
                    }`}
                  >
                    <span className="text-[var(--color-fg)]">{opt.label}</span>
                    {opt.sublabel && (
                      <span className="text-xs text-[var(--color-fg-muted)] ml-2">{opt.sublabel}</span>
                    )}
                  </button>
                ))
              )}
            </div>
          </div>
        )}
      </div>

      {error && (
        <p className="text-xs text-danger-500 flex items-center gap-1">
          <AlertTriangle className="w-3.5 h-3.5 shrink-0" /> {error}
        </p>
      )}
    </div>
  );
}
