import FormError from '@/components/ui/feedback/FormError';
import { Layers } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import SearchSelect from '@/components/ui/forms/SearchSelect';

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

                <SearchSelect
                    label="Département Parent de Rattachement"
                    required
                    value={formData.idDepartement}
                    onChange={(val) => {
                        setFormData({ ...formData, idDepartement: val });
                        if (fieldErrors.idDepartement && setFieldErrors) {
                            setFieldErrors(prev => ({ ...prev, idDepartement: undefined }));
                        }
                    }}
                    error={fieldErrors.idDepartement}
                    placeholder="-- Sélectionner un département --"
                    options={departements
                        .filter(d => d.estActif !== false && d.actif !== false || String(d.idDepartement) === String(formData.idDepartement))
                        .map((d) => ({
                            value: String(d.idDepartement),
                            label: `${d.libelle} (${d.codeDepartement})`
                        }))}
                />

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
