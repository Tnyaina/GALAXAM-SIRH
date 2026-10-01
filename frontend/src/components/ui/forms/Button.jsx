const variants = {
  primary:
    'bg-[var(--color-brand-500)] hover:bg-[var(--color-brand-hover)] text-[var(--color-on-color)] border border-[var(--color-brand-500)] hover:border-[var(--color-brand-hover)]',
  secondary:
    'bg-[var(--color-fg)] hover:bg-[var(--color-fg-muted)] text-[var(--color-bg)] border border-[var(--color-fg)]',
  outline:
    'border border-[var(--color-border)] text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] bg-transparent',
  ghost:
    'hover:bg-[var(--color-bg-subtle)] text-[var(--color-fg)] border border-transparent',
  danger:
    'bg-[var(--color-danger-500)] hover:bg-[var(--color-danger-600)] text-[var(--color-on-color)] border border-[var(--color-danger-500)]',
  success:
    'bg-[var(--color-brand-600)] hover:bg-[var(--color-brand-700)] text-[var(--color-on-color)] border border-[var(--color-brand-600)]',
};

const sizes = {
  xs: 'text-[11px] px-2.5 py-1 gap-1.5 min-h-[26px]',
  sm: 'text-xs px-3 py-1.5 gap-1.5 min-h-[30px]',
  md: 'text-xs px-3.5 py-2 gap-2 min-h-[34px]',
  lg: 'text-sm px-4.5 py-2.5 gap-2 min-h-[38px]',
};

const iconSizes = {
  xs: 'w-[26px] h-[26px] min-w-[26px] min-h-[26px] p-0',
  sm: 'w-[30px] h-[30px] min-w-[30px] min-h-[30px] p-0',
  md: 'w-[34px] h-[34px] min-w-[34px] min-h-[34px] p-0',
  lg: 'w-[38px] h-[38px] min-w-[38px] min-h-[38px] p-0',
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
      className={`
        inline-flex flex-row items-center justify-center font-semibold tracking-wide uppercase text-[0.75em]
        rounded-none transition-all duration-200 ease-in-out select-none ${wrapLabel ? 'whitespace-normal' : 'whitespace-nowrap'} leading-none
        focus:outline-none focus:ring-2 focus:ring-[var(--color-focus-ring)] focus:ring-offset-0
        ${!isDisabled ? 'active:scale-[0.98] cursor-pointer' : 'opacity-50 cursor-not-allowed shadow-none pointer-events-none disabled:pointer-events-none'}
        ${loading ? 'animate-pulse' : ''}
        ${variants[variant]}
        ${sizeClass}
        ${className}
      `}
      {...props}
    >
      {loading ? (
        <svg className="animate-spin w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/>
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
        </svg>
      ) : (iconLeft ?? icon)}
      {hasChildren && (
        <span className={`inline-flex flex-row items-center gap-2 ${wrapLabel ? 'min-w-0 flex-1 justify-start whitespace-normal text-left leading-snug' : 'justify-center whitespace-nowrap leading-none'} ${loading ? 'opacity-90' : ''}`}>{children}</span>
      )}
      {!loading && iconRight}
    </button>
  );
}