import { useId } from 'react';
import Modal from './Modal';
import Button from '@/components/ui/forms/Button';
import FormError from '@/components/ui/feedback/FormError';

/**
 * FormModal — Composant réutilisable pour standardiser toutes les modales de formulaire (CRUD).
 * Encapsule le Modal, la balise <form>, l'affichage d'erreur de soumission et le footer avec boutons Annuler / Valider.
 *
 * @param {boolean} isOpen - État d'ouverture
 * @param {function} onClose - Handler de fermeture
 * @param {function} onSubmit - Handler de soumission du formulaire
 * @param {string} title - Titre de la modale
 * @param {React.ReactNode} icon - Icône dans le header
 * @param {string} size - Taille de la modale ('sm' | 'md' | 'lg' | 'xl' | '2xl')
 * @param {boolean} isEdit - Indique si on est en modification (influence les labels par défaut)
 * @param {boolean} loading - Indique si la soumission est en cours (affiche spinner)
 * @param {string|null} error - Message d'erreur éventuel à afficher
 * @param {string} submitLabel - Label personnalisé du bouton submit
 * @param {string} cancelLabel - Label personnalisé du bouton annuler
 * @param {boolean} submitDisabled - Permet de désactiver le bouton submit
 * @param {React.ReactNode} customFooter - Surcharge complète éventuelle du footer
 * @param {string} formId - ID HTML unique du formulaire (auto-généré si omis)
 */
export default function FormModal({
    isOpen,
    onClose,
    onSubmit,
    title,
    icon,
    size = 'md',
    isEdit = false,
    loading = false,
    error = null,
    submitLabel,
    cancelLabel = 'Annuler',
    submitDisabled = false,
    customFooter,
    formId,
    className = '',
    noValidate = false,
    children,
}) {
    const generatedId = useId();
    const computedFormId = formId || `form-modal-${generatedId}`;

    const defaultSubmitLabel = isEdit ? 'Enregistrer' : 'Créer';
    const computedSubmitLabel = submitLabel || defaultSubmitLabel;

    const defaultFooter = (
        <>
            <Button
                type="button"
                onClick={onClose}
                variant="ghost"
                size="sm"
                wrapLabel
                disabled={loading}
            >
                {cancelLabel}
            </Button>
            <Button
                type="submit"
                form={computedFormId}
                variant="primary"
                size="sm"
                wrapLabel
                loading={loading}
                disabled={loading || submitDisabled}
            >
                {computedSubmitLabel}
            </Button>
        </>
    );

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={title}
            icon={icon}
            size={size}
            className={className}
            footer={customFooter !== undefined ? customFooter : defaultFooter}
        >
            <form id={computedFormId} noValidate={noValidate} onSubmit={onSubmit} className="space-y-4">
                {error && <FormError message={error} />}
                {children}
            </form>
        </Modal>
    );
}
