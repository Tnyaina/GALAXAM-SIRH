const variants = {
  primary:
    'bg-[var(--color-brand-500)] hover:bg-[var(--color-brand-hover)] text-[var(--color-on-color)] border border-[var(--color-brand-500)] hover:border-[var(--color-brand-hover)]',
  secondary:
    'bg-[var(--color-surface)] hover:bg-[var(--color-bg-subtle)] text-[var(--color-fg)] border border-[var(--color-border)]',
  outline:
    'border border-[var(--color-border)] text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] bg-transparent',
  ghost:
    'hover:bg-[var(--color-bg-subtle)] text-[var(--color-fg)] border border-transparent',
  danger:
    'bg-[var(--color-danger-500)] hover:bg-[var(--color-danger-600)] text-[var(--color-on-color)] border border-[var(--color-danger-500)]',
  success:
    'bg-[var(--color-success-600)] hover:bg-[var(--color-success-700)] text-[var(--color-on-color)] border border-[var(--color-success-600)]',
};

const sizes = {
  '2xs': 'ui-button-size-2xs',
  xs: 'ui-button-size-xs',
  sm: 'ui-button-size-sm',
  md: 'ui-button-size-md',
  lg: 'ui-button-size-lg',
};

const iconSizes = {
  '2xs': 'ui-button-icon-2xs',
  xs: 'ui-button-icon-xs',
  sm: 'ui-button-icon-sm',
  md: 'ui-button-icon-md',
  lg: 'ui-button-icon-lg',
};

export default function Button({
  children,
  variant = 'primary',
  size = 'md',
  icon,
  iconLeft,
  iconRight,
  iconOnly = false,
  wrapLabel = false,
  loading = false,
  disabled = false,
  plain = false,
  className = '',
  onClick,
  ...props
}) {
  const isDisabled = Boolean(disabled || loading);

  const handleClick = (e) => {
    if (isDisabled) {
      e.preventDefault();
      e.stopPropagation();
      return;
    }
    if (onClick) {
      onClick(e);
    }
  };

  const hasChildren = children !== undefined && children !== null && children !== false && children !== '';
  const isIconOnly = Boolean(iconOnly || (!hasChildren && !iconRight && (iconLeft || icon)));
  const sizeClass = isIconOnly ? (iconSizes[size] || sizes[size]) : (sizes[size] || sizes.md);

  return (
    <button
      disabled={isDisabled}
      aria-disabled={isDisabled}
      onClick={handleClick}
      className={plain ? `font-sans transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-focus-ring)] disabled:cursor-not-allowed ${className}` : `
        inline-flex flex-row items-center justify-center font-semibold tracking-wide uppercase
        rounded-none transition-all duration-200 ease-in-out select-none ${wrapLabel ? 'whitespace-normal' : 'whitespace-nowrap'} leading-none
        focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-focus-ring)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--color-surface)]
        ${!isDisabled ? 'active:scale-[0.98] cursor-pointer' : 'opacity-50 cursor-not-allowed shadow-none pointer-events-none disabled:pointer-events-none'}
        ${loading ? 'animate-pulse' : ''}
        ${variants[variant]}
        ${sizeClass}
        ${className}
      `}
      {...props}
    >
      {plain ? children : loading ? (
        <svg className="animate-spin w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/>
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
        </svg>
      ) : (iconLeft ?? icon)}
      {!plain && hasChildren && (
        <span className={`inline-flex flex-row items-center gap-2 ${wrapLabel ? 'min-w-0 flex-1 justify-start whitespace-normal text-left leading-snug' : 'justify-center whitespace-nowrap leading-none'} ${loading ? 'opacity-90' : ''}`}>{children}</span>
      )}
      {!plain && !loading && iconRight}
    </button>
  );
}
