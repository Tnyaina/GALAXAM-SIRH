import { useState, useEffect, useMemo } from 'react';
import { Calendar, History, AlertTriangle, CheckCircle, HelpCircle, ChevronDown, ChevronUp } from 'lucide-react';
import congeService from '@/features/leave/api/congeService';
import { getErrorMessage } from '@/api/apiClient';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
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

  // Aide au remplissage
  const [showHelpGuide, setShowHelpGuide] = useState(false);

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
    setShowHelpGuide(false);
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
      title="Initialisation & Reprise du Solde de Congés"
      size="2xl"
    >
      <form onSubmit={handleSubmit} className="space-y-4 font-sans text-sm">
        {error && <FormError message={error} />}

        {/* Fiche d'identification du collaborateur */}
        <div className="p-3.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] flex flex-wrap items-center justify-between gap-3 text-xs">
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
              <span className="text-[var(--color-fg-muted)]">Salaire de base contractuel :</span>{' '}
              <strong className="text-[var(--color-fg)] text-sm">{formatMoneyAr(salaireBase)}</strong>
              <span className="block text-[11px] text-[var(--color-fg-subtle)]">
                Base journalière (÷30) : ~{formatMoneyAr(salaireBase / 30)}/j
              </span>
            </div>
          )}
        </div>

        {/* Guide d'explication et d'aide au remplissage */}
        <div className="border border-[var(--color-brand-500)]/30 bg-[var(--color-brand-500)]/5 overflow-hidden">
          <button
            type="button"
            onClick={() => setShowHelpGuide(!showHelpGuide)}
            className="w-full px-3.5 py-2.5 flex items-center justify-between text-left hover:bg-[var(--color-brand-500)]/10 transition-colors"
          >
            <div className="flex items-center gap-2">
              <HelpCircle size={15} className="text-[var(--color-brand-600)]" />
              <span className="text-xs font-bold text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)]">
                Comment renseigner ce formulaire ? (Guide rapide)
              </span>
            </div>
            <div className="flex items-center gap-1 text-[11px] text-[var(--color-brand-600)] font-medium">
              <span>{showHelpGuide ? 'Masquer le guide' : 'Afficher les explications'}</span>
              {showHelpGuide ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
            </div>
          </button>

          {showHelpGuide && (
            <div className="px-3.5 pb-3 pt-1 text-xs text-[var(--color-fg)] space-y-2 border-t border-[var(--color-brand-500)]/20 bg-[var(--color-surface)]">
              <p className="text-[12px] leading-relaxed text-[var(--color-fg-muted)]">
                Ce formulaire permet d’établir la <strong>situation de départ</strong> des congés du collaborateur lors du passage sur GALAXAM SIRH.
              </p>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pt-1">
                <div className="p-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                  <h4 className="font-bold text-xs text-[var(--color-fg)] mb-1">
                    1. Solde disponible & Taux journalier
                  </h4>
                  <p className="text-[11px] text-[var(--color-fg-muted)] leading-relaxed">
                    • <strong>Solde disponible</strong> : Indiquez simplement le nombre de jours restant dus à la date choisie.<br />
                    • <strong>Valeur journalière</strong> : C'est le montant d'un jour de congé. Par défaut, le SIRH calcule <code>Salaire Brut ÷ 30</code>. Vous pouvez l'ajuster si vos relevés indiquent un autre taux.
                  </p>
                </div>
                <div className="p-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                  <h4 className="font-bold text-xs text-[var(--color-fg)] mb-1">
                    2. Historique de l'année en cours
                  </h4>
                  <p className="text-[11px] text-[var(--color-fg-muted)] leading-relaxed">
                    • <strong>Droits acquis</strong> : Total des jours accumulés depuis le début de l'année (ex: 2,5 j par mois écoulé).<br />
                    • <strong>Valorisation cumulée</strong> : montant total de valorisation des droits acquis avant la reprise, à reporter depuis le calcul Excel ou l'historique de paie. Cette valeur ne se déduit pas simplement des mois écoulés et du salaire actuel.
                  </p>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Étape 1 : Choix de la méthode d'initialisation */}
        <div className="p-3.5 border border-[var(--color-border-subtle)] space-y-2.5 bg-[var(--color-surface)]">
          <label className="block text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
            1. Méthode d'initialisation
          </label>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <label
              className={`p-3.5 border cursor-pointer flex flex-col justify-between transition-colors ${
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
                <p className="text-[11px] text-[var(--color-fg-muted)] mt-2 pl-5 leading-relaxed">
                  Idéal si vous avez les données de suivi ou de paie des mois précédents. Permet le calcul 100% automatique des indemnités légales (règle du 1/12ème vs maintien de salaire).
                </p>
              </div>
            </label>

            <label
              className={`p-3.5 border cursor-pointer flex flex-col justify-between transition-colors ${
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
                    <span>Saisie simplifiée (Solde & Taux seuls)</span>
                  </div>
                  <Badge variant="neutral" size="xs">Rapide</Badge>
                </div>
                <p className="text-[11px] text-[var(--color-fg-muted)] mt-2 pl-5 leading-relaxed">
                  Si vous ne connaissez que les jours restants et le salaire actuel. Le collaborateur peut immédiatement poser ses congés sans bloquer le système.
                </p>
              </div>
            </label>
          </div>
        </div>

        {/* Étape 2 : Situation arrêtée à la date de bascule */}
        <div className="p-3.5 border border-[var(--color-border-subtle)] space-y-3 bg-[var(--color-surface)]">
          <div className="flex items-center gap-2 font-bold text-xs text-[var(--color-fg)] uppercase tracking-wider">
            <History size={15} className="text-[var(--color-brand-500)]" />
            <span>2. Situation arrêtée à la date pivot</span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3.5">
            <div>
              <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                Date d'arrêté des compteurs <span className="text-[var(--color-danger-500)]">*</span>
              </label>
              <Input
                type="date"
                value={dateCutover}
                onChange={(e) => setDateCutover(e.target.value)}
                required
              />
              <span className="text-[11px] text-[var(--color-fg-muted)] mt-1 block">
                Date à laquelle le solde saisi est valide (ex: fin du mois passé ou ce jour).
              </span>
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
              <span className="text-[11px] text-[var(--color-fg-muted)] mt-1 block">
                Nombre exact de jours de congé restants dus au salarié à cette date.
              </span>
            </div>

            <div>
              <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                Valeur journalière de congé (Ar / jour) <span className="text-[var(--color-danger-500)]">*</span>
              </label>
              <Input
                type="number"
                step="0.01"
                min="0.01"
                value={tauxApplicable}
                onChange={(e) => setTauxApplicable(e.target.value)}
                placeholder="Ex: 11666.67"
                required
              />
              <span className="text-[11px] text-[var(--color-fg-muted)] mt-1 block">
                Pré-calculé : Salaire brut contractuel ÷ 30 (ajustable selon vos calculs).
              </span>
            </div>
          </div>
        </div>

        {/* Étape 3 : Période annuelle de référence & Cumuls passés */}
        <div className="p-3.5 border border-[var(--color-border-subtle)] space-y-3 bg-[var(--color-surface)]">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 font-bold text-xs text-[var(--color-fg)] uppercase tracking-wider">
              <Calendar size={15} className="text-[var(--color-brand-500)]" />
              <span>3. Période annuelle de référence (12 mois)</span>
            </div>

          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
            <div>
              <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                Date de début de l'année de référence <span className="text-[var(--color-danger-500)]">*</span>
              </label>
              <Input
                type="date"
                value={dateDebutReference}
                onChange={(e) => setDateDebutReference(e.target.value)}
                required
              />
              <span className="text-[11px] text-[var(--color-fg-muted)] mt-1 block">
                Point de départ de l'exercice de congés (généralement le 1er janvier).
              </span>
            </div>

            <div>
              <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                Date de fin calculée automatiquement
              </label>
              <div className="p-2 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] font-mono text-sm font-semibold text-[var(--color-fg)] flex items-center justify-between">
                <span>{dateFinCalculee || '—'}</span>
                <span className="text-[10px] uppercase font-bold text-[var(--color-brand-600)] bg-[var(--color-brand-500)]/10 px-1.5 py-0.5">
                  12 mois consécutifs
                </span>
              </div>
              <span className="text-[11px] text-[var(--color-fg-muted)] mt-1 block">
                Fin légale de l'exercice annuel de référence.
              </span>
            </div>
          </div>

          {/* Cumuls si reprise complète avec historique */}
          {modeReprise === 'AGGREGATED_OPENING' && (
            <div className="pt-3 border-t border-[var(--color-border-subtle)] space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-[var(--color-fg)]">
                  Données accumulées entre le {dateDebutReference || 'début'} et le {dateCutover || "l'arrêté"} :
                </span>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
                <div>
                  <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                    Total des droits acquis sur la période (jours) <span className="text-[var(--color-danger-500)]">*</span>
                  </label>
                  <Input
                    type="number"
                    step="0.0001"
                    min="0"
                    value={cumulDroitsOuverture}
                    onChange={(e) => setCumulDroitsOuverture(e.target.value)}
                    placeholder="Ex: 22.50 (environ 2.5 jours par mois)"
                    required
                  />
                  <span className="text-[11px] text-[var(--color-fg-muted)] mt-1 block">
                    Cumul total des jours acquis depuis le début de l'année de référence (hors reports antérieurs).
                  </span>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                    Valorisation financière cumulée (Ariary) <span className="text-[var(--color-danger-500)]">*</span>
                  </label>
                  <Input
                    type="number"
                    step="0.01"
                    min="0"
                    value={cumulValorisationOuverture}
                    onChange={(e) => setCumulValorisationOuverture(e.target.value)}
                    placeholder="Ex: 3150000.00"
                    required
                  />
                  <span className="text-[11px] text-[var(--color-fg-muted)] mt-1 block">
                    Somme des valorisations mensuelles des droits acquis, reprise du calcul KAJY ou de l'historique de paie. Le solde disponible inclut aussi les reports et n'est pas ce cumul.
                  </span>
                </div>
              </div>
            </div>
          )}

          {modeReprise === 'MINIMAL_OPENING' && (
            <div className="p-3 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 text-xs flex items-start gap-2.5 text-[var(--color-warning-800)] dark:text-[var(--color-warning-200)]">
              <AlertTriangle size={16} className="text-[var(--color-warning-600)] shrink-0 mt-0.5" />
              <span>
                <strong>Mode simplifié sélectionné :</strong> Les congés et la paie fonctionneront immédiatement avec le taux journalier de {formatMoneyAr(tauxApplicable || 0)}/j. Les mois futurs de l'exercice seront comptabilisés normalement au fil des paies.
              </span>
            </div>
          )}
        </div>

        {/* Motif ou référence */}
        <div>
          <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
            Motif / Référence de l'initialisation
          </label>
          <Input
            type="text"
            value={commentaire}
            onChange={(e) => setCommentaire(e.target.value)}
            placeholder="Ex: Initialisation du solde de congés au démarrage SIRH"
          />
        </div>

        {/* Prévisualisation Récapitulative */}
        <div className="p-3.5 bg-[var(--color-brand-500)]/10 border border-[var(--color-brand-500)]/20 text-xs">
          <div className="font-bold text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] mb-2 flex items-center gap-1.5">
            <CheckCircle size={15} />
            <span>Récapitulatif des données qui seront enregistrées :</span>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-[var(--color-fg)]">
            <div className="bg-[var(--color-surface)] p-2 border border-[var(--color-border-subtle)]">
              <span className="text-[var(--color-fg-muted)] block text-[11px]">Solde disponible :</span>
              <strong className="font-mono text-sm text-[var(--color-brand-600)]">{soldeJours || '0'} jours</strong>
            </div>
            <div className="bg-[var(--color-surface)] p-2 border border-[var(--color-border-subtle)]">
              <span className="text-[var(--color-fg-muted)] block text-[11px]">Valeur journalière :</span>
              <strong className="font-mono text-sm text-[var(--color-brand-600)]">{formatMoneyAr(tauxApplicable || 0)}/j</strong>
            </div>
            <div className="bg-[var(--color-surface)] p-2 border border-[var(--color-border-subtle)] col-span-2">
              <span className="text-[var(--color-fg-muted)] block text-[11px]">Exercice de référence :</span>
              <strong className="font-mono text-xs">{dateDebutReference || '—'} → {dateFinCalculee || '—'}</strong>
            </div>
          </div>
        </div>

        {/* Actions */}
        <div className="flex items-center justify-end gap-2.5 pt-2 border-t border-[var(--color-border-subtle)]">
          <Button variant="ghost" size="sm" onClick={onClose} disabled={loading} type="button">
            Annuler
          </Button>
          <Button variant="primary" size="sm" type="submit" loading={loading}>
            Confirmer et enregistrer la situation
          </Button>
        </div>
      </form>
    </Modal>
  );
}
