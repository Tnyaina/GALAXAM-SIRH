import { useId } from 'react';
import FieldError from './FieldError';

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
  inputRef,
  id,
  'aria-describedby': describedBy,
  'aria-invalid': ariaInvalid,
  ...props
}) {
  const reactId = useId();
  const fieldId = id || `field-${reactId}`;
  const feedbackId = `${fieldId}-feedback`;
  const leftIcon = icon || iconLeft;
  const hintText = hint || helpText || helperText;
  const feedback = error || hintText;
  const feedbackIds = [describedBy, feedback ? feedbackId : null].filter(Boolean).join(' ') || undefined;

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
        <label htmlFor={fieldId} className={`text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg)] ${labelClassName}`}>
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
          id={fieldId}
          ref={inputRef}
          aria-invalid={error ? true : ariaInvalid}
          aria-describedby={feedbackIds}
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
      {error && <FieldError id={feedbackId} className="mt-0.5">{error}</FieldError>}
      {hintText && !error && <p id={feedbackId} className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">{hintText}</p>}
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
  const reactId = useId();
  const fieldId = props.id || `textarea-${reactId}`;
  const describedBy = props['aria-describedby'];
  const hintText = hint || helpText || helperText;
  const feedback = error || hintText;
  const feedbackIds = [describedBy, feedback ? `${fieldId}-feedback` : null].filter(Boolean).join(' ') || undefined;

  return (
    <div className={`flex flex-col gap-1 ${containerClassName}`}>
      {label && (
        <label htmlFor={fieldId} className={`text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg)] ${labelClassName}`}>
          {label}
          {props.required && <span className="text-[var(--color-danger-500)] ml-1">*</span>}
        </label>
      )}
      <textarea
        {...props}
        id={fieldId}
        aria-invalid={error ? true : props['aria-invalid']}
        aria-describedby={feedbackIds}
        className={`
          w-full bg-[var(--color-surface)] border rounded-none text-xs sm:text-sm text-[var(--color-fg)] placeholder:text-[var(--color-fg-subtle)]
          px-3 py-1.5 outline-none resize-none
          focus:ring-0 focus:border-[var(--color-brand-500)]
          disabled:bg-[var(--color-bg-subtle)] disabled:cursor-not-allowed disabled:text-[var(--color-fg-subtle)]
          ${error ? 'border-[var(--color-danger-500)]' : 'border-[var(--color-border)] hover:border-[var(--color-fg-muted)]'}
          ${className}
        `}
      />
      {error && <FieldError id={`${fieldId}-feedback`} className="mt-0.5">{error}</FieldError>}
      {hintText && !error && <p id={`${fieldId}-feedback`} className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">{hintText}</p>}
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
  const reactId = useId();
  const fieldId = props.id || `select-${reactId}`;
  const describedBy = props['aria-describedby'];
  const hintText = hint || helpText || helperText;
  const feedback = error || hintText;
  const feedbackIds = [describedBy, feedback ? `${fieldId}-feedback` : null].filter(Boolean).join(' ') || undefined;

  return (
    <div className={`flex flex-col gap-1 ${containerClassName}`}>
      {label && (
        <label htmlFor={fieldId} className={`text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg)] ${labelClassName}`}>
          {label}
          {props.required && <span className="text-[var(--color-danger-500)] ml-1">*</span>}
        </label>
      )}
      <select
        {...props}
        id={fieldId}
        aria-invalid={error ? true : props['aria-invalid']}
        aria-describedby={feedbackIds}
        className={`
          w-full bg-[var(--color-surface)] border rounded-none text-xs sm:text-sm text-[var(--color-fg)]
          px-3 py-1.5 outline-none cursor-pointer
          focus:ring-0 focus:border-[var(--color-brand-500)]
          disabled:bg-[var(--color-bg-subtle)] disabled:cursor-not-allowed disabled:text-[var(--color-fg-subtle)]
          ${error ? 'border-[var(--color-danger-500)]' : 'border-[var(--color-border)] hover:border-[var(--color-fg-muted)]'}
          ${className}
        `}
      >
        {children || <option value="" disabled>Sélectionner...</option>}
      </select>
      {error && <FieldError id={`${fieldId}-feedback`} className="mt-0.5">{error}</FieldError>}
      {hintText && !error && <p id={`${fieldId}-feedback`} className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">{hintText}</p>}
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
  const reactId = useId();
  const feedbackId = `checkbox-${reactId}-feedback`;
  const hintText = hint || helpText || helperText;
  const describedBy = [props['aria-describedby'], error || hintText ? feedbackId : null].filter(Boolean).join(' ') || undefined;

  return (
    <div className={`flex flex-col gap-1 ${containerClassName}`}>
      <label className="flex items-center gap-2 cursor-pointer group">
        <input
          {...props}
          type="checkbox"
          aria-invalid={error ? true : props['aria-invalid']}
          aria-describedby={describedBy}
          className={`
            w-4 h-4 border-2 rounded-none accent-[var(--color-brand-500)] cursor-pointer
            disabled:cursor-not-allowed
            ${error ? 'border-[var(--color-danger-500)]' : 'border-[var(--color-border)]'}
            ${className}
          `}
        />
        {label && (
          <span className="text-sm text-[var(--color-fg)] group-hover:text-[var(--color-brand-500)]  ">
            {label}
            {props.required && <span className="text-[var(--color-danger-500)] ml-1">*</span>}
          </span>
        )}
      </label>
      {error && <FieldError id={feedbackId} className="mt-0.5">{error}</FieldError>}
      {hintText && !error && <p id={feedbackId} className="text-xs text-[var(--color-fg-muted)] mt-0.5">{hintText}</p>}
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
  const reactId = useId();
  const feedbackId = `radio-${reactId}-feedback`;
  const hintText = hint || helpText || helperText;
  const describedBy = [props['aria-describedby'], error || hintText ? feedbackId : null].filter(Boolean).join(' ') || undefined;

  return (
    <div className={`flex flex-col gap-1 ${containerClassName}`}>
      <label className="flex items-center gap-2 cursor-pointer group">
        <input
          {...props}
          type="radio"
          aria-invalid={error ? true : props['aria-invalid']}
          aria-describedby={describedBy}
          className={`
            w-4 h-4 border-2 accent-[var(--color-brand-500)] cursor-pointer
            disabled:cursor-not-allowed
            ${error ? 'border-[var(--color-danger-500)]' : 'border-[var(--color-border)]'}
            ${className}
          `}
        />
        {label && (
          <span className="text-sm text-[var(--color-fg)] group-hover:text-[var(--color-brand-500)]  ">
            {label}
            {props.required && <span className="text-[var(--color-danger-500)] ml-1">*</span>}
          </span>
        )}
      </label>
      {error && <FieldError id={feedbackId} className="mt-0.5">{error}</FieldError>}
      {hintText && !error && <p id={feedbackId} className="text-xs text-[var(--color-fg-muted)] mt-0.5">{hintText}</p>}
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
