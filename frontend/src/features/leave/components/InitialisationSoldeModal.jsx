import { useState, useEffect, useMemo } from 'react';
import { Calendar, History, AlertTriangle, CheckCircle } from 'lucide-react';
import congeService from '@/features/leave/api/congeService';
import { getErrorMessage } from '@/api/apiClient';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Modal from '@/components/ui/overlays/Modal';
import Alert from '@/components/ui/feedback/Alert';
import { useToast } from '@/components/ui/feedback/Toast';
import { formatMoneyAr } from '@/utils/moneyUtils';

/**
 * Calcule automatiquement la date de fin déterministe d'une période de référence de 12 mois.
 * Règle canonique : dateDebut + 12 mois - 1 jour (Section 3).
 */
function calculerDateFinAutomatique(dateDebutStr) {
  if (!dateDebutStr) return '';
  const d = new Date(dateDebutStr + 'T00:00:00');
  if (isNaN(d.getTime())) return '';
  d.setFullYear(d.getFullYear() + 1);
  d.setDate(d.getDate() - 1);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export default function InitialisationSoldeModal({
  isOpen,
  onClose,
  onSuccess,
  employe,
  soldeData = null
}) {
  // Contexte
  const toast = useToast();

  // État local
  // Mode de reprise : AGGREGATED_OPENING ou MINIMAL_OPENING.
  const [modeReprise, setModeReprise] = useState('AGGREGATED_OPENING');

  // Données communes
  const [dateCutover, setDateCutover] = useState('');
  const [soldeJours, setSoldeJours] = useState('');
  const [tauxApplicable, setTauxApplicable] = useState('');
  const [dateDebutReference, setDateDebutReference] = useState('');
  const [commentaire, setCommentaire] = useState('');

  // Données pour AGGREGATED_OPENING (Section 25)
  const [cumulDroitsOuverture, setCumulDroitsOuverture] = useState('');
  const [cumulValorisationOuverture, setCumulValorisationOuverture] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Données dérivées
  const salaireBase = Number(
    soldeData?.salaireBase ||
    employe?.salaireBase ||
    employe?.salaireBaseContractuel ||
    employe?.dernierSalaireBase ||
    0
  );

  // Données dérivées : prévisualisation de la période de référence.
  const dateFinCalculee = useMemo(() => {
    return calculerDateFinAutomatique(dateDebutReference);
  }, [dateDebutReference]);

  // Effet : initialise le formulaire à chaque ouverture de modale.
  useEffect(() => {
    if (!isOpen) return;

    const today = new Date();
    const todayStr = today.toISOString().split('T')[0];
    setDateCutover(todayStr);

    // Début d'année civile courante par défaut
    const anneeCourante = today.getFullYear();
    setDateDebutReference(`${anneeCourante}-01-01`);

    // Solde actuel ou prérempli
    if (soldeData?.soldeActuel !== undefined && soldeData?.soldeActuel !== null) {
      setSoldeJours(String(soldeData.soldeActuel));
    } else {
      setSoldeJours('0');
    }

    // TCONGE actuel ou prérempli
    if (soldeData?.tauxJournalierConge && Number(soldeData.tauxJournalierConge) > 0) {
      setTauxApplicable(String(Number(soldeData.tauxJournalierConge).toFixed(2)));
    } else if (salaireBase > 0) {
      setTauxApplicable((salaireBase / 30).toFixed(2));
    } else {
      setTauxApplicable('');
    }

    setCumulDroitsOuverture('');
    setCumulValorisationOuverture('');
    setCommentaire('Reprise initiale des données');
    setModeReprise('AGGREGATED_OPENING');
    setError(null);
  }, [isOpen, soldeData, employe, salaireBase]);

  // Action utilisateur
  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    const sVal = parseFloat(soldeJours);
    if (isNaN(sVal) || sVal < 0) {
      setError('Veuillez renseigner un solde de jours valide (≥ 0).');
      return;
    }

    const tVal = parseFloat(tauxApplicable);
    if (isNaN(tVal) || tVal <= 0) {
      setError('Veuillez renseigner un taux journalier TCONGE strictement positif.');
      return;
    }

    if (!dateCutover) {
      setError("La date d'arrêté / cutover est obligatoire.");
      return;
    }

    if (!dateDebutReference) {
      setError('La date de début de la période de référence est obligatoire.');
      return;
    }

    let cDroits = null;
    let cValo = null;

    if (modeReprise === 'AGGREGATED_OPENING') {
      cDroits = parseFloat(cumulDroitsOuverture);
      cValo = parseFloat(cumulValorisationOuverture);

      if (isNaN(cDroits) || cDroits < 0) {
        setError('Le cumul des droits déjà acquis à l’ouverture est obligatoire pour la reprise agrégée.');
        return;
      }
      if (isNaN(cValo) || cValo < 0) {
        setError('Le cumul de valorisation financière déjà acquis à l’ouverture est obligatoire pour la reprise agrégée.');
        return;
      }
    }

    setLoading(true);
    try {
      const payload = {
        idEmploye: employe?.idEmploye || employe?.id,
        dateCutover,
        modeReprise,
        soldeJours: sVal,
        tauxJournalierApplicable: tVal,
        dateDebutReference,
        cumulDroitsOuverture: cDroits,
        cumulValorisationOuverture: cValo,
        sourceReprise: 'REPRISE_CUTOVER',
        commentaire: commentaire || `Reprise cutover (${modeReprise})`
      };

      await congeService.cutoverOpening(payload);

      toast({
        title: 'Reprise enregistrée avec succès',
        message: `Solde (${sVal} j) et TCONGE (${formatMoneyAr(tVal)}) initialisés pour le cycle ${dateDebutReference} → ${dateFinCalculee}.`,
        type: 'success'
      });

      if (onSuccess) onSuccess();
      onClose();
    } catch (err) {
      setError(getErrorMessage(err, "Erreur lors de l'enregistrement du cutover."));
    } finally {
      setLoading(false);
    }
  };

  // Rendu
  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Reprise & Initialisation des Congés (Cutover)"
      size="lg"
    >
      <form onSubmit={handleSubmit} className="space-y-4 font-sans text-sm">
        {error && <Alert type="error" message={error} />}

        {/* Collaborateur Info Card */}
        <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] flex items-center justify-between text-xs">
          <div>
            <span className="text-[var(--color-fg-muted)]">Salarié :</span>{' '}
            <strong className="text-[var(--color-fg)]">
              {employe?.nom} {employe?.prenom}
            </strong>{' '}
            <span className="font-mono text-[var(--color-brand-600)]">
              ({employe?.matricule || `#${employe?.idEmploye}`})
            </span>
          </div>
          {salaireBase > 0 && (
            <div className="text-right">
              <span className="text-[var(--color-fg-muted)]">Base contractuelle :</span>{' '}
              <strong className="text-[var(--color-fg)]">{formatMoneyAr(salaireBase)}</strong>
            </div>
          )}
        </div>

        {/* Étape 1 : Choix du Mode de Reprise (Section 24) */}
        <div className="p-3 border border-[var(--color-border-subtle)] space-y-2.5 bg-[var(--color-bg-surface)]">
          <label className="block text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
            1. Données disponibles pour ce salarié
          </label>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <label
              className={`p-3 border cursor-pointer flex flex-col justify-between transition-colors ${
                modeReprise === 'AGGREGATED_OPENING'
                  ? 'border-[var(--color-brand-600)] bg-[var(--color-brand-500)]/10'
                  : 'border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)]'
              }`}
            >
              <div>
                <div className="flex items-center gap-2 font-semibold text-xs text-[var(--color-fg)]">
                  <input
                    type="radio"
                    name="modeReprise"
                    value="AGGREGATED_OPENING"
                    checked={modeReprise === 'AGGREGATED_OPENING'}
                    onChange={() => setModeReprise('AGGREGATED_OPENING')}
                    className="text-[var(--color-brand-600)]"
                  />
                  <span>Solde + Taux + Cumuls du cycle</span>
                </div>
                <p className="text-[11px] text-[var(--color-fg-muted)] mt-1.5 pl-5">
                  <strong>Recommandé</strong> si vous avez le tableau Excel de suivi. Permet la clôture annuelle 100% automatique.
                </p>
              </div>
            </label>

            <label
              className={`p-3 border cursor-pointer flex flex-col justify-between transition-colors ${
                modeReprise === 'MINIMAL_OPENING'
                  ? 'border-[var(--color-brand-600)] bg-[var(--color-brand-500)]/10'
                  : 'border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)]'
              }`}
            >
              <div>
                <div className="flex items-center gap-2 font-semibold text-xs text-[var(--color-fg)]">
                  <input
                    type="radio"
                    name="modeReprise"
                    value="MINIMAL_OPENING"
                    checked={modeReprise === 'MINIMAL_OPENING'}
                    onChange={() => setModeReprise('MINIMAL_OPENING')}
                    className="text-[var(--color-brand-600)]"
                  />
                  <span>Seulement le Solde et le Taux</span>
                </div>
                <p className="text-[11px] text-[var(--color-fg-muted)] mt-1.5 pl-5">
                  Si les mois passés de l'année sont inconnus. Les congés fonctionnent normalement ; le prochain taux annuel nécessitera une confirmation RH.
                </p>
              </div>
            </label>
          </div>
        </div>

        {/* Étape 2 : Solde, Taux et Date d'Arrêté */}
        <div className="p-3 border border-[var(--color-border-subtle)] space-y-3 bg-[var(--color-bg-surface)]">
          <div className="flex items-center gap-2 font-bold text-xs text-[var(--color-fg)] uppercase tracking-wider">
            <History size={15} className="text-[var(--color-brand-500)]" />
            <span>2. Situation arrêtée au cutover</span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                Date d'arrêté / cutover <span className="text-[var(--color-danger-500)]">*</span>
              </label>
              <Input
                type="date"
                value={dateCutover}
                onChange={(e) => setDateCutover(e.target.value)}
                required
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                Solde disponible (jours) <span className="text-[var(--color-danger-500)]">*</span>
              </label>
              <Input
                type="number"
                step="0.25"
                min="0"
                value={soldeJours}
                onChange={(e) => setSoldeJours(e.target.value)}
                placeholder="Ex: 14.50"
                required
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                Taux TCONGE actuel (Ar / j) <span className="text-[var(--color-danger-500)]">*</span>
              </label>
              <Input
                type="number"
                step="0.01"
                min="0.01"
                value={tauxApplicable}
                onChange={(e) => setTauxApplicable(e.target.value)}
                placeholder="Ex: 8669.52"
                required
              />
            </div>
          </div>
        </div>

        {/* Étape 3 : Cycle Annuel de Référence (Section 3 & 25) */}
        <div className="p-3 border border-[var(--color-border-subtle)] space-y-3 bg-[var(--color-bg-surface)]">
          <div className="flex items-center gap-2 font-bold text-xs text-[var(--color-fg)] uppercase tracking-wider">
            <Calendar size={15} className="text-[var(--color-brand-500)]" />
            <span>3. Cycle annuel de référence (12 mois)</span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                Début du cycle de référence <span className="text-[var(--color-danger-500)]">*</span>
              </label>
              <Input
                type="date"
                value={dateDebutReference}
                onChange={(e) => setDateDebutReference(e.target.value)}
                required
              />
              <span className="text-[11px] text-[var(--color-fg-muted)] mt-1 block">
                Date de début du cycle annuel (souvent le 1er janvier).
              </span>
            </div>

            <div>
              <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                Fin calculée automatiquement (12 mois)
              </label>
              <div className="p-2 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] font-mono text-sm font-semibold text-[var(--color-fg)] flex items-center justify-between">
                <span>{dateFinCalculee || '—'}</span>
                <span className="text-[10px] uppercase font-bold text-[var(--color-brand-600)] bg-[var(--color-brand-500)]/10 px-1.5 py-0.5">
                  Auto
                </span>
              </div>
              <span className="text-[11px] text-[var(--color-fg-muted)] mt-1 block">
                Calculé déterministement selon la réglementation légale (12 mois stricts).
              </span>
            </div>
          </div>

          {/* Cumuls si AGGREGATED_OPENING */}
          {modeReprise === 'AGGREGATED_OPENING' && (
            <div className="pt-2 border-t border-[var(--color-border-subtle)] grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                  Droits déjà accumulés sur le cycle (jours) <span className="text-[var(--color-danger-500)]">*</span>
                </label>
                <Input
                  type="number"
                  step="0.0001"
                  min="0"
                  value={cumulDroitsOuverture}
                  onChange={(e) => setCumulDroitsOuverture(e.target.value)}
                  placeholder="Ex: 22.4575 (de janvier à septembre)"
                  required
                />
                <span className="text-[11px] text-[var(--color-fg-muted)] mt-1 block">
                  Col. U du tableau KAJY / Excel (somme des jours acquis antérieurs).
                </span>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                  Valorisation accumulée sur le cycle (Ariary) <span className="text-[var(--color-danger-500)]">*</span>
                </label>
                <Input
                  type="number"
                  step="0.01"
                  min="0"
                  value={cumulValorisationOuverture}
                  onChange={(e) => setCumulValorisationOuverture(e.target.value)}
                  placeholder="Ex: 240092.52"
                  required
                />
                <span className="text-[11px] text-[var(--color-fg-muted)] mt-1 block">
                  Col. V du tableau KAJY / Excel (somme des valorisations mensuelles antérieures).
                </span>
              </div>
            </div>
          )}

          {modeReprise === 'MINIMAL_OPENING' && (
            <div className="p-2.5 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 text-xs flex items-start gap-2 text-[var(--color-warning-800)] dark:text-[var(--color-warning-200)]">
              <AlertTriangle size={16} className="text-[var(--color-warning-600)] shrink-0 mt-0.5" />
              <span>
                <strong>Historique du cycle incomplet :</strong> Les congés et la paie fonctionneront normalement avec le taux de {formatMoneyAr(tauxApplicable || 0)}/j. Les données post-cutover seront enregistrées mois par mois. La prochaine clôture annuelle nécessitera une confirmation ou reconduction RH explicite.
              </span>
            </div>
          )}
        </div>

        {/* Commentaire optionnel */}
        <div>
          <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
            Motif / Référence du cutover
          </label>
          <Input
            type="text"
            value={commentaire}
            onChange={(e) => setCommentaire(e.target.value)}
            placeholder="Ex: Reprise selon arrêté Excel du 30/09/2026"
          />
        </div>

        {/* Prévisualisation Récapitulative Avant Confirmation (Section 25 & 27) */}
        <div className="p-3 bg-[var(--color-brand-500)]/10 border border-[var(--color-brand-500)]/20 text-xs">
          <div className="font-bold text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] mb-1.5 flex items-center gap-1.5">
            <CheckCircle size={15} />
            <span>Récapitulatif de la situation qui sera enregistrée :</span>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-[var(--color-fg)]">
            <div>
              <span className="text-[var(--color-fg-muted)] block text-[11px]">Solde disponible :</span>
              <strong className="font-mono">{soldeJours || '0'} jours</strong>
            </div>
            <div>
              <span className="text-[var(--color-fg-muted)] block text-[11px]">Taux TCONGE :</span>
              <strong className="font-mono text-[var(--color-brand-600)]">{formatMoneyAr(tauxApplicable || 0)}/j</strong>
            </div>
            <div className="sm:col-span-2">
              <span className="text-[var(--color-fg-muted)] block text-[11px]">Cycle de référence :</span>
              <strong className="font-mono">{dateDebutReference || '—'} → {dateFinCalculee || '—'}</strong>
            </div>
          </div>
        </div>

        {/* Actions */}
        <div className="flex items-center justify-end gap-2 pt-2 border-t border-[var(--color-border-subtle)]">
          <Button variant="ghost" size="sm" onClick={onClose} disabled={loading} type="button">
            Annuler
          </Button>
          <Button variant="primary" size="sm" type="submit" loading={loading}>
            Confirmer et initialiser la situation
          </Button>
        </div>
      </form>
    </Modal>
  );
}
