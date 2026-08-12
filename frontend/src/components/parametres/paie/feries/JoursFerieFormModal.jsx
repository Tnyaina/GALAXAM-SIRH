import { Calendar, X, ShieldAlert } from 'lucide-react';
import Button from '@/components/ui/Button';
import Input, { Checkbox } from '@/components/ui/Input';

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
            <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-2xl w-full max-w-md overflow-hidden">
                <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 flex justify-between items-center bg-ink-50/50 dark:bg-ink-950/40">
                    <h3 className="font-bold text-ink-900 dark:text-white flex items-center gap-2">
                        <Calendar size={18} className="text-brand-500" />
                        {editingFerie ? "Modifier le Jour Férié" : "Nouveau Jour Férié"}
                    </h3>
                    <button onClick={onClose} className="text-ink-400 hover:text-ink-900 dark:hover:text-white p-1 rounded-sm">
                        <X size={18} />
                    </button>
                </div>

                <form onSubmit={onSubmit} className="p-6 space-y-4">
                    {actionError && (
                        <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs flex items-center gap-2">
                            <ShieldAlert size={16} />
                            {actionError}
                        </div>
                    )}

                    <Input
                        label="Date exacte"
                        type="date"
                        required
                        value={formData.dateFerie}
                        onChange={(e) => setFormData({ ...formData, dateFerie: e.target.value })}
                    />

                    <Input
                        label="Libellé de la fête / Férié"
                        required
                        value={formData.libelle}
                        onChange={(e) => setFormData({ ...formData, libelle: e.target.value })}
                        placeholder="Fête de l'Indépendance"
                    />

                    <div className="space-y-2 pt-2 border-t border-ink-150 dark:border-ink-700">
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

                    <div className="flex justify-end gap-3 pt-4 border-t border-ink-150 dark:border-ink-700">
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
