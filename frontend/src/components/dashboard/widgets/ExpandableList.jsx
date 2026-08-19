import { useState } from 'react';

export default function ExpandableList({ items, renderItem, initialCount = 5 }) {
    const [expanded, setExpanded] = useState(false);
    const visible = expanded ? items : items.slice(0, initialCount);
    const hidden = items.length - visible.length;

    return (
        <div className="divide-y divide-[var(--color-border-subtle)]">
            {visible.map(renderItem)}
            {hidden > 0 && (
                <button
                    onClick={() => setExpanded(true)}
                    className="w-full mt-2 py-2.5 min-h-11 text-xs font-bold uppercase tracking-wider text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] "
                >
                    Voir {hidden} de plus
                </button>
            )}
        </div>
    );
}
