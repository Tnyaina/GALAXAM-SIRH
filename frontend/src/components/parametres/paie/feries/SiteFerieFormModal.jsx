import { MapPin } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { Select } from '@/components/ui/forms/Input';
import { fmtDate } from '@/utils/paieUtils';
import Modal from '@/components/ui/overlays/Modal';
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
    submitting = false,
}) {
    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title="Affecter un Jour Férié à un Site"
            icon={<MapPin size={18} />}
            size="md"
            footer={
                <>
                    <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>
                        Annuler
                    </Button>
                    <Button type="submit" form="site-ferie-form" variant="primary" size="sm" loading={submitting}>
                        Affecter au site
                    </Button>
                </>
            }
        >
            <form id="site-ferie-form" onSubmit={onSubmit} className="space-y-4">
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
                    label="Jour Férié Régional / Local"
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
            </form>
        </Modal>
    );
}