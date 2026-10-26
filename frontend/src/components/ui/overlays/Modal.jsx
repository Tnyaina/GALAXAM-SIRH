import { useEffect, useState, useCallback, useRef } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import ConfirmDialog from './ConfirmDialog';
import useFocusTrap from '@/hooks/useFocusTrap';

/**
 * Modal — Modale standardisée pour le Design System Galaxam.
 * Fermeture sur Échap, clic extérieur, protection anti-perte de saisie (isDirty),
 * piège du focus clavier WCAG AA, fond sémantique et bordures nettes sans arrondis.
 */
export default function Modal({
    isOpen,
    onClose,
    title,
    icon,
    size = 'md', // 'sm' | 'md' | 'lg' | 'xl' | '2xl' | '3xl' | '4xl' | 'full'
    children,
    footer,
    className = '',
    closeOnBackdrop = true,
    closeOnEscape = true,
    isDirty = false,
    dirtyWarningTitle = 'Modifications non enregistrées',
    dirtyWarningMessage = 'Des informations ont été saisies dans ce formulaire. Si vous quittez maintenant, vos modifications seront perdues. Voulez-vous vraiment quitter ?',
    onConfirmDiscard,
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

    const modalRef = useRef(null);
    const [showDiscardConfirm, setShowDiscardConfirm] = useState(false);

    useFocusTrap(modalRef, isOpen && !showDiscardConfirm);

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
            {createPortal(<div className="fixed inset-0 z-50 flex items-center justify-center p-4">
                {/* Backdrop */}
                <div
                    className="absolute inset-0 bg-[var(--color-overlay)]/60 backdrop-blur-[2px]"
                    onClick={handleBackdropClick}
                />

                {/* Boîte Modale */}
                <div
                    ref={modalRef}
                    role="dialog"
                    aria-modal="true"
                    aria-labelledby="modal-title"
                    tabIndex={-1}
                    className={`
              ui-dialog-viewport relative w-full ${sizes[size] || sizes.md} bg-[var(--color-surface)] border border-[var(--color-border)]
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
                            onClick={handleRequestClose}
                            className="p-1 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-surface)] rounded-none cursor-pointer focus-visible:outline-2 focus-visible:outline-[var(--color-brand-500)]"
                            aria-label="Fermer la modale"
                        >
                            <X size={16} />
                        </button>
                    </div>

                    {/* Body */}
                    <div className="min-h-0 flex-1 p-4 sm:p-5 overflow-y-auto space-y-3.5">
                        {children}
                    </div>

                    {/* Footer */}
                    {footer && (
                        <div className="ui-modal-actions px-4 py-3 border-t border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] shrink-0">
                            {footer}
                        </div>
                    )}
                </div>
            </div>, document.body)}

            {/* Boîte de dialogue de confirmation d'abandon si formulaire modifié */}
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
                confirmLabel="Quitter sans enregistrer"
                cancelLabel="Continuer la saisie"
                variant="danger"
            />
        </>
    );
}
