import AuthenticatedImage from '@/components/ui/data-display/AuthenticatedImage';

/**
 * Avatar — Composant de photo de profil ou initiales employé.
 * `shape="round"` (défaut) : rounded-none — exception autorisée par le design system.
 * `shape="square"` : rounded-none — cohérent avec la règle d'or "PAS D'ARRONDI".
 * Dans les deux cas, la pastille de statut reste ronde (badge dot, toujours autorisé).
 */

const SIZES = {
    xs: 'w-5 h-5 text-[9px]',
    sm: 'w-7 h-7 text-[11px]',
    md: 'w-8 h-8 text-xs',
    lg: 'w-10 h-10 text-sm',
    xl: 'w-12 h-12 text-base',
};

const SHAPES = {
    round: 'rounded-none',
    square: 'rounded-none',
};

const STATUS_COLORS = {
    online: 'bg-[var(--color-success-500)]',
    offline: 'bg-[var(--color-fg-subtle)]',
    busy: 'bg-[var(--color-danger-500)]',
    away: 'bg-[var(--color-warning-500)]',
};

const STATUS_LABELS = {
    online: 'En ligne',
    offline: 'Hors ligne',
    busy: 'Occupé',
    away: 'Absent',
};

const STATUS_SIZES = {
    xs: 'w-1 h-1',
    sm: 'w-1.5 h-1.5',
    md: 'w-2 h-2',
    lg: 'w-2.5 h-2.5',
    xl: 'w-3 h-3',
};

// Calcul des initiales (ex: "Jean Dupont" -> "JD")
function getInitials(text) {
    if (!text) return '—';
    const parts = text.trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) return '—';
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export function Avatar({
    src,
    alt = '',
    name = '',
    size = 'sm', // 'xs' | 'sm' | 'md' | 'lg' | 'xl'
    shape = 'round', // 'round' | 'square'
    status, // 'online' | 'offline' | 'busy' | 'away'
    className = '',
}) {
    const shapeClass = SHAPES[shape] || SHAPES.round;

    const initials = (
        <div
            role="img"
            aria-label={name || 'Avatar'}
            className={`
            ${SIZES[size]} ${shapeClass} bg-[var(--color-brand-500)] text-white
            flex items-center justify-center font-bold font-data uppercase tracking-wider
            border border-[var(--color-brand-500)] select-none shadow-sm
          `}
        >
            {getInitials(name)}
        </div>
    );

    return (
        <div className={`relative inline-flex shrink-0 ${className}`}>
            <AuthenticatedImage
                src={src}
                alt={alt || name}
                loading="lazy"
                decoding="async"
                className={`${SIZES[size]} ${shapeClass} object-cover border border-[var(--color-border-subtle)]`}
                fallback={initials}
            />

            {status && (
                <span
                    role="img"
                    aria-label={STATUS_LABELS[status] || status}
                    className={`
            absolute bottom-0 right-0 rounded-none ring-2 ring-[var(--color-surface)]
            ${STATUS_SIZES[size]} ${STATUS_COLORS[status] || STATUS_COLORS.offline}
          `}
                />
            )}
        </div>
    );
}

export function AvatarGroup({ children, max = 4, size = 'sm', shape = 'round', className = '' }) {
    const childrenArray = Array.isArray(children) ? children : [children];
    const visibleAvatars = childrenArray.slice(0, max);
    const remainingCount = childrenArray.length - max;
    const shapeClass = SHAPES[shape] || SHAPES.round;
    const spacingClass = shape === 'square' ? '-space-x-1.5' : '-space-x-2';

    return (
        <div className={`flex items-center ${spacingClass} overflow-hidden ${className}`}>
            {visibleAvatars}
            {remainingCount > 0 && (
                <div
                    role="img"
                    aria-label={`+${remainingCount} autres`}
                    className={`
            ${SIZES[size]} ${shapeClass} bg-[var(--color-bg-subtle)]
            border-2 border-[var(--color-surface)] text-[var(--color-fg-muted)]
            font-data font-bold flex items-center justify-center select-none
          `}
                >
                    +{remainingCount}
                </div>
            )}
        </div>
    );
}
