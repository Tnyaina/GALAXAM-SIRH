import { UserCheck, X, ShieldAlert } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input, { Checkbox } from '@/components/ui/forms/Input';

export default function TypeIntervenantFormModal({
    isOpen,
    onClose,
    onSubmit,
    editingIntervenant,
    formData,
    setFormData,
    actionError,
}) {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-4">
            <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-2xl w-full max-w-md overflow-hidden">
                <div className="px-6 py-4 border-b border-[var(--color-border-subtle)] flex justify-between items-center bg-[var(--color-bg-subtle)]">
                    <h3 className="font-bold text-[var(--color-fg)] flex items-center gap-2">
                        <UserCheck size={18} className="text-[var(--color-brand-500)]" />
                        {editingIntervenant ? "Modifier Type d'Intervenant" : "Nouveau Type d'Intervenant"}
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
                        label="Code (ex: SALARIE)"
                        required
                        disabled={!!editingIntervenant}
                        value={formData.code}
                        onChange={(e) => setFormData({ ...formData, code: e.target.value.toUpperCase() })}
                        placeholder="SALARIE"
                    />

                    <Input
                        label="Libellé complet"
                        required
                        value={formData.libelle}
                        onChange={(e) => setFormData({ ...formData, libelle: e.target.value })}
                        placeholder="Salarié National de Droit Commun"
                    />

                    <div className="space-y-2 pt-2 border-t border-[var(--color-border-subtle)]">
                        <p className="text-xs font-bold text-[var(--color-fg)] uppercase">Assujettissements Fiscaux & Sociaux</p>
                        <Checkbox
                            label="Soumis à l'Impôt IRSA"
                            checked={formData.soumisIrsa}
                            onChange={(e) => setFormData({ ...formData, soumisIrsa: e.target.checked })}
                        />
                        <Checkbox
                            label="Soumis au prélèvement ISI 5% (Consultants/Prestataires)"
                            checked={formData.soumisIsi5}
                            onChange={(e) => setFormData({ ...formData, soumisIsi5: e.target.checked })}
                        />
                        <Checkbox
                            label="Cotise à la CNaPS"
                            checked={formData.soumisCnaps}
                            onChange={(e) => setFormData({ ...formData, soumisCnaps: e.target.checked })}
                        />
                        <Checkbox
                            label="Cotise aux organismes de santé (OSTIE, FUNRECO, etc.)"
                            checked={formData.soumisSante}
                            onChange={(e) => setFormData({ ...formData, soumisSante: e.target.checked })}
                        />
                    </div>

                    <div className="flex justify-end gap-3 pt-4 border-t border-[var(--color-border-subtle)]">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">
                            Annuler
                        </Button>
                        <Button type="submit" variant="primary" size="sm">
                            {editingIntervenant ? "Enregistrer" : "Créer"}
                        </Button>
                    </div>
                </form>
            </div>
        </div>
    );
}