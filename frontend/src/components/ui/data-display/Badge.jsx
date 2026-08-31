const variants = {
  default: 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border border-[var(--color-border-subtle)]',
  secondary: 'bg-slate-100 text-slate-700 dark:bg-slate-800/80 dark:text-slate-200 border border-slate-300 dark:border-slate-700',
  primary: 'bg-[var(--color-brand-500)]/15 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] border border-[var(--color-brand-500)]/30',
  success: 'bg-emerald-500/15 text-emerald-800 dark:text-emerald-300 border border-emerald-500/30',
  warning: 'bg-amber-500/15 text-amber-900 dark:text-amber-300 border border-amber-500/30',
  danger:  'bg-red-500/15 text-red-800 dark:text-red-300 border border-red-500/30',
  info:    'bg-indigo-500/15 text-indigo-800 dark:text-indigo-300 border border-indigo-500/30',
};

const sizes = {
  xs: 'text-[9px] px-1.5 py-0.5 min-h-[17px] leading-tight',
  sm: 'text-[10px] px-2 py-0.5 min-h-[19px] leading-tight',
  md: 'text-[11px] px-2.5 py-0.5 min-h-[21px] leading-tight',
  lg: 'text-xs px-3 py-1 min-h-[24px] leading-normal',
};

const dotSizes = {
  xs: 'w-1 h-1',
  sm: 'w-1.5 h-1.5',
  md: 'w-1.5 h-1.5',
  lg: 'w-2 h-2',
};

const dotColors = {
  success: 'bg-emerald-500',
  warning: 'bg-amber-500',
  danger:  'bg-red-500',
  primary: 'bg-[var(--color-brand-500)]',
  secondary: 'bg-slate-500',
  default: 'bg-[var(--color-fg-subtle)]',
  info:    'bg-indigo-500',
};

export default function Badge({
  children,
  variant = 'default',
  size = 'sm',
  dot = false,
  rounded = false,
  className = '',
  style,
  ...props
}) {
  const currentVariant = variants[variant] ? variant : 'default';
  const currentSize = sizes[size] ? size : 'md';

  return (
    <span
      style={style}
      {...props}
      className={`
        inline-flex items-center gap-1.5 font-bold tracking-wide uppercase
        ${rounded ? 'rounded-md' : 'rounded-none'}
        ${variants[currentVariant]}
        ${sizes[currentSize]}
        ${className}
      `}
    >
      {dot && (
        <span
          className={`
            ${dotSizes[currentSize]}
            rounded-full flex-shrink-0
            ${dotColors[currentVariant] ?? dotColors.default}
          `}
        />
      )}
      {children}
    </span>
  );
}