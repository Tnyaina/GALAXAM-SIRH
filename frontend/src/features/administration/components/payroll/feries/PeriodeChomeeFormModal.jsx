import { Radio as SharedRadio } from '@/components/ui/forms/Input';
import { useEffect } from 'react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Alert from '@/components/ui/feedback/Alert';
import FormError from '@/components/ui/feedback/FormError';

export default function PeriodeChomeeFormModal({
    isOpen,
    onClose,
    onSubmit,
    editingPeriode,
    formData,
    setFormData,
    sites,
    actionError,
    fieldErrors = {},
    setFieldErrors,
    submitting = false
}) {
    // Effet : garde l'initialisation au niveau du parent du formulaire.
    useEffect(() => {
        if (isOpen && !editingPeriode) {
            // Default initialization is handled by parent, but we can ensure portee is correct
        }
    }, [isOpen, editingPeriode]);

    // Données dérivées
    const isSite = formData.idSite !== '' && formData.idSite !== null;

    // Actions utilisateur
    const handleChange = (field, value) => {
        setFormData(prev => ({ ...prev, [field]: value }));
        if (fieldErrors[field] && setFieldErrors) {
            setFieldErrors(prev => ({ ...prev, [field]: undefined }));
        }
    };

    // Rendu
    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={editingPeriode ? "Modifier la période exceptionnelle" : "Déclarer une période exceptionnelle"}
            size="lg"
            footer={
                <>
                    <Button variant="ghost" onClick={onClose} disabled={submitting}>Annuler</Button>
                    <Button variant="primary" onClick={onSubmit} loading={submitting}>Enregistrer la période</Button>
                </>
            }
        >
            <div className="space-y-5">
                {actionError && <FormError message={typeof actionError === 'string' ? actionError : JSON.stringify(actionError)} />}
                
                <Alert type="info" message="Si la date de fin n'est pas encore connue, laissez le champ vide. La période sera considérée comme 'En cours'." />

                <Input
                    label="Événement"
                    placeholder="Ex : Cyclone XYZ, Inondation, Alerte météorologique..."
                    value={formData.libelle}
                    onChange={(e) => handleChange('libelle', e.target.value)}
                    error={fieldErrors.libelle}
                    required
                />

                <div className="grid grid-cols-2 gap-4">
                    <Input
                        label="Date de début"
                        type="date"
                        value={formData.dateDebut}
                        onChange={(e) => handleChange('dateDebut', e.target.value)}
                        error={fieldErrors.dateDebut}
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
                            <SharedRadio plain
                                type="radio"
                                name="portee"
                                checked={!isSite}
                                onChange={() => handleChange('idSite', '')}
                                className="text-[var(--color-brand-500)] focus:ring-[var(--color-brand-500)]"
                            />
                            Nationale (Tous sites)
                        </label>
                        <label className="flex items-center gap-2 text-sm font-semibold text-[var(--color-fg)]">
                            <SharedRadio plain
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
                        <SearchSelect
                            label="Site concerné"
                            value={formData.idSite}
                            onChange={(val) => handleChange('idSite', val)}
                            placeholder="-- Sélectionner un site --"
                            options={(sites || [])
                                .filter(s => s.estActif !== false && s.actif !== false || String(s.idSite) === String(formData.idSite))
                                .map(s => ({
                                    value: String(s.idSite),
                                    label: s.libelle,
                                    sublabel: s.codeSite
                                }))}
                        />
                    )}
                </div>

                <div className="p-4 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] space-y-3">
                    <label className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                        Traitement de Paie (Chômage Technique)
                    </label>
                    <div className="flex items-center gap-4">
                        <label className="flex items-center gap-2 text-sm font-semibold text-[var(--color-fg)]">
                            <SharedRadio plain
                                type="radio"
                                name="estPaye"
                                checked={formData.estPaye}
                                onChange={() => handleChange('estPaye', true)}
                                className="text-[var(--color-brand-500)] focus:ring-[var(--color-brand-500)]"
                            />
                            Maintenu / Payé
                        </label>
                        <label className="flex items-center gap-2 text-sm font-semibold text-[var(--color-fg)]">
                            <SharedRadio plain
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
