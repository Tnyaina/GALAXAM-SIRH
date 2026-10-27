import { MapPin } from 'lucide-react';
import SearchSelect from '@/components/ui/forms/SearchSelect';
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
    const siteOptions = sites
        .filter(s => (s.estActif !== false && s.actif !== false) || String(s.idSite) === String(formData.idSite))
        .map(s => ({ value: s.idSite, label: `${s.codeSite} — ${s.libelle}` }));

    const ferieOptions = feries
        .filter(f => !f.estNational)
        .map(f => ({ value: f.idFerie, label: `${fmtDate(f.dateFerie)} — ${f.libelle}` }));

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
                <SearchSelect
                    label="Site d'Exploitation"
                    required
                    placeholder="Choisir un site…"
                    value={formData.idSite}
                    onChange={(val) => {
                        setFormData({ ...formData, idSite: val });
                        if (fieldErrors.idSite && setFieldErrors) {
                            setFieldErrors(prev => ({ ...prev, idSite: undefined }));
                        }
                    }}
                    options={siteOptions}
                    error={fieldErrors.idSite}
                />

                <SearchSelect
                    label="Jour Férié Régional / Local"
                    required
                    placeholder="Choisir un jour férié…"
                    value={formData.idFerie}
                    onChange={(val) => {
                        setFormData({ ...formData, idFerie: val });
                        if (fieldErrors.idFerie && setFieldErrors) {
                            setFieldErrors(prev => ({ ...prev, idFerie: undefined }));
                        }
                    }}
                    options={ferieOptions}
                    error={fieldErrors.idFerie}
                />
            </div>
        </FormModal>
    );
}