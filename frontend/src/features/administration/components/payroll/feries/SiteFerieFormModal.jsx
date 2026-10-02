import { MapPin } from 'lucide-react';
import { Select } from '@/components/ui/forms/Input';
import FormModal from '@/components/ui/overlays/FormModal';
import { fmtDate } from '@/utils/dateUtils';

export default function SiteFerieFormModal({
    isOpen,
    onClose,
    onSubmit,
    formData,
    setFormData,
    sites = [],
    feries = [],
    actionError,
    fieldErrors = {},
    setFieldErrors,
    submitting = false,
}) {
    return (
        <FormModal
            isOpen={isOpen}
            onClose={onClose}
            onSubmit={onSubmit}
            title="Affecter un Jour Férié à un Site"
            icon={<MapPin size={18} />}
            size="md"
            submitLabel="Affecter au site"
            loading={submitting}
            error={actionError}
        >
            <div className="space-y-4">
                <Select
                    label="Site d'Exploitation"
                    required
                    value={formData.idSite}
                    onChange={(e) => {
                        setFormData({ ...formData, idSite: e.target.value });
                        if (fieldErrors.idSite && setFieldErrors) {
                            setFieldErrors(prev => ({ ...prev, idSite: undefined }));
                        }
                    }}
                    error={fieldErrors.idSite}
                >
                    <option value="">-- Choisir un site --</option>
                    {sites
                        .filter(s => (s.estActif !== false && s.actif !== false) || String(s.idSite) === String(formData.idSite))
                        .map((s) => (
                            <option key={s.idSite} value={s.idSite}>
                                {s.codeSite} — {s.libelle}
                            </option>
                        ))}
                </Select>

                <Select
                    label="Jour Férié Régional / Local"
                    required
                    value={formData.idFerie}
                    onChange={(e) => {
                        setFormData({ ...formData, idFerie: e.target.value });
                        if (fieldErrors.idFerie && setFieldErrors) {
                            setFieldErrors(prev => ({ ...prev, idFerie: undefined }));
                        }
                    }}
                    error={fieldErrors.idFerie}
                >
                    <option value="">-- Choisir un jour férié --</option>
                    {feries
                        .filter(f => !f.estNational)
                        .map((f) => (
                            <option key={f.idFerie} value={f.idFerie}>
                                {fmtDate(f.dateFerie)} — {f.libelle}
                            </option>
                        ))}
                </Select>
            </div>
        </FormModal>
    );
}