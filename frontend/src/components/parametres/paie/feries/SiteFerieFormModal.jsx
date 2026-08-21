import { MapPin, X, ShieldAlert } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { Select } from '@/components/ui/forms/Input';
import { fmtDate } from '@/utils/paieUtils';
import Alert from '@/components/ui/feedback/Alert';

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
            <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-2xl w-full max-w-md overflow-hidden">
                <div className="px-6 py-4 border-b border-[var(--color-border-subtle)] flex justify-between items-center bg-[var(--color-bg-subtle)]">
                    <h3 className="font-bold text-[var(--color-fg)] flex items-center gap-2">
                        <MapPin size={18} className="text-[var(--color-brand-500)]" />
                        Affecter un Jour Férié à un Site
                    </h3>
                    <button onClick={onClose} className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] p-1 rounded-none">
                        <X size={18} />
                    </button>
                </div>

                <form onSubmit={onSubmit} className="p-6 space-y-4">
                    {actionError && <Alert type="error" message={actionError} />}

                    <Select
                        label="Site d'Exploitation"
                        required
                        value={formData.idSite}
                        onChange={(e) => setFormData({ ...formData, idSite: e.target.value })}
                    >
                        <option value="">-- Choisir un site --</option>
                        {sites.map((s) => (
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
                        {feries.filter(f => f.estNational === false).map((f) => (
                            <option key={f.idFerie} value={f.idFerie}>
                                {fmtDate(f.dateFerie)} — {f.libelle}
                            </option>
                        ))}
                    </Select>

                    <div className="flex justify-end gap-3 pt-4 border-t border-[var(--color-border-subtle)]">
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