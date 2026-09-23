import { Landmark } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Alert from '@/components/ui/feedback/Alert';

export default function BanqueFormModal({
    isOpen,
    onClose,
    onSubmit,
    editingBanque,
    formData,
    setFormData,
    actionError,
    submitting = false,
}) {
    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={editingBanque ? "Modifier la Banque" : "Nouvelle Banque Partenaire"}
            icon={<Landmark size={18} />}
            size="md"
            footer={
                <>
                    <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>
                        Annuler
                    </Button>
                    <Button type="submit" form="banque-form" variant="primary" size="sm" loading={submitting}>
                        {editingBanque ? "Enregistrer" : "Créer la banque"}
                    </Button>
                </>
            }
        >
            <form id="banque-form" onSubmit={onSubmit} className="space-y-4">
                {actionError && <Alert type="error" message={actionError} />}

                <Input
                    label="Code Banque (5 chiffres normalisés)"
                    required
                    disabled={!!editingBanque}
                    value={formData.codeBanque}
                    onChange={(e) => setFormData({ ...formData, codeBanque: e.target.value.toUpperCase() })}
                    placeholder="Ex: 00004, 00005"
                />

                <Input
                    label="Raison Sociale / Nom de la Banque"
                    required
                    value={formData.nomBanque}
                    onChange={(e) => setFormData({ ...formData, nomBanque: e.target.value })}
                    placeholder="Ex: Bank of Africa Madagascar"
                />

                <Input
                    label="Code SWIFT / BIC (Optionnel)"
                    value={formData.codeSwift}
                    onChange={(e) => setFormData({ ...formData, codeSwift: e.target.value.toUpperCase() })}
                    placeholder="Ex: AFRIMGM1"
                />
            </form>
        </Modal>
    );
}