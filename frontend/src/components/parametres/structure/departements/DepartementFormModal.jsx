import { Building, X, ShieldAlert } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import { fmtDateInput } from '@/utils/paieUtils';

export default function DepartementFormModal({
    isOpen,
    onClose,
    onSubmit,
    editingDept,
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
                        <Building size={18} className="text-[var(--color-brand-500)]" />
                        {editingDept ? "Modifier le Département" : "Nouveau Département"}
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
                        label="Code Département"
                        required
                        disabled={!!editingDept}
                        value={formData.codeDepartement}
                        onChange={(e) => setFormData({ ...formData, codeDepartement: e.target.value.toUpperCase() })}
                        placeholder="Ex: LOG, LMD"
                    />

                    <Input
                        label="Libellé"
                        required
                        value={formData.libelle}
                        onChange={(e) => setFormData({ ...formData, libelle: e.target.value })}
                        placeholder="Ex: Galaxam Logistics Core"
                    />

                    <Input
                        label="Date Fin Prévue (Optionnel)"
                        type="date"
                        value={fmtDateInput(formData.dateFinPrevue)}
                        onChange={(e) => setFormData({ ...formData, dateFinPrevue: e.target.value })}
                    />

                    <div className="flex justify-end gap-3 pt-4 border-t border-[var(--color-border-subtle)]">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">
                            Annuler
                        </Button>
                        <Button type="submit" variant="primary" size="sm">
                            Enregistrer
                        </Button>
                    </div>
                </form>
            </div>
        </div>
    );
}