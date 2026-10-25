import { useState, useEffect } from 'react';
import { Calendar, History } from 'lucide-react';
import congeService from '@/features/leave/api/congeService';
import { getErrorMessage } from '@/api/apiClient';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import MoneyInput from '@/components/ui/forms/MoneyInput';
import Modal from '@/components/ui/overlays/Modal';
import FormError from '@/components/ui/feedback/FormError';
import { useToast } from '@/components/ui/feedback/Toast';
import { formatMoneyAr } from '@/utils/moneyUtils';
import employeService from '@/features/employees/api/employeService';

const emptyLot = () => ({ exercice: '', joursRestants: '', tauxHistorique: '' });
const valeurLot = (lot) => {
  const valeur = Number(lot.joursRestants) * Number(lot.tauxHistorique);
  return Number.isFinite(valeur) ? Number(valeur.toFixed(2)) : 0;
};

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
  const [repriseMode, setRepriseMode] = useState('GLOBAL');
  const [lotsReprise, setLotsReprise] = useState([emptyLot()]);

  // Données pour AGGREGATED_OPENING
  const [cumulDroitsOuverture, setCumulDroitsOuverture] = useState('');
  const [cumulValorisationOuverture, setCumulValorisationOuverture] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [employeDetail, setEmployeDetail] = useState(null);

  useEffect(() => {
    if (!isOpen) return;
    const matricule = employe?.matricule || employe?.matriculeEmploye;
    if (matricule || !(employe?.idEmploye || employe?.id)) {
      setEmployeDetail(null);
      return;
    }
    setEmployeDetail(null);
    let active = true;
    employeService.getEmployeFicheDetail(employe.idEmploye || employe.id)
      .then((result) => {
        if (active) setEmployeDetail(result?.employe || result || null);
      })
      .catch(() => {
        if (active) setEmployeDetail(null);
      });
    return () => { active = false; };
  }, [isOpen, employe?.idEmploye, employe?.id, employe?.matricule, employe?.matriculeEmploye]);

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
    setRepriseMode('GLOBAL');
    setLotsReprise([emptyLot()]);
    setError(null);
  }, [isOpen]);

  // Validation et soumission
  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    const detaille = repriseMode === 'DETAILLE';
    if (detaille && lotsReprise.some((lot) => !/^\d{4}$/.test(String(lot.exercice))
      || Number(lot.exercice) < 1900 || Number(lot.exercice) > Number(dateCutover.slice(0, 4))
      || !Number.isFinite(Number(lot.joursRestants)) || Number(lot.joursRestants) <= 0
      || !Number.isFinite(Number(lot.tauxHistorique)) || Number(lot.tauxHistorique) <= 0
      || valeurLot(lot) <= 0
      || !/^\d+(?:[.,]\d{1,2})?$/.test(String(lot.joursRestants)))) {
      setError('Chaque lot exige un exercice au plus tard celui de l’arrêté, des jours restants et un taux historique positifs.');
      return;
    }
    const sVal = parseFloat(soldeJours);
    if (isNaN(sVal) || sVal < 0) {
      setError('Veuillez renseigner un solde de jours valide (≥ 0).');
      return;
    }
    if (detaille && (sVal <= 0 || Number(lotsReprise.reduce((sum, lot) => sum + Number(lot.joursRestants), 0).toFixed(2)) !== sVal)) {
      setError('La somme des jours de tous les exercices doit correspondre au solde certifié.');
      return;
    }

    const vVal = detaille ? Number(lotsReprise.reduce((sum, lot) => sum + valeurLot(lot), 0).toFixed(2)) : parseFloat(valeurSoldeOuverture);
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
      if (detaille) payload.lotsReprise = [...lotsReprise]
        .sort((a, b) => Number(a.exercice) - Number(b.exercice))
        .map((lot) => ({ dateOrigine: `${lot.exercice}-01-01`, joursRestants: Number(lot.joursRestants), valeurRestante: valeurLot(lot) }));

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
      size="3xl"
    >
      <form onSubmit={handleSubmit} className="space-y-6 font-sans text-sm">
        {error && <FormError message={error} />}

        {/* Contexte d'usage */}
        <p className="border-l-4 border-[var(--color-brand-500)] bg-[var(--color-brand-500)]/6 px-3 py-2 text-xs leading-relaxed text-[var(--color-fg-muted)]">
          Pour les droits acquis avant la mise en service du SIRH, à partir d’une situation arrêtée par la DAF.
        </p>
        <details className="border-b border-[var(--color-border-subtle)] pb-3 text-xs">
          <summary className="cursor-pointer font-semibold text-[var(--color-brand-600)] focus-visible:outline-2 focus-visible:outline-[var(--color-brand-500)]">Quand utiliser cette reprise et comment préparer les chiffres</summary>
          <p className="mt-3 leading-relaxed text-[var(--color-fg-muted)]">Pour une nouvelle embauche sans droits antérieurs, ne saisissez rien ici : la première clôture de paie crée automatiquement la période civile et l'acquisition. La date d'arrêté inclut les acquisitions déjà comptées dans les cumuls saisis ; les paies closes à cette date ou avant ne les recréent pas.</p>
          <ol className="mt-2 ml-4 list-decimal space-y-1 text-[var(--color-fg-muted)]">
            <li>Choisissez la date de la dernière clôture de paie dont les acquisitions figurent dans l'arrêté DAF (par exemple le 25/09/2026). La clôture suivante doit venir après cette date.</li>
            <li>En mode global, saisissez le stock restant et sa valeur totale sans préciser l'origine des jours. En mode détaillé, saisissez le solde certifié, puis répartissez tous ses jours par exercice avec le taux historique de chaque lot.</li>
            <li>Le TCONGE est le dernier taux journalier applicable certifié par la DAF, pas le salaire actuel ni 240 000 ÷ 14,5 dans l'exemple.</li>
            <li>Les cumuls annuels sont les droits acquis et leur valorisation depuis le 1er janvier jusqu'à cette clôture, même si certains droits ont déjà été consommés. Exemple : 22,5 jours acquis et 315 000 Ar ; ces chiffres diffèrent du stock restant.</li>
            <li>La période civile est calculée automatiquement du 1er janvier au 31 décembre de l'année d'arrêté. Indiquez la référence identifiable de l'arrêté DAF qui justifie ces chiffres.</li>
          </ol>
          <p className="mt-2 text-[var(--color-fg-muted)]">La valeur de chaque lot détaillé est calculée à partir de ses jours restants et de son taux. Une nouvelle embauche sans antériorité ne passe pas par ce formulaire.</p>
        </details>
        {/* Fiche d'identification du collaborateur */}
        <div className="border-b border-[var(--color-border-subtle)] pb-4 text-xs">
          <div>
              <span className="text-[var(--color-fg-muted)]">Salarié :</span>{' '}
            <strong className="text-[var(--color-fg)] text-sm">
              {employe?.nom || employeDetail?.nom} {employe?.prenom || employeDetail?.prenom}
            </strong>{' '}
            <span className="font-mono text-[var(--color-brand-600)] font-semibold">
              {employe?.matricule || employe?.matriculeEmploye || employeDetail?.matricule
                ? `(${employe?.matricule || employe?.matriculeEmploye || employeDetail?.matricule})`
                : '(Matricule indisponible)'}
            </span>
          </div>
        </div>

        {/* Situation arrêtée à la date de bascule */}
        <section className="space-y-5">
          <div className="flex items-center gap-2 border-b border-[var(--color-border-subtle)] pb-2 font-semibold text-sm text-[var(--color-fg)]">
            <History size={14} className="text-[var(--color-brand-500)]" />
            <span>1. Situation à la date de bascule</span>
          </div>

          <fieldset className="space-y-2">
            <legend className="text-xs font-medium text-[var(--color-fg)]">Mode de reprise</legend>
            <div className="flex flex-wrap gap-2 text-xs">
              <label className={`flex min-h-10 cursor-pointer items-center gap-2 border px-3 py-2 ${repriseMode === 'GLOBAL' ? 'border-[var(--color-brand-500)] bg-[var(--color-brand-500)]/10' : 'border-[var(--color-border-subtle)]'}`}><input type="radio" name="repriseMode" checked={repriseMode === 'GLOBAL'} onChange={() => setRepriseMode('GLOBAL')} /> Solde global · origine inconnue</label>
              <label className={`flex min-h-10 cursor-pointer items-center gap-2 border px-3 py-2 ${repriseMode === 'DETAILLE' ? 'border-[var(--color-brand-500)] bg-[var(--color-brand-500)]/10' : 'border-[var(--color-border-subtle)]'}`}><input type="radio" name="repriseMode" checked={repriseMode === 'DETAILLE'} onChange={() => setRepriseMode('DETAILLE')} /> Lots par exercice</label>
            </div>
          </fieldset>

          <details className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] px-3 py-2 text-xs">
            <summary className="cursor-pointer font-semibold text-[var(--color-brand-600)] focus-visible:outline-2 focus-visible:outline-[var(--color-brand-500)]">Aide pour choisir les jours et les montants</summary>
            <div className="mt-2 space-y-1.5 leading-relaxed text-[var(--color-fg-muted)]">
              <p><strong className="text-[var(--color-fg)]">Solde disponible :</strong> jours encore utilisables à la date de l’arrêté DAF, après les congés déjà pris.</p>
              {repriseMode === 'GLOBAL' ? (
                <p><strong className="text-[var(--color-fg)]">Valeur du stock :</strong> montant restant indiqué dans l’arrêté. Il ne se déduit pas automatiquement du dernier TCONGÉ.</p>
              ) : (
                <p><strong className="text-[var(--color-fg)]">Taux de chaque exercice :</strong> taux historique certifié pour les jours de ce lot. Exemple : 2 j de 2024 à 8 000 Ar/j = 16 000 Ar ; 3 j de 2025 à 9 000 Ar/j = 27 000 Ar. Le stock de ces deux lots vaut 43 000 Ar.</p>
              )}
              <p><strong className="text-[var(--color-fg)]">Dernier TCONGÉ :</strong> taux applicable à l’arrêté pour les acquisitions suivantes ; il peut différer des taux des lots.</p>
              <p><strong className="text-[var(--color-fg)]">Cumuls de l’année :</strong> tous les jours acquis du 1er janvier à l’arrêté et leur valorisation, y compris les jours déjà consommés. Reprenez ces deux chiffres de l’historique de paie ou de l’arrêté, sans les confondre avec le stock restant.</p>
            </div>
          </details>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
            <div>
              <label className="block text-xs font-medium text-[var(--color-fg)] mb-1 leading-tight">
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
              <label className="block text-xs font-medium text-[var(--color-fg)] mb-1 leading-tight">
                Solde certifié disponible (jours) <span className="text-[var(--color-danger-500)]">*</span>
              </label>
              <Input
                type="number"
                step={repriseMode === 'DETAILLE' ? '0.01' : '0.25'}
                min="0"
                value={soldeJours}
                onChange={(e) => setSoldeJours(e.target.value)}
                placeholder="Ex. 14.5"
                required
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-[var(--color-fg)] mb-1 leading-tight">
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
                Taux en vigueur à l'arrêté pour les acquisitions suivantes. Chaque lot détaillé garde son propre taux historique.
              </span>
            </div>
          </div>
          {repriseMode === 'GLOBAL' && <div className="md:max-w-[calc(50%-0.625rem)]">
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
              Montant restant certifié par la DAF ; ne pas le déduire du TCONGÉ actuel.
            </span>
          </div>}
          {repriseMode === 'DETAILLE' && <div className="space-y-2 border-t border-[var(--color-border-subtle)] pt-3">
            <p className="text-xs text-[var(--color-fg-muted)]">Répartissez les {soldeJours || 'jours du solde'} par exercice d’acquisition (ex. 2 j de 2024, 3 j de 2025…). Les exercices les plus anciens sont consommés en premier.</p>
            {lotsReprise.map((lot, index) => <div key={index} className="grid grid-cols-1 md:grid-cols-[minmax(0,0.8fr)_minmax(0,0.8fr)_minmax(0,1fr)_minmax(0,0.9fr)_auto] gap-3 items-end border-b border-[var(--color-border-subtle)] py-3">
              <Input label={`Lot ${index + 1} · exercice`} type="number" min="1900" max={dateCutover ? dateCutover.slice(0, 4) : undefined} step="1" placeholder="Ex. 2024" value={lot.exercice} required onChange={(e) => setLotsReprise((prev) => prev.map((item, i) => i === index ? { ...item, exercice: e.target.value } : item))} />
              <Input label="Jours restants" type="number" min="0.01" step="0.01" value={lot.joursRestants} required onChange={(e) => setLotsReprise((prev) => prev.map((item, i) => i === index ? { ...item, joursRestants: e.target.value } : item))} />
              <MoneyInput label="Taux historique (Ar/j)" maxDecimals={2} min="0.01" value={lot.tauxHistorique} required onValueChange={(value) => setLotsReprise((prev) => prev.map((item, i) => i === index ? { ...item, tauxHistorique: value } : item))} />
              <div className="text-xs"><span className="block text-[var(--color-fg-muted)]">Valeur restante</span><strong>{lot.joursRestants && lot.tauxHistorique ? formatMoneyAr(valeurLot(lot)) : '—'}</strong></div>
              <Button type="button" variant="ghost" size="sm" disabled={lotsReprise.length === 1} onClick={() => setLotsReprise((prev) => prev.filter((_, i) => i !== index))}>Retirer</Button>
            </div>)}
            <Button type="button" variant="ghost" size="sm" onClick={() => setLotsReprise((prev) => [...prev, emptyLot()])}>Ajouter un exercice</Button>
            <p className="text-xs font-semibold">Lots renseignés : {lotsReprise.reduce((sum, lot) => sum + (Number(lot.joursRestants) || 0), 0).toFixed(2)} / {soldeJours || '—'} j · {formatMoneyAr(lotsReprise.reduce((sum, lot) => sum + valeurLot(lot), 0))}</p>
          </div>}
        </section>

        {/* Étape 3 : Période annuelle de référence & Cumuls passés */}
        <section className="space-y-5">
          <div className="flex items-center gap-2 border-b border-[var(--color-border-subtle)] pb-2 font-semibold text-sm text-[var(--color-fg)]">
            <Calendar size={14} className="text-[var(--color-brand-500)]" />
            <span>2. Période annuelle de référence</span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
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
          <div className="pt-4 border-t border-[var(--color-border-subtle)] space-y-3">
            <span className="text-xs font-medium text-[var(--color-fg)] block">
              Données cumulées de l'exercice en cours ({dateDebutReference || 'début'} → {dateCutover || 'arrêté'}) :
            </span>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
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
                  Valorisation de tous les jours acquis cette année, y compris ceux déjà pris ; voir l’arrêté ou l’historique de paie.
                </span>
              </div>
            </div>
          </div>

        </section>

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

        {/* Résumé à vérifier avant écriture */}
        <details className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-4 text-xs">
          <summary className="cursor-pointer font-semibold text-sm text-[var(--color-fg)] focus-visible:outline-2 focus-visible:outline-[var(--color-brand-500)]">Vérifier le récapitulatif avant validation</summary>
          <div className="mt-4">
          <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-5 gap-y-2">
            <div><dt className="text-[var(--color-fg-muted)]">Date d’arrêté</dt><dd className="font-medium">{dateCutover ? new Date(`${dateCutover}T00:00:00`).toLocaleDateString('fr-FR') : 'À renseigner'}</dd></div>
            <div><dt className="text-[var(--color-fg-muted)]">Période de référence</dt><dd className="font-medium">{dateDebutReference || '—'} → {dateFinCalculee || '—'}</dd></div>
            <div><dt className="text-[var(--color-fg-muted)]">Stock certifié · {repriseMode === 'DETAILLE' ? `${lotsReprise.length} lot(s)` : 'global'}</dt><dd className="font-medium">{repriseMode === 'DETAILLE' ? `${lotsReprise.reduce((sum, lot) => sum + (Number(lot.joursRestants) || 0), 0).toFixed(2)} jours · ${formatMoneyAr(lotsReprise.reduce((sum, lot) => sum + valeurLot(lot), 0))}` : `${soldeJours || 'À renseigner'} jours · ${valeurSoldeOuverture === '' ? 'valeur à renseigner' : formatMoneyAr(valeurSoldeOuverture)}`}</dd></div>
            <div><dt className="text-[var(--color-fg-muted)]">TCONGE applicable</dt><dd className="font-medium">{tauxApplicable ? `${formatMoneyAr(tauxApplicable)} / jour` : 'À renseigner'}</dd></div>
            <div className="sm:col-span-2"><dt className="text-[var(--color-fg-muted)]">Cumul acquis cette année</dt><dd className="font-medium">{cumulDroitsOuverture === '' ? 'jours à renseigner' : `${cumulDroitsOuverture} jours`} · {cumulValorisationOuverture === '' ? 'valeur à renseigner' : formatMoneyAr(cumulValorisationOuverture)}</dd></div>
            <div className="sm:col-span-2"><dt className="text-[var(--color-fg-muted)]">Référence DAF</dt><dd className="font-medium break-words">{referenceArrete.trim() || 'À renseigner'}</dd></div>
          </dl>
          <p className="mt-2 border-t border-[var(--color-border-subtle)] pt-2 text-[var(--color-fg-muted)]">
            {repriseMode === 'GLOBAL' ? 'La reprise globale crée un lot agrégé sans origine historique.' : 'Chaque exercice crée un lot valorisé à son taux historique, consommé du plus ancien au plus récent.'}
          </p>
          </div>
        </details>

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
