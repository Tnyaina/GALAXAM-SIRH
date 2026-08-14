import { Landmark, X, ShieldAlert } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';

export default function BanqueFormModal({
    isOpen,
    onClose,
    onSubmit,
    editingBanque,
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
                        <Landmark size={18} className="text-[var(--color-brand-500)]" />
                        {editingBanque ? "Modifier Banque" : "Nouvelle Banque"}
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
                        label="Code Banque (ex: 00004)"
                        required
                        disabled={!!editingBanque}
                        value={formData.codeBanque}
                        onChange={(e) => setFormData({ ...formData, codeBanque: e.target.value.toUpperCase() })}
                        placeholder="00004"
                    />

                    <Input
                        label="Nom de la banque"
                        required
                        value={formData.nomBanque}
                        onChange={(e) => setFormData({ ...formData, nomBanque: e.target.value })}
                        placeholder="BOA Madagascar"
                    />

                    <Input
                        label="Code SWIFT / BIC (optionnel)"
                        value={formData.codeSwift}
                        onChange={(e) => setFormData({ ...formData, codeSwift: e.target.value.toUpperCase() })}
                        placeholder="AFRIMGM1"
                    />

                    <div className="flex justify-end gap-3 pt-4 border-t border-[var(--color-border-subtle)]">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">
                            Annuler
                        </Button>
                        <Button type="submit" variant="primary" size="sm">
                            {editingBanque ? "Enregistrer" : "Créer"}
                        </Button>
                    </div>
                </form>
            </div>
        </div>
    );
}