import { useState } from 'react';
import { ChevronDown } from 'lucide-react';

export function AccordionItem({ title, subtitle, children, defaultOpen = false }) {
    const [open, setOpen] = useState(defaultOpen);

    return (
        <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] rounded-none">
            <button
                type="button"
                onClick={() => setOpen((v) => !v)}
                className="w-full px-5 py-3.5 flex items-center justify-between text-left hover:bg-[var(--color-bg-subtle)] transition-colors rounded-none cursor-pointer"
            >
                <div>
                    <p className="text-sm font-bold text-[var(--color-fg)]">{title}</p>
                    {subtitle && <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">{subtitle}</p>}
                </div>
                <ChevronDown
                    size={16}
                    className={`text-[var(--color-fg-muted)] transition-transform duration-200 ${open ? 'rotate-180' : ''}`}
                />
            </button>
            {open && (
                <div className="px-5 py-4 border-t border-[var(--color-border-subtle)] text-xs text-[var(--color-fg)] space-y-2">
                    {children}
                </div>
            )}
        </div>
    );
}

export default AccordionItem;
