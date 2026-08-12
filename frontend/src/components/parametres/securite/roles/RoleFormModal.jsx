import { Shield, X, ShieldAlert } from 'lucide-react';
import Button from '@/components/ui/Button';
import Input from '@/components/ui/Input';

export default function RoleFormModal({
    isOpen,
    onClose,
    onSubmit,
    editingRole,
    formData,
    setFormData,
    roleModalError,
}) {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-4">
            <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-2xl w-full max-w-md overflow-hidden">
                <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 flex justify-between items-center bg-ink-50/50 dark:bg-ink-950/40">
                    <h3 className="font-bold text-ink-900 dark:text-white flex items-center gap-2">
                        <Shield size={18} className="text-brand-500" />
                        {editingRole ? "Modifier le Rôle" : "Nouveau Rôle"}
                    </h3>
                    <button
                        onClick={onClose}
                        className="text-ink-400 hover:text-ink-900 dark:hover:text-white p-1 rounded-sm"
                    >
                        <X size={18} />
                    </button>
                </div>

                <form onSubmit={onSubmit} className="p-6 space-y-4">
                    {roleModalError && (
                        <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs flex items-center gap-2">
                            <ShieldAlert size={16} />
                            {roleModalError}
                        </div>
                    )}

                    <Input
                        label="Code du rôle (ex: ADMIN_PAIE)"
                        required
                        disabled={!!editingRole}
                        value={formData.codeRole}
                        onChange={(e) => setFormData({ ...formData, codeRole: e.target.value })}
                        placeholder="ADMIN_RH"
                    />

                    <Input
                        label="Libellé affiché"
                        required
                        value={formData.libelle}
                        onChange={(e) => setFormData({ ...formData, libelle: e.target.value })}
                        placeholder="Administrateur RH"
                    />

                    <div className="flex justify-end gap-3 pt-4 border-t border-ink-150 dark:border-ink-700">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">
                            Annuler
                        </Button>
                        <Button type="submit" variant="primary" size="sm">
                            {editingRole ? "Mettre à jour" : "Créer le rôle"}
                        </Button>
                    </div>
                </form>
            </div>
        </div>
    );
}
