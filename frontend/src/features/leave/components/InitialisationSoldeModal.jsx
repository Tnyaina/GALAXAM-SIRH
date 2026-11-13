import { useState, useEffect } from 'react';
import congeService from '@/features/leave/api/congeService';
import { getErrorMessage } from '@/api/apiClient';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import MoneyInput from '@/components/ui/forms/MoneyInput';
import Modal from '@/components/ui/overlays/Modal';
import FormError from '@/components/ui/feedback/FormError';
import { useToast } from '@/components/ui/feedback/Toast';
import { formatMoneyAr, formatLeaveRateAr } from '@/utils/moneyUtils';
import employeService from '@/features/employees/api/employeService';

const emptyLot = () => ({ id: crypto.randomUUID(), exercice: '', joursRestants: '', tauxHistorique: '' });
const parseJours = (value) => Number(String(value ?? '').replace(',', '.'));
const referenceFromDate = (date) => date ? `Arrêté DAF du ${date.slice(8, 10)}/${date.slice(5, 7)}/${date.slice(0, 4)}` : '';
const generatedReference = /^Arrêté DAF du \d{2}\/\d{2}\/\d{4}$/;
const valeurLot = (lot) => {
  const valeur = parseJours(lot.joursRestants) * Number(lot.tauxHistorique);
  return Number.isFinite(valeur) ? Number(valeur.toFixed(2)) : 0;
};

const getInitials = (nom, prenom) => {
  const n = (nom || '').trim();
  const p = (prenom || '').trim();
  if (n && p) return `${n[0]}${p[0]}`.toUpperCase();
  if (n) return n.slice(0, 2).toUpperCase();
  return 'AC';
};

export default function InitialisationSoldeModal({
  isOpen,
  onClose,
  onSuccess,
  employe
}) {
  const toast = useToast();

  // Données communes
  const [dateCutover, setDateCutover] = useState('');
  const [customDateDebut, setCustomDateDebut] = useState('');
  const [soldeJours, setSoldeJours] = useState('');
  const [valeurSoldeOuverture, setValeurSoldeOuverture] = useState('');
  const [tauxApplicable, setTauxApplicable] = useState('');
  const [referenceArrete, setReferenceArrete] = useState('');
  const [commentaire, setCommentaire] = useState('');
  const [repriseMode, setRepriseMode] = useState('DETAILLE');
  const [lotsReprise, setLotsReprise] = useState([emptyLot()]);

  // Données pour AGGREGATED_OPENING
  const [cumulDroitsOuverture, setCumulDroitsOuverture] = useState('');
  const [cumulValorisationOuverture, setCumulValorisationOuverture] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [employeDetail, setEmployeDetail] = useState(null);
  const [showHelpModal, setShowHelpModal] = useState(false);

  const updateDateCutover = (date) => {
    setDateCutover(date);
    setReferenceArrete(previous => !previous.trim() || generatedReference.test(previous.trim())
      ? referenceFromDate(date) : previous);
  };

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
  const dateDebutReference = customDateDebut || (dateCutover ? `${dateCutover.slice(0, 4)}-01-01` : '');
  const dateFinCalculee = dateCutover ? `${dateCutover.slice(0, 4)}-12-31` : '';
  const joursLots = Number(lotsReprise.reduce((sum, lot) => sum + (parseJours(lot.joursRestants) || 0), 0).toFixed(2));
  const valeurLots = Number(lotsReprise.reduce((sum, lot) => sum + valeurLot(lot), 0).toFixed(2));

  // Initialisation à chaque ouverture
  useEffect(() => {
    if (!isOpen) return;

    setDateCutover('');
    setCustomDateDebut('');
    setSoldeJours('');
    setTauxApplicable('');
    setReferenceArrete('');

    setValeurSoldeOuverture('');
    setCumulDroitsOuverture('');
    setCumulValorisationOuverture('');
    setCommentaire('');
    setRepriseMode('DETAILLE');
    setLotsReprise([emptyLot()]);
    setError(null);
    setShowHelpModal(false);
  }, [isOpen]);

  // Validation et soumission
  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    const detaille = repriseMode === 'DETAILLE';
    if (detaille && lotsReprise.some((lot) => !/^\d{4}$/.test(String(lot.exercice))
      || Number(lot.exercice) < 1900 || Number(lot.exercice) > Number(dateCutover.slice(0, 4))
      || !Number.isFinite(parseJours(lot.joursRestants)) || parseJours(lot.joursRestants) <= 0
      || !Number.isFinite(Number(lot.tauxHistorique)) || Number(lot.tauxHistorique) <= 0
      || valeurLot(lot) <= 0
      || !/^\d+(?:[.,]\d{1,2})?$/.test(String(lot.joursRestants)))) {
      setError('Chaque lot exige un exercice au plus tard celui de l’arrêté, des jours restants et un taux historique positifs.');
      return;
    }
    const sVal = detaille ? joursLots : parseJours(soldeJours);
    if (isNaN(sVal) || sVal < 0) {
      setError('Veuillez renseigner un solde de jours valide (≥ 0).');
      return;
    }
    const vVal = detaille ? valeurLots : parseFloat(valeurSoldeOuverture);
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
        commentaire: (commentaire || '').trim()
      };
      if (detaille) payload.lotsReprise = [...lotsReprise]
        .sort((a, b) => Number(a.exercice) - Number(b.exercice))
        .map((lot) => ({ dateOrigine: `${lot.exercice}-01-01`, joursRestants: parseJours(lot.joursRestants), valeurRestante: valeurLot(lot) }));

      await congeService.cutoverOpening(payload);

      toast({
        title: 'Reprise historique enregistrée',
        message: `Stock repris : ${sVal} j / ${formatMoneyAr(vVal)} ; TCONGE applicable : ${formatLeaveRateAr(tVal)}/j.`,
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

  const nomAffichage = [employe?.nom || employeDetail?.nom, employe?.prenom || employeDetail?.prenom].filter(Boolean).join(' ') || 'Collaborateur';
  const matriculeAffichage = employe?.matricule || employe?.matriculeEmploye || employeDetail?.matricule || '—';

  return (
    <>
      <Modal
        isOpen={isOpen}
        onClose={onClose}
        title="Reprise historique des congés"
        size="3xl"
        footer={
          <div className="flex items-center justify-between w-full">
            <div className="text-xs text-[var(--color-fg-muted)]">
              Champs obligatoires marqués *
            </div>
            <div className="flex items-center gap-2.5">
              <Button
                variant="outline"
                size="sm"
                onClick={onClose}
                disabled={loading}
                type="button"
                className="font-bold text-xs uppercase px-4 py-2"
              >
                ANNULER
              </Button>
              <Button
                variant="primary"
                size="sm"
                type="button"
                onClick={handleSubmit}
                loading={loading}
                className="font-bold text-xs uppercase px-5 py-2"
              >
                ENREGISTRER LA REPRISE
              </Button>
            </div>
          </div>
        }
      >
        <form onSubmit={handleSubmit} className="space-y-4 font-sans text-sm">
          {error && <FormError message={error} />}

          {/* Fiche d'identification du collaborateur & Contexte */}
          <div className="flex items-center justify-between p-3.5 border border-[var(--color-border)] bg-[var(--color-surface)]">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-[var(--color-brand-600)] text-white font-bold text-sm flex items-center justify-center shrink-0">
                {getInitials(employe?.nom || employeDetail?.nom, employe?.prenom || employeDetail?.prenom)}
              </div>
              <div>
                <div className="font-bold text-sm text-[var(--color-fg)] uppercase tracking-wide">
                  {nomAffichage} · {matriculeAffichage}
                </div>
                <div className="text-xs text-[var(--color-fg-muted)]">
                  Droits acquis avant la mise en service du SIRH, d&apos;après une situation arrêtée par la DAF
                </div>
              </div>
            </div>
            <div>
              <button
                type="button"
                onClick={() => setShowHelpModal(true)}
                className="text-xs font-semibold text-[var(--color-brand-600)] hover:underline cursor-pointer"
              >
                Quand utiliser cette reprise ?
              </button>
            </div>
          </div>

          {/* Section 1 : Situation à la date de bascule */}
          <div className="border border-[var(--color-border)] bg-[var(--color-surface)] p-4 space-y-4">
            <div className="flex items-center gap-2.5 font-bold text-sm text-[var(--color-fg)]">
              <div className="w-5 h-5 bg-[#0f172a] text-white text-xs font-bold flex items-center justify-center">
                1
              </div>
              <span>Situation à la date de bascule</span>
            </div>

            {/* Mode de reprise : 2 cartes horizontales bien réparties */}
            <div className="space-y-1.5">
              <label className="text-xs font-bold text-[var(--color-fg)] block">
                Mode de reprise
              </label>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div
                  onClick={() => setRepriseMode('DETAILLE')}
                  className={`p-3 border cursor-pointer transition-all ${
                    repriseMode === 'DETAILLE'
                      ? 'border-2 border-[var(--color-brand-600)] bg-[var(--color-brand-50)]'
                      : 'border border-[var(--color-border)] bg-[var(--color-surface)] hover:border-[var(--color-border-strong)]'
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <div className={`w-3.5 h-3.5 rounded-full border flex items-center justify-center shrink-0 ${
                      repriseMode === 'DETAILLE' ? 'border-[var(--color-brand-600)]' : 'border-[var(--color-border-strong)]'
                    }`}>
                      {repriseMode === 'DETAILLE' && <div className="w-1.5 h-1.5 rounded-full bg-[var(--color-brand-600)]" />}
                    </div>
                    <span className="font-bold text-xs text-[var(--color-fg)]">Plusieurs lots</span>
                  </div>
                  <div className="text-[11px] text-[var(--color-fg-muted)] ml-5.5 mt-0.5 leading-snug">
                    Un lot par exercice ou par taux. Le plus ancien est utilisé d&apos;abord.
                  </div>
                </div>

                <div
                  onClick={() => setRepriseMode('GLOBAL')}
                  className={`p-3 border cursor-pointer transition-all ${
                    repriseMode === 'GLOBAL'
                      ? 'border-2 border-[var(--color-brand-600)] bg-[var(--color-brand-50)]'
                      : 'border border-[var(--color-border)] bg-[var(--color-surface)] hover:border-[var(--color-border-strong)]'
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <div className={`w-3.5 h-3.5 rounded-full border flex items-center justify-center shrink-0 ${
                      repriseMode === 'GLOBAL' ? 'border-[var(--color-brand-600)]' : 'border-[var(--color-border-strong)]'
                    }`}>
                      {repriseMode === 'GLOBAL' && <div className="w-1.5 h-1.5 rounded-full bg-[var(--color-brand-600)]" />}
                    </div>
                    <span className="font-bold text-xs text-[var(--color-fg)]">Solde global</span>
                  </div>
                  <div className="text-[11px] text-[var(--color-fg-muted)] ml-5.5 mt-0.5 leading-snug">
                    Un seul solde certifié et la valeur du stock restant.
                  </div>
                </div>
              </div>
            </div>

            {/* Si Mode DETAILLE : Date de clôture + TCONGE puis Tableau des lots */}
            {repriseMode === 'DETAILLE' && (
              <div className="space-y-4 pt-1">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 items-start">
                  <div>
                    <label className="text-xs font-bold text-[var(--color-fg)] mb-1.5 block">
                      Dernière clôture de paie reprise <span className="text-[var(--color-danger-500)]">*</span>
                    </label>
                    <Input
                      type="date"
                      value={dateCutover}
                      onChange={(e) => updateDateCutover(e.target.value)}
                      placeholder="jj/mm/aaaa"
                      required
                    />
                  </div>

                  <div>
                    <label className="text-xs font-bold text-[var(--color-fg)] mb-1.5 block">
                      Dernier TCONGE certifié (Ar / jour) <span className="text-[var(--color-danger-500)]">*</span>
                    </label>
                    <MoneyInput
                      maxDecimals={6}
                      min="0.000001"
                      value={tauxApplicable}
                      onValueChange={setTauxApplicable}
                      placeholder="Ex. 10 000"
                      required
                    />
                    <p className="text-[11px] text-[var(--color-fg-muted)] mt-1 leading-snug">
                      Le TCONGE s&apos;applique aux acquisitions suivantes. Chaque lot garde son taux historique.
                    </p>
                  </div>
                </div>

                <div className="pt-2 border-t border-[var(--color-border-subtle)] space-y-2.5">
                  <div className="flex items-center justify-between">
                    <div>
                      <h4 className="text-xs font-bold text-[var(--color-fg)]">Lots de reprise</h4>
                      <p className="text-[11px] text-[var(--color-fg-muted)]">Un lot par exercice ou par taux</p>
                    </div>
                    <button
                      type="button"
                      onClick={() => setLotsReprise((prev) => [...prev, emptyLot()])}
                      className="border border-[var(--color-border)] bg-[var(--color-surface)] hover:bg-[var(--color-bg-subtle)] text-[var(--color-fg)] font-bold text-xs uppercase px-3 py-1.5 transition-colors cursor-pointer"
                    >
                      + AJOUTER UN LOT
                    </button>
                  </div>

                  <div className="border border-[var(--color-border)] bg-[var(--color-surface)] overflow-x-auto">
                    <table className="w-full text-left text-xs">
                      <thead className="border-b border-[var(--color-border)] bg-[var(--color-bg-subtle)] text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                        <tr>
                          <th className="p-2.5 w-16">LOT</th>
                          <th className="p-2.5">EXERCICE *</th>
                          <th className="p-2.5">JOURS RESTANTS *</th>
                          <th className="p-2.5">TAUX HISTORIQUE (AR/J) *</th>
                          <th className="p-2.5">VALEUR RESTANTE</th>
                          <th className="p-2.5 text-right w-20"></th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-[var(--color-border-subtle)]">
                        {lotsReprise.map((lot, index) => (
                          <tr key={lot.id}>
                            <td className="p-2.5 font-bold text-[var(--color-fg)]">
                              Lot {index + 1}
                            </td>
                            <td className="p-2.5">
                              <Input
                                type="number"
                                min="1900"
                                max={dateCutover ? dateCutover.slice(0, 4) : undefined}
                                step="1"
                                placeholder="Ex. 2025"
                                value={lot.exercice}
                                required
                                onChange={(e) => setLotsReprise((prev) => prev.map((item, i) => i === index ? { ...item, exercice: e.target.value } : item))}
                              />
                            </td>
                            <td className="p-2.5">
                              <Input
                                type="number"
                                min="0.01"
                                step="0.01"
                                placeholder="Ex. 2,5"
                                value={lot.joursRestants}
                                required
                                onChange={(e) => setLotsReprise((prev) => prev.map((item, i) => i === index ? { ...item, joursRestants: e.target.value } : item))}
                              />
                            </td>
                            <td className="p-2.5">
                              <MoneyInput
                                maxDecimals={6}
                                min="0.000001"
                                placeholder="Ex. 10 000"
                                value={lot.tauxHistorique}
                                required
                                onValueChange={(value) => setLotsReprise((prev) => prev.map((item, i) => i === index ? { ...item, tauxHistorique: value } : item))}
                              />
                            </td>
                            <td className="p-2.5 font-data text-xs">
                              {lot.joursRestants && lot.tauxHistorique ? formatMoneyAr(valeurLot(lot)) : '—'}
                            </td>
                            <td className="p-2.5 text-right">
                              {lotsReprise.length > 1 && (
                                <button
                                  type="button"
                                  onClick={() => setLotsReprise((prev) => prev.filter((_, i) => i !== index))}
                                  className="text-[11px] font-bold uppercase text-[var(--color-fg-muted)] hover:text-[var(--color-danger-600)] cursor-pointer"
                                >
                                  RETIRER
                                </button>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                    <div className="p-2.5 bg-[var(--color-bg-subtle)] border-t border-[var(--color-border)] flex items-center justify-between text-xs">
                      <span className="font-bold text-[var(--color-fg)]">
                        Stock calculé : {joursLots.toFixed(2).replace('.', ',')} j · {formatMoneyAr(valeurLots)}
                      </span>
                      <span className="text-[11px] text-[var(--color-fg-muted)]">
                        À vérifier avec l&apos;arrêté DAF
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* Si Mode GLOBAL : 4 colonnes harmonieuses, alignées avec la Section 2 */}
            {repriseMode === 'GLOBAL' && (
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4 items-start pt-1">
                <div>
                  <label className="text-xs font-bold text-[var(--color-fg)] mb-1.5 block">
                    Dernière clôture reprise <span className="text-[var(--color-danger-500)]">*</span>
                  </label>
                  <Input
                    type="date"
                    value={dateCutover}
                    onChange={(e) => updateDateCutover(e.target.value)}
                    placeholder="jj/mm/aaaa"
                    required
                  />
                </div>

                <div>
                  <label className="text-xs font-bold text-[var(--color-fg)] mb-1.5 block">
                    Dernier TCONGE (Ar / j) <span className="text-[var(--color-danger-500)]">*</span>
                  </label>
                  <MoneyInput
                    maxDecimals={6}
                    min="0.000001"
                    value={tauxApplicable}
                    onValueChange={setTauxApplicable}
                    placeholder="Ex. 10 000"
                    required
                  />
                  <p className="text-[11px] text-[var(--color-fg-muted)] mt-1 leading-snug">
                    Pour les acquisitions futures.
                  </p>
                </div>

                <div>
                  <label className="text-xs font-bold text-[var(--color-fg)] mb-1.5 block">
                    Solde disponible (jours) <span className="text-[var(--color-danger-500)]">*</span>
                  </label>
                  <Input
                    type="number"
                    step="0.01"
                    min="0"
                    value={soldeJours}
                    onChange={(e) => setSoldeJours(e.target.value)}
                    placeholder="Ex. 2,44"
                    required
                  />
                </div>

                <div>
                  <label className="text-xs font-bold text-[var(--color-fg)] mb-1.5 block">
                    Valeur du stock restant (Ar) <span className="text-[var(--color-danger-500)]">*</span>
                  </label>
                  <MoneyInput
                    maxDecimals={2}
                    min="0"
                    value={valeurSoldeOuverture}
                    onValueChange={setValeurSoldeOuverture}
                    placeholder="Ex. 25 000"
                    required
                  />
                  <p className="text-[11px] text-[var(--color-fg-muted)] mt-1 leading-snug">
                    Montant certifié par la DAF.
                  </p>
                </div>
              </div>
            )}
          </div>

          {/* Section 2 : Période annuelle de référence */}
          <div className="border border-[var(--color-border)] bg-[var(--color-surface)] p-4 space-y-4">
            <div className="flex items-center gap-2.5 font-bold text-sm text-[var(--color-fg)]">
              <div className="w-5 h-5 bg-[#0f172a] text-white text-xs font-bold flex items-center justify-center">
                2
              </div>
              <span>Période annuelle de référence</span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4 items-start">
              <div>
                <label className="text-xs font-bold text-[var(--color-fg)] mb-1.5 block">
                  Date de début <span className="text-[var(--color-danger-500)]">*</span>
                </label>
                <Input
                  type="date"
                  value={dateDebutReference}
                  onChange={(e) => setCustomDateDebut(e.target.value)}
                  placeholder="jj/mm/aaaa"
                  required
                />
              </div>

              <div>
                <label className="text-xs font-bold text-[var(--color-fg)] mb-1.5 block">
                  Date de fin (calculée)
                </label>
                <div className="p-2 border border-[var(--color-border)] bg-[var(--color-bg-subtle)] text-xs font-data flex items-center justify-between h-[34px]">
                  <span>{dateFinCalculee || '—'}</span>
                  <span className="text-[10px] font-semibold text-[var(--color-fg-muted)] border border-[var(--color-border)] bg-[var(--color-surface)] px-1.5 py-0.5">
                    12 mois
                  </span>
                </div>
              </div>

              <div>
                <label className="text-xs font-bold text-[var(--color-fg)] mb-1.5 block">
                  Jours acquis cumulés <span className="text-[var(--color-danger-500)]">*</span>
                </label>
                <Input
                  type="number"
                  step="0.0001"
                  min="0"
                  value={cumulDroitsOuverture}
                  onChange={(e) => setCumulDroitsOuverture(e.target.value)}
                  placeholder="Ex. 22,5"
                  required
                />
                <p className="text-[11px] text-[var(--color-fg-muted)] mt-1 leading-snug">
                  Depuis le 1er janvier, jours déjà pris inclus. Ce n&apos;est pas le solde restant.
                </p>
              </div>

              <div>
                <label className="text-xs font-bold text-[var(--color-fg)] mb-1.5 block">
                  Valeur des jours acquis (Ar) <span className="text-[var(--color-danger-500)]">*</span>
                </label>
                <MoneyInput
                  maxDecimals={2}
                  min="0"
                  value={cumulValorisationOuverture}
                  onValueChange={setCumulValorisationOuverture}
                  placeholder="Ex. 100 000"
                  required
                />
                <p className="text-[11px] text-[var(--color-fg-muted)] mt-1 leading-snug">
                  Tous les jours acquis cette année, y compris ceux déjà pris.
                </p>
              </div>
            </div>
          </div>

          {/* Section 3 & Résultat de la reprise (2 colonnes) */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 items-stretch">
            {/* Section 3 : Justificatif */}
            <div className="border border-[var(--color-border)] bg-[var(--color-surface)] p-4 space-y-3 flex flex-col justify-between">
              <div className="flex items-center gap-2.5 font-bold text-sm text-[var(--color-fg)]">
                <div className="w-5 h-5 bg-[#0f172a] text-white text-xs font-bold flex items-center justify-center">
                  3
                </div>
                <span>Justificatif</span>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-bold text-[var(--color-fg)] block">
                  Référence de l&apos;arrêté DAF utilisé <span className="text-[var(--color-danger-500)]">*</span>
                </label>
                <Input
                  type="text"
                  value={referenceArrete}
                  onChange={(e) => setReferenceArrete(e.target.value)}
                  placeholder="Choisissez d’abord la date d’arrêté"
                  required
                />
                <p className="text-[11px] text-[var(--color-fg-muted)] leading-snug">
                  Prérempli avec la date d’arrêté. Vérifiez et remplacez ce texte si le document DAF porte une référence précise. Aucun fichier n&apos;est importé ici.
                </p>
              </div>
            </div>

            {/* Box Résultat de la reprise */}
            <div className="border border-[var(--color-brand-200)] bg-[var(--color-brand-50)] p-4 space-y-4 flex flex-col justify-between">
              <h4 className="text-xs font-bold text-[var(--color-brand-800)]">
                Résultat de la reprise
              </h4>

              <div className="grid grid-cols-2 gap-y-4 gap-x-6">
                <div>
                  <div className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-700)]">MODE</div>
                  <div className="text-xs font-bold text-[var(--color-fg)] mt-0.5">
                    {repriseMode === 'DETAILLE' ? `${lotsReprise.length} lot${lotsReprise.length > 1 ? 's' : ''}` : 'Solde global'}
                  </div>
                </div>
                <div>
                  <div className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-700)]">PÉRIODE</div>
                  <div className="text-xs font-bold text-[var(--color-fg)] mt-0.5">12 mois</div>
                </div>
                <div>
                  <div className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-700)]">
                    {repriseMode === 'DETAILLE' ? 'STOCK' : 'SOLDE'}
                  </div>
                  <div className="text-base font-bold font-data text-[var(--color-fg)] mt-0.5">
                    {repriseMode === 'DETAILLE'
                      ? `${joursLots.toFixed(2).replace('.', ',')} j`
                      : (soldeJours ? `${String(soldeJours).replace('.', ',')} j` : '— j')}
                  </div>
                </div>
                <div>
                  <div className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-700)]">VALEUR</div>
                  <div className="text-base font-bold font-data text-[var(--color-fg)] mt-0.5">
                    {repriseMode === 'DETAILLE'
                      ? formatMoneyAr(valeurLots)
                      : (valeurSoldeOuverture ? formatMoneyAr(parseFloat(valeurSoldeOuverture)) : '— Ar')}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </form>
      </Modal>

      {/* Modal d'aide contextuelle */}
      <Modal
        isOpen={showHelpModal}
        onClose={() => setShowHelpModal(false)}
        title="Quand utiliser cette reprise historique ?"
        size="lg"
        footer={
          <div className="flex justify-end w-full">
            <Button variant="primary" size="sm" onClick={() => setShowHelpModal(false)}>
              Fermer
            </Button>
          </div>
        }
      >
        <div className="space-y-3 text-xs leading-relaxed text-[var(--color-fg)]">
          <p className="font-semibold text-[var(--color-brand-700)]">
            Pour les droits acquis avant la mise en service du SIRH, à partir d’une situation arrêtée par la DAF.
          </p>
          <p className="text-[var(--color-fg-muted)]">
            Pour une nouvelle embauche sans droits antérieurs, ne saisissez rien ici : la première clôture de paie crée automatiquement la période civile et l&apos;acquisition. La date d&apos;arrêté inclut les acquisitions déjà comptées dans les cumuls saisis ; les paies closes à cette date ou avant ne les recréent pas.
          </p>
          <ol className="ml-4 list-decimal space-y-1.5 text-[var(--color-fg-muted)]">
            <li>Choisissez la date de la dernière clôture de paie dont les acquisitions figurent dans l&apos;arrêté DAF (par exemple le 25/09/2026). La clôture suivante doit venir après cette date.</li>
            <li>En mode détaillé, ajoutez un lot par exercice ou par taux historique. Vérifiez le total calculé avec l&apos;arrêté DAF.</li>
            <li>Le TCONGE est le dernier taux journalier applicable certifié par la DAF, pas le salaire actuel.</li>
            <li>Les cumuls annuels sont les droits acquis et leur valorisation depuis le 1er janvier jusqu&apos;à cette clôture, même si certains droits ont déjà été consommés. Exemple : 22,5 jours acquis et 3 150 000 Ar ; ces chiffres diffèrent du stock restant.</li>
            <li>La période civile est calculée automatiquement du 1er janvier au 31 décembre de l&apos;année d&apos;arrêté. Indiquez la référence identifiable de l&apos;arrêté DAF qui justifie ces chiffres.</li>
          </ol>
        </div>
      </Modal>
    </>
  );
}
