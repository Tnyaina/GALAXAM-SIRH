import { useEffect, useState, useCallback, useRef } from 'react';
import { X } from 'lucide-react';
import ConfirmDialog from './ConfirmDialog';
import useFocusTrap from '@/hooks/useFocusTrap';

const widths = {
    sm: 'max-w-sm',
    md: 'max-w-md',
    lg: 'max-w-xl',
    xl: 'max-w-2xl',
    '2xl': 'max-w-3xl',
    '3xl': 'max-w-4xl'
};

/**
 * SlideOver — panneau latéral plein écran (droite).
 * Utilise strictement le Design System Galaxam (bordures nettes, tokens sémantiques).
 * Intègre la protection contre la perte de saisie (isDirty) et le piège du focus clavier WCAG AA.
 */
export default function SlideOver({
    // Effets d'accessibilité, actions de fermeture et rendu du panneau.
    isOpen,
    onClose,
    title,
    subtitle,
    icon,
    width = 'xl',
    children,
    footer,
    /** Ref optionnel sur la zone scrollable du body — permet au parent de contrôler le scrollTop. */
    bodyRef,
    closeOnBackdrop = true,
    closeOnEscape = true,
    isDirty = false,
    dirtyWarningTitle = 'Modifications non enregistrées',
    dirtyWarningMessage = 'Des informations ont été saisies dans ce panneau. Si vous le fermez maintenant, vos modifications seront perdues. Voulez-vous vraiment continuer ?',
    onConfirmDiscard,
}) {
    const slideOverRef = useRef(null);
    const [showDiscardConfirm, setShowDiscardConfirm] = useState(false);

    useFocusTrap(slideOverRef, isOpen && !showDiscardConfirm);

    const handleRequestClose = useCallback(() => {
        if (isDirty) {
            setShowDiscardConfirm(true);
        } else {
            onClose?.();
        }
    }, [isDirty, onClose]);

    const handleBackdropClick = () => {
        if (!closeOnBackdrop) return;
        handleRequestClose();
    };

    // Fermeture sur la touche Échap
    useEffect(() => {
        const handleKeyDown = (e) => {
            if (e.key === 'Escape' && isOpen && !showDiscardConfirm) {
                if (!closeOnEscape) return;
                handleRequestClose();
            }
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, closeOnEscape, handleRequestClose, showDiscardConfirm]);

    if (!isOpen) return null;

    return (
        <>
            <div className="fixed inset-0 z-50 flex justify-end">
                <div
                    className="absolute inset-0 bg-[var(--color-overlay)]/60 backdrop-blur-[2px]"
                    onClick={handleBackdropClick}
                />

                <div
                    ref={slideOverRef}
                    role="dialog"
                    aria-modal="true"
                    aria-labelledby="slideover-title"
                    tabIndex={-1}
                    className={`
                        relative w-full ${widths[width]} h-full flex flex-col rounded-none
                        bg-[var(--color-surface)] border-l border-[var(--color-border)] shadow-xl
                    `}
                >
                    {/* Header */}
                    <div className="flex items-center justify-between px-4 py-3 border-b border-[var(--color-border-subtle)] shrink-0">
                        <div className="flex items-center gap-2.5 min-w-0">
                            {icon && (
                                <div className="w-8 h-8 flex items-center justify-center bg-[var(--color-brand-500)]/10 text-[var(--color-brand-500)] shrink-0 rounded-none border border-[var(--color-brand-500)]/20">
                                    {icon}
                                </div>
                            )}
                            <div className="min-w-0">
                                {title && (
                                    <h2 id="slideover-title" className="text-sm sm:text-base font-bold text-[var(--color-fg)] truncate">
                                        {title}
                                    </h2>
                                )}
                                {subtitle && (
                                    <p className="text-[11px] text-[var(--color-fg-muted)] truncate">{subtitle}</p>
                                )}
                            </div>
                        </div>
                        <button
                            type="button"
                            onClick={handleRequestClose}
                            aria-label="Fermer"
                            className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] shrink-0 rounded-none cursor-pointer"
                        >
                            <X size={16} />
                        </button>
                    </div>

                    {/* Body */}
                    <div ref={bodyRef} className="flex-1 overflow-y-auto overflow-x-hidden p-4 sm:p-5">{children}</div>

                    {/* Footer */}
                    {footer && (
                        <div className="px-4 py-3 bg-[var(--color-bg-subtle)] border-t border-[var(--color-border-subtle)] flex items-center justify-between gap-2.5 shrink-0">
                            {footer}
                        </div>
                    )}
                </div>
            </div>

            {/* Confirmation d'abandon de saisie */}
            <ConfirmDialog
                isOpen={showDiscardConfirm}
                onClose={() => setShowDiscardConfirm(false)}
                onConfirm={() => {
                    setShowDiscardConfirm(false);
                    if (onConfirmDiscard) {
                        onConfirmDiscard();
                    } else {
                        onClose?.();
                    }
                }}
                title={dirtyWarningTitle}
                message={dirtyWarningMessage}
                confirmLabel="Abandonner les modifications"
                cancelLabel="Poursuivre la saisie"
                variant="danger"
            />
        </>
    );
}
