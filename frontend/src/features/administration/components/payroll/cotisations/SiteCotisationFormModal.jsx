import { MapPin } from 'lucide-react';
import { Select } from '@/components/ui/forms/Input';
import FormModal from '@/components/ui/overlays/FormModal';

export default function SiteCotisationFormModal({
    isOpen,
    onClose,
    onSubmit,
    formData,
    setFormData,
    sites = [],
    cotisations = [],
    actionError,
    submitting = false,
}) {
    return (
        <FormModal
            isOpen={isOpen}
            onClose={onClose}
            onSubmit={onSubmit}
            title="Associer une Cotisation à un Site"
            icon={<MapPin size={18} />}
            size="md"
            submitLabel="Associer"
            loading={submitting}
            error={actionError}
        >
            <Select
                label="Site d'Exploitation"
                required
                value={formData.idSite}
                onChange={(e) => setFormData({ ...formData, idSite: e.target.value })}
            >
                <option value="">-- Sélectionner un site --</option>
                {sites
                    .filter(s => s.estActif !== false && s.actif !== false || String(s.idSite) === String(formData.idSite))
                    .map((s) => (
                        <option key={s.idSite} value={s.idSite}>
                            {s.libelle} ({s.codeSite})
                        </option>
                    ))}
            </Select>

            <Select
                label="Type de Cotisation"
                required
                value={formData.idTypeCotisation}
                onChange={(e) => setFormData({ ...formData, idTypeCotisation: e.target.value })}
            >
                <option value="">-- Sélectionner une cotisation --</option>
                {cotisations
                    .filter(c => !c.portee || c.portee === 'SITE')
                    .map((c) => (
                        <option key={c.idTypeCotisation} value={c.idTypeCotisation}>
                            {c.code} - {c.libelle} (Salarial: {c.tauxSalarial}%, Patronal: {c.tauxPatronal}%)
                        </option>
                    ))}
            </Select>
        </FormModal>
    );
}