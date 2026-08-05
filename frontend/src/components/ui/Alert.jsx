import { CheckCircle, AlertTriangle, XCircle, Info, X } from 'lucide-react';

const types = {
  success: {
    wrapper: 'bg-emerald-50 border-emerald-200',
    icon: <CheckCircle size={18} className="text-emerald-500 flex-shrink-0 mt-0.5" />,
    title: 'text-emerald-800',
    text: 'text-emerald-700',
  },
  warning: {
    wrapper: 'bg-amber-50 border-amber-200',
    icon: <AlertTriangle size={18} className="text-amber-500 flex-shrink-0 mt-0.5" />,
    title: 'text-amber-800',
    text: 'text-amber-700',
  },
  error: {
    wrapper: 'bg-red-50 border-red-200',
    icon: <XCircle size={18} className="text-red-500 flex-shrink-0 mt-0.5" />,
    title: 'text-red-800',
    text: 'text-red-700',
  },
  info: {
    wrapper: 'bg-blue-50 border-blue-200',
    icon: <Info size={18} className="text-[#2563EB] flex-shrink-0 mt-0.5" />,
    title: 'text-blue-900',
    text: 'text-blue-700',
  },
};

export default function Alert({ type = 'info', title, children, onClose }) {
  const t = types[type];
  return (
    <div className={`flex items-start gap-3 p-4 rounded-xl border ${t.wrapper}`}>
      {t.icon}
      <div className="flex-1 min-w-0">
        {title && <p className={`text-sm font-semibold ${t.title}`}>{title}</p>}
        {children && <p className={`text-sm mt-0.5 ${t.text}`}>{children}</p>}
      </div>
      {onClose && (
        <button onClick={onClose} className={`p-0.5 rounded hover:bg-black/5 ${t.text} flex-shrink-0`}>
          <X size={15} />
        </button>
      )}
    </div>
  );
}
