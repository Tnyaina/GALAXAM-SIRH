import { X } from 'lucide-react';

const widths = {
    md: 'max-w-md',
    lg: 'max-w-xl',
    xl: 'max-w-2xl',
    '2xl': 'max-w-3xl',
};

/**
 * SlideOver — panneau latéral plein écran (droite).
 * Utilise strictement le Design System Galaxam (bordures nettes, tokens sémantiques).
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
            <div className="absolute inset-0 bg-black/60 backdrop-blur-[2px]" onClick={onClose} />

            <div
                className={`
                    relative w-full ${widths[width]} h-full flex flex-col rounded-none
                    bg-[var(--color-surface)] border-l border-[var(--color-border)] shadow-xl
                `}
            >
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-[var(--color-border-subtle)] shrink-0">
                    <div className="flex items-center gap-3 min-w-0">
                        {icon && (
                            <div className="w-9 h-9 flex items-center justify-center bg-[var(--color-brand-500)]/10 text-[var(--color-brand-500)] shrink-0 rounded-none border border-[var(--color-brand-500)]/20">
                                {icon}
                            </div>
                        )}
                        <div className="min-w-0">
                            {title && (
                                <h2 className="text-base font-bold text-[var(--color-fg)] truncate">
                                    {title}
                                </h2>
                            )}
                            {subtitle && (
                                <p className="text-xs text-[var(--color-fg-muted)] truncate">{subtitle}</p>
                            )}
                        </div>
                    </div>
                    <button
                        onClick={onClose}
                        className="p-2 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] transition-colors shrink-0 rounded-none"
                    >
                        <X size={18} />
                    </button>
                </div>

                {/* Body */}
                <div className="flex-1 overflow-y-auto p-6">{children}</div>

                {/* Footer */}
                {footer && (
                    <div className="px-6 py-4 bg-[var(--color-bg-subtle)] border-t border-[var(--color-border-subtle)] flex items-center justify-between gap-3 shrink-0">
                        {footer}
                    </div>
                )}
            </div>
        </div>
    );
}