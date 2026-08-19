import { ChevronRight } from 'lucide-react';

export function Breadcrumb({ items = [] }) {
    return (
        <nav aria-label="Breadcrumb" className="flex items-center gap-1.5 text-xs text-[var(--color-fg-muted)]">
            {items.map((item, index) => {
                const isLast = index === items.length - 1;
                return (
                    <div key={index} className="flex items-center gap-1.5">
                        {index > 0 && <ChevronRight size={12} className="text-[var(--color-fg-subtle)] shrink-0" />}
                        {isLast || !item.href ? (
                            <span className={`font-semibold ${isLast ? 'text-[var(--color-fg)] font-data' : ''}`}>
                                {item.label}
                            </span>
                        ) : (
                            <a
                                href={item.href}
                                className="hover:text-[var(--color-brand-500)]  underline-offset-2 hover:underline"
                            >
                                {item.label}
                            </a>
                        )}
                    </div>
                );
            })}
        </nav>
    );
}

export default Breadcrumb;
