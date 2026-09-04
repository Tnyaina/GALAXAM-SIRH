import { useId } from 'react';

export default function Input({
  label,
  error,
  hint,
  helpText,
  helperText,
  icon,
  iconLeft,
  iconRight,
  className = '',
  labelClassName = '',
  containerClassName = '',
  onWheel,
  ...props
}) {
  const leftIcon = icon || iconLeft;
  const hintText = hint || helpText || helperText;

  const handleWheel = (e) => {
    if (props.type === 'number') {
      e.target.blur();
    }
    if (onWheel) {
      onWheel(e);
    }
  };

  return (
    <div className={`flex flex-col gap-1 ${containerClassName}`}>
      {label && (
        <label className={`text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg)] ${labelClassName}`}>
          {label}
          {props.required && <span className="text-[var(--color-danger-500)] ml-1">*</span>}
        </label>
      )}
      <div className="relative">
        {leftIcon && (
          <div className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]">
            {leftIcon}
          </div>
        )}
        <input
          onWheel={handleWheel}
          className={`
            w-full bg-[var(--color-surface)] border rounded-none text-xs sm:text-sm text-[var(--color-fg)] placeholder:text-[var(--color-fg-subtle)]
            px-3 py-1.5 outline-none  
            focus:ring-0 focus:border-[var(--color-brand-500)]
            disabled:bg-[var(--color-bg-subtle)] disabled:cursor-not-allowed disabled:text-[var(--color-fg-subtle)]
            ${error ? 'border-[var(--color-danger-500)]' : 'border-[var(--color-border)] hover:border-[var(--color-fg-muted)]'}
            ${leftIcon ? 'pl-8' : ''}
            ${iconRight ? 'pr-8' : ''}
            ${className}
          `}
          {...props}
        />
        {iconRight && (
          <div className="absolute right-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]">
            {iconRight}
          </div>
        )}
      </div>
      {error && <p className="text-[11px] text-[var(--color-danger-500)] font-semibold mt-0.5">{error}</p>}
      {hintText && !error && <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">{hintText}</p>}
    </div>
  );
}

export function Textarea({
  label,
  error,
  hint,
  helpText,
  helperText,
  containerClassName = '',
  labelClassName = '',
  className = '',
  ...props
}) {
  const hintText = hint || helpText || helperText;

  return (
    <div className={`flex flex-col gap-1 ${containerClassName}`}>
      {label && (
        <label className={`text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg)] ${labelClassName}`}>
          {label}
          {props.required && <span className="text-[var(--color-danger-500)] ml-1">*</span>}
        </label>
      )}
      <textarea
        className={`
          w-full bg-[var(--color-surface)] border rounded-none text-xs sm:text-sm text-[var(--color-fg)] placeholder:text-[var(--color-fg-subtle)]
          px-3 py-1.5 outline-none resize-none
          focus:ring-0 focus:border-[var(--color-brand-500)]
          disabled:bg-[var(--color-bg-subtle)] disabled:cursor-not-allowed disabled:text-[var(--color-fg-subtle)]
          ${error ? 'border-[var(--color-danger-500)]' : 'border-[var(--color-border)] hover:border-[var(--color-fg-muted)]'}
          ${className}
        `}
        {...props}
      />
      {error && <p className="text-[11px] text-[var(--color-danger-500)] font-semibold mt-0.5">{error}</p>}
      {hintText && !error && <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">{hintText}</p>}
    </div>
  );
}

export function Select({
  label,
  error,
  hint,
  helpText,
  helperText,
  children,
  containerClassName = '',
  labelClassName = '',
  className = '',
  ...props
}) {
  const hintText = hint || helpText || helperText;

  return (
    <div className={`flex flex-col gap-1 ${containerClassName}`}>
      {label && (
        <label className={`text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg)] ${labelClassName}`}>
          {label}
          {props.required && <span className="text-[var(--color-danger-500)] ml-1">*</span>}
        </label>
      )}
      <select
        className={`
          w-full bg-[var(--color-surface)] border rounded-none text-xs sm:text-sm text-[var(--color-fg)]
          px-3 py-1.5 outline-none cursor-pointer
          focus:ring-0 focus:border-[var(--color-brand-500)]
          disabled:bg-[var(--color-bg-subtle)] disabled:cursor-not-allowed disabled:text-[var(--color-fg-subtle)]
          ${error ? 'border-[var(--color-danger-500)]' : 'border-[var(--color-border)] hover:border-[var(--color-fg-muted)]'}
          ${className}
        `}
        {...props}
      >
        {children}
      </select>
      {error && <p className="text-[11px] text-[var(--color-danger-500)] font-semibold mt-0.5">{error}</p>}
      {hintText && !error && <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">{hintText}</p>}
    </div>
  );
}

export function Checkbox({
  label,
  error,
  hint,
  helpText,
  helperText,
  containerClassName = '',
  className = '',
  ...props
}) {
  const hintText = hint || helpText || helperText;

  return (
    <div className={`flex flex-col gap-1 ${containerClassName}`}>
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
          <span className="text-sm text-[var(--color-fg)] group-hover:text-[var(--color-brand-500)]  ">
            {label}
            {props.required && <span className="text-[var(--color-danger-500)] ml-1">*</span>}
          </span>
        )}
      </label>
      {error && <p className="text-xs text-[var(--color-danger-500)] font-semibold mt-0.5">{error}</p>}
      {hintText && !error && <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">{hintText}</p>}
    </div>
  );
}

export function Radio({
  label,
  error,
  hint,
  helpText,
  helperText,
  containerClassName = '',
  className = '',
  ...props
}) {
  const hintText = hint || helpText || helperText;

  return (
    <div className={`flex flex-col gap-1 ${containerClassName}`}>
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
          <span className="text-sm text-[var(--color-fg)] group-hover:text-[var(--color-brand-500)]  ">
            {label}
            {props.required && <span className="text-[var(--color-danger-500)] ml-1">*</span>}
          </span>
        )}
      </label>
      {error && <p className="text-xs text-[var(--color-danger-500)] font-semibold mt-0.5">{error}</p>}
      {hintText && !error && <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">{hintText}</p>}
    </div>
  );
}

export function AutocompleteInput({
  label,
  options = [],
  id,
  listId,
  error,
  hint,
  helpText,
  helperText,
  className = '',
  ...props
}) {
  const reactId = useId();
  const generatedId = id || listId || `list-${reactId}`;
  const hintText = hint || helpText || helperText;

  return (
    <div className="flex flex-col gap-1">
      <Input
        label={label}
        error={error}
        hint={hintText}
        list={generatedId}
        className={className}
        {...props}
      />
      <datalist id={generatedId}>
        {options.map((opt) => {
          const val = typeof opt === 'string' ? opt : (opt.value || opt.label);
          return <option key={val} value={val} />;
        })}
      </datalist>
    </div>
  );
}