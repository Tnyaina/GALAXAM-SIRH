import { Calendar as CalendarIcon } from 'lucide-react';
import { fmtDateInput } from '@/utils/paieUtils';

/**
 * DatePicker — Champ de saisie et sélection de date standardisé Galaxam.
 */
export default function DatePicker({
    label,
    value,
    onChange,
    required = false,
    error,
    hint,
    helpText,
    helperText,
    min,
    max,
    disabled = false,
    className = '',
    ...props
}) {
    const hintText = hint || helpText || helperText;
    return (
        <div className="flex flex-col gap-1">
            {label && (
                <label className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                    {label}
                    {required && <span className="text-[var(--color-danger-500)] ml-1">*</span>}
                </label>
            )}
            <div className="relative">
                <div className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)] pointer-events-none">
                    <CalendarIcon size={15} />
                </div>
                <input
                    type="date"
                    required={required}
                    disabled={disabled}
                    value={fmtDateInput(value)}
                    onChange={(e) => onChange && onChange(e.target.value)}
                    min={fmtDateInput(min)}
                    max={fmtDateInput(max)}
                    className={`
            w-full bg-[var(--color-surface)] border rounded-none text-xs sm:text-sm text-[var(--color-fg)] font-data
            pl-8 pr-3 py-1.5 outline-none cursor-pointer
            focus:ring-0 focus:border-[var(--color-brand-500)]
            disabled:bg-[var(--color-bg-subtle)] disabled:cursor-not-allowed disabled:text-[var(--color-fg-subtle)]
            ${error ? 'border-[var(--color-danger-500)]' : 'border-[var(--color-border)] hover:border-[var(--color-fg-muted)]'}
            ${className}
          `}
                    {...props}
                />
            </div>
            {error && <p className="text-[11px] text-[var(--color-danger-500)] font-semibold mt-0.5">{error}</p>}
            {hintText && !error && <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">{hintText}</p>}
        </div>
    );
}

/**
 * DateRangePicker — Filtre combiné de dates début et fin.
 */
export function DateRangePicker({
    startDate,
    endDate,
    onStartDateChange,
    onEndDateChange,
    startLabel = 'Date de début',
    endLabel = 'Date de fin',
    className = '',
}) {
    return (
        <div className={`grid grid-cols-1 sm:grid-cols-2 gap-4 ${className}`}>
            <DatePicker
                label={startLabel}
                value={startDate}
                onChange={onStartDateChange}
                max={endDate}
            />
            <DatePicker
                label={endLabel}
                value={endDate}
                onChange={onEndDateChange}
                min={startDate}
            />
        </div>
    );
}