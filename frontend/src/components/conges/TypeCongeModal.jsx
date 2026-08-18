import { useState, useEffect } from 'react';
import { Calendar, Info, AlertTriangle } from 'lucide-react';
import congeService from '@/api/congeService';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Alert from '@/components/ui/feedback/Alert';
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
};

export default function TypeCongeModal({ isOpen, onClose, onSuccess, initialData = null }) {
  const [formData, setFormData] = useState(EMPTY_FORM);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const toast = useToast();

  // Réinitialise TOUT l'état (formulaire + erreurs) à chaque ouverture,
  // pour ne jamais laisser une erreur ou une saisie du tour précédent traîner.
  useEffect(() => {
    if (!isOpen) return;
    if (initialData) {
      setFormData({
        ...EMPTY_FORM,
        ...initialData,
        dureeFixeJours: initialData.dureeFixeJours ?? '',
        plafondJoursEpisode: initialData.plafondJoursEpisode ?? '',
        exigeAncienneteMinMois: initialData.exigeAncienneteMinMois ?? 0,
      });
    } else {
      setFormData(EMPTY_FORM);
    }
    setError(null);
    setFieldErrors({});
    setLoading(false);
  }, [initialData, isOpen]);

  // Fermeture au clavier (Échap) — désactivée pendant une soumission en cours
  // pour éviter de perdre une requête en vol.
  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && !loading) onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, loading, onClose]);

  if (!isOpen) return null;

  const handleChange = (field, value) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
    setFieldErrors((prev) => ({ ...prev, [field]: null }));
  };

  const handleBackdropClick = () => {
    if (!loading) onClose();
  };

  const dureeEtPlafondEnConflit =
    formData.dureeFixeJours !== '' && formData.plafondJoursEpisode !== '';

  const handleSubmit = async (e) => {
    e.preventDefault();

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
      setError('Veuillez corriger les champs indiqués ci-dessous avant de continuer.');
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
      // Les données saisies restent affichées : on ne réinitialise jamais le formulaire sur erreur.
      const msg = err.response?.data?.message || err.response?.data || err.message
        || "Impossible d'enregistrer le type de congé. Veuillez réessayer.";
      setError(typeof msg === 'string' ? msg : "Impossible d'enregistrer le type de congé. Veuillez réessayer.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-[var(--color-brand-900)]/80 backdrop-blur-sm p-4"
      onClick={handleBackdropClick}
    >
      <div
        className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none w-full max-w-3xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh] font-sans"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between gap-3 px-6 py-4 border-b border-[var(--color-border-subtle)] shrink-0">
          <div className="flex items-center gap-3">
            <Calendar className="text-[var(--color-brand-500)]" size={22} />
            <div>
              <h2 className="text-sm font-bold text-[var(--color-fg)]">
                {initialData ? 'Modifier le type de congé' : 'Nouveau type de congé'}
              </h2>
              <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                Règles de décompte, plafonds et préavis légaux
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={loading}
            aria-label="Fermer"
            className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] text-xl leading-none w-8 h-8 flex items-center justify-center shrink-0 disabled:opacity-40"
          >
            ×
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-6 py-5">
          <form id="typeCongeForm" onSubmit={handleSubmit} className="space-y-6 text-sm">
            {error && <Alert type="error" message={error} />}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <Input
                  label="Code"
                  required={!initialData}
                  value={formData.code}
                  onChange={(e) => handleChange('code', e.target.value)}
                  placeholder="Code unique, ex : ANNUEL"
                  disabled={!!initialData}
                  hint={initialData ? 'Le code ne peut pas être modifié après création.' : 'Identifiant technique, ex : ANNUEL, MALADIE'}
                />
                {fieldErrors.code && (
                  <p className="text-xs text-danger-500 flex items-center gap-1">
                    <AlertTriangle className="w-3.5 h-3.5 shrink-0" /> {fieldErrors.code}
                  </p>
                )}
              </div>
              <div className="space-y-1.5">
                <Input
                  label="Libellé"
                  required
                  value={formData.libelle}
                  onChange={(e) => handleChange('libelle', e.target.value)}
                  placeholder="Ex : Congé annuel payé"
                />
                {fieldErrors.libelle && (
                  <p className="text-xs text-danger-500 flex items-center gap-1">
                    <AlertTriangle className="w-3.5 h-3.5 shrink-0" /> {fieldErrors.libelle}
                  </p>
                )}
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
                  labelClassName="min-h-[1.5rem] flex items-end pb-0.5"
                />
                {fieldErrors.exigeAncienneteMinMois && (
                  <p className="text-xs text-danger-500 flex items-center gap-1">
                    <AlertTriangle className="w-3.5 h-3.5 shrink-0" /> {fieldErrors.exigeAncienneteMinMois}
                  </p>
                )}
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
                  labelClassName="min-h-[1.5rem] flex items-end pb-0.5"
                />
                {fieldErrors.dureeFixeJours && (
                  <p className="text-xs text-danger-500 flex items-center gap-1">
                    <AlertTriangle className="w-3.5 h-3.5 shrink-0" /> {fieldErrors.dureeFixeJours}
                  </p>
                )}
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
                  hint="Max. par absence continue (épisode)"
                  labelClassName="min-h-[1.5rem] flex items-end pb-0.5"
                />
                {fieldErrors.plafondJoursEpisode && (
                  <p className="text-xs text-danger-500 flex items-center gap-1">
                    <AlertTriangle className="w-3.5 h-3.5 shrink-0" /> {fieldErrors.plafondJoursEpisode}
                  </p>
                )}
              </div>
            </div>

            {/* Alerte : durée fixe ET plafond par épisode sont deux mécanismes
                mutuellement exclusifs. Les remplir tous les deux est probablement une erreur. */}
            {dureeEtPlafondEnConflit && (
              <div className="flex gap-2.5 items-start pl-3 border-l-2 border-warning-500 py-1">
                <AlertTriangle className="w-4 h-4 text-warning-500 shrink-0 mt-0.5" />
                <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                  <strong className="text-warning-600 dark:text-warning-500">Configuration inhabituelle :</strong> une
                  {' '}<strong>durée fixe légale</strong> et un <strong>plafond par demande</strong> sont renseignés en même
                  temps. Ce sont deux règles distinctes (durée forfaitaire vs. limite par épisode) — vérifiez qu'une seule
                  s'applique réellement à ce type de congé.
                </p>
              </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3 bg-[var(--color-bg-subtle)] p-4 border border-[var(--color-border-subtle)]">
              <label className="flex items-start gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={formData.estPaye}
                  onChange={(e) => handleChange('estPaye', e.target.checked)}
                  className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0"
                />
                <span className="text-sm text-[var(--color-fg)]">Congé payé <span className="block text-xs text-[var(--color-fg-muted)]">Maintien de salaire à 100 %</span></span>
              </label>

              <label className="flex items-start gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={formData.decompteSoldeAcquis}
                  onChange={(e) => handleChange('decompteSoldeAcquis', e.target.checked)}
                  className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0"
                />
                <span className="text-sm text-[var(--color-fg)]">Déductible du solde acquis <span className="block text-xs text-[var(--color-fg-muted)]">Solde annuel, 2,5 j/mois</span></span>
              </label>

              <label className="flex items-start gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={formData.exigeJustificatif}
                  onChange={(e) => handleChange('exigeJustificatif', e.target.checked)}
                  className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0"
                />
                <span className="text-sm text-[var(--color-fg)]">Justificatif obligatoire <span className="block text-xs text-[var(--color-fg-muted)]">Certificat, acte, etc.</span></span>
              </label>

              <label className="flex items-start gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={formData.soumisDelaiPreavis}
                  onChange={(e) => handleChange('soumisDelaiPreavis', e.target.checked)}
                  className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0"
                />
                <span className="text-sm text-[var(--color-fg)]">Soumis au délai de préavis <span className="block text-xs text-[var(--color-fg-muted)]">15 jours légaux</span></span>
              </label>

              <label className="flex items-start gap-3 cursor-pointer md:col-span-2">
                <input
                  type="checkbox"
                  checked={formData.suspendAcquisitionSolde}
                  onChange={(e) => handleChange('suspendAcquisitionSolde', e.target.checked)}
                  className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0"
                />
                <span className="text-sm text-[var(--color-fg)]">Suspend l'acquisition automatique de solde <span className="block text-xs text-[var(--color-fg-muted)]">Période non assimilée à du temps de travail effectif</span></span>
              </label>
            </div>

            <div className="flex gap-2.5 items-start bg-info-500/10 border border-info-500/20 text-info-600 dark:text-info-400 p-3.5 text-xs">
              <Info size={16} className="shrink-0 mt-0.5" />
              <div className="space-y-1">
                <p className="font-bold">Règles automatiques de bascule et de pools partagés</p>
                <p className="text-[11px] text-[var(--color-fg-muted)] leading-relaxed">
                  <strong>Plafond par demande (épisode)</strong> : durée continue maximale d'un arrêt (ex : 30 jours pour
                  Maladie). Au-delà, l'application bascule automatiquement vers Maladie sans solde.
                  <br />
                  <strong>Congés d'événements familiaux</strong> : les motifs (mariage, naissance, décès) sont rattachés
                  automatiquement au pool annuel partagé légal de 10 jours/an.
                </p>
              </div>
            </div>
          </form>
        </div>

        {/* Actions */}
        <div className="flex justify-end gap-3 px-6 py-4 border-t border-[var(--color-border-subtle)] shrink-0">
          <Button type="button" variant="outline" onClick={onClose} disabled={loading} className="rounded-none">
            Annuler
          </Button>
          <Button type="submit" form="typeCongeForm" variant="primary" loading={loading} className="rounded-none font-bold">
            {initialData ? 'Enregistrer les modifications' : 'Créer le type de congé'}
          </Button>
        </div>
      </div>
    </div>
  );
}