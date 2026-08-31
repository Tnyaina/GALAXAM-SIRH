import { UserCheck } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input, { Checkbox } from '@/components/ui/forms/Input';
import Alert from '@/components/ui/feedback/Alert';

export default function TypeIntervenantFormModal({
    isOpen,
    onClose,
    onSubmit,
    editingIntervenant,
    formData,
    setFormData,
    actionError,
    submitting = false,
}) {
    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={editingIntervenant ? "Modifier le Type d'Intervenant" : "Nouveau Type d'Intervenant"}
            icon={<UserCheck size={18} />}
            size="md"
            footer={
                <>
                    <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>
                        Annuler
                    </Button>
                    <Button type="submit" form="intervenant-form" variant="primary" size="sm" loading={submitting}>
                        {editingIntervenant ? "Enregistrer" : "Créer le type"}
                    </Button>
                </>
            }
        >
            <form id="intervenant-form" onSubmit={onSubmit} className="space-y-4">
                {actionError && <Alert type="error" message={actionError} />}

                <Input
                    label="Code du Type d'Intervenant"
                    required
                    disabled={!!editingIntervenant}
                    value={formData.code}
                    onChange={(e) => setFormData({ ...formData, code: e.target.value.toUpperCase() })}
                    placeholder="Ex: SALARIE, CONSULTANT, STAGIAIRE"
                />

                <Input
                    label="Libellé Complet"
                    required
                    value={formData.libelle}
                    onChange={(e) => setFormData({ ...formData, libelle: e.target.value })}
                    placeholder="Ex: Salarié National de Droit Commun"
                />

                <div className="space-y-2 pt-2 border-t border-[var(--color-border-subtle)]">
                    <p className="text-xs font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">
                        Régime Fiscal & Cotisations Sociales
                    </p>
                    <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] rounded-none space-y-2.5">
                        <Checkbox
                            label="Soumis à l'Impôt IRSA (Salariés / Traitements)"
                            checked={formData.soumisIrsa}
                            onChange={(e) => setFormData({ ...formData, soumisIrsa: e.target.checked })}
                        />
                        <Checkbox
                            label="Soumis au prélèvement libératoire ISI 5% (Prestataires / Honoraires)"
                            checked={formData.soumisIsi5}
                            onChange={(e) => setFormData({ ...formData, soumisIsi5: e.target.checked })}
                        />
                        <Checkbox
                            label="Cotise à la CNaPS (Caisse Nationale de Prévoyance Sociale)"
                            checked={formData.soumisCnaps}
                            onChange={(e) => setFormData({ ...formData, soumisCnaps: e.target.checked })}
                        />
                        <Checkbox
                            label="Cotise aux organismes de santé interentreprises (OSTIE, FUNRECO, etc.)"
                            checked={formData.soumisSante}
                            onChange={(e) => setFormData({ ...formData, soumisSante: e.target.checked })}
                        />
                    </div>
                </div>
            </form>
        </Modal>
    );
}