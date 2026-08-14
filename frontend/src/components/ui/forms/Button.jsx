const variants = {
  primary:
    'bg-[var(--color-brand-500)] hover:bg-[var(--color-brand-hover)] text-white border border-[var(--color-brand-500)] hover:border-[var(--color-brand-hover)]',
  secondary:
    'bg-[var(--color-fg)] hover:bg-[var(--color-fg-muted)] text-[var(--color-bg)] border border-[var(--color-fg)]',
  outline:
    'border border-[var(--color-border)] text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] bg-transparent',
  ghost:
    'hover:bg-[var(--color-bg-subtle)] text-[var(--color-fg)] border border-transparent',
  danger:
    'bg-[var(--color-danger-500)] hover:bg-[var(--color-danger-600)] text-white border border-[var(--color-danger-500)]',
  success:
    'bg-[var(--color-success-500)] hover:bg-[var(--color-success-600)] text-white border border-[var(--color-success-500)]',
};

const sizes = {
  xs: 'text-xs px-3 py-2 gap-1.5 min-h-[36px]',
  sm: 'text-sm px-4 py-2.5 gap-2 min-h-[40px]',
  md: 'text-sm px-5 py-3 gap-2 min-h-[44px]',
  lg: 'text-base px-6 py-3.5 gap-2.5 min-h-[48px]',
};

export default function Button({
  children,
  variant = 'primary',
  size = 'md',
  icon,
  iconLeft,
  iconRight,
  loading = false,
  disabled = false,
  className = '',
  ...props
}) {
  return (
    <button
      disabled={disabled || loading}
      className={`
        inline-flex items-center justify-center font-semibold tracking-wide uppercase text-[0.75em]
        transition-all duration-150 rounded-none
        focus:outline-none focus:ring-2 focus:ring-[var(--color-focus-ring)] focus:ring-offset-0
        disabled:opacity-40 disabled:cursor-not-allowed
        ${variants[variant]}
        ${sizes[size]}
        ${className}
      `}
      {...props}
    >
      {loading ? (
        <svg className="animate-spin w-4 h-4" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/>
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
        </svg>
      ) : (iconLeft ?? icon)}
      {children}
      {!loading && iconRight}
    </button>
  );
}