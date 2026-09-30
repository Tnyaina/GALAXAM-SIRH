import { useState, useEffect, useMemo } from 'react';
import { Calendar, History, AlertTriangle, CheckCircle } from 'lucide-react';
import congeService from '@/features/leave/api/congeService';
import { getErrorMessage } from '@/api/apiClient';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import MoneyInput from '@/components/ui/forms/MoneyInput';
import Modal from '@/components/ui/overlays/Modal';
import FormError from '@/components/ui/feedback/FormError';
import Badge from '@/components/ui/data-display/Badge';
import { useToast } from '@/components/ui/feedback/Toast';
import { formatMoneyAr } from '@/utils/moneyUtils';

/**
 * Calcule automatiquement la date de fin déterministe d'une période de référence de 12 mois.
 * Règle légale : dateDebut + 12 mois - 1 jour.
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
  soldeData = null,
  situationData = null
}) {
  // Contexte
  const toast = useToast();

  // Mode de reprise : AGGREGATED_OPENING ou MINIMAL_OPENING.
  const [modeReprise, setModeReprise] = useState('AGGREGATED_OPENING');

  // Données communes
  const [dateCutover, setDateCutover] = useState('');
  const [soldeJours, setSoldeJours] = useState('');
  const [tauxApplicable, setTauxApplicable] = useState('');
  const [dateDebutReference, setDateDebutReference] = useState('');
  const [commentaire, setCommentaire] = useState('');

  // Données pour AGGREGATED_OPENING
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

  const dateFinCalculee = useMemo(() => {
    return calculerDateFinAutomatique(dateDebutReference);
  }, [dateDebutReference]);

  // Initialisation à chaque ouverture
  useEffect(() => {
    if (!isOpen) return;

    const today = new Date();
    const todayStr = today.toISOString().split('T')[0];
    setDateCutover(todayStr);

    const anneeCourante = today.getFullYear();
    setDateDebutReference(`${anneeCourante}-01-01`);

    if (soldeData?.soldeActuel !== undefined && soldeData?.soldeActuel !== null) {
      setSoldeJours(String(soldeData.soldeActuel));
    } else {
      setSoldeJours('0');
    }

    if (situationData?.tauxApplicable?.montant && Number(situationData.tauxApplicable.montant) > 0) {
      setTauxApplicable(String(Number(situationData.tauxApplicable.montant).toFixed(2)));
    } else if (soldeData?.tauxJournalierConge && Number(soldeData.tauxJournalierConge) > 0) {
      setTauxApplicable(String(Number(soldeData.tauxJournalierConge).toFixed(2)));
    } else if (salaireBase > 0) {
      setTauxApplicable((salaireBase / 30).toFixed(2));
    } else {
      setTauxApplicable('');
    }

    setCumulDroitsOuverture('');
    setCumulValorisationOuverture('');
    setCommentaire('Initialisation du solde de congés');
    setModeReprise('AGGREGATED_OPENING');
    setError(null);
  }, [isOpen, soldeData, employe, salaireBase, situationData?.tauxApplicable?.montant]);

  // Validation et soumission
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
      setError('Veuillez renseigner une valeur journalière strictement positive.');
      return;
    }

    if (!dateCutover) {
      setError("La date d'arrêté est obligatoire.");
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
        setError('Le total des droits déjà acquis sur la période est obligatoire en mode complet.');
        return;
      }
      if (isNaN(cValo) || cValo < 0) {
        setError('La valorisation financière cumulée est obligatoire en mode complet.');
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
        commentaire: commentaire || `Initialisation solde (${modeReprise})`
      };

      await congeService.cutoverOpening(payload);

      toast({
        title: 'Initialisation enregistrée avec succès',
        message: `Solde (${sVal} j) et valeur journalière (${formatMoneyAr(tVal)}) initialisés pour le cycle ${dateDebutReference} → ${dateFinCalculee}.`,
        type: 'success'
      });

      if (onSuccess) onSuccess();
      onClose();
    } catch (err) {
      setError(getErrorMessage(err, "Erreur lors de l'enregistrement de l'initialisation."));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Initialisation du solde de congés"
      size="2xl"
    >
      <form onSubmit={handleSubmit} className="space-y-4 font-sans text-sm">
        {error && <FormError message={error} />}

        {/* Fiche d'identification du collaborateur */}
        <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] flex flex-wrap items-center justify-between gap-3 text-xs">
          <div>
            <span className="text-[var(--color-fg-muted)]">Salarié :</span>{' '}
            <strong className="text-[var(--color-fg)] text-sm">
              {employe?.nom} {employe?.prenom}
            </strong>{' '}
            <span className="font-mono text-[var(--color-brand-600)] font-semibold">
              (Matricule #{employe?.matricule || employe?.idEmploye})
            </span>
          </div>
          {salaireBase > 0 && (
            <div className="text-right">
              <span className="text-[var(--color-fg-muted)]">Salaire de base :</span>{' '}
              <strong className="text-[var(--color-fg)] text-sm">{formatMoneyAr(salaireBase)}</strong>
              <span className="block text-[11px] text-[var(--color-fg-subtle)]">
                Base journalière (÷30) : ~{formatMoneyAr(salaireBase / 30)}/j
              </span>
            </div>
          )}
        </div>

        {/* Étape 1 : Choix de la méthode d'initialisation */}
        <div className="p-3.5 border border-[var(--color-border-subtle)] space-y-2.5 bg-[var(--color-surface)]">
          <label className="block text-xs font-semibold text-[var(--color-fg)]">
            1. Méthode d'initialisation
          </label>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <label
              className={`p-3 border cursor-pointer flex flex-col justify-between transition-colors ${
                modeReprise === 'AGGREGATED_OPENING'
                  ? 'border-[var(--color-brand-600)] bg-[var(--color-brand-500)]/10 ring-1 ring-[var(--color-brand-600)]'
                  : 'border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)]'
              }`}
            >
              <div>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2 font-semibold text-xs text-[var(--color-fg)]">
                    <input
                      type="radio"
                      name="modeReprise"
                      value="AGGREGATED_OPENING"
                      checked={modeReprise === 'AGGREGATED_OPENING'}
                      onChange={() => setModeReprise('AGGREGATED_OPENING')}
                      className="text-[var(--color-brand-600)]"
                    />
                    <span>Reprise complète avec historique</span>
                  </div>
                  <Badge variant="success" size="xs">Recommandé</Badge>
                </div>
                <p className="text-[11px] text-[var(--color-fg-muted)] mt-1.5 pl-5 leading-normal">
                  Calcul automatique des indemnités légales à partir de l'historique de l'exercice.
                </p>
              </div>
            </label>

            <label
              className={`p-3 border cursor-pointer flex flex-col justify-between transition-colors ${
                modeReprise === 'MINIMAL_OPENING'
                  ? 'border-[var(--color-brand-600)] bg-[var(--color-brand-500)]/10 ring-1 ring-[var(--color-brand-600)]'
                  : 'border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)]'
              }`}
            >
              <div>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2 font-semibold text-xs text-[var(--color-fg)]">
                    <input
                      type="radio"
                      name="modeReprise"
                      value="MINIMAL_OPENING"
                      checked={modeReprise === 'MINIMAL_OPENING'}
                      onChange={() => setModeReprise('MINIMAL_OPENING')}
                      className="text-[var(--color-brand-600)]"
                    />
                    <span>Saisie simplifiée</span>
                  </div>
                  <Badge variant="neutral" size="xs">Rapide</Badge>
                </div>
                <p className="text-[11px] text-[var(--color-fg-muted)] mt-1.5 pl-5 leading-normal">
                  Renseignement direct du solde restant et de la valeur journalière sans historique.
                </p>
              </div>
            </label>
          </div>
        </div>

        {/* Étape 2 : Situation arrêtée à la date de bascule */}
        <div className="p-3.5 border border-[var(--color-border-subtle)] space-y-3 bg-[var(--color-surface)]">
          <div className="flex items-center gap-2 font-semibold text-xs text-[var(--color-fg)]">
            <History size={14} className="text-[var(--color-brand-500)]" />
            <span>2. Situation à la date pivot</span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label className="block text-xs font-medium text-[var(--color-fg)] mb-1">
                Date d'arrêté <span className="text-[var(--color-danger-500)]">*</span>
              </label>
              <Input
                type="date"
                value={dateCutover}
                onChange={(e) => setDateCutover(e.target.value)}
                required
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-[var(--color-fg)] mb-1">
                Solde disponible (jours) <span className="text-[var(--color-danger-500)]">*</span>
              </label>
              <Input
                type="number"
                step="0.25"
                min="0"
                value={soldeJours}
                onChange={(e) => setSoldeJours(e.target.value)}
                placeholder="Ex. 14.5"
                required
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-[var(--color-fg)] mb-1">
                Valeur journalière (Ar / jour) <span className="text-[var(--color-danger-500)]">*</span>
              </label>
              <MoneyInput
                maxDecimals={2}
                min="0.01"
                value={tauxApplicable}
                onValueChange={setTauxApplicable}
                placeholder="Ex. 16 666.67"
                required
              />
              <span className="text-[10.5px] text-[var(--color-fg-muted)] mt-0.5 block">
                Base : salaire brut contractuel ÷ 30
              </span>
            </div>
          </div>
        </div>

        {/* Étape 3 : Période annuelle de référence & Cumuls passés */}
        <div className="p-3.5 border border-[var(--color-border-subtle)] space-y-3 bg-[var(--color-surface)]">
          <div className="flex items-center gap-2 font-semibold text-xs text-[var(--color-fg)]">
            <Calendar size={14} className="text-[var(--color-brand-500)]" />
            <span>3. Période annuelle de référence</span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-[var(--color-fg)] mb-1">
                Date de début <span className="text-[var(--color-danger-500)]">*</span>
              </label>
              <Input
                type="date"
                value={dateDebutReference}
                onChange={(e) => setDateDebutReference(e.target.value)}
                required
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-[var(--color-fg)] mb-1">
                Date de fin (calculée)
              </label>
              <div className="p-2 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] font-mono text-xs font-semibold text-[var(--color-fg)] flex items-center justify-between">
                <span>{dateFinCalculee || '-'}</span>
                <span className="text-[10px] font-medium text-[var(--color-brand-600)] bg-[var(--color-brand-500)]/10 px-1.5 py-0.5">
                  12 mois
                </span>
              </div>
            </div>
          </div>

          {/* Cumuls si reprise complète avec historique */}
          {modeReprise === 'AGGREGATED_OPENING' && (
            <div className="pt-3 border-t border-[var(--color-border-subtle)] space-y-2">
              <span className="text-xs font-medium text-[var(--color-fg)] block">
                Données cumulées de l'exercice en cours ({dateDebutReference || 'début'} → {dateCutover || 'arrêté'}) :
              </span>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-[var(--color-fg)] mb-1">
                    Droits acquis sur la période (jours) <span className="text-[var(--color-danger-500)]">*</span>
                  </label>
                  <Input
                    type="number"
                    step="0.0001"
                    min="0"
                    value={cumulDroitsOuverture}
                    onChange={(e) => setCumulDroitsOuverture(e.target.value)}
                    placeholder="Ex. 22.5"
                    required
                  />
                  <span className="text-[10.5px] text-[var(--color-fg-muted)] mt-0.5 block">
                    Total des jours acquis depuis le début de la période
                  </span>
                </div>

                <div>
                  <label className="block text-xs font-medium text-[var(--color-fg)] mb-1">
                    Valorisation financière cumulée (Ariary) <span className="text-[var(--color-danger-500)]">*</span>
                  </label>
                  <MoneyInput
                    maxDecimals={2}
                    min="0"
                    value={cumulValorisationOuverture}
                    onValueChange={setCumulValorisationOuverture}
                    placeholder="Ex. 3 150 000"
                    required
                  />
                  <span className="text-[10.5px] text-[var(--color-fg-muted)] mt-0.5 block">
                    Cumul des valorisations mensuelles issu de la paie
                  </span>
                </div>
              </div>
            </div>
          )}

          {modeReprise === 'MINIMAL_OPENING' && (
            <div className="p-2.5 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 text-xs flex items-center gap-2 text-[var(--color-warning-800)] dark:text-[var(--color-warning-200)]">
              <AlertTriangle size={15} className="text-[var(--color-warning-600)] shrink-0" />
              <span>
                Mode simplifié : les congés et la paie utiliseront la valeur de {formatMoneyAr(tauxApplicable || 0)}/j.
              </span>
            </div>
          )}
        </div>

        {/* Motif ou référence */}
        <div>
          <label className="block text-xs font-medium text-[var(--color-fg)] mb-1">
            Motif / Référence
          </label>
          <Input
            type="text"
            value={commentaire}
            onChange={(e) => setCommentaire(e.target.value)}
            placeholder="Ex. Initialisation solde reprise d'antériorité"
          />
        </div>

        {/* Prévisualisation Récapitulative */}
        <div className="p-3 bg-[var(--color-brand-500)]/10 border border-[var(--color-brand-500)]/20 text-xs">
          <div className="font-semibold text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] mb-1.5 flex items-center gap-1.5">
            <CheckCircle size={14} />
            <span>Données enregistrées :</span>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-[var(--color-fg)]">
            <div className="bg-[var(--color-surface)] p-2 border border-[var(--color-border-subtle)]">
              <span className="text-[var(--color-fg-muted)] block text-[10.5px]">Solde disponible :</span>
              <strong className="font-mono text-xs text-[var(--color-brand-600)]">{soldeJours || '0'} jours</strong>
            </div>
            <div className="bg-[var(--color-surface)] p-2 border border-[var(--color-border-subtle)]">
              <span className="text-[var(--color-fg-muted)] block text-[10.5px]">Valeur journalière :</span>
              <strong className="font-mono text-xs text-[var(--color-brand-600)]">{formatMoneyAr(tauxApplicable || 0)}/j</strong>
            </div>
            <div className="bg-[var(--color-surface)] p-2 border border-[var(--color-border-subtle)] col-span-2">
              <span className="text-[var(--color-fg-muted)] block text-[10.5px]">Exercice de référence :</span>
              <strong className="font-mono text-xs">{dateDebutReference || '-'} → {dateFinCalculee || '-'}</strong>
            </div>
          </div>
        </div>

        {/* Actions */}
        <div className="flex items-center justify-end gap-2.5 pt-2 border-t border-[var(--color-border-subtle)]">
          <Button variant="ghost" size="sm" onClick={onClose} disabled={loading} type="button">
            Annuler
          </Button>
          <Button variant="primary" size="sm" type="submit" loading={loading} className="font-semibold">
            Valider l'initialisation
          </Button>
        </div>
      </form>
    </Modal>
  );
}
