import { useId } from 'react';
import { ChevronDown } from 'lucide-react';

/** Native disclosure section: keyboard-operable and styled with shared theme tokens. */
export default function CollapsibleSection({
  title,
  description,
  children,
  defaultOpen = false,
  className = '',
  contentClassName = '',
}) {
  const contentId = useId();

  return (
    <details
      className={`group border border-[var(--color-border-subtle)] bg-[var(--color-surface)] ${className}`}
      open={defaultOpen}
    >
      <summary
        aria-controls={contentId}
        className="flex cursor-pointer list-none items-center justify-between gap-3 px-4 py-3 text-sm font-semibold text-[var(--color-fg)] marker:hidden focus-visible:outline-2 focus-visible:outline-[var(--color-focus-ring)] focus-visible:outline-offset-[-2px]"
      >
        <span className="min-w-0">
          <span className="block">{title}</span>
          {description && <span className="mt-0.5 block text-xs font-normal text-[var(--color-fg-muted)]">{description}</span>}
        </span>
        <ChevronDown size={16} aria-hidden="true" className="shrink-0 text-[var(--color-fg-muted)] transition-transform group-open:rotate-180" />
      </summary>
      <div id={contentId} className={`border-t border-[var(--color-border-subtle)] px-4 py-3 ${contentClassName}`}>
        {children}
      </div>
    </details>
  );
}
