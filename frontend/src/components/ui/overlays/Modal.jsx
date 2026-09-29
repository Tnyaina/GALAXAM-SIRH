import { useEffect } from 'react';
import { X } from 'lucide-react';

/**
 * Modal — Modale standardisée pour le Design System Galaxam.
 * Fermeture sur Échap, clic extérieur, dimensions strictes, fond sémantique.
 */
export default function Modal({
    // Effets d'accessibilité, actions de fermeture et rendu de la modale.
    isOpen,
    onClose,
    title,
    icon,
    size = 'md', // 'sm' | 'md' | 'lg' | 'xl' | '2xl'
    children,
    footer,
    className = '',
}) {
    const sizes = {
        sm: 'max-w-sm',
        md: 'max-w-md',
        lg: 'max-w-xl',
        xl: 'max-w-2xl',
        '2xl': 'max-w-4xl',
        '3xl': 'max-w-5xl',
        '4xl': 'max-w-6xl',
        full: 'max-w-7xl',
    };

    // Fermeture sur la touche Échap
    useEffect(() => {
        const handleKeyDown = (e) => {
            if (e.key === 'Escape' && isOpen) {
                onClose();
            }
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, onClose]);

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            {/* Backdrop */}
            <div
                className="absolute inset-0 bg-black/60 backdrop-blur-[2px] "
                onClick={onClose}
            />

            {/* Boîte Modale */}
            <div
                role="dialog"
                aria-modal="true"
                aria-labelledby="modal-title"
                tabIndex={-1}
                className={`
          relative w-full ${sizes[size] || sizes.md} bg-[var(--color-surface)] border border-[var(--color-border)]
          shadow-2xl rounded-none flex flex-col overflow-hidden animate-in fade-in zoom-in-95 
          ${className}
        `}
            >
                {/* Header */}
                <div className="px-4 py-3 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] flex items-center justify-between shrink-0">
                    <h3 id="modal-title" className="font-bold text-[var(--color-fg)] text-sm sm:text-base flex items-center gap-2 truncate">
                        {icon && <span className="text-[var(--color-brand-500)] shrink-0">{icon}</span>}
                        <span className="truncate">{title}</span>
                    </h3>
                    <button
                        type="button"
                        onClick={onClose}
                        className="p-1 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-surface)] rounded-none cursor-pointer"
                        aria-label="Fermer la modale"
                    >
                        <X size={16} />
                    </button>
                </div>

                {/* Body */}
                <div className="p-4 sm:p-5 overflow-y-auto max-h-[82vh] space-y-3.5">
                    {children}
                </div>

                {/* Footer */}
                {footer && (
                    <div className="px-4 py-3 border-t border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] flex justify-end items-center gap-2 shrink-0">
                        {footer}
                    </div>
                )}
            </div>
        </div>
    );
}
