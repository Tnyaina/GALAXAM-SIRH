import { Calendar, X, ShieldAlert } from 'lucide-react';
import Button from '@/components/ui/Button';
import Input, { Checkbox } from '@/components/ui/Input';
import { fmtDateInput } from '@/utils/paieUtils';

export default function JoursFerieFormModal({
    isOpen,
    onClose,
    onSubmit,
    editingFerie,
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
                        <Calendar size={18} className="text-[var(--color-brand-500)]" />
                        {editingFerie ? "Modifier le Jour Férié" : "Nouveau Jour Férié"}
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
                        label="Date exacte"
                        type="date"
                        required
                        value={fmtDateInput(formData.dateFerie)}
                        onChange={(e) => setFormData({ ...formData, dateFerie: e.target.value })}
                    />

                    <Input
                        label="Libellé de la fête / Férié"
                        required
                        value={formData.libelle}
                        onChange={(e) => setFormData({ ...formData, libelle: e.target.value })}
                        placeholder="Fête de l'Indépendance"
                    />

                    <div className="space-y-2 pt-2 border-t border-[var(--color-border-subtle)]">
                        <Checkbox
                            label="Jour férié chômé et payé"
                            checked={formData.estChome}
                            onChange={(e) => setFormData({ ...formData, estChome: e.target.checked })}
                        />
                        <Checkbox
                            label="Férié national (s'applique par défaut à tous les sites)"
                            checked={formData.estNational}
                            onChange={(e) => setFormData({ ...formData, estNational: e.target.checked })}
                        />
                    </div>

                    <div className="flex justify-end gap-3 pt-4 border-t border-[var(--color-border-subtle)]">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">
                            Annuler
                        </Button>
                        <Button type="submit" variant="primary" size="sm">
                            {editingFerie ? "Enregistrer" : "Créer"}
                        </Button>
                    </div>
                </form>
            </div>
        </div>
    );
}