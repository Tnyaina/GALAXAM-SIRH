import { useEffect } from 'react';

/**
 * useUnsavedChanges — Hook pour protéger les saisies en cours contre le rechargement
 * ou la fermeture involontaire de la page/navigateur (événement beforeunload).
 *
 * @param {boolean} isDirty - Indique si des modifications non sauvegardées sont en cours
 * @param {string} [message] - Message d'avertissement optionnel
 */
export function useUnsavedChanges(isDirty, message = 'Vous avez des modifications non enregistrées. Voulez-vous vraiment quitter ?') {
    useEffect(() => {
        if (!isDirty) return;

        const handleBeforeUnload = (event) => {
            event.preventDefault();
            event.returnValue = message;
            return message;
        };

        window.addEventListener('beforeunload', handleBeforeUnload);
        return () => window.removeEventListener('beforeunload', handleBeforeUnload);
    }, [isDirty, message]);
}

export default useUnsavedChanges;
