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

export default function Alert({ type = 'info', title, message, children, onClose }) {
  const t = types[type] || types.info;
  const content = children || message;

  return (
    <div className={`flex items-start gap-2.5 p-3 rounded-none border ${t.wrapper}`}>
      {t.icon}
      <div className="flex-1 min-w-0">
        {title && <p className={`text-xs sm:text-[13px] font-bold ${t.title}`}>{title}</p>}
        {content && <p className={`text-xs sm:text-[13px] mt-0.5 ${t.text}`}>{content}</p>}
      </div>
      {onClose && (
        <button
          onClick={onClose}
          className={`p-0.5 rounded-none hover:bg-black/5 dark:hover:bg-white/10 ${t.text} flex-shrink-0 cursor-pointer`}
          aria-label="Fermer l'alerte"
        >
          <X size={14} />
        </button>
      )}
    </div>
  );
}