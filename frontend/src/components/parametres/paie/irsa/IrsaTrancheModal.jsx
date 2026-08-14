import { Percent, X, ShieldAlert } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import { fmtDateInput } from '@/utils/paieUtils';

export default function IrsaTrancheModal({
    isOpen,
    onClose,
    onSubmit,
    editingIrsa,
    formData,
    setFormData,
    actionError,
}) {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-4">
            <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-2xl max-w-md w-full overflow-hidden">
                <div className="px-6 py-4 border-b border-[var(--color-border-subtle)] flex justify-between items-center bg-[var(--color-bg-subtle)]">
                    <h3 className="font-bold text-[var(--color-fg)] flex items-center gap-2">
                        <Percent size={18} className="text-[var(--color-brand-500)]" />
                        {editingIrsa ? "Modifier la Tranche IRSA" : "Nouvelle Tranche IRSA"}
                    </h3>
                    <button onClick={onClose} className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] p-1 rounded-none">
                        <X size={18} />
                    </button>
                </div>

                <form onSubmit={onSubmit} className="p-6 space-y-4">
                    {actionError && (
                        <div className="bg-[var(--color-danger-500)]/10 text-[var(--color-danger-500)] border border-[var(--color-danger-500)]/20 rounded-none p-3 text-xs flex items-center gap-2">
                            <ShieldAlert size={16} />
                            {actionError}
                        </div>
                    )}

                    <Input
                        label="Numéro de Tranche"
                        type="number"
                        required
                        value={formData.numeroTranche}
                        onChange={(e) => setFormData({ ...formData, numeroTranche: parseInt(e.target.value) || 1 })}
                    />

                    <div className="grid grid-cols-2 gap-4">
                        <Input
                            label="Montant Min (Ar)"
                            type="number"
                            required
                            value={formData.trancheMin}
                            onChange={(e) => setFormData({ ...formData, trancheMin: parseFloat(e.target.value) || 0 })}
                        />
                        <Input
                            label="Montant Max (Ar)"
                            type="number"
                            value={formData.trancheMax}
                            onChange={(e) => setFormData({ ...formData, trancheMax: e.target.value })}
                            placeholder="Vide si illimité"
                        />
                    </div>

                    <Input
                        label="Taux IRSA (%)"
                        type="number"
                        step="0.01"
                        required
                        value={formData.taux}
                        onChange={(e) => setFormData({ ...formData, taux: parseFloat(e.target.value) || 0 })}
                    />

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
                            value={fmtDateInput(formData.dateFin)}
                            onChange={(e) => setFormData({ ...formData, dateFin: e.target.value })}
                        />
                    </div>

                    <div className="flex justify-end gap-3 pt-4 border-t border-[var(--color-border-subtle)]">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">
                            Annuler
                        </Button>
                        <Button type="submit" variant="primary" size="sm">
                            {editingIrsa ? "Enregistrer" : "Créer"}
                        </Button>
                    </div>
                </form>
            </div>
        </div>
    );
}