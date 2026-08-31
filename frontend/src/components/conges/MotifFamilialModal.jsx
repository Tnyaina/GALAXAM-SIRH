import { useState, useEffect } from 'react';
import { Users } from 'lucide-react';
import congeService from '@/api/congeService';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Modal from '@/components/ui/overlays/Modal';
import Alert from '@/components/ui/feedback/Alert';
import { useToast } from '@/components/ui/feedback/Toast';

export default function MotifFamilialModal({ isOpen, onClose, onSuccess, initialData = null }) {
  const [formData, setFormData] = useState({
    code: '',
    libelle: '',
    nbJoursAutorises: '',
    justificatifRequis: true,
  });

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const toast = useToast();

  useEffect(() => {
    if (initialData) {
      setFormData({
        ...initialData,
        nbJoursAutorises: initialData.nbJoursAutorises || '',
      });
    } else {
      setFormData({
        code: '',
        libelle: '',
        nbJoursAutorises: '',
        justificatifRequis: true,
      });
    }
  }, [initialData, isOpen]);

  const handleChange = (field, value) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (loading) return;
    setLoading(true);
    setError(null);

    try {
      const payload = {
        ...formData,
        code: formData.code ? formData.code.trim().toUpperCase() : '',
        libelle: formData.libelle ? formData.libelle.trim() : '',
        nbJoursAutorises: formData.nbJoursAutorises ? parseFloat(formData.nbJoursAutorises) : 0,
        estActif: true
      };

      if (initialData) {
        await congeService.modifierMotifFamilial(initialData.idMotif, payload);
        toast({ title: 'Succès', message: 'L\'événement a été modifié.', type: 'success' });
      } else {
        await congeService.creerMotifFamilial(payload);
        toast({ title: 'Succès', message: 'L\'événement a été créé.', type: 'success' });
      }

      if (onSuccess) onSuccess();
      onClose();
    } catch (err) {
      const msg = err.response?.data || err.message;
      setError(typeof msg === 'string' ? msg : JSON.stringify(msg));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={initialData ? 'Modifier l\'Événement' : 'Nouvel Événement Familial'}
      icon={<Users size={18} />}
      size="md"
      footer={
        <>
          <Button type="button" variant="ghost" size="sm" onClick={onClose} disabled={loading}>
            Annuler
          </Button>
          <Button type="submit" form="motifForm" variant="primary" size="sm" loading={loading}>
            {initialData ? 'Enregistrer' : 'Créer'}
          </Button>
        </>
      }
    >
      <form id="motifForm" onSubmit={handleSubmit} className="space-y-4">
        {error && <Alert type="error" message={error} />}

        <Input
          label="Code (ex: MARIAGE, NAISSANCE)"
          required
          value={formData.code}
          onChange={(e) => handleChange('code', e.target.value)}
          placeholder="Code unique"
          disabled={!!initialData}
        />

        <Input
          label="Libellé de l'événement"
          required
          value={formData.libelle}
          onChange={(e) => handleChange('libelle', e.target.value)}
          placeholder="Ex: Naissance d'un enfant"
        />

        <Input
          type="number"
          step="0.5"
          label="Jours autorisés"
          required
          value={formData.nbJoursAutorises}
          onChange={(e) => handleChange('nbJoursAutorises', e.target.value)}
          placeholder="Ex: 2"
        />

        <div className="bg-[var(--color-bg-subtle)] p-3.5 rounded-none border border-[var(--color-border-subtle)]">
          <label className="flex items-center gap-3 cursor-pointer">
            <input
              type="checkbox"
              checked={formData.justificatifRequis}
              onChange={(e) => handleChange('justificatifRequis', e.target.checked)}
              className="w-4 h-4 rounded-none accent-[var(--color-brand-500)]"
            />
            <span className="text-sm font-medium text-[var(--color-fg)]">Justificatif obligatoire (Acte d'état civil, certificat...)</span>
          </label>
        </div>
      </form>
    </Modal>
  );
}

