import { useId, useState } from 'react';
import { Info } from 'lucide-react';
import Alert from './Alert';

export default function InfoDisclosure({ label = 'Informations', message, children, className = '' }) {
  const [open, setOpen] = useState(false);
  const contentId = useId();

  return (
    <div className={className}>
      <button
        type="button"
        aria-expanded={open}
        aria-controls={contentId}
        onClick={() => setOpen(value => !value)}
        className="inline-flex items-center gap-1.5 px-2 py-1 text-xs font-medium text-[var(--color-info-600)] hover:bg-[var(--color-info-500)]/10 focus-visible:outline-2 focus-visible:outline-[var(--color-focus-ring)]"
      >
        <Info size={14} aria-hidden="true" />
        {label}
      </button>
      {open && <div id={contentId} className="mt-1"><Alert type="info" message={message}>{children}</Alert></div>}
    </div>
  );
}
