import { Landmark } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import FormError from '@/components/ui/feedback/FormError';

export default function BanqueFormModal({
    isOpen,
    onClose,
    onSubmit,
    editingBanque,
    formData,
    setFormData,
    actionError,
    fieldErrors = {},
    setFieldErrors,
    submitting = false
}) {
    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={editingBanque ? 'Modifier la Banque' : 'Nouvelle Banque Partenaire'}
            icon={<Landmark size={18} />}
            size="lg"
            footer={
                <>
                    <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>
                        Annuler
                    </Button>
                    <Button type="submit" form="banque-form" variant="primary" size="sm" loading={submitting}>
                        {editingBanque ? 'Enregistrer' : 'Créer la banque'}
                    </Button>
                </>
            }
        >
            <form id="banque-form" onSubmit={onSubmit} className="space-y-4">
                {actionError && <FormError message={actionError} />}
                <Input
                    label="Code Banque (5 chiffres normalisés)"
                    required
                    disabled={!!editingBanque}
                    value={formData.codeBanque}
                    onChange={(event) => {
                        setFormData({ ...formData, codeBanque: event.target.value.toUpperCase() });
                        if (fieldErrors.codeBanque && setFieldErrors) {
                            setFieldErrors(prev => ({ ...prev, codeBanque: undefined }));
                        }
                    }}
                    error={fieldErrors.codeBanque}
                    placeholder="Ex: 00004, 00005"
                />
                <Input
                    label="Raison Sociale / Nom de la Banque"
                    required
                    value={formData.nomBanque}
                    onChange={(event) => {
                        setFormData({ ...formData, nomBanque: event.target.value });
                        if (fieldErrors.nomBanque && setFieldErrors) {
                            setFieldErrors(prev => ({ ...prev, nomBanque: undefined }));
                        }
                    }}
                    error={fieldErrors.nomBanque}
                    placeholder="Ex: Bank of Africa Madagascar"
                />
                <Input
                    label="Code SWIFT / BIC (Optionnel)"
                    value={formData.codeSwift}
                    onChange={(event) => setFormData({ ...formData, codeSwift: event.target.value.toUpperCase() })}
                    placeholder="Ex: AFRIMGM1"
                />
            </form>
        </Modal>
    );
}
