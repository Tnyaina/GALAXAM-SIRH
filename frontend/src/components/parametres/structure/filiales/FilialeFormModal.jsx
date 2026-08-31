import { Building2 } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Alert from '@/components/ui/feedback/Alert';

export default function FilialeFormModal({
    isOpen,
    onClose,
    onSubmit,
    selectedFiliale,
    formData,
    setFormData,
    actionError,
    submitting = false,
}) {
    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={selectedFiliale ? "Modifier la Filiale" : "Nouvelle Filiale"}
            icon={<Building2 size={18} />}
            size="md"
            footer={
                <>
                    <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>
                        Annuler
                    </Button>
                    <Button type="submit" form="filiale-form" variant="primary" size="sm" loading={submitting}>
                        {selectedFiliale ? "Enregistrer les modifications" : "Créer la filiale"}
                    </Button>
                </>
            }
        >
            <form id="filiale-form" onSubmit={onSubmit} className="space-y-4">
                {actionError && <Alert type="error" message={actionError} />}

                <div className="space-y-3">
                    <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                        Identification Juridique
                    </h4>
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                        <div className="sm:col-span-1">
                            <Input
                                label="Code"
                                required
                                disabled={!!selectedFiliale}
                                value={formData.codeFiliale}
                                onChange={(e) => setFormData({ ...formData, codeFiliale: e.target.value.toUpperCase() })}
                                placeholder="Ex: LOG"
                            />
                        </div>
                        <div className="sm:col-span-2">
                            <Input
                                label="Raison Sociale"
                                required
                                value={formData.raisonSociale}
                                onChange={(e) => setFormData({ ...formData, raisonSociale: e.target.value })}
                                placeholder="Ex: Galaxam Logistics SARL"
                            />
                        </div>
                    </div>
                </div>

                <div className="space-y-3 pt-2 border-t border-[var(--color-border-subtle)]">
                    <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                        Immatriculations Fiscales & Légales
                    </h4>
                    <div className="grid grid-cols-2 gap-3">
                        <Input
                            label="Numéro NIF"
                            value={formData.nif}
                            onChange={(e) => setFormData({ ...formData, nif: e.target.value })}
                            placeholder="3000123456"
                        />
                        <Input
                            label="Numéro STAT"
                            value={formData.stat}
                            onChange={(e) => setFormData({ ...formData, stat: e.target.value })}
                            placeholder="49231 11 2020 0 10234"
                        />
                    </div>
                </div>

                <div className="space-y-2 pt-2 border-t border-[var(--color-border-subtle)]">
                    <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                        Paramètres Sociaux & Alertes RH
                    </h4>
                    <Input
                        label="Seuil d'Alerte Effectif (Délégués du personnel / CSE)"
                        type="number"
                        min="1"
                        value={formData.seuilEffectifAlerte}
                        onChange={(e) => setFormData({ ...formData, seuilEffectifAlerte: parseInt(e.target.value) || 0 })}
                        hint="Génère une alerte lorsque l'effectif actif de cette filiale atteint ce palier réglementaire."
                    />
                </div>
            </form>
        </Modal>
    );
}