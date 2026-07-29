const variants = {
  primary: 'bg-[#2d2d2d] hover:bg-[#1a9ab8] text-white border border-[#2d2d2d] hover:border-[#1a9ab8]',
  secondary: 'bg-[#2d2d2d] hover:bg-[#3a3a3a] text-white border border-[#2d2d2d]',
  outline: 'border border-[#2d2d2d] text-[#2d2d2d] hover:bg-[#2d2d2d] hover:text-white bg-transparent',
  ghost: 'hover:bg-[#f0f0f0] text-[#2d2d2d] border border-transparent',
  danger: 'bg-[#c0392b] hover:bg-[#a93226] text-white border border-[#c0392b]',
  success: 'bg-[#27ae60] hover:bg-[#1e8449] text-white border border-[#27ae60]',
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
        focus:outline-none focus:ring-2 focus:ring-[#1A82B1]/40 focus:ring-offset-0
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