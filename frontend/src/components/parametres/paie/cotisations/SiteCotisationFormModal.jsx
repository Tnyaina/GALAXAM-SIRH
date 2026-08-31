import { MapPin } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { Select } from '@/components/ui/forms/Input';
import Modal from '@/components/ui/overlays/Modal';
import Alert from '@/components/ui/feedback/Alert';

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
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title="Associer une Cotisation à un Site"
            icon={<MapPin size={18} />}
            size="md"
            footer={
                <>
                    <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>
                        Annuler
                    </Button>
                    <Button type="submit" form="site-cotisation-form" variant="primary" size="sm" loading={submitting}>
                        Associer
                    </Button>
                </>
            }
        >
            <form id="site-cotisation-form" onSubmit={onSubmit} className="space-y-4">
                {actionError && (
                    <Alert type="error" message={actionError} />
                )}

                <Select
                    label="Site d'Exploitation"
                    required
                    value={formData.idSite}
                    onChange={(e) => setFormData({ ...formData, idSite: e.target.value })}
                >
                    <option value="">-- Sélectionner un site --</option>
                    {sites.map((s) => (
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
                    {cotisations.map((c) => (
                        <option key={c.idTypeCotisation} value={c.idTypeCotisation}>
                            {c.code} - {c.libelle} (Salarial: {c.tauxSalarial}%, Patronal: {c.tauxPatronal}%)
                        </option>
                    ))}
                </Select>
            </form>
        </Modal>
    );
}