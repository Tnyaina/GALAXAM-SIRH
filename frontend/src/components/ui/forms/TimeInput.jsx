import { useState, useEffect, useRef } from 'react';
import { Clock } from 'lucide-react';

/**
 * Composant de saisie d'heure ultra-fluide avec icône horloge intégrée (Capture 1),
 * auto-formatage 4 chiffres (ex: 0800 -> 08:00), touches directionnelles et saut automatique vers le champ suivant.
 */
export default function TimeInput({
    value = '',
    onChange,
    onComplete,
    onKeyDown,
    placeholder = '--:--',
    disabled = false,
    className = '',
    inputRef
}) {
    // Référence et état local du texte saisi.
    const internalRef = useRef(null);
    const refToUse = inputRef || internalRef;

    const [inputValue, setInputValue] = useState(value || '');

    // Effet : synchronise la valeur contrôlée fournie par le parent.
    useEffect(() => {
        setInputValue(value || '');
    }, [value]);

    // Actions utilisateur : formatage et validation de l'heure.
    const handleChange = (e) => {
        let raw = e.target.value.replace(/[^0-9]/g, '');

        if (raw.length > 4) raw = raw.slice(0, 4);

        let formatted = raw;
        if (raw.length >= 3) {
            formatted = raw.slice(0, 2) + ':' + raw.slice(2);
        }

        setInputValue(formatted);
        if (onChange) onChange(formatted);

        // Si 4 chiffres complets saisis (ex: 0800 -> 08:00), on valide et on saute au champ suivant
        if (raw.length === 4) {
            let hh = parseInt(raw.slice(0, 2), 10);
            let mm = parseInt(raw.slice(2, 4), 10);
            if (hh > 23) hh = 23;
            if (mm > 59) mm = 59;

            const finalStr = String(hh).padStart(2, '0') + ':' + String(mm).padStart(2, '0');
            setInputValue(finalStr);
            if (onChange) onChange(finalStr);
            if (onComplete) onComplete(finalStr);
        }
    };

    const handleKeyDown = (e) => {
        if (onKeyDown) onKeyDown(e);
        if (e.key === 'Enter' && onComplete) {
            e.preventDefault();
            onComplete(inputValue);
        }
    };

    // Rendu
    return (
        <div className={`relative inline-flex items-center group ${disabled ? 'opacity-60 cursor-not-allowed' : ''}`}>
            <input
                ref={refToUse}
                type="text"
                maxLength={5}
                value={inputValue}
                onChange={handleChange}
                onKeyDown={handleKeyDown}
                placeholder={placeholder}
                disabled={disabled}
                className={`h-7 w-[82px] pl-2 pr-6 text-xs font-mono font-bold text-center bg-[var(--color-surface)] border border-[var(--color-border-subtle)] focus:border-[var(--color-brand-500)] focus:ring-1 focus:ring-[var(--color-brand-500)]/30 outline-none rounded-none disabled:bg-[var(--color-bg-subtle)] disabled:text-[var(--color-fg-muted)]/70 disabled:cursor-not-allowed shadow-2xs transition-all tracking-wide ${className}`}
            />
            <span className="absolute right-1.5 pointer-events-none text-[var(--color-fg-muted)]/60 group-focus-within:text-brand-500 transition-colors">
                <Clock size={11} strokeWidth={2} />
            </span>
        </div>
    );
}
