import { useState, useEffect } from 'react';
import { Calendar, Info } from 'lucide-react';
import congeService from '@/api/congeService';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Alert from '@/components/ui/feedback/Alert';
import { useToast } from '@/components/ui/feedback/Toast';

export default function TypeCongeModal({ isOpen, onClose, onSuccess, initialData = null }) {
  const [formData, setFormData] = useState({
    code: '',
    libelle: '',
    estPaye: true,
    decompteSoldeAcquis: true,
    exigeAncienneteMinMois: 0,
    dureeFixeJours: '',
    plafondJoursEpisode: '',
    exigeJustificatif: false,
    soumisDelaiPreavis: false,
    suspendAcquisitionSolde: false,
  });

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const toast = useToast();

  useEffect(() => {
    if (initialData) {
      setFormData({
        ...initialData,
        dureeFixeJours: initialData.dureeFixeJours || '',
        plafondJoursEpisode: initialData.plafondJoursEpisode || '',
        exigeAncienneteMinMois: initialData.exigeAncienneteMinMois || 0,
      });
    } else {
      setFormData({
        code: '',
        libelle: '',
        estPaye: true,
        decompteSoldeAcquis: true,
        exigeAncienneteMinMois: 0,
        dureeFixeJours: '',
        plafondJoursEpisode: '',
        exigeJustificatif: false,
        soumisDelaiPreavis: false,
        suspendAcquisitionSolde: false,
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
        dureeFixeJours: formData.dureeFixeJours ? parseFloat(formData.dureeFixeJours) : null,
        plafondJoursEpisode: formData.plafondJoursEpisode ? parseFloat(formData.plafondJoursEpisode) : null,
        exigeAncienneteMinMois: parseInt(formData.exigeAncienneteMinMois) || 0,
        estActif: true
      };

      if (initialData) {
        await congeService.modifierTypeConge(initialData.idTypeConge, payload);
        toast({ title: 'Succès', message: 'Le type de congé a été modifié.', type: 'success' });
      } else {
        await congeService.creerTypeConge(payload);
        toast({ title: 'Succès', message: 'Le type de congé a été créé.', type: 'success' });
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
      <div className="bg-[var(--color-surface)] w-full max-w-2xl shadow-xl overflow-hidden flex flex-col max-h-[90vh]">
        <div className="flex items-center gap-3 p-4 md:p-6 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]">
          <Calendar className="text-[var(--color-brand-600)]" size={24} />
          <h2 className="text-xl font-bold text-[var(--color-fg)]">
            {initialData ? 'Modifier le Type de Congé' : 'Nouveau Type de Congé'}
          </h2>
        </div>

        <div className="flex-1 overflow-y-auto p-4 md:p-6">
          <form id="typeCongeForm" onSubmit={handleSubmit} className="space-y-6">
            {error && <Alert type="error" message={error} />}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <Input
                label="Code (ex: ANNUEL, MALADIE)"
                required
                value={formData.code}
                onChange={(e) => handleChange('code', e.target.value)}
                placeholder="Code unique"
                disabled={!!initialData} // On ne modifie pas le code d'un type existant pour éviter les conflits
              />
              <Input
                label="Libellé"
                required
                value={formData.libelle}
                onChange={(e) => handleChange('libelle', e.target.value)}
                placeholder="Ex: Congé annuel payé"
              />
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <Input
                type="number"
                label="Ancienneté Min (mois)"
                value={formData.exigeAncienneteMinMois}
                onChange={(e) => handleChange('exigeAncienneteMinMois', e.target.value)}
              />
              <Input
                type="number"
                step="0.5"
                label="Durée fixe (jours)"
                value={formData.dureeFixeJours}
                onChange={(e) => handleChange('dureeFixeJours', e.target.value)}
                placeholder="Optionnel"
              />
              <Input
                type="number"
                step="0.5"
                label="Plafond par épisode"
                value={formData.plafondJoursEpisode}
                onChange={(e) => handleChange('plafondJoursEpisode', e.target.value)}
                placeholder="Optionnel"
              />
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 bg-[var(--color-bg-subtle)] p-4 rounded-lg border border-[var(--color-border-subtle)]">
              <label className="flex items-center gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={formData.estPaye}
                  onChange={(e) => handleChange('estPaye', e.target.checked)}
                  className="w-4 h-4 rounded text-brand-600 focus:ring-brand-500"
                />
                <span className="text-sm font-medium text-[var(--color-fg)]">Congé Payé (maintien de salaire)</span>
              </label>

              <label className="flex items-center gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={formData.decompteSoldeAcquis}
                  onChange={(e) => handleChange('decompteSoldeAcquis', e.target.checked)}
                  className="w-4 h-4 rounded text-brand-600 focus:ring-brand-500"
                />
                <span className="text-sm font-medium text-[var(--color-fg)]">Décompte du solde principal acquis</span>
              </label>

              <label className="flex items-center gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={formData.exigeJustificatif}
                  onChange={(e) => handleChange('exigeJustificatif', e.target.checked)}
                  className="w-4 h-4 rounded text-brand-600 focus:ring-brand-500"
                />
                <span className="text-sm font-medium text-[var(--color-fg)]">Exige un justificatif obligatoire</span>
              </label>

              <label className="flex items-center gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={formData.soumisDelaiPreavis}
                  onChange={(e) => handleChange('soumisDelaiPreavis', e.target.checked)}
                  className="w-4 h-4 rounded text-brand-600 focus:ring-brand-500"
                />
                <span className="text-sm font-medium text-[var(--color-fg)]">Soumis au délai de préavis (15j)</span>
              </label>

              <label className="flex items-center gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={formData.suspendAcquisitionSolde}
                  onChange={(e) => handleChange('suspendAcquisitionSolde', e.target.checked)}
                  className="w-4 h-4 rounded text-brand-600 focus:ring-brand-500"
                />
                <span className="text-sm font-medium text-[var(--color-fg)]">Suspend l'acquisition du solde</span>
              </label>
            </div>
            
            <div className="flex gap-2 items-start bg-sky-50 text-sky-800 p-3 text-xs rounded-md">
                <Info size={16} className="shrink-0 mt-0.5" />
                <p>Certains paramètres (Plafonds, Types bascules, Pools mutualisés) requièrent une configuration avancée et ne sont pas tous modifiables ici.</p>
            </div>
          </form>
        </div>

        <div className="flex justify-end gap-3 p-4 md:p-6 border-t border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]">
          <Button variant="ghost" onClick={onClose} disabled={loading}>
            Annuler
          </Button>
          <Button type="submit" form="typeCongeForm" variant="primary" loading={loading}>
            {initialData ? 'Enregistrer' : 'Créer'}
          </Button>
        </div>
      </div>
    </div>
  );
}
