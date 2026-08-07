import { AlertTriangle } from 'lucide-react';
import Button from '@/components/ui/Button';

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
        danger: 'text-danger-500 bg-danger-50 dark:bg-danger-900/30 border-danger-200 dark:border-danger-800',
        warning: 'text-warning-500 bg-warning-50 dark:bg-warning-900/30 border-warning-200 dark:border-warning-800',
        primary: 'text-brand-500 bg-brand-50 dark:bg-brand-900/30 border-brand-200 dark:border-brand-800',
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
            <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                <div className="p-6 space-y-4">
                    <div className="flex items-start gap-4">
                        <div className={`p-3 border shrink-0 ${iconColors[variant] || iconColors.danger}`}>
                            <AlertTriangle size={20} />
                        </div>
                        <div className="space-y-1 min-w-0 flex-1">
                            <h3 className="text-base font-bold text-ink-900 dark:text-white">
                                {title}
                            </h3>
                            {message && (
                                <p className="text-xs text-ink-500 dark:text-ink-400 leading-relaxed">
                                    {message}
                                </p>
                            )}
                        </div>
                    </div>

                    {children && <div className="pt-2">{children}</div>}
                </div>

                <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
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
