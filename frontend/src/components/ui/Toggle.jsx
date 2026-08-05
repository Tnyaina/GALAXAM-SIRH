export function Toggle({ checked, onChange, label, description }) {
  return (
    <div className="flex items-start gap-3">
      <button
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className={`
          relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent
          transition-colors duration-200 focus:outline-none focus:ring-2 focus:ring-[#2563EB]/30 focus:ring-offset-1
          ${checked ? 'bg-[#2563EB]' : 'bg-slate-200'}
        `}
      >
        <span className={`
          pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-md
          transition duration-200 ease-in-out
          ${checked ? 'translate-x-5' : 'translate-x-0'}
        `} />
      </button>
      {(label || description) && (
        <div>
          {label && <p className="text-sm font-semibold text-slate-800">{label}</p>}
          {description && <p className="text-xs text-slate-400 mt-0.5">{description}</p>}
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
          w-4 h-4 rounded border-2 flex items-center justify-center transition-all duration-150
          ${checked || isMixed ? 'bg-[#2563EB] border-[#2563EB]' : 'border-slate-300 group-hover:border-slate-400 bg-white'}
        `}>
          {checked && !isMixed && (
            <svg className="w-2.5 h-2.5 text-white" viewBox="0 0 10 8" fill="none">
              <path d="M1 4L3.5 6.5L9 1" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          )}
          {isMixed && (
            <span className="w-2 h-0.5 rounded-full bg-white" />
          )}
        </div>
      </div>
      {(label || description) && (
        <div>
          {label && <p className="text-sm font-medium text-slate-800">{label}</p>}
          {description && <p className="text-xs text-slate-400 mt-0.5">{description}</p>}
        </div>
      )}
    </label>
  );
}
