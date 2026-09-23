import { Building } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Alert from '@/components/ui/feedback/Alert';
import { fmtDateInput } from '@/utils/dateUtils';

export default function DepartementFormModal({
    isOpen,
    onClose,
    onSubmit,
    editingDept,
    formData,
    setFormData,
    actionError,
    submitting = false,
}) {
    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={editingDept ? "Modifier le Département" : "Nouveau Département"}
            icon={<Building size={18} />}
            size="md"
            footer={
                <>
                    <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>
                        Annuler
                    </Button>
                    <Button type="submit" form="dept-form" variant="primary" size="sm" loading={submitting}>
                        {editingDept ? "Enregistrer les modifications" : "Créer le département"}
                    </Button>
                </>
            }
        >
            <form id="dept-form" onSubmit={onSubmit} className="space-y-4">
                {actionError && <Alert type="error" message={actionError} />}

                <Input
                    label="Code Département"
                    required
                    disabled={!!editingDept}
                    value={formData.codeDepartement}
                    onChange={(e) => setFormData({ ...formData, codeDepartement: e.target.value.toUpperCase() })}
                    placeholder="Ex: LOG, DAF, TRANSIT"
                />

                <Input
                    label="Libellé du Département"
                    required
                    value={formData.libelle}
                    onChange={(e) => setFormData({ ...formData, libelle: e.target.value })}
                    placeholder="Ex: Direction des Opérations & Logistique"
                />

                <Input
                    label="Date de Fin Prévue / Clôture (Optionnel)"
                    type="date"
                    value={fmtDateInput(formData.dateFinPrevue)}
                    onChange={(e) => setFormData({ ...formData, dateFinPrevue: e.target.value })}
                    hint="À renseigner en cas de restructuration ou de département temporaire."
                />
            </form>
        </Modal>
    );
}