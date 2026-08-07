import { X } from 'lucide-react';

const widths = {
    md: 'max-w-md',
    lg: 'max-w-xl',
    xl: 'max-w-2xl',
    '2xl': 'max-w-3xl',
};

/**
 * SlideOver — panneau latéral plein écran (droite), pour les flux qui
 * méritent plus d'air qu'une modale centrée classique (formulaires longs,
 * assistants multi-étapes...). Même socle visuel que Modal (bordures nettes,
 * pas d'arrondi), mais pensé pour du contenu plus dense.
 */
export default function SlideOver({
    isOpen,
    onClose,
    title,
    subtitle,
    icon,
    width = 'xl',
    children,
    footer,
}) {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex justify-end">
            <div className="absolute inset-0 bg-black/50 backdrop-blur-[1px]" onClick={onClose} />

            <div
                className={`
                    relative w-full ${widths[width]} h-full flex flex-col
                    bg-white dark:bg-ink-900 border-l border-ink-150 dark:border-ink-700 shadow-lg
                `}
            >
                <div className="flex items-center justify-between px-6 py-4 border-b border-ink-150 dark:border-ink-700 shrink-0">
                    <div className="flex items-center gap-3 min-w-0">
                        {icon && (
                            <div className="w-9 h-9 flex items-center justify-center bg-brand-50 dark:bg-brand-900/30 text-brand-500 shrink-0">
                                {icon}
                            </div>
                        )}
                        <div className="min-w-0">
                            {title && (
                                <h2 className="text-base font-bold text-ink-800 dark:text-ink-50 truncate">
                                    {title}
                                </h2>
                            )}
                            {subtitle && (
                                <p className="text-xs text-ink-400 truncate">{subtitle}</p>
                            )}
                        </div>
                    </div>
                    <button
                        onClick={onClose}
                        className="p-2 text-ink-400 hover:text-ink-800 dark:hover:text-white hover:bg-ink-100 dark:hover:bg-ink-800 transition-colors shrink-0"
                    >
                        <X size={18} />
                    </button>
                </div>

                <div className="flex-1 overflow-y-auto">{children}</div>

                {footer && (
                    <div className="px-6 py-4 border-t border-ink-150 dark:border-ink-700 flex items-center justify-between gap-3 shrink-0">
                        {footer}
                    </div>
                )}
            </div>
        </div>
    );
}