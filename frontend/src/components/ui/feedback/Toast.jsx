import { createContext, useContext, useState, useCallback, useMemo } from 'react';
import { CheckCircle, XCircle, AlertTriangle, Info, X } from 'lucide-react';
import { cleanUserFacingMessage, getErrorMessage } from '@/api/apiClient';

const ToastContext = createContext(null);

const icons = {
  success: <CheckCircle size={17} className="text-[var(--color-success-500)] flex-shrink-0" />,
  error:   <XCircle size={17} className="text-[var(--color-danger-500)] flex-shrink-0" />,
  warning: <AlertTriangle size={17} className="text-[var(--color-warning-500)] flex-shrink-0" />,
  info:    <Info size={17} className="text-[var(--color-info-500)] flex-shrink-0" />,
};

const borders = {
  success: 'border-l-4 border-[var(--color-success-500)]',
  error:   'border-l-4 border-[var(--color-danger-500)]',
  warning: 'border-l-4 border-[var(--color-warning-500)]',
  info:    'border-l-4 border-[var(--color-info-500)]',
};

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const toastFn = useCallback(({ title, message, type = 'info', duration = 4000 }) => {
    let cleanMsg = '';
    if (typeof message === 'string') {
      cleanMsg = cleanUserFacingMessage(message, type === 'error' ? 'Une erreur est survenue.' : undefined);
    } else if (message instanceof Error || (message && (message.response || message.userMessage || message.message))) {
      cleanMsg = getErrorMessage(message, 'Une erreur est survenue.');
    } else if (message && typeof message === 'object') {
      cleanMsg = cleanUserFacingMessage(message.message || '', 'Une notification est disponible.');
    }

    const id = Date.now() + Math.random();
    setToasts(prev => [...prev, { id, title, message: cleanMsg, type }]);
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), duration);
  }, []);

  const toast = useMemo(() => {
    const normalizePayload = (msg, title, type) => {
      let resolvedMsg = msg;
      let resolvedTitle = title;
      if (typeof msg === 'string') {
        resolvedMsg = cleanUserFacingMessage(msg);
      } else if (msg instanceof Error || (msg && (msg.response || msg.userMessage))) {
        resolvedMsg = getErrorMessage(msg);
        resolvedTitle = title || (type === 'error' ? 'Erreur' : undefined);
      } else if (msg && typeof msg === 'object') {
        resolvedMsg = msg.message;
        resolvedTitle = title || msg.title;
      }
      return { type, message: resolvedMsg, title: resolvedTitle };
    };

    const t = Object.assign(
      (args) => {
        if (typeof args === 'string') {
          return toastFn({ message: args, type: 'info' });
        }
        return toastFn(args || {});
      },
      {
        success: (msg, title) => toastFn(normalizePayload(msg, title, 'success')),
        error: (msg, title) => toastFn(normalizePayload(msg, title, 'error')),
        warning: (msg, title) => toastFn(normalizePayload(msg, title, 'warning')),
        info: (msg, title) => toastFn(normalizePayload(msg, title, 'info'))
      }
    );
    t.toast = t;
    return t;
  }, [toastFn]);

  const remove = (id) => setToasts(prev => prev.filter(t => t.id !== id));

  return (
    <ToastContext.Provider value={toast}>
      {children}
      <div className="fixed top-5 right-5 z-[100] flex flex-col gap-2 w-80">
        {toasts.map(t => (
          <div
            key={t.id}
            className={`
              bg-[var(--color-surface)] border border-[var(--color-border-subtle)]
              rounded-none shadow-lg ${borders[t.type] || borders.info}
              p-4 flex items-start gap-3   animate-in slide-in-from-right
            `}
          >
            {icons[t.type] || icons.info}
            <div className="flex-1 min-w-0">
              {t.title && <p className="text-sm font-semibold text-[var(--color-fg)]">{t.title}</p>}
              {t.message && <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">{t.message}</p>}
            </div>
            <button
              onClick={() => remove(t.id)}
              className="p-0.5 text-[var(--color-fg-subtle)] hover:text-[var(--color-fg)]  rounded-none"
              aria-label="Fermer la notification"
            >
              <X size={15} />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside ToastProvider');
  return ctx;
}