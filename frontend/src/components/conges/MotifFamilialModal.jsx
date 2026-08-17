import { useState, useEffect } from 'react';
import { Users } from 'lucide-react';
import congeService from '@/api/congeService';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
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

  if (!isOpen) return null;

  const handleChange = (field, value) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      const payload = {
        ...formData,
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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="bg-[var(--color-surface)] w-full max-w-lg shadow-xl overflow-hidden flex flex-col max-h-[90vh]">
        <div className="flex items-center gap-3 p-4 md:p-6 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]">
          <Users className="text-[var(--color-brand-600)]" size={24} />
          <h2 className="text-xl font-bold text-[var(--color-fg)]">
            {initialData ? 'Modifier l\'Événement' : 'Nouvel Événement Familial'}
          </h2>
        </div>

        <div className="flex-1 overflow-y-auto p-4 md:p-6">
          <form id="motifForm" onSubmit={handleSubmit} className="space-y-6">
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
              label="Libellé"
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

            <div className="bg-[var(--color-bg-subtle)] p-4 rounded-lg border border-[var(--color-border-subtle)]">
              <label className="flex items-center gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={formData.justificatifRequis}
                  onChange={(e) => handleChange('justificatifRequis', e.target.checked)}
                  className="w-4 h-4 rounded text-brand-600 focus:ring-brand-500"
                />
                <span className="text-sm font-medium text-[var(--color-fg)]">Justificatif obligatoire (Acte de mariage, etc.)</span>
              </label>
            </div>
          </form>
        </div>

        <div className="flex justify-end gap-3 p-4 md:p-6 border-t border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]">
          <Button variant="ghost" onClick={onClose} disabled={loading}>
            Annuler
          </Button>
          <Button type="submit" form="motifForm" variant="primary" loading={loading}>
            {initialData ? 'Enregistrer' : 'Créer'}
          </Button>
        </div>
      </div>
    </div>
  );
}
