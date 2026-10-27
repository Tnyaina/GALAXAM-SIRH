import { forwardRef } from 'react';

/**
 * Card — composant de carte avec de bonnes valeurs par défaut.
 * Respecte strictement les tokens du design system (pas d'arrondi, tokens sémantiques).
 */

export const Card = forwardRef(function Card({
  as: Component = 'div',
  children,
  className = '',
  hover = false,
  padding = false,
  bordered = true,
  shadow = 'sm',
  ...props
}, ref) {
  const shadows = {
    none: '',
    sm: 'shadow-sm',
    md: 'shadow-md',
    lg: 'shadow-lg',
  };

  return (
    <Component
      ref={ref}
      className={[
        'bg-[var(--color-surface)] text-[var(--color-fg)] rounded-none',
        bordered ? 'border border-[var(--color-border-subtle)]' : '',
        shadows[shadow] ?? shadows.sm,
        hover ? '  hover:border-[var(--color-brand-500)] cursor-pointer' : '',
        padding ? 'p-4 sm:p-5' : '',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      {...props}
    >
      {children}
    </Component>
  );
});

export function CardHeader({
  children,
  className = '',
  divided = true,
}) {
  return (
    <div
      className={[
        'px-4 py-3',
        divided ? 'border-b border-[var(--color-border-subtle)]' : '',
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
    sm: 'text-xs sm:text-sm',
    base: 'text-sm sm:text-base',
    lg: 'text-base sm:text-lg',
    xl: 'text-lg sm:text-xl',
  };

  return (
    <h3
      className={[
        sizes[size] ?? sizes.base,
        'font-bold leading-snug tracking-tight',
        muted ? 'text-[var(--color-fg-muted)]' : 'text-[var(--color-fg)]',
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
    <p className={`mt-0.5 text-xs text-[var(--color-fg-muted)] ${className}`}>{children}</p>
  );
}

export function CardBody({
  children,
  className = '',
  flush = false,
}) {
  return (
    <div className={[flush ? '' : 'px-4 py-3 sm:px-5 sm:py-3.5', className].filter(Boolean).join(' ')}>
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
        'px-4 py-2.5',
        divided ? 'border-t border-[var(--color-border-subtle)]' : '',
        tinted ? 'bg-[var(--color-bg-subtle)]' : '',
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