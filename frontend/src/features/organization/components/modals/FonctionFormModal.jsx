import { Briefcase } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input, { Checkbox } from '@/components/ui/forms/Input';
import FormError from '@/components/ui/feedback/FormError';

export default function FonctionFormModal({
    isOpen,
    onClose,
    onSubmit,
    editingFonction,
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
            title={editingFonction ? "Modifier le Poste / Fonction" : "Nouveau Poste / Fonction"}
            icon={<Briefcase size={18} />}
            size="lg"
            footer={
                <>
                    <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>
                        Annuler
                    </Button>
                    <Button type="submit" form="fonction-form" variant="primary" size="sm" loading={submitting}>
                        {editingFonction ? "Enregistrer les modifications" : "Créer le poste"}
                    </Button>
                </>
            }
        >
            <form id="fonction-form" onSubmit={onSubmit} className="space-y-4">
                {actionError && <FormError message={actionError} />}
                <Input
                    label="Code Fonction / Poste"
                    required
                    disabled={!!editingFonction}
                    value={formData.codeFonction}
                    onChange={(event) => {
                        setFormData({ ...formData, codeFonction: event.target.value.toUpperCase() });
                        if (fieldErrors.codeFonction && setFieldErrors) {
                            setFieldErrors(prev => ({ ...prev, codeFonction: undefined }));
                        }
                    }}
                    error={fieldErrors.codeFonction}
                    placeholder="Ex: DG, DAF, DECLARANT, CHAUFFEUR"
                />
                <Input
                    label="Intitulé du Poste"
                    required
                    value={formData.libelle}
                    onChange={(event) => {
                        setFormData({ ...formData, libelle: event.target.value });
                        if (fieldErrors.libelle && setFieldErrors) {
                            setFieldErrors(prev => ({ ...prev, libelle: undefined }));
                        }
                    }}
                    error={fieldErrors.libelle}
                    placeholder="Ex: Responsable des Opérations Douanières"
                />
                <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] rounded-none space-y-1">
                    <Checkbox
                        label="Dispense de pointage (Poste / Cadre autonome)"
                        checked={formData.dispensePointage}
                        onChange={(event) => setFormData({ ...formData, dispensePointage: event.target.checked })}
                    />
                    <p className="text-[11px] text-[var(--color-fg-muted)] ml-6">
                        Les collaborateurs occupant ce poste ne seront pas assujettis au pointage quotidien et ne généreront pas d'anomalies de retard.
                    </p>
                </div>
            </form>
        </Modal>
    );
}
