import { useState, useEffect } from 'react';
import { Calendar, Info, AlertTriangle } from 'lucide-react';
import congeService from '@/features/leave/api/congeService';
import { getErrorMessage } from '@/api/apiClient';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Modal from '@/components/ui/overlays/Modal';
import FormError from '@/components/ui/feedback/FormError';
import { useToast } from '@/components/ui/feedback/Toast';

const EMPTY_FORM = {
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
  description: ''
};

export default function TypeCongeModal({ isOpen, onClose, onSuccess, initialData = null }) {
  // Contexte
  const toast = useToast();

  // État local
  const [formData, setFormData] = useState(EMPTY_FORM);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  // Effet : préremplit le formulaire ou rétablit le formulaire vide à son ouverture.
  useEffect(() => {
    if (!isOpen) return;
    if (initialData) {
      setFormData({
        code: initialData.code || '',
        libelle: initialData.libelle || '',
        estPaye: initialData.estPaye !== false,
        decompteSoldeAcquis: initialData.decompteSoldeAcquis !== false,
        exigeAncienneteMinMois: initialData.exigeAncienneteMinMois || 0,
        dureeFixeJours: initialData.dureeFixeJours ?? '',
        plafondJoursEpisode: initialData.plafondJoursEpisode ?? '',
        exigeJustificatif: Boolean(initialData.exigeJustificatif),
        soumisDelaiPreavis: Boolean(initialData.soumisDelaiPreavis),
        suspendAcquisitionSolde: Boolean(initialData.suspendAcquisitionSolde),
        description: initialData.description || ''
      });
    } else {
      setFormData(EMPTY_FORM);
    }
    setError(null);
    setFieldErrors({});
    setLoading(false);
  }, [initialData, isOpen]);

  // Données dérivées
  const dureeEtPlafondEnConflit =
    formData.dureeFixeJours !== '' && formData.plafondJoursEpisode !== '';

  // Actions utilisateur
  const handleChange = (field, value) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
    setFieldErrors((prev) => ({ ...prev, [field]: null }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (loading) return;

    const nextFieldErrors = {};
    if (!formData.code.trim()) nextFieldErrors.code = 'Le code est obligatoire.';
    if (!formData.libelle.trim()) nextFieldErrors.libelle = 'Le libellé est obligatoire.';
    if (formData.exigeAncienneteMinMois < 0) nextFieldErrors.exigeAncienneteMinMois = 'Ne peut pas être négatif.';
    if (formData.dureeFixeJours !== '' && Number(formData.dureeFixeJours) < 0) {
      nextFieldErrors.dureeFixeJours = 'Ne peut pas être négatif.';
    }
    if (formData.plafondJoursEpisode !== '' && Number(formData.plafondJoursEpisode) < 0) {
      nextFieldErrors.plafondJoursEpisode = 'Ne peut pas être négatif.';
    }

    if (Object.keys(nextFieldErrors).length > 0) {
      setFieldErrors(nextFieldErrors);
      setError(null);
      return;
    }

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
      setError(getErrorMessage(err, "Impossible d'enregistrer le type de congé. Veuillez réessayer."));
    } finally {
      setLoading(false);
    }
  };

  // Rendu
  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={initialData ? 'Modifier le type de congé' : 'Nouveau type de congé'}
      icon={<Calendar size={18} />}
      size="lg"
      footer={
        <>
          <Button type="button" variant="ghost" size="sm" onClick={onClose} disabled={loading}>
            Annuler
          </Button>
          <Button type="submit" form="typeCongeForm" variant="primary" size="sm" loading={loading}>
            {initialData ? 'Enregistrer les modifications' : 'Créer le type de congé'}
          </Button>
        </>
      }
    >
      <form id="typeCongeForm" onSubmit={handleSubmit} className="space-y-5 text-sm">
        {error && <FormError message={error} />}

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-1.5">
            <Input
              label="Code"
              required={!initialData}
              value={formData.code}
              onChange={(e) => handleChange('code', e.target.value)}
              placeholder="Code unique, ex : ANNUEL"
              disabled={!!initialData}
              error={fieldErrors.code}
              hint={initialData ? 'Le code ne peut pas être modifié après création.' : 'Identifiant technique, ex : ANNUEL, MALADIE'}
            />
          </div>
          <div className="space-y-1.5">
            <Input
              label="Libellé"
              required
              value={formData.libelle}
              onChange={(e) => handleChange('libelle', e.target.value)}
              placeholder="Ex : Congé annuel payé"
              error={fieldErrors.libelle}
            />
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 items-start">
          <div className="space-y-1.5">
            <Input
              type="number"
              min="0"
              step="1"
              label="Ancienneté min. (mois)"
              value={formData.exigeAncienneteMinMois}
              onChange={(e) => handleChange('exigeAncienneteMinMois', e.target.value)}
              hint="Ancienneté requise, ex : 12 mois"
              error={fieldErrors.exigeAncienneteMinMois}
            />
          </div>
          <div className="space-y-1.5">
            <Input
              type="number"
              min="0"
              step="0.5"
              label="Durée fixe légale (jours)"
              value={formData.dureeFixeJours}
              onChange={(e) => handleChange('dureeFixeJours', e.target.value)}
              placeholder="Ex : 98 pour Maternité"
              hint="Durée forfaitaire obligatoire"
              error={fieldErrors.dureeFixeJours}
            />
          </div>
          <div className="space-y-1.5">
            <Input
              type="number"
              min="0"
              step="0.5"
              label="Plafond par demande (jours)"
              value={formData.plafondJoursEpisode}
              onChange={(e) => handleChange('plafondJoursEpisode', e.target.value)}
              placeholder="Ex : 30 pour Maladie"
              hint="Max. par absence continue"
              error={fieldErrors.plafondJoursEpisode}
            />
          </div>
        </div>

        {/* Alerte conflit durée fixe / plafond */}
        {dureeEtPlafondEnConflit && (
          <div className="flex gap-2.5 items-start pl-3 border-l-2 border-warning-500 py-1">
            <AlertTriangle className="w-4 h-4 text-warning-500 shrink-0 mt-0.5" />
            <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
              <strong className="text-warning-600 dark:text-warning-500">Configuration inhabituelle :</strong> une
              {' '}<strong>durée fixe légale</strong> et un <strong>plafond par demande</strong> sont renseignés en même
              temps.
            </p>
          </div>
        )}

        <div className="grid grid-cols-1 md:grid-cols-2 gap-3 bg-[var(--color-bg-subtle)] p-4 border border-[var(--color-border-subtle)] rounded-none">
          <label className="flex items-start gap-3 cursor-pointer">
            <input
              type="checkbox"
              checked={formData.estPaye}
              onChange={(e) => handleChange('estPaye', e.target.checked)}
              className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0 rounded-none"
            />
            <span className="text-sm text-[var(--color-fg)]">Congé payé <span className="block text-xs text-[var(--color-fg-muted)]">Maintien de salaire à 100 %</span></span>
          </label>

          <label className="flex items-start gap-3 cursor-pointer">
            <input
              type="checkbox"
              checked={formData.decompteSoldeAcquis}
              onChange={(e) => handleChange('decompteSoldeAcquis', e.target.checked)}
              className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0 rounded-none"
            />
            <span className="text-sm text-[var(--color-fg)]">Déductible du solde acquis <span className="block text-xs text-[var(--color-fg-muted)]">Solde annuel, 2,5 j/mois</span></span>
          </label>

          <label className="flex items-start gap-3 cursor-pointer">
            <input
              type="checkbox"
              checked={formData.exigeJustificatif}
              onChange={(e) => handleChange('exigeJustificatif', e.target.checked)}
              className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0 rounded-none"
            />
            <span className="text-sm text-[var(--color-fg)]">Justificatif obligatoire <span className="block text-xs text-[var(--color-fg-muted)]">Certificat, acte, etc.</span></span>
          </label>

          <label className="flex items-start gap-3 cursor-pointer">
            <input
              type="checkbox"
              checked={formData.soumisDelaiPreavis}
              onChange={(e) => handleChange('soumisDelaiPreavis', e.target.checked)}
              className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0 rounded-none"
            />
            <span className="text-sm text-[var(--color-fg)]">Soumis au délai de préavis <span className="block text-xs text-[var(--color-fg-muted)]">15 jours légaux</span></span>
          </label>

          <label className="flex items-start gap-3 cursor-pointer md:col-span-2">
            <input
              type="checkbox"
              checked={formData.suspendAcquisitionSolde}
              onChange={(e) => handleChange('suspendAcquisitionSolde', e.target.checked)}
              className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0 rounded-none"
            />
            <span className="text-sm text-[var(--color-fg)]">Suspend l'acquisition automatique de solde <span className="block text-xs text-[var(--color-fg-muted)]">Période non assimilée à du temps de travail effectif</span></span>
          </label>
        </div>

        <div className="flex gap-2.5 items-start bg-info-500/10 border border-info-500/20 text-info-600 dark:text-info-400 p-3.5 text-xs rounded-none">
          <Info size={16} className="shrink-0 mt-0.5" />
          <div className="space-y-1">
            <p className="font-bold">Règles automatiques de bascule et de pools partagés</p>
            <p className="text-[11px] text-[var(--color-fg-muted)] leading-relaxed">
              <strong>Plafond par demande (épisode)</strong> : durée continue maximale d'un arrêt (ex : 30 jours pour Maladie).
              <br />
              <strong>Congés d'événements familiaux</strong> : rattachés au pool annuel partagé et à son plafond configuré.
            </p>
          </div>
        </div>
      </form>
    </Modal>
  );
}
