/**
 * Avatar — Composant de photo de profil ou initiales employé.
 * L'avatar rond est l'une des rares exceptions autorisées avec `rounded-full`.
 */
export function Avatar({
    src,
    alt = '',
    name = '',
    size = 'md', // 'xs' | 'sm' | 'md' | 'lg' | 'xl'
    status, // 'online' | 'offline' | 'busy' | 'away'
    className = '',
}) {
    const sizes = {
        xs: 'w-6 h-6 text-[10px]',
        sm: 'w-8 h-8 text-xs',
        md: 'w-10 h-10 text-sm',
        lg: 'w-12 h-12 text-base',
        xl: 'w-16 h-16 text-lg',
    };

    const statusColors = {
        online: 'bg-[var(--color-success-500)]',
        offline: 'bg-[var(--color-fg-subtle)]',
        busy: 'bg-[var(--color-danger-500)]',
        away: 'bg-[var(--color-warning-500)]',
    };

    const statusSizes = {
        xs: 'w-1.5 h-1.5',
        sm: 'w-2 h-2',
        md: 'w-2.5 h-2.5',
        lg: 'w-3 h-3',
        xl: 'w-3.5 h-3.5',
    };

    // Calcul des initiales (ex: "Jean Dupont" -> "JD")
    const getInitials = (text) => {
        if (!text) return '—';
        const parts = text.trim().split(/\s+/);
        if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
        return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
    };

    return (
        <div className={`relative inline-flex shrink-0 ${className}`}>
            {src ? (
                <img
                    src={src}
                    alt={alt || name}
                    className={`${sizes[size]} rounded-full object-cover border border-[var(--color-border-subtle)]`}
                />
            ) : (
                <div
                    className={`
            ${sizes[size]} rounded-full bg-[var(--color-brand-500)] text-white
            flex items-center justify-center font-bold font-data uppercase tracking-wider
            border border-[var(--color-brand-500)] select-none shadow-sm
          `}
                >
                    {getInitials(name)}
                </div>
            )}

            {status && (
                <span
                    className={`
            absolute bottom-0 right-0 rounded-full ring-2 ring-[var(--color-surface)]
            ${statusSizes[size]} ${statusColors[status] || statusColors.offline}
          `}
                />
            )}
        </div>
    );
}

export function AvatarGroup({ children, max = 4, className = '' }) {
    const childrenArray = Array.isArray(children) ? children : [children];
    const visibleAvatars = childrenArray.slice(0, max);
    const remainingCount = childrenArray.length - max;

    return (
        <div className={`flex items-center -space-x-2 overflow-hidden ${className}`}>
            {visibleAvatars}
            {remainingCount > 0 && (
                <div className="w-8 h-8 rounded-full bg-[var(--color-bg-subtle)] border-2 border-[var(--color-surface)] text-[var(--color-fg-muted)] font-data font-bold text-xs flex items-center justify-center select-none">
                    +{remainingCount}
                </div>
            )}
        </div>
    );
}