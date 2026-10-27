import { X, XCircle } from 'lucide-react';

/** Erreur de soumission/action à afficher dans un formulaire, distincte d'une alerte de page. */
export default function FormError({ title, message, children, onClose, className = '' }) {
  const content = children || message;
  if (!content) return null;

  return (
    <div
      role="alert"
      aria-live="assertive"
      aria-atomic="true"
      className={`flex items-start gap-2 border-s-2 border-[var(--color-danger-500)] bg-[var(--color-danger-50)] px-3 py-2 text-xs text-[var(--color-danger-800)] dark:border-[var(--color-danger-400)] dark:bg-[var(--color-danger-950)] dark:text-[var(--color-danger-100)] ${className}`}
    >
      <XCircle size={15} className="mt-0.5 shrink-0 text-[var(--color-danger-600)] dark:text-[var(--color-danger-300)]" aria-hidden="true" />
      <div className="min-w-0 flex-1">
        {title && <p className="font-bold">{title}</p>}
        <div>{content}</div>
      </div>
      {onClose && (
        <button type="button" onClick={onClose} className="shrink-0 p-0.5 text-current hover:bg-[var(--color-danger-100)] dark:hover:bg-[var(--color-danger-900)]" aria-label="Fermer le message d’erreur">
          <X size={14} />
        </button>
      )}
    </div>
  );
}
