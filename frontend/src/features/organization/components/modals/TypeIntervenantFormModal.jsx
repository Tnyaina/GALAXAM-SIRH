import { UserCheck } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input, { Checkbox } from '@/components/ui/forms/Input';
import FormError from '@/components/ui/feedback/FormError';

export default function TypeIntervenantFormModal({
    isOpen,
    onClose,
    onSubmit,
    formData,
    setFormData,
    actionError,
    fieldErrors = {},
    setFieldErrors,
    submitting = false
}) {
    const toggles = [
        ['soumisIrsa', "Soumis à l'Impôt sur le Revenu Salarial (IRSA)"],
        ['soumisCnaps', "Cotise à la CNaPS (Caisse Nationale de Prévoyance Sociale)"],
        ['soumisSante', "Cotise aux organismes de santé interentreprises (OSTIE / OMSI)"],
    ];

    const handleToggle = (field, checked) => {
        const next = { ...formData, [field]: checked };
        next.soumisIsi5 = false;
        setFormData(next);
    };

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title="Modifier le Régime de l'Intervenant"
            icon={<UserCheck size={18} />}
            size="lg"
            footer={
                <>
                    <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>
                        Annuler
                    </Button>
                    <Button type="submit" form="intervenant-form" variant="primary" size="sm" loading={submitting}>
                        Enregistrer
                    </Button>
                </>
            }
        >
            <form id="intervenant-form" onSubmit={onSubmit} className="space-y-4">
                {actionError && <FormError message={actionError} />}
                <Input
                    label="Code du Type d'Intervenant"
                    required
                    disabled
                    value={formData.code}
                    onChange={(event) => setFormData({ ...formData, code: event.target.value.toUpperCase() })}
                    placeholder="Ex: SALARIE, CONSULTANT, STAGIAIRE"
                />
                <Input
                    label="Libellé Complet"
                    required
                    value={formData.libelle}
                    onChange={(event) => {
                        setFormData({ ...formData, libelle: event.target.value });
                        if (fieldErrors.libelle && setFieldErrors) {
                            setFieldErrors(prev => ({ ...prev, libelle: undefined }));
                        }
                    }}
                    error={fieldErrors.libelle}
                    placeholder="Ex: Salarié National de Droit Commun"
                />
                <div className="space-y-2 pt-2 border-t border-[var(--color-border-subtle)]">
                    <p className="text-xs font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">
                        Régime Fiscal & Cotisations Sociales
                    </p>
                    <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] rounded-none space-y-2.5">
                        {toggles.map(([field, label]) => (
                            <Checkbox
                                key={field}
                                label={label}
                                checked={formData[field]}
                                onChange={(event) => handleToggle(field, event.target.checked)}
                            />
                        ))}
                    </div>
                    <p className="text-[11px] text-[var(--color-fg-muted)]">
                        Les salariés sont traités en paie. Les consultants indépendants sont payés sur facture, hors de ce référentiel de cotisations.
                    </p>
                </div>
            </form>
        </Modal>
    );
}
