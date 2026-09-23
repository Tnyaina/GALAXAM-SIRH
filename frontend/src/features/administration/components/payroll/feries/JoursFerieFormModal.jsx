import { Calendar } from 'lucide-react';
import Input, { Checkbox } from '@/components/ui/forms/Input';
import FormModal from '@/components/ui/overlays/FormModal';
import { fmtDateInput } from '@/utils/dateUtils';

export default function JoursFerieFormModal({
    isOpen,
    onClose,
    onSubmit,
    editingFerie,
    formData,
    setFormData,
    actionError,
    submitting = false,
}) {
    return (
        <FormModal
            isOpen={isOpen}
            onClose={onClose}
            onSubmit={onSubmit}
            title={editingFerie ? "Modifier le Jour Férié" : "Nouveau Jour Férié"}
            icon={<Calendar size={18} />}
            size="md"
            isEdit={!!editingFerie}
            loading={submitting}
            error={actionError}
        >
            <div className="space-y-4">
                <Input
                    label="Date exacte du jour férié"
                    type="date"
                    required
                    value={fmtDateInput(formData.dateFerie)}
                    onChange={(e) => setFormData({ ...formData, dateFerie: e.target.value })}
                />

                <Input
                    label="Libellé / Fête célébrée"
                    required
                    value={formData.libelle}
                    onChange={(e) => setFormData({ ...formData, libelle: e.target.value })}
                    placeholder="Ex: Fête Nationale de l'Indépendance"
                />

                <div className="space-y-2 p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] rounded-none">
                    <Checkbox
                        label="Jour férié chômé et payé"
                        checked={formData.estChome}
                        onChange={(e) => setFormData({ ...formData, estChome: e.target.checked })}
                    />
                    <Checkbox
                        label="Férié national (applicable d'office à l'ensemble des sites)"
                        checked={formData.estNational}
                        onChange={(e) => setFormData({ ...formData, estNational: e.target.checked })}
                    />
                </div>
            </div>
        </FormModal>
    );
}