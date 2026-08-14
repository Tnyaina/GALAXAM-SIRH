import { MapPin, X, ShieldAlert } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { Select } from '@/components/ui/forms/Input';

export default function SiteCotisationFormModal({
    isOpen,
    onClose,
    onSubmit,
    formData,
    setFormData,
    sites = [],
    cotisations = [],
    actionError,
}) {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-4">
            <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-2xl max-w-md w-full overflow-hidden">
                <div className="px-6 py-4 border-b border-[var(--color-border-subtle)] flex justify-between items-center bg-[var(--color-bg-subtle)]">
                    <h3 className="font-bold text-[var(--color-fg)] flex items-center gap-2">
                        <MapPin size={18} className="text-[var(--color-brand-500)]" />
                        Associer une Cotisation à un Site
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

                    <Select
                        label="Site d'Exploitation"
                        required
                        value={formData.idSite}
                        onChange={(e) => setFormData({ ...formData, idSite: e.target.value })}
                    >
                        <option value="">-- Sélectionner un site --</option>
                        {sites.map((s) => (
                            <option key={s.idSite} value={s.idSite}>
                                {s.libelle} ({s.codeSite})
                            </option>
                        ))}
                    </Select>

                    <Select
                        label="Type de Cotisation"
                        required
                        value={formData.idTypeCotisation}
                        onChange={(e) => setFormData({ ...formData, idTypeCotisation: e.target.value })}
                    >
                        <option value="">-- Sélectionner une cotisation --</option>
                        {cotisations.map((c) => (
                            <option key={c.idTypeCotisation} value={c.idTypeCotisation}>
                                {c.code} - {c.libelle} (S: {c.tauxSalarial}%, P: {c.tauxPatronal}%)
                            </option>
                        ))}
                    </Select>

                    <div className="flex justify-end gap-3 pt-4 border-t border-[var(--color-border-subtle)]">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">
                            Annuler
                        </Button>
                        <Button type="submit" variant="primary" size="sm">
                            Associer
                        </Button>
                    </div>
                </form>
            </div>
        </div>
    );
}