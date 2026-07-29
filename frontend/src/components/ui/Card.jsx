/**
 * Card — composant de carte avec de bonnes valeurs par défaut.
 * Utilisation minimale : <Card><CardHeader><CardTitle>Titre</CardTitle></CardHeader><CardBody>Contenu</CardBody></Card>
 */

export function Card({
  children,
  className = '',
  hover = false,
  padding = false,     // active le padding direct sur la Card (sans CardBody)
  bordered = true,     // bordure visible
  shadow = 'sm',       // 'none' | 'sm' | 'md' | 'lg'
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
        'bg-white',
        bordered ? 'border border-slate-200' : '',
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
  divided = true,      // séparateur bas par défaut
}) {
  return (
    <div
      className={[
        'px-6 py-4',
        divided ? 'border-b border-slate-200' : '',
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
  size = 'base',       // 'sm' | 'base' | 'lg' | 'xl'
  muted = false,       // texte secondaire
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
        'font-semibold leading-snug',
        muted ? 'text-slate-500' : 'text-slate-900',
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
    <p className={`mt-0.5 text-sm text-slate-500 ${className}`}>{children}</p>
  );
}

export function CardBody({
  children,
  className = '',
  flush = false,       // flush=true enlève le padding (ex: image plein format)
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
  divided = true,      // séparateur haut par défaut
  tinted = true,       // fond légèrement grisé par défaut
}) {
  return (
    <div
      className={[
        'px-6 py-3',
        divided ? 'border-t border-slate-200' : '',
        tinted ? 'bg-slate-50' : '',
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