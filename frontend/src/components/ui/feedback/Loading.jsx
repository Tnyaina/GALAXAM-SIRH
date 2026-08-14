const variants = {
    brand:   'text-[var(--color-brand-500)]',
    info:    'text-[var(--color-info-500)]',
    success: 'text-[var(--color-success-500)]',
    warning: 'text-[var(--color-warning-500)]',
    danger:  'text-[var(--color-danger-500)]',
};

const sizes = {
    sm: { spinner: 'w-4 h-4 border-[1.5px]', text: 'text-xs', dots: 'w-1.5 h-1.5' },
    md: { spinner: 'w-[20px] h-[20px] border-2', text: 'text-sm', dots: 'w-2 h-2' },
    lg: { spinner: 'w-8 h-8 border-2', text: 'text-base', dots: 'w-2.5 h-2.5' },
    xl: { spinner: 'w-12 h-12 border-3', text: 'text-lg', dots: 'w-3 h-3' },
};

function Spinner({ colorClass, sizeClass }) {
    return (
        <div
            className={`rounded-full border-transparent border-t-current animate-spin flex-shrink-0 ${colorClass} ${sizeClass}`}
        />
    );
}

function Dots({ colorClass, dotClass }) {
    return (
        <div className={`flex items-center gap-1.5 flex-shrink-0 ${colorClass}`}>
            {[0, 1, 2].map((i) => (
                <div
                    key={i}
                    className={`rounded-full bg-current animate-pulse ${dotClass}`}
                    style={{ animationDelay: `${i * 0.2}s` }}
                />
            ))}
        </div>
    );
}

/**
 * Loading component
 *
 * @param {'spinner'|'dots'}                                  type    - Animation style (default: 'spinner')
 * @param {'brand'|'info'|'success'|'warning'|'danger'}       color   - Color variant (default: 'brand')
 * @param {'sm'|'md'|'lg'|'xl'}                               size    - Size variant (default: 'md')
 * @param {string}                                            label   - Texte optionnel de chargement
 * @param {'inline'|'pill'|'overlay'|'page'|'fullscreen'}      display - Mode d'affichage (default: 'inline')
 */
export default function Loading({
    type = 'spinner',
    color = 'brand',
    size = 'md',
    label,
    display = 'inline',
}) {
    const colorClass = variants[color] ?? variants.brand;
    const s = sizes[size] ?? sizes.md;

    const icon =
        type === 'dots' ? (
            <Dots colorClass={colorClass} dotClass={s.dots} />
        ) : (
            <Spinner colorClass={colorClass} sizeClass={s.spinner} />
        );

    // 1. Rempli tout le conteneur principal/page et centré au milieu
    if (display === 'page') {
        return (
            <div className="w-full min-h-[350px] flex-1 flex flex-col items-center justify-center gap-3 p-8">
                {icon}
                {label && (
                    <p className={`text-[var(--color-fg-muted)] font-medium tracking-wide ${s.text}`}>
                        {label}
                    </p>
                )}
            </div>
        );
    }

    // 2. Plein écran global au-dessus de tout
    if (display === 'fullscreen') {
        return (
            <div className="fixed inset-0 z-50 flex flex-col items-center justify-center gap-3 bg-[var(--color-bg)]/80 backdrop-blur-[2px]">
                {icon}
                {label && (
                    <p className={`text-[var(--color-fg)] font-semibold tracking-wide ${s.text}`}>
                        {label}
                    </p>
                )}
            </div>
        );
    }

    // 3. Overlay centré sur la carte / section parente
    if (display === 'overlay') {
        return (
            <div className="flex flex-col items-center justify-center gap-2 p-4">
                {icon}
                {label && <p className={`text-[var(--color-fg-muted)] ${s.text}`}>{label}</p>}
            </div>
        );
    }

    // 4. Pill compact
    if (display === 'pill') {
        return (
            <span className="inline-flex items-center gap-2 px-3 py-1.5 rounded-none border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] text-xs font-semibold uppercase tracking-wider">
                {icon}
                {label}
            </span>
        );
    }

    // 5. Inline (default)
    return (
        <div className="flex items-center gap-2.5">
            {icon}
            {label && <p className={`text-[var(--color-fg-muted)] ${s.text}`}>{label}</p>}
        </div>
    );
}