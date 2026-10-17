import { useEffect, useState } from 'react';
import { ArrowLeft } from 'lucide-react';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';

/** Page de travail du dossier ; conserve la même fiche métier que l'ancienne modale. */
export default function DossierSortieWorkspaceShell({ isOpen, onClose, title, children, footer, isDirty }) {
    const [confirmLeave, setConfirmLeave] = useState(false);

    useEffect(() => {
        if (!isOpen || !isDirty) return undefined;
        const warn = (event) => {
            event.preventDefault();
            event.returnValue = '';
        };
        window.addEventListener('beforeunload', warn);
        return () => window.removeEventListener('beforeunload', warn);
    }, [isOpen, isDirty]);

    if (!isOpen) return null;

    return (
        <div className="mx-auto w-full max-w-7xl space-y-5 pb-10">
            <header className="flex flex-wrap items-center justify-between gap-3 border-b border-[var(--color-border-subtle)] pb-4">
                <div className="flex min-w-0 items-center gap-3">
                    <button type="button" onClick={() => isDirty ? setConfirmLeave(true) : onClose?.()}
                        aria-label="Retour aux dossiers de sortie"
                        className="flex h-10 w-10 shrink-0 items-center justify-center border border-[var(--color-border-subtle)] text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)]">
                        <ArrowLeft size={18} />
                    </button>
                    <div className="min-w-0">
                        <p className="text-xs font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]">Départs · Dossier individuel</p>
                        <h1 className="truncate text-xl font-bold text-[var(--color-fg)] sm:text-2xl">{title}</h1>
                    </div>
                </div>
                <span className="text-xs text-[var(--color-fg-muted)]">Les étapes restent consultables à tout moment.</span>
            </header>
            <main className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-4 shadow-xs sm:p-6">
                {children}
            </main>
            {footer && <div className="sticky bottom-3 z-20 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-3 shadow-lg sm:p-4">{footer}</div>}
            <ConfirmDialog isOpen={confirmLeave} onClose={() => setConfirmLeave(false)}
                onConfirm={() => { setConfirmLeave(false); onClose?.(); }}
                title="Quitter sans enregistrer"
                message="Les modifications de préparation non enregistrées seront perdues."
                confirmLabel="Quitter sans enregistrer" variant="warning" />
        </div>
    );
}
