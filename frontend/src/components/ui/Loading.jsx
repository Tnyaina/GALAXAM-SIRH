const variants = {
    blue: 'text-blue-500',
    purple: 'text-violet-500',
    green: 'text-emerald-500',
    amber: 'text-amber-500',
    red: 'text-red-500',
};

const sizes = {
    sm: { spinner: 'w-4 h-4 border-[1.5px]', text: 'text-xs', dots: 'w-1.5 h-1.5' },
    md: { spinner: 'w-[18px] h-[18px] border-2', text: 'text-sm', dots: 'w-2 h-2' },
    lg: { spinner: 'w-6 h-6 border-2', text: 'text-base', dots: 'w-2.5 h-2.5' },
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
        <div className={`flex items-center gap-1 flex-shrink-0 ${colorClass}`}>
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
 * @param {'spinner'|'dots'}                     type    - Animation style (default: 'spinner')
 * @param {'blue'|'purple'|'green'|'amber'|'red'} color  - Color variant (default: 'blue')
 * @param {'sm'|'md'|'lg'}                        size   - Size variant (default: 'md')
 * @param {string}                                label  - Optional text label
 * @param {'inline'|'pill'|'overlay'}             display - Layout mode (default: 'inline')
 *
 * Usage:
 *   <Loading />
 *   <Loading type="dots" color="purple" label="Traitement en cours" />
 *   <Loading display="pill" label="Envoi en cours..." />
 *   <Loading display="overlay" size="lg" label="Chargement des données..." />
 */
export default function Loading({
    type = 'spinner',
    color = 'blue',
    size = 'md',
    label,
    display = 'inline',
}) {
    const colorClass = variants[color] ?? variants.blue;
    const s = sizes[size] ?? sizes.md;

    const icon =
        type === 'dots' ? (
            <Dots colorClass={colorClass} dotClass={s.dots} />
        ) : (
            <Spinner colorClass={colorClass} sizeClass={s.spinner} />
        );

    if (display === 'overlay') {
        return (
            <div className="flex flex-col items-center justify-center gap-2">
                {icon}
                {label && <p className={`text-gray-500 ${s.text}`}>{label}</p>}
            </div>
        );
    }

    if (display === 'pill') {
        return (
            <span className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full border border-gray-200 bg-gray-50 text-gray-500 text-sm">
                {icon}
                {label}
            </span>
        );
    }

    // inline (default)
    return (
        <div className="flex items-center gap-2.5">
            {icon}
            {label && <p className={`text-gray-500 ${s.text}`}>{label}</p>}
        </div>
    );
}