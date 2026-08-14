import { AlertTriangle } from 'lucide-react';
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
    variant = 'danger', // 'danger' | 'warning' | 'primary'
    loading = false,
    children
}) {
    if (!isOpen) return null;

    const iconColors = {
        danger: 'text-[var(--color-danger-500)] bg-[var(--color-danger-500)]/10 border-[var(--color-danger-500)]/30',
        warning: 'text-[var(--color-warning-500)] bg-[var(--color-warning-500)]/10 border-[var(--color-warning-500)]/30',
        primary: 'text-[var(--color-brand-500)] bg-[var(--color-brand-500)]/10 border-[var(--color-brand-500)]/30',
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-4">
            <div className="bg-[var(--color-surface)] border border-[var(--color-border)] shadow-xl max-w-md w-full overflow-hidden rounded-none">
                <div className="p-6 space-y-4">
                    <div className="flex items-start gap-4">
                        <div className={`p-3 border shrink-0 rounded-none ${iconColors[variant] || iconColors.danger}`}>
                            <AlertTriangle size={20} />
                        </div>
                        <div className="space-y-1 min-w-0 flex-1">
                            <h3 className="text-base font-bold text-[var(--color-fg)]">
                                {title}
                            </h3>
                            {message && (
                                <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                                    {message}
                                </p>
                            )}
                        </div>
                    </div>

                    {children && <div className="pt-2">{children}</div>}
                </div>

                <div className="px-6 py-4 bg-[var(--color-bg-subtle)] border-t border-[var(--color-border-subtle)] flex justify-end gap-3">
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
                        variant={variant === 'danger' ? 'danger' : variant === 'warning' ? 'warning' : 'primary'}
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