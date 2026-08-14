export default function Input({
  label,
  error,
  hint,
  icon,
  iconRight,
  className = '',
  ...props
}) {
  return (
    <div className="flex flex-col gap-1">
      {label && (
        <label className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
          {label}
          {props.required && <span className="text-[var(--color-danger-500)] ml-1">*</span>}
        </label>
      )}
      <div className="relative">
        {icon && (
          <div className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]">
            {icon}
          </div>
        )}
        <input
          className={`
            w-full bg-[var(--color-surface)] border-2 rounded-none text-sm text-[var(--color-fg)] placeholder:text-[var(--color-fg-subtle)]
            px-4 py-2.5 outline-none transition-all duration-150
            focus:ring-0 focus:border-[var(--color-brand-500)]
            disabled:bg-[var(--color-bg-subtle)] disabled:cursor-not-allowed disabled:text-[var(--color-fg-subtle)]
            ${error ? 'border-[var(--color-danger-500)]' : 'border-[var(--color-border)] hover:border-[var(--color-fg-muted)]'}
            ${icon ? 'pl-10' : ''}
            ${iconRight ? 'pr-10' : ''}
            ${className}
          `}
          {...props}
        />
        {iconRight && (
          <div className="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]">
            {iconRight}
          </div>
        )}
      </div>
      {error && <p className="text-xs text-[var(--color-danger-500)] font-semibold mt-0.5">{error}</p>}
      {hint && !error && <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">{hint}</p>}
    </div>
  );
}

export function Textarea({ label, error, hint, className = '', ...props }) {
  return (
    <div className="flex flex-col gap-1">
      {label && (
        <label className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
          {label}
          {props.required && <span className="text-[var(--color-danger-500)] ml-1">*</span>}
        </label>
      )}
      <textarea
        className={`
          w-full bg-[var(--color-surface)] border-2 rounded-none text-sm text-[var(--color-fg)] placeholder:text-[var(--color-fg-subtle)]
          px-4 py-2.5 outline-none transition-all duration-150 resize-none
          focus:ring-0 focus:border-[var(--color-brand-500)]
          disabled:bg-[var(--color-bg-subtle)] disabled:cursor-not-allowed disabled:text-[var(--color-fg-subtle)]
          ${error ? 'border-[var(--color-danger-500)]' : 'border-[var(--color-border)] hover:border-[var(--color-fg-muted)]'}
          ${className}
        `}
        {...props}
      />
      {error && <p className="text-xs text-[var(--color-danger-500)] font-semibold mt-0.5">{error}</p>}
      {hint && !error && <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">{hint}</p>}
    </div>
  );
}

export function Select({ label, error, hint, children, className = '', ...props }) {
  return (
    <div className="flex flex-col gap-1">
      {label && (
        <label className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
          {label}
          {props.required && <span className="text-[var(--color-danger-500)] ml-1">*</span>}
        </label>
      )}
      <select
        className={`
          w-full bg-[var(--color-surface)] border-2 rounded-none text-sm text-[var(--color-fg)]
          px-4 py-2.5 outline-none transition-all duration-150 cursor-pointer
          focus:ring-0 focus:border-[var(--color-brand-500)]
          disabled:bg-[var(--color-bg-subtle)] disabled:cursor-not-allowed disabled:text-[var(--color-fg-subtle)]
          ${error ? 'border-[var(--color-danger-500)]' : 'border-[var(--color-border)] hover:border-[var(--color-fg-muted)]'}
          ${className}
        `}
        {...props}
      >
        {children}
      </select>
      {error && <p className="text-xs text-[var(--color-danger-500)] font-semibold mt-0.5">{error}</p>}
      {hint && !error && <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">{hint}</p>}
    </div>
  );
}

export function Checkbox({ label, error, hint, className = '', ...props }) {
  return (
    <div className="flex flex-col gap-1">
      <label className="flex items-center gap-2 cursor-pointer group">
        <input
          type="checkbox"
          className={`
            w-4 h-4 border-2 rounded-none accent-[var(--color-brand-500)] cursor-pointer
            disabled:cursor-not-allowed
            ${error ? 'border-[var(--color-danger-500)]' : 'border-[var(--color-border)]'}
            ${className}
          `}
          {...props}
        />
        {label && (
          <span className="text-sm text-[var(--color-fg)] group-hover:text-[var(--color-brand-500)] transition-colors duration-150">
            {label}
            {props.required && <span className="text-[var(--color-danger-500)] ml-1">*</span>}
          </span>
        )}
      </label>
      {error && <p className="text-xs text-[var(--color-danger-500)] font-semibold mt-0.5">{error}</p>}
      {hint && !error && <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">{hint}</p>}
    </div>
  );
}

export function Radio({ label, error, hint, className = '', ...props }) {
  return (
    <div className="flex flex-col gap-1">
      <label className="flex items-center gap-2 cursor-pointer group">
        <input
          type="radio"
          className={`
            w-4 h-4 border-2 accent-[var(--color-brand-500)] cursor-pointer
            disabled:cursor-not-allowed
            ${error ? 'border-[var(--color-danger-500)]' : 'border-[var(--color-border)]'}
            ${className}
          `}
          {...props}
        />
        {label && (
          <span className="text-sm text-[var(--color-fg)] group-hover:text-[var(--color-brand-500)] transition-colors duration-150">
            {label}
            {props.required && <span className="text-[var(--color-danger-500)] ml-1">*</span>}
          </span>
        )}
      </label>
      {error && <p className="text-xs text-[var(--color-danger-500)] font-semibold mt-0.5">{error}</p>}
      {hint && !error && <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">{hint}</p>}
    </div>
  );
}