import { useState } from 'react';
import { Skeleton } from '@/components/ui/feedback/Skeleton';

export function SkeletonListRows({ count = 3 }) {
    return (
        <div className="divide-y divide-[var(--color-border-subtle)] py-1">
            {Array.from({ length: count }).map((_, index) => (
                <div key={index} className="flex items-center gap-3 py-3 px-1">
                    <Skeleton className="w-2 h-2 rounded-none shrink-0" />
                    <div className="flex-1 space-y-1 min-w-0">
                        <Skeleton className="h-3.5 w-1/3" />
                        <Skeleton className="h-2.5 w-1/2" />
                    </div>
                    <Skeleton className="h-5 w-14 shrink-0" />
                </div>
            ))}
        </div>
    );
}

export function ExpandableList({ items = [], renderItem, initialCount = 5, maxHeight = 'max-h-[300px]' }) {
    const [expanded, setExpanded] = useState(false);
    const visible = expanded ? items : items.slice(0, initialCount);
    const hidden = items.length - visible.length;

    return (
        <div className="space-y-1">
            <div className={`${expanded ? `${maxHeight} overflow-y-auto pr-1` : ''} divide-y divide-[var(--color-border-subtle)]`}>
                {visible.map(renderItem)}
            </div>
            {hidden > 0 && !expanded && (
                <button onClick={() => setExpanded(true)} className="w-full mt-2 py-2 text-xs font-bold uppercase tracking-wider text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] border border-dashed border-[var(--color-border)] transition-colors">
                    Voir {hidden} de plus (dérouler)
                </button>
            )}
            {expanded && items.length > initialCount && (
                <button onClick={() => setExpanded(false)} className="w-full mt-2 py-1.5 text-[11px] font-semibold text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] transition-colors text-center">
                    ▲ Réduire la liste
                </button>
            )}
        </div>
    );
}
