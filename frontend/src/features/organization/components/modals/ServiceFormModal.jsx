import FormError from '@/components/ui/feedback/FormError';
import { Layers } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input, { Select } from '@/components/ui/forms/Input';

export default function ServiceFormModal({
    isOpen,
    onClose,
    onSubmit,
    editingService,
    formData,
    setFormData,
    departements = [],
    actionError,
    fieldErrors = {},
    setFieldErrors,
    submitting = false,
}) {
    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={editingService ? "Modifier le Service" : "Nouveau Service"}
            icon={<Layers size={18} />}
            size="lg"
            footer={
                <>
                    <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>
                        Annuler
                    </Button>
                    <Button type="submit" form="service-form" variant="primary" size="sm" loading={submitting}>
                        {editingService ? "Enregistrer les modifications" : "Créer le service"}
                    </Button>
                </>
            }
        >
            <form id="service-form" onSubmit={onSubmit} className="space-y-4">
                {actionError && <FormError message={actionError} />}

                <Select
                    label="Département Parent de Rattachement"
                    required
                    value={formData.idDepartement}
                    onChange={(e) => {
                        setFormData({ ...formData, idDepartement: e.target.value });
                        if (fieldErrors.idDepartement && setFieldErrors) {
                            setFieldErrors(prev => ({ ...prev, idDepartement: undefined }));
                        }
                    }}
                    error={fieldErrors.idDepartement}
                >
                    <option value="">-- Sélectionner un département --</option>
                    {departements
                        .filter(d => d.estActif !== false && d.actif !== false || String(d.idDepartement) === String(formData.idDepartement))
                        .map((d) => (
                            <option key={d.idDepartement} value={d.idDepartement}>
                                {d.libelle} ({d.codeDepartement})
                            </option>
                        ))}
                </Select>

                <Input
                    label="Code Service"
                    required
                    disabled={!!editingService}
                    value={formData.codeService}
                    onChange={(e) => {
                        setFormData({ ...formData, codeService: e.target.value.toUpperCase() });
                        if (fieldErrors.codeService && setFieldErrors) {
                            setFieldErrors(prev => ({ ...prev, codeService: undefined }));
                        }
                    }}
                    error={fieldErrors.codeService}
                    placeholder="Ex: ADM, REC, PAIE"
                />

                <Input
                    label="Libellé du Service"
                    required
                    value={formData.libelle}
                    onChange={(e) => {
                        setFormData({ ...formData, libelle: e.target.value });
                        if (fieldErrors.libelle && setFieldErrors) {
                            setFieldErrors(prev => ({ ...prev, libelle: undefined }));
                        }
                    }}
                    error={fieldErrors.libelle}
                    placeholder="Ex: Administration du Personnel & Paie"
                />
            </form>
        </Modal>
    );
}
