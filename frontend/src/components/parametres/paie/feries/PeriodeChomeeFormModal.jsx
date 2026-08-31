import { useEffect } from 'react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import { Select } from '@/components/ui/forms/Input';
import Alert from '@/components/ui/feedback/Alert';

export default function PeriodeChomeeFormModal({
    isOpen,
    onClose,
    onSubmit,
    editingPeriode,
    formData,
    setFormData,
    sites,
    actionError,
    submitting = false
}) {
    // Reset form when modal opens
    useEffect(() => {
        if (isOpen && !editingPeriode) {
            // Default initialization is handled by parent, but we can ensure portee is correct
        }
    }, [isOpen, editingPeriode]);

    const isSite = formData.idSite !== '' && formData.idSite !== null;

    const handleChange = (field, value) => {
        setFormData(prev => ({ ...prev, [field]: value }));
    };

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={editingPeriode ? "Modifier la période exceptionnelle" : "Déclarer une période exceptionnelle"}
            size="md"
            footer={
                <>
                    <Button variant="ghost" onClick={onClose} disabled={submitting}>Annuler</Button>
                    <Button variant="primary" onClick={onSubmit} loading={submitting}>Enregistrer la période</Button>
                </>
            }
        >
            <div className="space-y-5">
                {actionError && <Alert type="error" message={typeof actionError === 'string' ? actionError : JSON.stringify(actionError)} />}
                
                <Alert type="info" message="Si la date de fin n'est pas encore connue, laissez le champ vide. La période sera considérée comme 'En cours'." />

                <Input
                    label="Événement (Ex: Cyclone XYZ)"
                    value={formData.libelle}
                    onChange={(e) => handleChange('libelle', e.target.value)}
                    required
                />

                <div className="grid grid-cols-2 gap-4">
                    <Input
                        label="Date de début"
                        type="date"
                        value={formData.dateDebut}
                        onChange={(e) => handleChange('dateDebut', e.target.value)}
                        required
                    />
                    <Input
                        label="Date de reprise (Fin)"
                        type="date"
                        min={formData.dateDebut || undefined}
                        value={formData.dateFin}
                        onChange={(e) => handleChange('dateFin', e.target.value)}
                        helpText="Laissez vide si indéterminée"
                    />
                </div>

                <div className="p-4 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] space-y-3">
                    <label className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                        Portée de la suspension
                    </label>
                    <div className="flex items-center gap-4">
                        <label className="flex items-center gap-2 text-sm font-semibold text-[var(--color-fg)]">
                            <input
                                type="radio"
                                name="portee"
                                checked={!isSite}
                                onChange={() => handleChange('idSite', '')}
                                className="text-[var(--color-brand-500)] focus:ring-[var(--color-brand-500)]"
                            />
                            Nationale (Tous sites)
                        </label>
                        <label className="flex items-center gap-2 text-sm font-semibold text-[var(--color-fg)]">
                            <input
                                type="radio"
                                name="portee"
                                checked={isSite}
                                onChange={() => handleChange('idSite', sites.length > 0 ? sites[0].idSite : '')}
                                className="text-[var(--color-brand-500)] focus:ring-[var(--color-brand-500)]"
                            />
                            Spécifique à un Site
                        </label>
                    </div>

                    {isSite && (
                        <Select
                            label="Site concerné"
                            value={formData.idSite}
                            onChange={(e) => handleChange('idSite', e.target.value)}
                        >
                            <option value="">-- Sélectionner un site --</option>
                            {sites.map(s => (
                                <option key={s.idSite} value={s.idSite}>{s.libelle}</option>
                            ))}
                        </Select>
                    )}
                </div>

                <div className="p-4 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] space-y-3">
                    <label className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                        Traitement de Paie (Chômage Technique)
                    </label>
                    <div className="flex items-center gap-4">
                        <label className="flex items-center gap-2 text-sm font-semibold text-[var(--color-fg)]">
                            <input
                                type="radio"
                                name="estPaye"
                                checked={formData.estPaye}
                                onChange={() => handleChange('estPaye', true)}
                                className="text-[var(--color-brand-500)] focus:ring-[var(--color-brand-500)]"
                            />
                            Maintenu / Payé
                        </label>
                        <label className="flex items-center gap-2 text-sm font-semibold text-[var(--color-fg)]">
                            <input
                                type="radio"
                                name="estPaye"
                                checked={!formData.estPaye}
                                onChange={() => handleChange('estPaye', false)}
                                className="text-[var(--color-brand-500)] focus:ring-[var(--color-brand-500)]"
                            />
                            Non Payé
                        </label>
                    </div>

                    {formData.estPaye && (
                        <Input
                            label="Taux de paiement (%)"
                            type="number"
                            min="0"
                            max="100"
                            step="0.01"
                            value={formData.tauxPaiement}
                            onChange={(e) => handleChange('tauxPaiement', parseFloat(e.target.value))}
                            helpText="Ex: 100 pour maintien de salaire intégral, 50 pour demi-salaire."
                        />
                    )}
                </div>
            </div>
        </Modal>
    );
}
