const variants = {
  default: 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border border-[var(--color-border-subtle)]',
  primary: 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-500)] border border-[var(--color-brand-500)]/20',
  success: 'bg-[var(--color-success-500)]/10 text-[var(--color-success-500)] border border-[var(--color-success-500)]/20',
  warning: 'bg-[var(--color-warning-500)]/10 text-[var(--color-warning-500)] border border-[var(--color-warning-500)]/20',
  danger:  'bg-[var(--color-danger-500)]/10 text-[var(--color-danger-500)] border border-[var(--color-danger-500)]/20',
  info:    'bg-[var(--color-info-500)]/10 text-[var(--color-info-500)] border border-[var(--color-info-500)]/20',
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