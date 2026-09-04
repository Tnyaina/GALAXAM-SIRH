import { Shield } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Modal from '@/components/ui/overlays/Modal';
import Alert from '@/components/ui/feedback/Alert';

export default function RoleFormModal({
    isOpen,
    onClose,
    onSubmit,
    editingRole,
    formData,
    setFormData,
    roleModalError,
    submitting = false,
}) {
    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={editingRole ? "Modifier le Rôle" : "Nouveau Rôle"}
            icon={<Shield size={18} />}
            size="md"
            footer={
                <>
                    <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>
                        Annuler
                    </Button>
                    <Button type="submit" form="role-form" variant="primary" size="sm" loading={submitting}>
                        {editingRole ? "Mettre à jour" : "Créer le rôle"}
                    </Button>
                </>
            }
        >
            <form id="role-form" onSubmit={onSubmit} className="space-y-4">
                {roleModalError && <Alert type="error" message={roleModalError} />}

                <Input
                    label="Code du rôle (ex: ADMIN_PAIE, SUPERVISEUR)"
                    required
                    disabled={!!editingRole}
                    value={formData.codeRole}
                    onChange={(e) => setFormData({ ...formData, codeRole: e.target.value.toUpperCase() })}
                    placeholder="ADMIN_RH"
                />

                <Input
                    label="Libellé complet"
                    required
                    value={formData.libelle}
                    onChange={(e) => setFormData({ ...formData, libelle: e.target.value })}
                    placeholder="Administrateur RH"
                />
            </form>
        </Modal>
    );
}