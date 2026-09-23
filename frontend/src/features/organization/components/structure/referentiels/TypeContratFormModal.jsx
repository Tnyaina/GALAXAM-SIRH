import { FileText } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input, { Checkbox } from '@/components/ui/forms/Input';
import Alert from '@/components/ui/feedback/Alert';

export default function TypeContratFormModal({
    isOpen,
    onClose,
    onSubmit,
    _editingContrat,
    formData,
    setFormData,
    actionError,
    submitting = false,
}) {
    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title="Modifier le Type de Contrat"
            icon={<FileText size={18} />}
            size="md"
            footer={
                <>
                    <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>
                        Annuler
                    </Button>
                    <Button type="submit" form="contrat-form" variant="primary" size="sm" loading={submitting}>
                        Enregistrer
                    </Button>
                </>
            }
        >
            <form id="contrat-form" onSubmit={onSubmit} className="space-y-4">
                {actionError && <Alert type="error" message={actionError} />}

                <Input
                    label="Code du Contrat"
                    required
                    disabled
                    value={formData.code}
                    onChange={(e) => setFormData({ ...formData, code: e.target.value.toUpperCase() })}
                    placeholder="Ex: CDI, CDD, STAGE, INTERIM"
                />

                <Input
                    label="Libellé Complet"
                    required
                    value={formData.libelle}
                    onChange={(e) => setFormData({ ...formData, libelle: e.target.value })}
                    placeholder="Ex: Contrat de Travail à Durée Indéterminée"
                />

                <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] rounded-none space-y-1">
                    <Checkbox
                        label="Date de fin obligatoire lors de la saisie d'un contrat"
                        checked={formData.exigeDateFin}
                        onChange={(e) => setFormData({ ...formData, exigeDateFin: e.target.checked })}
                    />
                    <p className="text-[11px] text-[var(--color-fg-muted)] ml-6">
                        Cochez cette option pour les contrats temporaires (CDD, stage, prestation) nécessitant impérativement un terme défini.
                    </p>
                </div>
            </form>
        </Modal>
    );
}