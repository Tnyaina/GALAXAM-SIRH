export function Toggle({ checked, onChange, label, description }) {
  return (
    <div className="flex items-start gap-3">
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className={`
          relative inline-flex h-5 w-9 flex-shrink-0 cursor-pointer rounded-none border-2 border-transparent
            focus:outline-none focus:ring-2 focus:ring-[var(--color-focus-ring)]
          ${checked ? 'bg-[var(--color-brand-500)]' : 'bg-[var(--color-border)]'}
        `}
      >
        <span className={`
          pointer-events-none inline-block h-4 w-4 transform rounded-none bg-white shadow-xs
          ${checked ? 'translate-x-4' : 'translate-x-0'}
        `} />
      </button>
      {(label || description) && (
        <div>
          {label && <p className="text-xs sm:text-[13px] font-bold text-[var(--color-fg)]">{label}</p>}
          {description && <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">{description}</p>}
        </div>
      )}
    </div>
  );
}

export function Checkbox({ checked, onChange, label, description, indeterminate }) {
  const isMixed = Boolean(indeterminate && !checked);

  return (
    <label className="flex items-start gap-3 cursor-pointer group">
      <div className="relative flex-shrink-0 mt-0.5">
        <input
          type="checkbox"
          checked={checked}
          onChange={(e) => onChange(e.target.checked)}
          aria-checked={isMixed ? 'mixed' : checked}
          className="sr-only"
        />
        <div className={`
          w-4 h-4 rounded-none border-2 flex items-center justify-center  
          ${checked || isMixed
            ? 'bg-[var(--color-brand-500)] border-[var(--color-brand-500)] text-white'
            : 'border-[var(--color-border)] group-hover:border-[var(--color-fg-muted)] bg-[var(--color-surface)]'}
        `}>
          {checked && !isMixed && (
            <svg className="w-2.5 h-2.5 text-white" viewBox="0 0 10 8" fill="none">
              <path d="M1 4L3.5 6.5L9 1" stroke="currentColor" strokeWidth="1.8" strokeLinecap="square" strokeLinejoin="miter"/>
            </svg>
          )}
          {isMixed && (
            <span className="w-2 h-0.5 rounded-none bg-white" />
          )}
        </div>
      </div>
      {(label || description) && (
        <div>
          {label && <p className="text-sm font-medium text-[var(--color-fg)]">{label}</p>}
          {description && <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">{description}</p>}
        </div>
      )}
    </label>
  );
}