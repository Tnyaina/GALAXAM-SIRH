import { useState, useEffect, useRef } from 'react';

/**
 * Composant de saisie d'heure ultra-fluide avec auto-formatage 4 chiffres (ex: 0800 -> 08:00)
 * et saut automatique vers le champ suivant au 4ème chiffre.
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
    const internalRef = useRef(null);
    const refToUse = inputRef || internalRef;

    const [inputValue, setInputValue] = useState(value || '');

    useEffect(() => {
        setInputValue(value || '');
    }, [value]);

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

    return (
        <input
            ref={refToUse}
            type="text"
            maxLength={5}
            value={inputValue}
            onChange={handleChange}
            onKeyDown={handleKeyDown}
            placeholder={placeholder}
            disabled={disabled}
            className={`w-20 px-2 py-1 text-xs font-mono font-bold text-center bg-[var(--color-surface)] border border-[var(--color-border-subtle)] focus:border-[var(--color-brand-500)] focus:ring-1 focus:ring-[var(--color-brand-500)] outline-none rounded-none transition-colors ${className}`}
        />
    );
}
