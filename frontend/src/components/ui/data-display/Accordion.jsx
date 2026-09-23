import { useState } from 'react';
import { ChevronDown } from 'lucide-react';

export function AccordionItem({ title, subtitle, children, defaultOpen = false }) {
    // État local et rendu d'une section repliable.
    const [open, setOpen] = useState(defaultOpen);

    return (
        <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] rounded-none">
            <button
                type="button"
                onClick={() => setOpen((v) => !v)}
                className="w-full px-3.5 py-2.5 flex items-center justify-between text-left hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer"
            >
                <div>
                    <p className="text-xs sm:text-[13px] font-bold text-[var(--color-fg)]">{title}</p>
                    {subtitle && <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">{subtitle}</p>}
                </div>
                <ChevronDown
                    size={14}
                    className={`text-[var(--color-fg-muted)] ${open ? 'rotate-180' : ''}`}
                />
            </button>
            {open && (
                <div className="px-3.5 py-3 border-t border-[var(--color-border-subtle)] text-xs text-[var(--color-fg)] space-y-1.5">
                    {children}
                </div>
            )}
        </div>
    );
}

export default AccordionItem;
