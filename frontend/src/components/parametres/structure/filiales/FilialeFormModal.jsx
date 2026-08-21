import { Building2, X, ShieldAlert } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';

export default function FilialeFormModal({
    isOpen,
    onClose,
    onSubmit,
    selectedFiliale,
    formData,
    setFormData,
    actionError,
    submitting = false,
}) {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-4">
            <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-2xl max-w-md w-full overflow-hidden">
                <div className="px-6 py-4 border-b border-[var(--color-border-subtle)] flex justify-between items-center bg-[var(--color-bg-subtle)]">
                    <h3 className="font-bold text-[var(--color-fg)] flex items-center gap-2">
                        <Building2 size={18} className="text-[var(--color-brand-500)]" />
                        {selectedFiliale ? "Modifier la Filiale" : "Nouvelle Filiale"}
                    </h3>
                    <button onClick={onClose} className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] p-1 rounded-none">
                        <X size={18} />
                    </button>
                </div>

                <form onSubmit={onSubmit} className="p-6 space-y-4">
                    {actionError && (
                        <div className="bg-[var(--color-danger-500)]/10 text-[var(--color-danger-500)] border border-[var(--color-danger-500)]/20 rounded-none p-3 text-xs flex items-center gap-1.5">
                            <ShieldAlert size={14} />
                            {actionError}
                        </div>
                    )}

                    <Input
                        label="Code Filiale"
                        required
                        disabled={!!selectedFiliale}
                        value={formData.codeFiliale}
                        onChange={(e) => setFormData({ ...formData, codeFiliale: e.target.value.toUpperCase() })}
                        placeholder="Ex: LOG, VA"
                    />

                    <Input
                        label="Raison Sociale"
                        required
                        value={formData.raisonSociale}
                        onChange={(e) => setFormData({ ...formData, raisonSociale: e.target.value })}
                        placeholder="Ex: Galaxam Logistics SARL"
                    />

                    <div className="grid grid-cols-2 gap-4">
                        <Input
                            label="NIF"
                            value={formData.nif}
                            onChange={(e) => setFormData({ ...formData, nif: e.target.value })}
                            placeholder="Numéro NIF"
                        />
                        <Input
                            label="STAT"
                            value={formData.stat}
                            onChange={(e) => setFormData({ ...formData, stat: e.target.value })}
                            placeholder="Numéro STAT"
                        />
                    </div>

                    <Input
                        label="Seuil d'Alerte Effectif"
                        type="number"
                        value={formData.seuilEffectifAlerte}
                        onChange={(e) => setFormData({ ...formData, seuilEffectifAlerte: parseInt(e.target.value) || 0 })}
                    />

                    <div className="flex justify-end gap-3 pt-4 border-t border-[var(--color-border-subtle)]">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">
                            Annuler
                        </Button>
                        <Button type="submit" variant="primary" size="sm" loading={submitting}>
                            Enregistrer
                        </Button>
                    </div>
                </form>
            </div>
        </div>
    );
}