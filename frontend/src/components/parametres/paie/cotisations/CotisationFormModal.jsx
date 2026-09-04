import { useState } from 'react';
import { ShieldCheck } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Modal from '@/components/ui/overlays/Modal';
import Alert from '@/components/ui/feedback/Alert';
import { fmtDateInput } from '@/utils/paieUtils';

export default function CotisationFormModal({
    isOpen,
    onClose,
    onSubmit,
    editingCotisation,
    formData,
    setFormData,
    actionError,
    submitting = false,
}) {
    const [localError, setLocalError] = useState(null);

    const handleSubmit = (e) => {
        e.preventDefault();
        setLocalError(null);

        const salarial = parseFloat(formData.tauxSalarial);
        const patronal = parseFloat(formData.tauxPatronal);

        if (isNaN(salarial) || salarial < 0 || salarial > 100) {
            setLocalError("Le taux salarial doit être compris entre 0% et 100%.");
            return;
        }

        if (isNaN(patronal) || patronal < 0 || patronal > 100) {
            setLocalError("Le taux patronal doit être compris entre 0% et 100%.");
            return;
        }

        if (formData.dateFin && formData.dateDebut && formData.dateFin < formData.dateDebut) {
            setLocalError("La date de fin ne peut pas être antérieure à la date de début.");
            return;
        }

        onSubmit(e);
    };

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={editingCotisation ? "Modifier la Cotisation Sociale" : "Nouvelle Cotisation Sociale"}
            icon={<ShieldCheck size={18} />}
            size="md"
            footer={
                <>
                    <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>
                        Annuler
                    </Button>
                    <Button type="submit" form="cotisation-form" variant="primary" size="sm" loading={submitting}>
                        {editingCotisation ? "Enregistrer" : "Créer la cotisation"}
                    </Button>
                </>
            }
        >
            <form id="cotisation-form" onSubmit={handleSubmit} className="space-y-4">
                {(actionError || localError) && (
                    <Alert type="error" message={localError || actionError} />
                )}

                <Input
                    label="Code Cotisation"
                    required
                    disabled={!!editingCotisation}
                    value={formData.code}
                    onChange={(e) => setFormData({ ...formData, code: e.target.value.toUpperCase() })}
                    placeholder="Ex: CNAPS, OSTIE, FUNERAIRE"
                />

                <Input
                    label="Libellé Complet"
                    required
                    value={formData.libelle}
                    onChange={(e) => setFormData({ ...formData, libelle: e.target.value })}
                    placeholder="Ex: Caisse Nationale de Prévoyance Sociale"
                />

                <div className="grid grid-cols-2 gap-4">
                    <Input
                        label="Part Salariale (%)"
                        type="number"
                        min="0"
                        max="100"
                        step="0.01"
                        required
                        value={formData.tauxSalarial}
                        onChange={(e) => setFormData({ ...formData, tauxSalarial: parseFloat(e.target.value) || 0 })}
                    />
                    <Input
                        label="Part Patronale (%)"
                        type="number"
                        min="0"
                        max="100"
                        step="0.01"
                        required
                        value={formData.tauxPatronal}
                        onChange={(e) => setFormData({ ...formData, tauxPatronal: parseFloat(e.target.value) || 0 })}
                    />
                </div>

                <div className="grid grid-cols-2 gap-4">
                    <Input
                        label="Date Début (Prise d'effet)"
                        type="date"
                        required
                        value={fmtDateInput(formData.dateDebut)}
                        onChange={(e) => setFormData({ ...formData, dateDebut: e.target.value })}
                    />
                    <Input
                        label="Date Fin (Optionnel)"
                        type="date"
                        min={fmtDateInput(formData.dateDebut)}
                        value={fmtDateInput(formData.dateFin)}
                        onChange={(e) => setFormData({ ...formData, dateFin: e.target.value })}
                    />
                </div>
            </form>
        </Modal>
    );
}