import { Building } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import FormError from '@/components/ui/feedback/FormError';

export default function DepartementFormModal({
    isOpen,
    onClose,
    onSubmit,
    editingDept,
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
            title={editingDept ? 'Modifier le Département' : 'Nouveau Département'}
            icon={<Building size={18} />}
            size="lg"
            footer={
                <>
                    <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>
                        Annuler
                    </Button>
                    <Button type="submit" form="dept-form" variant="primary" size="sm" loading={submitting}>
                        {editingDept ? 'Enregistrer les modifications' : 'Créer le département'}
                    </Button>
                </>
            }
        >
            <form id="dept-form" onSubmit={onSubmit} className="space-y-4">
                {actionError && <FormError message={actionError} />}
                <Input
                    label="Code Département"
                    required
                    disabled={!!editingDept}
                    value={formData.codeDepartement}
                    onChange={(event) => {
                        setFormData({ ...formData, codeDepartement: event.target.value.toUpperCase() });
                        if (fieldErrors.codeDepartement && setFieldErrors) {
                            setFieldErrors(prev => ({ ...prev, codeDepartement: undefined }));
                        }
                    }}
                    error={fieldErrors.codeDepartement}
                    placeholder="Ex: LOG, DAF, TRANSIT"
                />
                <Input
                    label="Libellé du Département"
                    required
                    value={formData.libelle}
                    onChange={(event) => {
                        setFormData({ ...formData, libelle: event.target.value });
                        if (fieldErrors.libelle && setFieldErrors) {
                            setFieldErrors(prev => ({ ...prev, libelle: undefined }));
                        }
                    }}
                    error={fieldErrors.libelle}
                    placeholder="Ex: Direction des Opérations & Logistique"
                />
            </form>
        </Modal>
    );
}
