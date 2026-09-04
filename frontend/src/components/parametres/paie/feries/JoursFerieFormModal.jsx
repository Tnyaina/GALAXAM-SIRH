import { Calendar } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input, { Checkbox } from '@/components/ui/forms/Input';
import Modal from '@/components/ui/overlays/Modal';
import Alert from '@/components/ui/feedback/Alert';
import { fmtDateInput } from '@/utils/paieUtils';

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
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={editingFerie ? "Modifier le Jour Férié" : "Nouveau Jour Férié"}
            icon={<Calendar size={18} />}
            size="md"
            footer={
                <>
                    <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>
                        Annuler
                    </Button>
                    <Button type="submit" form="ferie-form" variant="primary" size="sm" loading={submitting}>
                        {editingFerie ? "Enregistrer" : "Créer le jour férié"}
                    </Button>
                </>
            }
        >
            <form id="ferie-form" onSubmit={onSubmit} className="space-y-4">
                {actionError && <Alert type="error" message={actionError} />}

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
            </form>
        </Modal>
    );
}