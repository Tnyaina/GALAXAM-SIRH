const variants = {
  default: 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border border-[var(--color-border-subtle)]',
  primary: 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] border border-[var(--color-brand-500)]/20',
  success: 'bg-[var(--color-success-500)]/15 text-emerald-800 dark:text-emerald-300 border border-emerald-500/25',
  warning: 'bg-[var(--color-warning-500)]/15 text-amber-900 dark:text-amber-300 border border-amber-500/25',
  danger:  'bg-[var(--color-danger-500)]/15 text-red-800 dark:text-red-300 border border-red-500/25',
  info:    'bg-[var(--color-info-500)]/15 text-indigo-800 dark:text-indigo-300 border border-indigo-500/25',
};

const sizes = {
  sm: 'text-[10px] px-2 py-0.5',
  md: 'text-xs px-2.5 py-1',
  lg: 'text-sm px-3 py-1.5',
};

const dotSizes = {
  sm: 'w-1 h-1',
  md: 'w-1.5 h-1.5',
  lg: 'w-2 h-2',
};

const dotColors = {
  success: 'bg-[var(--color-success-500)]',
  warning: 'bg-[var(--color-warning-500)]',
  danger:  'bg-[var(--color-danger-500)]',
  primary: 'bg-[var(--color-brand-500)]',
  default: 'bg-[var(--color-fg-subtle)]',
  info:    'bg-[var(--color-info-500)]',
};

export default function Badge({
  children,
  variant = 'default',
  size = 'md',
  dot = false,
  rounded = false,
  className = '',
  style,
  ...props
}) {
  const currentVariant = variants[variant] ? variant : 'default';

  return (
    <span
      style={style}
      {...props}
      className={`
        inline-flex items-center gap-1.5 font-semibold tracking-wide uppercase text-[0.7em]
        ${rounded ? 'rounded-full' : 'rounded-none'}
        ${variants[currentVariant]}
        ${sizes[size]}
        ${className}
      `}
    >
      {dot && (
        <span
          className={`
            ${dotSizes[size]}
            rounded-full
            ${dotColors[currentVariant] ?? dotColors.default}
          `}
        />
      )}
      {children}
    </span>
  );
}