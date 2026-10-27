import { ChevronRight } from 'lucide-react';
import { Link } from 'react-router-dom';

export function Breadcrumb({ items = [] }) {
    return (
        <nav aria-label="Breadcrumb" className="flex items-center gap-1.5 text-xs text-[var(--color-fg-muted)]">
            {items.map((item, index) => {
                const isLast = index === items.length - 1;
                const destination = item.to || item.href;
                return (
                    <div key={index} className="flex items-center gap-1.5">
                        {index > 0 && <ChevronRight size={12} className="text-[var(--color-fg-subtle)] shrink-0" />}
                        {isLast || !destination ? (
                            <span className={`font-semibold ${isLast ? 'text-[var(--color-fg)]' : ''}`}>
                                {item.label}
                            </span>
                        ) : (
                            <Link
                                to={destination}
                                className="hover:text-[var(--color-brand-500)] underline-offset-2 hover:underline transition-colors"
                            >
                                {item.label}
                            </Link>
                        )}
                    </div>
                );
            })}
        </nav>
    );
}

export default Breadcrumb;
