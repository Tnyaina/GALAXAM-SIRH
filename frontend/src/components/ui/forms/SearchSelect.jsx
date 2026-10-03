import { useState, useEffect, useRef, useId } from 'react';
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
}) {
  // État local et référence DOM.
  const [searchQuery, setSearchQuery] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef(null);
  const reactId = useId();
  const fieldId = id || `search-select-${reactId}`;
  const feedbackId = `${fieldId}-feedback`;

  // Données dérivées : sélection et options visibles.
  const selectedOption = options.find((opt) => String(opt.value) === String(value));

  // Effet : fermeture lors d'un clic extérieur.
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Actions utilisateur
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
    if (opt.estActif === false || opt.actif === false) {
      if (String(opt.value) !== String(value)) return false;
    }
    const query = searchQuery.toLowerCase().trim();
    if (!query) return true;
    const optLabel = (opt.label || '').toLowerCase();
    const optSublabel = (opt.sublabel || '').toLowerCase();
    return optLabel.includes(query) || optSublabel.includes(query);
  });

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
          id={fieldId}
          type="button"
          aria-invalid={!!error}
          aria-required={required}
          aria-describedby={error ? feedbackId : undefined}
          disabled={disabled}
          onClick={() => !disabled && setIsOpen((v) => !v)}
          className={`w-full px-3 py-1.5 bg-[var(--color-surface)] border text-left text-xs sm:text-sm flex items-center justify-between outline-none cursor-pointer ${
            error
              ? 'border-danger-500'
              : 'border-[var(--color-border)] hover:border-[var(--color-fg-muted)] focus:border-[var(--color-brand-500)]'
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
            <span className="text-[var(--color-fg-muted)]">{placeholder}</span>
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

      {error && <FieldError id={feedbackId}>{error}</FieldError>}
      {hint && !error && <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">{hint}</p>}
    </div>
  );
}
