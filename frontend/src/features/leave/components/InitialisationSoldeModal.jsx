import { useState, useEffect, useMemo } from 'react';
import { Calendar, History, CheckCircle } from 'lucide-react';
import congeService from '@/features/leave/api/congeService';
import { getErrorMessage } from '@/api/apiClient';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import MoneyInput from '@/components/ui/forms/MoneyInput';
import Modal from '@/components/ui/overlays/Modal';
import FormError from '@/components/ui/feedback/FormError';
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

  // Données communes
  const [dateCutover, setDateCutover] = useState('');
  const [soldeJours, setSoldeJours] = useState('');
  const [valeurSoldeOuverture, setValeurSoldeOuverture] = useState('');
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
    const mois = String(today.getMonth() + 1).padStart(2, '0');
    const jour = String(today.getDate()).padStart(2, '0');
    setDateCutover(`${today.getFullYear()}-${mois}-${jour}`);

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
    } else {
      setTauxApplicable('');
    }

    setValeurSoldeOuverture('');
    setCumulDroitsOuverture('');
    setCumulValorisationOuverture('');
    setCommentaire('Initialisation du solde de congés');
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

    const vVal = parseFloat(valeurSoldeOuverture);
    if (isNaN(vVal) || vVal < 0) {
      setError('La valeur du stock est obligatoire (montant Ariary arrêté par la DAF).');
      return;
    }
    if (sVal > 0 && vVal === 0) {
      setError('Vous avez saisi des jours disponibles mais aucune valeur monétaire. La valeur du stock ne peut pas être 0 si le solde est positif — renseignez le montant réel arrêté par la DAF.');
      return;
    }
    if (sVal === 0 && vVal !== 0) {
      setError('La valeur du stock doit être 0 si aucun jour n\'est disponible.');
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

    const cDroits = parseFloat(cumulDroitsOuverture);
    const cValo = parseFloat(cumulValorisationOuverture);
    if (isNaN(cDroits) || cDroits < 0) {
      setError('Le total des droits déjà acquis sur la période est obligatoire (0 si aucun).');
      return;
    }
    if (isNaN(cValo) || cValo < 0) {
      setError('La valorisation déjà acquise sur la période est obligatoire (0 si aucune).');
      return;
    }

    setLoading(true);
    try {
      const payload = {
        idEmploye: employe?.idEmploye || employe?.id,
        dateCutover,
        modeReprise: 'AGGREGATED_OPENING',
        soldeJours: sVal,
        valeurSoldeOuverture: vVal,
        tauxJournalierApplicable: tVal,
        dateDebutReference,
        cumulDroitsOuverture: cDroits,
        cumulValorisationOuverture: cValo,
        sourceReprise: 'REPRISE_CUTOVER',
        commentaire: commentaire || 'Reprise du solde et des cumuls de congés'
      };

      await congeService.cutoverOpening(payload);

      toast({
        title: 'Initialisation enregistrée avec succès',
        message: `Stock repris : ${sVal} j / ${formatMoneyAr(vVal)} ; TCONGE applicable : ${formatMoneyAr(tVal)}/j.`,
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

        {/* Contexte d'usage */}
        <div className="p-2.5 border-l-4 border-[var(--color-brand-500)] bg-[var(--color-brand-500)]/6">
          <p className="text-xs text-[var(--color-fg-muted)]">
            Reprise des droits et valorisations acquis avant l'entrée en gestion dans le SIRH.
          </p>
        </div>
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
            </div>
          )}
        </div>

        {/* Situation arrêtée à la date de bascule */}
        <div className="p-3.5 border border-[var(--color-border-subtle)] space-y-3 bg-[var(--color-surface)]">
          <div className="flex items-center gap-2 font-semibold text-xs text-[var(--color-fg)]">
            <History size={14} className="text-[var(--color-brand-500)]" />
            <span>1. Situation à la date de bascule</span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label className="block text-xs font-medium text-[var(--color-fg)] mb-1">
                Date de début de gestion SIRH <span className="text-[var(--color-danger-500)]">*</span>
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
                TCONGE antérieur applicable (Ar / jour) <span className="text-[var(--color-danger-500)]">*</span>
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
                Dernier taux journalier de congé clôturé.
              </span>
            </div>
          </div>
          <div>
            <label className="block text-xs font-medium text-[var(--color-fg)] mb-1">
              Valeur du stock restant (Ar) <span className="text-[var(--color-danger-500)]">*</span>
            </label>
            <MoneyInput
              maxDecimals={2}
              min="0"
              value={valeurSoldeOuverture}
              onValueChange={setValeurSoldeOuverture}
              placeholder="Ex. 240 000"
              required
            />
            <span className="text-[10.5px] text-[var(--color-fg-muted)] mt-0.5 block">
              Valeur comptable totale du stock de congés à la date de bascule.
            </span>
          </div>
        </div>

        {/* Étape 3 : Période annuelle de référence & Cumuls passés */}
        <div className="p-3.5 border border-[var(--color-border-subtle)] space-y-3 bg-[var(--color-surface)]">
          <div className="flex items-center gap-2 font-semibold text-xs text-[var(--color-fg)]">
            <Calendar size={14} className="text-[var(--color-brand-500)]" />
            <span>2. Période annuelle de référence</span>
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

          {/* Cumuls de la période courante, indépendants du stock restant. */}
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
              <span className="text-[var(--color-fg-muted)] block text-[10.5px]">Valeur du stock :</span>
              <strong className="font-mono text-xs text-[var(--color-brand-600)]">{formatMoneyAr(valeurSoldeOuverture || 0)}</strong>
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
