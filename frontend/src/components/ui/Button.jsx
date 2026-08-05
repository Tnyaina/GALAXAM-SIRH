// Couleurs pilotées par les tokens définis dans index.css (@theme).
// Mêmes valeurs visuelles qu'avant en mode clair + un premier passage
// dark: à affiner visuellement si besoin.
const variants = {
  primary:
    'bg-ink-800 hover:bg-brand-hover text-white border border-ink-800 hover:border-brand-hover ' +
    'dark:bg-brand-500 dark:hover:bg-brand-hover dark:border-brand-500 dark:hover:border-brand-hover',
  secondary:
    'bg-ink-800 hover:bg-ink-700 text-white border border-ink-800 ' +
    'dark:bg-ink-700 dark:hover:bg-ink-600 dark:border-ink-700',
  outline:
    'border border-ink-800 text-ink-800 hover:bg-ink-800 hover:text-white bg-transparent ' +
    'dark:border-ink-200 dark:text-ink-50 dark:hover:bg-ink-200 dark:hover:text-ink-900',
  ghost:
    'hover:bg-ink-100 text-ink-800 border border-transparent ' +
    'dark:text-ink-50 dark:hover:bg-ink-800',
  danger:
    'bg-danger-500 hover:bg-danger-600 text-white border border-danger-500',
  success:
    'bg-success-500 hover:bg-success-600 text-white border border-success-500',
};

const sizes = {
  xs: 'text-xs px-3 py-1.5 gap-1.5',
  sm: 'text-sm px-4 py-2 gap-2',
  md: 'text-sm px-5 py-2.5 gap-2',
  lg: 'text-base px-6 py-3 gap-2.5',
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
        focus:outline-none focus:ring-2 focus:ring-brand-500/40 focus:ring-offset-0
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