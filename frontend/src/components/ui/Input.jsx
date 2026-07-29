export default function Input({
  label,
  error,
  hint,
  icon,
  iconRight,
  className = '',
  ...props
}) {
  return (
    <div className="flex flex-col gap-1">
      {label && (
        <label className="text-xs font-bold uppercase tracking-wider text-[#2d2d2d]">
          {label}
          {props.required && <span className="text-[#c0392b] ml-1">*</span>}
        </label>
      )}
      <div className="relative">
        {icon && (
          <div className="absolute left-3 top-1/2 -translate-y-1/2 text-[#888]">
            {icon}
          </div>
        )}
        <input
          className={`
            w-full bg-white border-2 rounded-none text-sm text-[#2d2d2d] placeholder:text-[#aaa]
            px-4 py-2.5 outline-none transition-all duration-150
            focus:ring-0 focus:border-[#1A82B1]
            disabled:bg-[#f5f5f5] disabled:cursor-not-allowed disabled:text-[#999]
            ${error ? 'border-[#c0392b]' : 'border-[#d0d0d0] hover:border-[#aaa]'}
            ${icon ? 'pl-10' : ''}
            ${iconRight ? 'pr-10' : ''}
            ${className}
          `}
          {...props}
        />
        {iconRight && (
          <div className="absolute right-3 top-1/2 -translate-y-1/2 text-[#888]">
            {iconRight}
          </div>
        )}
      </div>
      {error && <p className="text-xs text-[#c0392b] font-semibold mt-0.5">{error}</p>}
      {hint && !error && <p className="text-xs text-[#999] mt-0.5">{hint}</p>}
    </div>
  );
}

export function Textarea({ label, error, hint, className = '', ...props }) {
  return (
    <div className="flex flex-col gap-1">
      {label && (
        <label className="text-xs font-bold uppercase tracking-wider text-[#2d2d2d]">
          {label}
          {props.required && <span className="text-[#c0392b] ml-1">*</span>}
        </label>
      )}
      <textarea
        className={`
          w-full bg-white border-2 rounded-none text-sm text-[#2d2d2d] placeholder:text-[#aaa]
          px-4 py-2.5 outline-none transition-all duration-150 resize-none
          focus:ring-0 focus:border-[#1A82B1]
          ${error ? 'border-[#c0392b]' : 'border-[#d0d0d0] hover:border-[#aaa]'}
          ${className}
        `}
        {...props}
      />
      {error && <p className="text-xs text-[#c0392b] font-semibold mt-0.5">{error}</p>}
      {hint && !error && <p className="text-xs text-[#999] mt-0.5">{hint}</p>}
    </div>
  );
}

export function Select({ label, error, hint, children, className = '', ...props }) {
  return (
    <div className="flex flex-col gap-1">
      {label && (
        <label className="text-xs font-bold uppercase tracking-wider text-[#2d2d2d]">
          {label}
          {props.required && <span className="text-[#c0392b] ml-1">*</span>}
        </label>
      )}
      <select
        className={`
          w-full bg-white border-2 rounded-none text-sm text-[#2d2d2d]
          px-4 py-2.5 outline-none transition-all duration-150 cursor-pointer
          focus:ring-0 focus:border-[#1A82B1]
          ${error ? 'border-[#c0392b]' : 'border-[#d0d0d0] hover:border-[#aaa]'}
          ${className}
        `}
        {...props}
      >
        {children}
      </select>
      {error && <p className="text-xs text-[#c0392b] font-semibold mt-0.5">{error}</p>}
      {hint && !error && <p className="text-xs text-[#999] mt-0.5">{hint}</p>}
    </div>
  );
}

export function Checkbox({ label, error, hint, className = '', ...props }) {
  return (
    <div className="flex flex-col gap-1">
      <label className="flex items-center gap-2 cursor-pointer group">
        <input
          type="checkbox"
          className={`
            w-4 h-4 border-2 rounded-none accent-[#1A82B1] cursor-pointer
            disabled:cursor-not-allowed
            ${error ? 'border-[#c0392b]' : 'border-[#d0d0d0]'}
            ${className}
          `}
          {...props}
        />
        {label && (
          <span className="text-sm text-[#2d2d2d] group-hover:text-[#1A82B1] transition-colors duration-150">
            {label}
            {props.required && <span className="text-[#c0392b] ml-1">*</span>}
          </span>
        )}
      </label>
      {error && <p className="text-xs text-[#c0392b] font-semibold mt-0.5">{error}</p>}
      {hint && !error && <p className="text-xs text-[#999] mt-0.5">{hint}</p>}
    </div>
  );
}

export function Radio({ label, error, hint, className = '', ...props }) {
  return (
    <div className="flex flex-col gap-1">
      <label className="flex items-center gap-2 cursor-pointer group">
        <input
          type="radio"
          className={`
            w-4 h-4 border-2 accent-[#1A82B1] cursor-pointer
            disabled:cursor-not-allowed
            ${error ? 'border-[#c0392b]' : 'border-[#d0d0d0]'}
            ${className}
          `}
          {...props}
        />
        {label && (
          <span className="text-sm text-[#2d2d2d] group-hover:text-[#1A82B1] transition-colors duration-150">
            {label}
            {props.required && <span className="text-[#c0392b] ml-1">*</span>}
          </span>
        )}
      </label>
      {error && <p className="text-xs text-[#c0392b] font-semibold mt-0.5">{error}</p>}
      {hint && !error && <p className="text-xs text-[#999] mt-0.5">{hint}</p>}
    </div>
  );
}