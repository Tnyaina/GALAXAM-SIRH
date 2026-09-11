import { useState } from 'react';
import { ShieldCheck, Building2, MapPin } from 'lucide-react';
import Input from '@/components/ui/forms/Input';
import FormModal from '@/components/ui/overlays/FormModal';
import { fmtDateInput } from '@/utils/paieUtils';

export default function CotisationFormModal({
    isOpen,
    onClose,
    onSubmit,
    editingCotisation,
    formData,
    setFormData,
    actionError,
    submitting = false,
}) {
    const [localError, setLocalError] = useState(null);

    const handleSubmit = (e) => {
        e.preventDefault();
        setLocalError(null);

        const salarial = parseFloat(formData.tauxSalarial);
        const patronal = parseFloat(formData.tauxPatronal);

        if (isNaN(salarial) || salarial < 0 || salarial > 100) {
            setLocalError("Le taux salarial doit être compris entre 0% et 100%.");
            return;
        }
        if (isNaN(patronal) || patronal < 0 || patronal > 100) {
            setLocalError("Le taux patronal doit être compris entre 0% et 100%.");
            return;
        }
        if (formData.dateFin && formData.dateDebut && formData.dateFin < formData.dateDebut) {
            setLocalError("La date de fin ne peut pas être antérieure à la date de début.");
            return;
        }

        onSubmit(e);
    };

    const portee = formData.portee || 'SITE';

    return (
        <FormModal
            isOpen={isOpen}
            onClose={onClose}
            onSubmit={handleSubmit}
            title={editingCotisation ? "Modifier la Cotisation Sociale" : "Nouvelle Cotisation Sociale"}
            icon={<ShieldCheck size={18} />}
            size="lg"
            isEdit={!!editingCotisation}
            loading={submitting}
            error={localError || actionError}
        >
            <div className="grid grid-cols-2 gap-4">
                <Input
                    label="Code Cotisation"
                    required
                    disabled={!!editingCotisation}
                    value={formData.code}
                    onChange={(e) => setFormData({ ...formData, code: e.target.value.toUpperCase() })}
                    placeholder="Ex: CNAPS, OSTIE, FUNERAIRE"
                />
                {/* Périmètre */}
                <div>
                    <label className="block text-xs font-bold uppercase tracking-wide text-[var(--color-fg-muted)] mb-1">
                        Périmètre d'application <span className="text-[var(--color-danger-500)]">*</span>
                    </label>
                    <div className="flex gap-2">
                        <button
                            type="button"
                            onClick={() => setFormData({ ...formData, portee: 'SITE' })}
                            className={`flex-1 flex items-center justify-center gap-1.5 px-3 py-2 border text-xs font-semibold transition-all ${
                                portee === 'SITE'
                                    ? 'bg-[var(--color-success-500)]/10 border-[var(--color-success-500)]/40 text-[var(--color-success-600)]'
                                    : 'border-[var(--color-border-subtle)] text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'
                            }`}
                        >
                            <MapPin size={13} />
                            Par site
                        </button>
                        <button
                            type="button"
                            onClick={() => setFormData({ ...formData, portee: 'ENTREPRISE' })}
                            className={`flex-1 flex items-center justify-center gap-1.5 px-3 py-2 border text-xs font-semibold transition-all ${
                                portee === 'ENTREPRISE'
                                    ? 'bg-[var(--color-brand-500)]/10 border-[var(--color-brand-500)]/40 text-[var(--color-brand-600)]'
                                    : 'border-[var(--color-border-subtle)] text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'
                            }`}
                        >
                            <Building2 size={13} />
                            Entreprise entière
                        </button>
                    </div>
                    <p className="text-[11px] text-[var(--color-fg-muted)] mt-1">
                        {portee === 'ENTREPRISE'
                            ? 'Appliquée à tous les salariés sans affectation par site (ex : FMFP, ISI).'
                            : 'Nécessite une association dans l\'onglet « Cotisations par Site » (ex : CNaPS, OSTIE).'}
                    </p>
                </div>
            </div>

            <Input
                label="Libellé Complet"
                required
                value={formData.libelle}
                onChange={(e) => setFormData({ ...formData, libelle: e.target.value })}
                placeholder="Ex: Caisse Nationale de Prévoyance Sociale"
            />

            <div className="grid grid-cols-2 gap-4">
                <Input
                    label="Part Salariale (%)"
                    type="number"
                    min="0"
                    max="100"
                    step="0.01"
                    required
                    value={formData.tauxSalarial}
                    onChange={(e) => setFormData({ ...formData, tauxSalarial: parseFloat(e.target.value) || 0 })}
                />
                <Input
                    label="Part Patronale (%)"
                    type="number"
                    min="0"
                    max="100"
                    step="0.01"
                    required
                    value={formData.tauxPatronal}
                    onChange={(e) => setFormData({ ...formData, tauxPatronal: parseFloat(e.target.value) || 0 })}
                />
            </div>

            <div className="grid grid-cols-2 gap-4">
                <Input
                    label="Date Début (Prise d'effet)"
                    type="date"
                    required
                    value={fmtDateInput(formData.dateDebut)}
                    onChange={(e) => setFormData({ ...formData, dateDebut: e.target.value })}
                />
                <Input
                    label="Date Fin (Optionnel)"
                    type="date"
                    min={fmtDateInput(formData.dateDebut)}
                    value={fmtDateInput(formData.dateFin)}
                    onChange={(e) => setFormData({ ...formData, dateFin: e.target.value })}
                />
            </div>
        </FormModal>
    );
}