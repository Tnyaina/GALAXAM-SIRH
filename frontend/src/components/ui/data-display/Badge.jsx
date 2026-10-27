const variants = {
  default:   'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]',
  secondary: 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]',
  primary:   'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)]',
  brand:     'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)]',
  success:   'bg-[var(--color-success-500)]/10 text-[var(--color-success-700)] dark:text-[var(--color-success-300)]',
  warning:   'bg-[var(--color-warning-500)]/10 text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)]',
  danger:    'bg-[var(--color-danger-500)]/10 text-[var(--color-danger-700)] dark:text-[var(--color-danger-300)]',
  error:     'bg-[var(--color-danger-500)]/10 text-[var(--color-danger-700)] dark:text-[var(--color-danger-300)]',
  info:      'bg-[var(--color-info-surface)] text-[var(--color-info-text)] dark:text-[var(--color-info-text)]',
  sky:       'bg-[var(--color-sky-500)]/10 text-[var(--color-sky-800)] dark:text-[var(--color-sky-300)]',
  purple:    'bg-[var(--color-purple-500)]/10 text-[var(--color-purple-800)] dark:text-[var(--color-purple-300)]',
  orange:    'bg-[var(--color-orange-500)]/10 text-[var(--color-orange-800)] dark:text-[var(--color-orange-300)]',
  neutral:   'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]',
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
  sky:     'bg-[var(--color-sky-500)]',
  purple:  'bg-[var(--color-purple-500)]',
  orange:  'bg-[var(--color-orange-500)]',
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
        inline-flex items-center gap-1.5 font-semibold tracking-wide uppercase rounded-none
        ${variants[currentVariant]}
        ${sizes[currentSize]}
        ${className}
      `}
    >
      {dot && (
        <span
          className={`
            ${dotSizes[currentSize]}
            rounded-none flex-shrink-0
            ${dotColors[currentVariant] ?? dotColors.default}
          `}
        />
      )}
      {children}
    </span>
  );
}
