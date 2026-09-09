export default function Select({
    label,
    options = [],
    value,
    onChange,
    disabled = false,
    className = '',
    containerClassName = '',
    placeholder,
    ...props
}) {
    const validOptions = (Array.isArray(options) ? options : []).filter(opt => {
        if (opt.estActif === false || opt.actif === false) {
            return String(opt.value) === String(value);
        }
        return true;
    });

    return (
        <div className={`flex flex-col ${label ? 'gap-1' : ''} ${containerClassName || 'w-full'}`}>
            {label && (
                <label className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg)]">
                    {label}
                </label>
            )}
            <select
                value={value ?? ''}
                disabled={disabled || validOptions.length === 0}
                onChange={(e) => onChange?.(e.target.value)}
                className={`
                    w-full bg-[var(--color-surface)] border border-[var(--color-border-subtle)] text-xs sm:text-[13px] font-bold text-[var(--color-fg)]
                    px-3 py-1.5 outline-none
                    focus:border-[var(--color-brand-500)]
                    disabled:bg-[var(--color-bg-subtle)] disabled:cursor-not-allowed disabled:text-[var(--color-fg-muted)]
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
        </div>
    );
}
