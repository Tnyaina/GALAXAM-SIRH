import { MapPin, X, ShieldAlert } from 'lucide-react';
import Button from '@/components/ui/Button';
import { Select } from '@/components/ui/Input';

function formatDateDisplay(dateStr) {
    if (!dateStr) return '-';
    const cleaned = dateStr.includes('T') ? dateStr.split('T')[0] : dateStr;
    const parts = cleaned.split('-');
    if (parts.length === 3) {
        return `${parts[2]}/${parts[1]}/${parts[0]}`;
    }
    return dateStr;
}

export default function SiteFerieFormModal({
    isOpen,
    onClose,
    onSubmit,
    formData,
    setFormData,
    sites = [],
    feries = [],
    actionError,
}) {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-4">
            <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-2xl w-full max-w-md overflow-hidden">
                <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 flex justify-between items-center bg-ink-50/50 dark:bg-ink-950/40">
                    <h3 className="font-bold text-ink-900 dark:text-white flex items-center gap-2">
                        <MapPin size={18} className="text-brand-500" />
                        Affecter un Jour Férié à un Site
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

                    <Select
                        label="Site d'Exploitation"
                        required
                        value={formData.idSite}
                        onChange={(e) => setFormData({ ...formData, idSite: e.target.value })}
                    >
                        <option value="">-- Choisir un site --</option>
                        {sites.map(s => (
                            <option key={s.idSite} value={s.idSite}>
                                {s.codeSite} — {s.libelle}
                            </option>
                        ))}
                    </Select>

                    <Select
                        label="Jour Férié"
                        required
                        value={formData.idFerie}
                        onChange={(e) => setFormData({ ...formData, idFerie: e.target.value })}
                    >
                        <option value="">-- Choisir un jour férié --</option>
                        {feries.map(f => (
                            <option key={f.idFerie} value={f.idFerie}>
                                {formatDateDisplay(f.dateFerie)} — {f.libelle}
                            </option>
                        ))}
                    </Select>

                    <div className="flex justify-end gap-3 pt-4 border-t border-ink-150 dark:border-ink-700">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">
                            Annuler
                        </Button>
                        <Button type="submit" variant="primary" size="sm">
                            Affecter au site
                        </Button>
                    </div>
                </form>
            </div>
        </div>
    );
}
