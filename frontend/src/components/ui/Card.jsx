/**
 * Card — composant de carte avec de bonnes valeurs par défaut.
 * Respecte strictement les tokens de design system (ink-*, pas de slate).
 */

export function Card({
  children,
  className = '',
  hover = false,
  padding = false,
  bordered = true,
  shadow = 'sm',
  ...props
}) {
  const shadows = {
    none: '',
    sm: 'shadow-sm',
    md: 'shadow-md',
    lg: 'shadow-lg',
  };

  return (
    <div
      className={[
        'bg-white dark:bg-ink-900',
        bordered ? 'border border-ink-150 dark:border-ink-700' : '',
        shadows[shadow] ?? shadows.sm,
        hover ? 'transition-shadow hover:shadow-md cursor-pointer' : '',
        padding ? 'p-6' : '',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      {...props}
    >
      {children}
    </div>
  );
}

export function CardHeader({
  children,
  className = '',
  divided = true,
}) {
  return (
    <div
      className={[
        'px-6 py-4',
        divided ? 'border-b border-ink-150 dark:border-ink-700' : '',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {children}
    </div>
  );
}

export function CardTitle({
  children,
  className = '',
  size = 'base',
  muted = false,
}) {
  const sizes = {
    sm: 'text-sm',
    base: 'text-base',
    lg: 'text-lg',
    xl: 'text-xl',
  };

  return (
    <h3
      className={[
        sizes[size] ?? sizes.base,
        'font-bold leading-snug',
        muted ? 'text-ink-400' : 'text-ink-900 dark:text-white',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {children}
    </h3>
  );
}

export function CardSubtitle({ children, className = '' }) {
  return (
    <p className={`mt-0.5 text-xs text-ink-400 ${className}`}>{children}</p>
  );
}

export function CardBody({
  children,
  className = '',
  flush = false,
}) {
  return (
    <div className={[flush ? '' : 'px-6 py-4', className].filter(Boolean).join(' ')}>
      {children}
    </div>
  );
}

export function CardFooter({
  children,
  className = '',
  divided = true,
  tinted = true,
}) {
  return (
    <div
      className={[
        'px-6 py-3',
        divided ? 'border-t border-ink-150 dark:border-ink-700' : '',
        tinted ? 'bg-ink-50 dark:bg-ink-950' : '',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {children}
    </div>
  );
}

export function CardImage({ src, alt = '', className = '' }) {
  return (
    <img
      src={src}
      alt={alt}
      className={`w-full object-cover ${className}`}
    />
  );
}

export default Card;