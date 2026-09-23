import { useEffect } from 'react';
import { AlertTriangle, CheckCircle } from 'lucide-react';
import Button from '@/components/ui/forms/Button';

/**
 * ConfirmDialog — Modale de confirmation réutilisable (désactivation, suppression, etc.)
 * Bordures nettes, pas d'arrondi, respect des tokens du design system.
 */
export default function ConfirmDialog({
    isOpen,
    onClose,
    onConfirm,
    title = 'Confirmation',
    message = 'Êtes-vous sûr de vouloir effectuer cette action ?',
    confirmLabel = 'Confirmer',
    cancelLabel = 'Annuler',
    variant = 'danger', // 'danger' | 'warning' | 'primary' | 'success'
    loading = false,
    children
}) {
  // Effets, actions et rendu de la boîte de confirmation.
    // Fermeture sur la touche Échap
    useEffect(() => {
        const handleKeyDown = (e) => {
            if (e.key === 'Escape' && isOpen && !loading) {
                onClose();
            }
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, onClose, loading]);

    if (!isOpen) return null;

    const iconColors = {
        danger: 'text-[var(--color-danger-500)] bg-[var(--color-danger-500)]/10 border-[var(--color-danger-500)]/30',
        warning: 'text-[var(--color-warning-500)] bg-[var(--color-warning-500)]/10 border-[var(--color-warning-500)]/30',
        primary: 'text-[var(--color-brand-500)] bg-[var(--color-brand-500)]/10 border-[var(--color-brand-500)]/30',
        success: 'text-[var(--color-success-500)] bg-[var(--color-success-500)]/10 border-[var(--color-success-500)]/30',
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-4">
            <div
                role="dialog"
                aria-modal="true"
                aria-labelledby="confirm-dialog-title"
                tabIndex={-1}
                className="bg-[var(--color-surface)] border border-[var(--color-border)] shadow-xl max-w-md w-full overflow-hidden rounded-none animate-in fade-in zoom-in-95"
            >
                <div className="p-4 sm:p-5 space-y-3">
                    <div className="flex items-start gap-3">
                        <div className={`p-2.5 border shrink-0 rounded-none ${iconColors[variant] || iconColors.danger}`}>
                            {variant === 'success' ? <CheckCircle size={18} /> : <AlertTriangle size={18} />}
                        </div>
                        <div className="space-y-0.5 min-w-0 flex-1">
                            <h3 id="confirm-dialog-title" className="text-sm sm:text-base font-bold text-[var(--color-fg)]">
                                {title}
                            </h3>
                            {message && (
                                <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                                    {message}
                                </p>
                            )}
                        </div>
                    </div>

                    {children && <div className="pt-1.5">{children}</div>}
                </div>

                <div className="px-4 py-3 bg-[var(--color-bg-subtle)] border-t border-[var(--color-border-subtle)] flex justify-end gap-2">
                    <Button
                        type="button"
                        onClick={onClose}
                        variant="ghost"
                        size="sm"
                        disabled={loading}
                    >
                        {cancelLabel}
                    </Button>
                    <Button
                        type="button"
                        onClick={onConfirm}
                        variant={variant === 'danger' ? 'danger' : variant === 'warning' ? 'warning' : variant === 'success' ? 'success' : 'primary'}
                        size="sm"
                        loading={loading}
                    >
                        {confirmLabel}
                    </Button>
                </div>
            </div>
        </div>
    );
}
