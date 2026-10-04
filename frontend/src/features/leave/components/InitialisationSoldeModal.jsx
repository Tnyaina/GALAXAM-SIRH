import { useState, useEffect } from 'react';
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


export default function InitialisationSoldeModal({
  isOpen,
  onClose,
  onSuccess,
  employe
}) {
  // Contexte
  const toast = useToast();

  // Données communes
  const [dateCutover, setDateCutover] = useState('');
  const [soldeJours, setSoldeJours] = useState('');
  const [valeurSoldeOuverture, setValeurSoldeOuverture] = useState('');
  const [tauxApplicable, setTauxApplicable] = useState('');
  const [referenceArrete, setReferenceArrete] = useState('');
  const [commentaire, setCommentaire] = useState('');

  // Données pour AGGREGATED_OPENING
  const [cumulDroitsOuverture, setCumulDroitsOuverture] = useState('');
  const [cumulValorisationOuverture, setCumulValorisationOuverture] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Données dérivées
  const dateDebutReference = dateCutover ? `${dateCutover.slice(0, 4)}-01-01` : '';
  const dateFinCalculee = dateCutover ? `${dateCutover.slice(0, 4)}-12-31` : '';

  // Initialisation à chaque ouverture
  useEffect(() => {
    if (!isOpen) return;

    setDateCutover('');
    setSoldeJours('');
    setTauxApplicable('');
    setReferenceArrete('');

    setValeurSoldeOuverture('');
    setCumulDroitsOuverture('');
    setCumulValorisationOuverture('');
    setCommentaire('');
    setError(null);
  }, [isOpen]);

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
    if (!referenceArrete.trim()) {
      setError("Indiquez la référence de l'arrêté DAF utilisé pour ces valeurs.");
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
    if ((cDroits === 0) !== (cValo === 0)) {
      setError('Les cumuls de l’année doivent être tous deux nuls, ou tous deux positifs : jours acquis et leur valorisation.');
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
        referenceArrete: referenceArrete.trim(),
        commentaire: commentaire.trim()
      };

      await congeService.cutoverOpening(payload);

      toast({
        title: 'Reprise historique enregistrée',
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
      title="Reprise historique des congés"
      size="2xl"
    >
      <form onSubmit={handleSubmit} className="space-y-4 font-sans text-sm">
        {error && <FormError message={error} />}

        {/* Contexte d'usage */}
        <div className="p-2.5 border-l-4 border-[var(--color-brand-500)] bg-[var(--color-brand-500)]/6">
          <p className="text-xs text-[var(--color-fg-muted)]">
            Réservé aux salariés ayant un historique arrêté par la DAF avant leur entrée dans le SIRH.
            Pour une nouvelle embauche sans droits antérieurs, ne saisissez rien ici : la première clôture de paie crée automatiquement la période civile et l'acquisition.
            La date d'arrêté inclut les acquisitions déjà comptées dans les cumuls saisis ; les paies closes à cette date ou avant ne les recréent pas.
          </p>
        </div>
        <details className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] px-3 py-2.5 text-xs">
          <summary className="cursor-pointer font-semibold text-[var(--color-fg)]">Guide rapide : préparer la reprise avec les arrêtés DAF</summary>
          <ol className="mt-2 ml-4 list-decimal space-y-1 text-[var(--color-fg-muted)]">
            <li>Choisissez la date de la dernière clôture de paie dont les acquisitions figurent dans l'arrêté DAF (par exemple le 25/09/2026). La clôture suivante doit venir après cette date.</li>
            <li>Saisissez le stock de jours encore disponible après les congés déjà pris, puis sa valeur monétaire restante. Exemple : 14,5 jours pour 240 000 Ar.</li>
            <li>Le TCONGE est le dernier taux journalier applicable certifié par la DAF, pas le salaire actuel ni 240 000 ÷ 14,5 dans l'exemple.</li>
            <li>Les cumuls annuels sont les droits acquis et leur valorisation depuis le 1er janvier jusqu'à cette clôture, même si certains droits ont déjà été consommés. Exemple : 22,5 jours acquis et 315 000 Ar ; ces chiffres diffèrent du stock restant.</li>
            <li>La période civile est calculée automatiquement du 1er janvier au 31 décembre de l'année d'arrêté. Indiquez la référence identifiable de l'arrêté DAF qui justifie ces chiffres.</li>
          </ol>
          <p className="mt-2 text-[var(--color-fg-muted)]">Cette reprise actuelle enregistre un stock global et les cumuls de l'année ; elle n'importe pas le détail mensuel des anciens lots. Une nouvelle embauche sans antériorité ne passe pas par ce formulaire.</p>
        </details>
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
                Date de la dernière clôture de paie reprise <span className="text-[var(--color-danger-500)]">*</span>
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
                Dernier TCONGE applicable certifié (Ar / jour) <span className="text-[var(--color-danger-500)]">*</span>
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
                Taux en vigueur à la date d'arrêté, indépendant de la valeur du stock restant.
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
              <div className="p-2 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] font-mono text-xs font-semibold text-[var(--color-fg)]">
                {dateDebutReference || '-'}
              </div>
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
                  Total des jours acquis depuis le 1er janvier <span className="text-[var(--color-danger-500)]">*</span>
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
                  Inclut les jours déjà pris ; ce n'est pas le solde restant.
                </span>
              </div>

              <div>
                <label className="block text-xs font-medium text-[var(--color-fg)] mb-1">
                  Valeur totale de ces jours acquis (Ariary) <span className="text-[var(--color-danger-500)]">*</span>
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
                  Cumul des valorisations des acquisitions de l'année, pas valeur du stock.
                </span>
              </div>
            </div>
          </div>

        </div>

        {/* Pièce source de la reprise et précision éventuelle */}
        <div>
          <label className="block text-xs font-medium text-[var(--color-fg)] mb-1">
            Référence de l'arrêté DAF utilisé <span className="text-[var(--color-danger-500)]">*</span>
          </label>
          <Input
            type="text"
            value={referenceArrete}
            onChange={(e) => setReferenceArrete(e.target.value)}
            placeholder="Ex. Arrêté congés DAF au 25/09/2026, dossier RH-2026-14"
            required
          />
          <span className="text-[10.5px] text-[var(--color-fg-muted)] mt-0.5 block">Nom ou numéro permettant de retrouver le document source. Aucun fichier n'est importé ici.</span>
        </div>
        <div>
          <Input label="Précision complémentaire (facultative)" type="text"
            value={commentaire} onChange={(e) => setCommentaire(e.target.value)} />
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
            <div className="bg-[var(--color-surface)] p-2 border border-[var(--color-border-subtle)] col-span-2">
              <span className="text-[var(--color-fg-muted)] block text-[10.5px]">Acquisitions de l'année déjà reprises :</span>
              <strong className="font-mono text-xs">{cumulDroitsOuverture || '0'} jours · {formatMoneyAr(cumulValorisationOuverture || 0)}</strong>
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
