import { useState } from 'react';
import { ShieldCheck, X, ShieldAlert } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
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

    if (!isOpen) return null;

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

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-4">
            <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-2xl max-w-md w-full overflow-hidden">
                <div className="px-6 py-4 border-b border-[var(--color-border-subtle)] flex justify-between items-center bg-[var(--color-bg-subtle)]">
                    <h3 className="font-bold text-[var(--color-fg)] flex items-center gap-2">
                        <ShieldCheck size={18} className="text-[var(--color-brand-500)]" />
                        {editingCotisation ? "Modifier la Cotisation" : "Nouvelle Cotisation"}
                    </h3>
                    <button onClick={onClose} className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] p-1 rounded-none">
                        <X size={18} />
                    </button>
                </div>

                <form onSubmit={handleSubmit} className="p-6 space-y-4">
                    {(actionError || localError) && (
                        <div className="bg-[var(--color-danger-500)]/10 text-[var(--color-danger-500)] border border-[var(--color-danger-500)]/20 rounded-none p-3 text-xs flex items-center gap-2">
                            <ShieldAlert size={16} />
                            {localError || actionError}
                        </div>
                    )}

                    <Input
                        label="Code Cotisation"
                        required
                        disabled={!!editingCotisation}
                        value={formData.code}
                        onChange={(e) => setFormData({ ...formData, code: e.target.value.toUpperCase() })}
                        placeholder="Ex: CNAPS, OSTIE, FUNERAIRE"
                    />

                    <Input
                        label="Libellé"
                        required
                        value={formData.libelle}
                        onChange={(e) => setFormData({ ...formData, libelle: e.target.value })}
                        placeholder="Ex: Caisse Nationale de Prévoyance Sociale"
                    />

                    <div className="grid grid-cols-2 gap-4">
                        <Input
                            label="Taux Salarial (%)"
                            type="number"
                            min="0"
                            max="100"
                            step="0.01"
                            required
                            value={formData.tauxSalarial}
                            onChange={(e) => setFormData({ ...formData, tauxSalarial: parseFloat(e.target.value) || 0 })}
                        />
                        <Input
                            label="Taux Patronal (%)"
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
                            label="Date Début"
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

                    <div className="flex justify-end gap-3 pt-4 border-t border-[var(--color-border-subtle)]">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">
                            Annuler
                        </Button>
                        <Button type="submit" variant="primary" size="sm" loading={submitting}>
                            {editingCotisation ? "Enregistrer" : "Créer"}
                        </Button>
                    </div>
                </form>
            </div>
        </div>
    );
}