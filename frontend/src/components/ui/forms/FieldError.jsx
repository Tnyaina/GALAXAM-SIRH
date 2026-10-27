import { AlertCircle } from 'lucide-react';

/** Message de validation rattaché à un champ, sans apparence de bannière globale. */
export default function FieldError({ id, children, className = '' }) {
  if (!children) return null;

  return (
    <p id={id} role="alert" className={`flex items-start gap-1 text-[11px] font-semibold text-[var(--color-danger-600)] dark:text-[var(--color-danger-300)] ${className}`}>
      <AlertCircle size={13} className="mt-0.5 shrink-0" aria-hidden="true" />
      <span>{children}</span>
    </p>
  );
}
