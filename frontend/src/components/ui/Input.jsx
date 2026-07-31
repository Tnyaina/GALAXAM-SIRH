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
        <label className="text-xs font-bold uppercase tracking-wider text-ink-800 dark:text-ink-50">
          {label}
          {props.required && <span className="text-danger-500 ml-1">*</span>}
        </label>
      )}
      <div className="relative">
        {icon && (
          <div className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-400 dark:text-ink-300">
            {icon}
          </div>
        )}
        <input
          className={`
            w-full bg-white dark:bg-ink-900 border-2 rounded-none text-sm text-ink-800 dark:text-ink-50 placeholder:text-ink-300 dark:placeholder:text-ink-500
            px-4 py-2.5 outline-none transition-all duration-150
            focus:ring-0 focus:border-brand-500
            disabled:bg-ink-50 dark:disabled:bg-ink-800 disabled:cursor-not-allowed disabled:text-ink-350
            ${error ? 'border-danger-500' : 'border-ink-200 hover:border-ink-300 dark:border-ink-700 dark:hover:border-ink-600'}
            ${icon ? 'pl-10' : ''}
            ${iconRight ? 'pr-10' : ''}
            ${className}
          `}
          {...props}
        />
        {iconRight && (
          <div className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-400 dark:text-ink-300">
            {iconRight}
          </div>
        )}
      </div>
      {error && <p className="text-xs text-danger-500 font-semibold mt-0.5">{error}</p>}
      {hint && !error && <p className="text-xs text-ink-350 mt-0.5">{hint}</p>}
    </div>
  );
}

export function Textarea({ label, error, hint, className = '', ...props }) {
  return (
    <div className="flex flex-col gap-1">
      {label && (
        <label className="text-xs font-bold uppercase tracking-wider text-ink-800 dark:text-ink-50">
          {label}
          {props.required && <span className="text-danger-500 ml-1">*</span>}
        </label>
      )}
      <textarea
        className={`
          w-full bg-white dark:bg-ink-900 border-2 rounded-none text-sm text-ink-800 dark:text-ink-50 placeholder:text-ink-300 dark:placeholder:text-ink-500
          px-4 py-2.5 outline-none transition-all duration-150 resize-none
          focus:ring-0 focus:border-brand-500
          ${error ? 'border-danger-500' : 'border-ink-200 hover:border-ink-300 dark:border-ink-700 dark:hover:border-ink-600'}
          ${className}
        `}
        {...props}
      />
      {error && <p className="text-xs text-danger-500 font-semibold mt-0.5">{error}</p>}
      {hint && !error && <p className="text-xs text-ink-350 mt-0.5">{hint}</p>}
    </div>
  );
}

export function Select({ label, error, hint, children, className = '', ...props }) {
  return (
    <div className="flex flex-col gap-1">
      {label && (
        <label className="text-xs font-bold uppercase tracking-wider text-ink-800 dark:text-ink-50">
          {label}
          {props.required && <span className="text-danger-500 ml-1">*</span>}
        </label>
      )}
      <select
        className={`
          w-full bg-white dark:bg-ink-900 border-2 rounded-none text-sm text-ink-800 dark:text-ink-50
          px-4 py-2.5 outline-none transition-all duration-150 cursor-pointer
          focus:ring-0 focus:border-brand-500
          ${error ? 'border-danger-500' : 'border-ink-200 hover:border-ink-300 dark:border-ink-700 dark:hover:border-ink-600'}
          ${className}
        `}
        {...props}
      >
        {children}
      </select>
      {error && <p className="text-xs text-danger-500 font-semibold mt-0.5">{error}</p>}
      {hint && !error && <p className="text-xs text-ink-350 mt-0.5">{hint}</p>}
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
            w-4 h-4 border-2 rounded-none accent-brand-500 cursor-pointer
            disabled:cursor-not-allowed
            ${error ? 'border-danger-500' : 'border-ink-200 dark:border-ink-600'}
            ${className}
          `}
          {...props}
        />
        {label && (
          <span className="text-sm text-ink-800 dark:text-ink-50 group-hover:text-brand-500 transition-colors duration-150">
            {label}
            {props.required && <span className="text-danger-500 ml-1">*</span>}
          </span>
        )}
      </label>
      {error && <p className="text-xs text-danger-500 font-semibold mt-0.5">{error}</p>}
      {hint && !error && <p className="text-xs text-ink-350 mt-0.5">{hint}</p>}
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
            w-4 h-4 border-2 accent-brand-500 cursor-pointer
            disabled:cursor-not-allowed
            ${error ? 'border-danger-500' : 'border-ink-200 dark:border-ink-600'}
            ${className}
          `}
          {...props}
        />
        {label && (
          <span className="text-sm text-ink-800 dark:text-ink-50 group-hover:text-brand-500 transition-colors duration-150">
            {label}
            {props.required && <span className="text-danger-500 ml-1">*</span>}
          </span>
        )}
      </label>
      {error && <p className="text-xs text-danger-500 font-semibold mt-0.5">{error}</p>}
      {hint && !error && <p className="text-xs text-ink-350 mt-0.5">{hint}</p>}
    </div>
  );
} 