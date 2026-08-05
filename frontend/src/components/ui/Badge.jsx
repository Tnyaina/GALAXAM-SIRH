const variants = {
  default: 'bg-slate-100 text-slate-600',
  primary: 'bg-[#dbeafe] text-[#2563EB]',
  success: 'bg-emerald-50 text-emerald-600',
  warning: 'bg-amber-50 text-amber-600',
  danger: 'bg-red-50 text-red-500',
  purple: 'bg-purple-50 text-purple-600',
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
  success: 'bg-emerald-500',
  warning: 'bg-amber-500',
  danger: 'bg-red-500',
  primary: 'bg-[#2563EB]',
  default: 'bg-slate-400',
  purple: 'bg-purple-500',
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
  return (
    <span
      style={style}
      {...props}
      className={`
        inline-flex items-center gap-1.5 font-semibold
        ${rounded ? 'rounded-full' : 'rounded-none'}
        ${variants[variant]}
        ${sizes[size]}
        ${className}
      `}
    >
      {dot && (
        <span
          className={`
            ${dotSizes[size]}
            ${rounded ? 'rounded-full' : 'rounded-none'}
            ${dotColors[variant] ?? dotColors.default}
          `}
        />
      )}
      {children}
    </span>
  );
}