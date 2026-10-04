import { useId } from 'react';
import FieldError from './FieldError';

export default function Select({
    id,
    label,
    options = [],
    value,
    onChange,
    disabled = false,
    error,
    hint,
    helpText,
    helperText,
    className = '',
    containerClassName = '',
    placeholder,
    ...props
}) {
    const hintText = hint || helpText || helperText;
    const reactId = useId();
    const fieldId = id || `select-${reactId}`;
    const feedbackId = `${fieldId}-feedback`;
    const describedBy = [props['aria-describedby'], error || hintText ? feedbackId : null].filter(Boolean).join(' ') || undefined;

    const validOptions = (Array.isArray(options) ? options : []).filter(opt => {
        if (opt.estActif === false || opt.actif === false) {
            return String(opt.value) === String(value);
        }
        return true;
    });

    const displayLabel = typeof label === 'string' && props.required ? label.trim().replace(/\s*\*+$/, '') : label;

    return (
        <div className={`flex flex-col ${label ? 'gap-1' : ''} ${containerClassName || 'w-full'}`}>
            {label && (
                <label htmlFor={fieldId} className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg)]">
                    {displayLabel}
                    {props.required && <span className="text-[var(--color-danger-500)] ml-1">*</span>}
                </label>
            )}
            <select
                id={fieldId}
                value={value ?? ''}
                disabled={disabled || validOptions.length === 0}
                onChange={(e) => onChange?.(e.target.value)}
                aria-invalid={error ? true : props['aria-invalid']}
                aria-describedby={describedBy}
                className={`
                    w-full bg-[var(--color-surface)] border text-xs sm:text-[13px] font-bold text-[var(--color-fg)]
                    px-3 py-1.5 outline-none rounded-none
                    focus:border-[var(--color-brand-500)]
                    disabled:bg-[var(--color-bg-subtle)] disabled:cursor-not-allowed disabled:text-[var(--color-fg-muted)]
                    ${error ? 'border-[var(--color-danger-500)]' : 'border-[var(--color-border-subtle)] hover:border-[var(--color-fg-muted)]'}
                    ${className}
                `}
                {...props}
            >
                {validOptions.length === 0 ? (
                    <option value="" disabled>
                        {placeholder || 'Chargement des options...'}
                    </option>
                ) : (
                    <>
                        {placeholder && <option value="">{placeholder}</option>}
                        {validOptions.map((opt) => (
                            <option key={opt.value} value={opt.value}>
                                {opt.label}
                            </option>
                        ))}
                    </>
                )}
            </select>
            {error && <FieldError id={feedbackId} className="mt-0.5">{error}</FieldError>}
            {hintText && !error && <p id={feedbackId} className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">{hintText}</p>}
        </div>
    );
}
