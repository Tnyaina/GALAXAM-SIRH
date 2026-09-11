import { useState, useCallback } from 'react';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';

/**
 * useConfirm — Hook réutilisable pour standardiser et simplifier l'utilisation de ConfirmDialog.
 * Supprime le besoin de déclarer manuellement l'état confirmState et son JSX verbeux.
 *
 * Exemple d'utilisation :
 *   const { confirm, ConfirmModal, isConfirming } = useConfirm();
 *
 *   confirm({
 *       title: "Désactiver l'indemnité",
 *       message: "Cette action désactivera également tous ses tarifs par site.",
 *       variant: "danger",
 *       confirmLabel: "Désactiver",
 *       onConfirm: async () => {
 *           await paieConfigService.deactivateIndemnite(id);
 *       }
 *   });
 *
 *   // Dans le rendu :
 *   return (
 *       <div>
 *           ...
 *           <ConfirmModal />
 *       </div>
 *   );
 */
export function useConfirm() {
    const [state, setState] = useState({
        isOpen: false,
        title: 'Confirmation',
        message: '',
        confirmLabel: 'Confirmer',
        cancelLabel: 'Annuler',
        variant: 'danger',
        onConfirm: null,
        loading: false,
    });

    const close = useCallback(() => {
        setState((prev) => ({ ...prev, isOpen: false, loading: false }));
    }, []);

    const confirm = useCallback((options) => {
        setState({
            isOpen: true,
            title: options.title || 'Confirmation',
            message: options.message || '',
            confirmLabel: options.confirmLabel || 'Confirmer',
            cancelLabel: options.cancelLabel || 'Annuler',
            variant: options.variant || 'danger',
            loading: false,
            onConfirm: async () => {
                if (!options.onConfirm) {
                    close();
                    return;
                }
                try {
                    setState((prev) => ({ ...prev, loading: true }));
                    await options.onConfirm();
                    close();
                } catch (error) {
                    setState((prev) => ({ ...prev, loading: false }));
                    throw error;
                }
            },
        });
    }, [close]);

    const ConfirmModal = useCallback(() => {
        if (!state.isOpen) return null;
        return (
            <ConfirmDialog
                isOpen={state.isOpen}
                onClose={close}
                onConfirm={state.onConfirm}
                title={state.title}
                message={state.message}
                confirmLabel={state.confirmLabel}
                cancelLabel={state.cancelLabel}
                variant={state.variant}
                loading={state.loading}
            />
        );
    }, [state, close]);

    return {
        confirm,
        close,
        ConfirmModal,
        isConfirming: state.loading,
        isOpen: state.isOpen,
    };
}

export default useConfirm;
