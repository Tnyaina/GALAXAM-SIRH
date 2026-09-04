const variants = {
  default:   'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border border-[var(--color-border-subtle)]',
  secondary: 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border border-[var(--color-border)]',
  primary:   'bg-[var(--color-brand-500)]/15 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] border border-[var(--color-brand-500)]/30',
  brand:     'bg-[var(--color-brand-500)]/15 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] border border-[var(--color-brand-500)]/30',
  success:   'bg-[var(--color-success-500)]/15 text-[var(--color-success-600)] dark:text-[var(--color-success-400)] border border-[var(--color-success-500)]/30',
  warning:   'bg-[var(--color-warning-500)]/15 text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] border border-[var(--color-warning-500)]/30',
  danger:    'bg-[var(--color-danger-500)]/15 text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)] border border-[var(--color-danger-500)]/30',
  error:     'bg-[var(--color-danger-500)]/15 text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)] border border-[var(--color-danger-500)]/30',
  info:      'bg-[var(--color-info-500)]/15 text-[var(--color-info-600)] dark:text-[var(--color-info-200)] border border-[var(--color-info-500)]/30',
  sky:       'bg-sky-500/15 text-sky-700 dark:text-sky-300 border border-sky-500/30',
  purple:    'bg-purple-500/15 text-purple-700 dark:text-purple-300 border border-purple-500/30',
  orange:    'bg-orange-500/15 text-orange-700 dark:text-orange-300 border border-orange-500/30',
  neutral:   'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border border-[var(--color-border-subtle)]',
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
  success: 'bg-[var(--color-success-500)]',
  warning: 'bg-[var(--color-warning-500)]',
  danger:  'bg-[var(--color-danger-500)]',
  error:   'bg-[var(--color-danger-500)]',
  primary: 'bg-[var(--color-brand-500)]',
  brand:   'bg-[var(--color-brand-500)]',
  secondary: 'bg-[var(--color-fg-muted)]',
  default: 'bg-[var(--color-fg-subtle)]',
  neutral: 'bg-[var(--color-fg-subtle)]',
  info:    'bg-[var(--color-info-500)]',
  sky:     'bg-sky-500',
  purple:  'bg-purple-500',
  orange:  'bg-orange-500',
};

export default function Badge({
  children,
  variant = 'default',
  size = 'sm',
  dot = false,
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
        inline-flex items-center gap-1.5 font-bold tracking-wide uppercase rounded-none
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