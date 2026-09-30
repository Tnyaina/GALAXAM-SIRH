import { CheckCircle, AlertTriangle, XCircle, Info, X } from 'lucide-react';

const types = {
  success: {
    wrapper: 'bg-[var(--color-success-500)]/10 border-[var(--color-success-500)]/30',
    icon: <CheckCircle size={18} className="text-[var(--color-success-500)] flex-shrink-0 mt-0.5" />,
    title: 'text-[var(--color-success-500)]',
    text: 'text-[var(--color-fg)]',
  },
  warning: {
    wrapper: 'bg-[var(--color-warning-500)]/10 border-[var(--color-warning-500)]/30',
    icon: <AlertTriangle size={18} className="text-[var(--color-warning-500)] flex-shrink-0 mt-0.5" />,
    title: 'text-[var(--color-warning-500)]',
    text: 'text-[var(--color-fg)]',
  },
  error: {
    wrapper: 'bg-[var(--color-danger-500)]/10 border-[var(--color-danger-500)]/30',
    icon: <XCircle size={18} className="text-[var(--color-danger-500)] flex-shrink-0 mt-0.5" />,
    title: 'text-[var(--color-danger-500)]',
    text: 'text-[var(--color-fg)]',
  },
  info: {
    wrapper: 'bg-[var(--color-info-500)]/10 border-[var(--color-info-500)]/30',
    icon: <Info size={18} className="text-[var(--color-info-500)] flex-shrink-0 mt-0.5" />,
    title: 'text-[var(--color-info-500)]',
    text: 'text-[var(--color-fg)]',
  },
};

export default function Alert({ type, variant, title, message, children, onClose, className = '' }) {
  const severity = variant || type || 'info';
  const normalizedSeverity = severity === 'danger' ? 'error' : severity;
  const t = types[normalizedSeverity] || types.info;
  const content = children || message;

  return (
    <div
      role={normalizedSeverity === 'error' ? 'alert' : 'status'}
      aria-live={normalizedSeverity === 'error' ? 'assertive' : 'polite'}
      aria-atomic="true"
      className={`flex items-start gap-2.5 p-3 rounded-none border ${t.wrapper} ${className}`}
    >
      {t.icon}
      <div className="flex-1 min-w-0">
        {title && <p className={`text-xs sm:text-[13px] font-bold ${t.title}`}>{title}</p>}
        {content && <div className={`text-xs sm:text-[13px] mt-0.5 ${t.text}`}>{content}</div>}
      </div>
      {onClose && (
        <button
          type="button"
          onClick={onClose}
          className={`p-0.5 rounded-none hover:bg-[var(--color-overlay)]/5 dark:hover:bg-[var(--color-surface)]/10 ${t.text} flex-shrink-0 cursor-pointer`}
          aria-label="Fermer l'alerte"
        >
          <X size={14} />
        </button>
      )}
    </div>
  );
}
