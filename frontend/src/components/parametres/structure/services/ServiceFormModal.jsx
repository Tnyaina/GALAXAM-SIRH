import { Layers, X, ShieldAlert } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input, { Select } from '@/components/ui/forms/Input';

export default function ServiceFormModal({
    isOpen,
    onClose,
    onSubmit,
    editingService,
    formData,
    setFormData,
    departements = [],
    actionError,
    submitting = false,
}) {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-4">
            <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-2xl max-w-md w-full overflow-hidden">
                <div className="px-6 py-4 border-b border-[var(--color-border-subtle)] flex justify-between items-center bg-[var(--color-bg-subtle)]">
                    <h3 className="font-bold text-[var(--color-fg)] flex items-center gap-2">
                        <Layers size={18} className="text-[var(--color-brand-500)]" />
                        {editingService ? "Modifier le Service" : "Nouveau Service"}
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

                    <Select
                        label="Département de Rattachement"
                        required
                        value={formData.idDepartement}
                        onChange={(e) => setFormData({ ...formData, idDepartement: e.target.value })}
                    >
                        <option value="">-- Sélectionner un département --</option>
                        {departements.map((d) => (
                            <option key={d.idDepartement} value={d.idDepartement}>
                                {d.libelle} ({d.codeDepartement})
                            </option>
                        ))}
                    </Select>

                    <Input
                        label="Code Service"
                        required
                        value={formData.codeService}
                        onChange={(e) => setFormData({ ...formData, codeService: e.target.value.toUpperCase() })}
                        placeholder="Ex: ADM, OPE"
                    />

                    <Input
                        label="Libellé du Service"
                        required
                        value={formData.libelle}
                        onChange={(e) => setFormData({ ...formData, libelle: e.target.value })}
                        placeholder="Ex: Administration & RH"
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