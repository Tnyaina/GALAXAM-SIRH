import { useState, useEffect } from 'react';
import { Calendar, Info, ShieldAlert, ChevronDown, AlertTriangle } from 'lucide-react';
import congeService from '@/api/congeService';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea } from '@/components/ui/forms/Input';
import EmployeSearchSelect from '@/components/ui/forms/EmployeSearchSelect';
import Alert from '@/components/ui/feedback/Alert';
import { useToast } from '@/components/ui/feedback/Toast';

export default function DemandeCongeModal({ idEmploye: idEmployeProp, referentiels, isOpen, onClose, onSuccess }) {
  const [selectedEmployeId, setSelectedEmployeId] = useState('');
  const [idTypeConge, setIdTypeConge] = useState('');
  const [idMotifEvenementFamilial, setIdMotifEvenementFamilial] = useState('');
  const [dateDebut, setDateDebut] = useState('');
  const [dateFin, setDateFin] = useState('');
  const [commentaire, setCommentaire] = useState('');
  const [justificatifFourni, setJustificatifFourni] = useState(false);

  // Dérogations RH
  const [derogationSoldeNegatif, setDerogationSoldeNegatif] = useState(false);
  const [derogationDelaiPreavis, setDerogationDelaiPreavis] = useState(false);
  const [extensionPathologiqueMaternite, setExtensionPathologiqueMaternite] = useState(false);
  const [showOptionsAvancees, setShowOptionsAvancees] = useState(false);

  // Simulation & State
  const [nbJoursDecomptes, setNbJoursDecomptes] = useState(0);
  const [nbJoursWeekEnd, setNbJoursWeekEnd] = useState(0);
  const [nbJoursOuvres, setNbJoursOuvres] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const toast = useToast();

  const typesConge = referentiels?.typesConge || [];
  const motifsFamiliaux = referentiels?.motifsFamiliaux || [];

  const selectedType = typesConge.find((t) => String(t.idTypeConge) === String(idTypeConge));
  const isExceptionnel = selectedType && selectedType.code === 'EXCEPTIONNEL';
  const isMaternite = selectedType && selectedType.code === 'MATERNITE';

  useEffect(() => {
    if (isOpen) {
      setSelectedEmployeId(idEmployeProp ? String(idEmployeProp) : '');
      setError(null);
      setFieldErrors({});
    }
  }, [isOpen, idEmployeProp]);

  // Pré-remplissage des durées fixes légales (ex: Paternité 3j, Maternité 98j)
  useEffect(() => {
    if (selectedType && selectedType.dureeFixeJours && dateDebut) {
      const dDebut = new Date(dateDebut);
      let nbJoursFixes = Number(selectedType.dureeFixeJours);
      if (isMaternite && extensionPathologiqueMaternite) {
        nbJoursFixes = 119;
      }
      const dFin = new Date(dDebut);
      dFin.setDate(dFin.getDate() + nbJoursFixes - 1);
      setDateFin(dFin.toISOString().split('T')[0]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idTypeConge, dateDebut, extensionPathologiqueMaternite]);

  // Simulation automatique des jours calendaires stricts + validation inline des dates
  useEffect(() => {
    if (dateDebut && dateFin) {
      const dDebut = new Date(dateDebut);
      const dFin = new Date(dateFin);
      if (dFin >= dDebut) {
        const diffTime = dFin.getTime() - dDebut.getTime();
        const diffDays = Math.ceil(diffTime / (1000 * 3600 * 24)) + 1;

        let countWeekends = 0;
        let currentDate = new Date(dDebut);
        while (currentDate <= dFin) {
          const day = currentDate.getDay(); // 0 is Sunday, 6 is Saturday
          if (day === 0 || day === 6) {
            countWeekends++;
          }
          currentDate.setDate(currentDate.getDate() + 1);
        }

        setNbJoursDecomptes(diffDays);
        setNbJoursWeekEnd(countWeekends);
        setNbJoursOuvres(diffDays - countWeekends);
        setFieldErrors((prev) => ({ ...prev, dateFin: null }));
      } else {
        setNbJoursDecomptes(0);
        setNbJoursWeekEnd(0);
        setNbJoursOuvres(0);
        setFieldErrors((prev) => ({ ...prev, dateFin: 'La date de fin doit être postérieure ou égale à la date de début.' }));
      }
    } else {
      setNbJoursDecomptes(0);
      setNbJoursWeekEnd(0);
      setNbJoursOuvres(0);
    }
  }, [dateDebut, dateFin]);

  if (!isOpen) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();

    const targetEmployeId = idEmployeProp || selectedEmployeId;
    const nextFieldErrors = {};
    if (!targetEmployeId) nextFieldErrors.idEmploye = 'Sélectionnez un employé.';
    if (!idTypeConge) nextFieldErrors.idTypeConge = 'Sélectionnez un type de congé.';
    if (!dateDebut) nextFieldErrors.dateDebut = 'Renseignez la date de début.';
    if (!dateFin) nextFieldErrors.dateFin = 'Renseignez la date de fin.';
    if (isExceptionnel && !idMotifEvenementFamilial) {
      nextFieldErrors.idMotifEvenementFamilial = "Sélectionnez un motif d'événement familial.";
    }

    if (Object.keys(nextFieldErrors).length > 0) {
      setFieldErrors((prev) => ({ ...prev, ...nextFieldErrors }));
      setError('Veuillez corriger les champs indiqués ci-dessous avant de continuer.');
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const payload = {
        idEmploye: parseInt(targetEmployeId),
        idTypeConge: parseInt(idTypeConge),
        idMotifEvenementFamilial: idMotifEvenementFamilial ? parseInt(idMotifEvenementFamilial) : null,
        dateDebut,
        dateFin,
        commentaire,
        justificatifFourni,
        derogationSoldeNegatif,
        derogationDelaiPreavis,
        extensionPathologiqueMaternite,
      };

      await congeService.creerDemande(payload);
      toast({
        title: 'Demande soumise',
        message: 'Votre demande de congé a été enregistrée avec succès.',
        type: 'success',
        duration: 3000
      });
      if (onSuccess) onSuccess();
      onClose();
    } catch (err) {
      // On ne vide jamais le formulaire en cas d'erreur : les données saisies sont conservées.
      const message = err?.response?.data?.message || err?.response?.data || err.message
        || "Impossible d'enregistrer la demande. Veuillez réessayer.";
      setError(typeof message === 'string' ? message : "Impossible d'enregistrer la demande. Veuillez réessayer.");
    } finally {
      setLoading(false);
    }
  };

  const inputErrorClass = (hasError) =>
    hasError ? 'border-danger-500 focus:border-danger-500' : '';

  return (
    <div className="fixed inset-0 bg-[var(--color-brand-900)]/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none max-w-2xl w-full shadow-2xl max-h-[90vh] flex flex-col font-sans">

        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-[var(--color-border-subtle)] shrink-0">
          <h3 className="text-sm font-bold text-[var(--color-fg)] flex items-center gap-2">
            <Calendar className="w-5 h-5 text-[var(--color-brand-500)]" />
            Nouvelle demande de congé / absence
          </h3>
          <button
            onClick={onClose}
            aria-label="Fermer"
            className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] text-xl leading-none w-8 h-8 flex items-center justify-center"
          >
            ×
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col overflow-hidden flex-1">
          <div className="px-6 py-5 space-y-6 overflow-y-auto text-sm">

            {/* Message d'erreur global (résumé) */}
            {error && (
              <Alert type="error" message={typeof error === 'string' ? error : JSON.stringify(error)} />
            )}

            {/* ── Section 0 : Employé concerné (si idEmploye n'est pas verrouillé) ────────────────────── */}
            {!idEmployeProp && (
              <EmployeSearchSelect
                label="Employé concerné"
                required
                value={selectedEmployeId}
                onChange={(empId) => {
                  setSelectedEmployeId(empId);
                  setFieldErrors((prev) => ({ ...prev, idEmploye: null }));
                }}
                error={fieldErrors.idEmploye}
              />
            )}

            {/* ── Section 1 : Type de congé ────────────────────────────── */}
            <div className="space-y-1.5">
              <label htmlFor="idTypeConge" className="block text-sm font-semibold text-[var(--color-fg)]">
                Type de congé / absence <span className="text-danger-500">*</span>
              </label>
              <select
                id="idTypeConge"
                value={idTypeConge}
                onChange={(e) => {
                  setIdTypeConge(e.target.value);
                  setIdMotifEvenementFamilial('');
                  setFieldErrors((prev) => ({ ...prev, idTypeConge: null }));
                }}
                aria-invalid={!!fieldErrors.idTypeConge}
                className={`w-full px-3 py-2.5 bg-[var(--color-bg-subtle)] border rounded-none text-[var(--color-fg)] focus:outline-none cursor-pointer text-sm ${inputErrorClass(fieldErrors.idTypeConge) || 'border-[var(--color-border-subtle)] focus:border-[var(--color-brand-500)]'}`}
              >
                <option value="">Sélectionner un type de congé…</option>
                {typesConge.map((t) => (
                  <option key={t.idTypeConge} value={t.idTypeConge}>
                    {t.libelle} — {t.estPaye ? 'Rémunéré' : 'Sans solde'}{t.soumisDelaiPreavis ? ' · Préavis requis' : ''}
                  </option>
                ))}
              </select>
              {fieldErrors.idTypeConge && (
                <p className="text-xs text-danger-500 flex items-center gap-1">
                  <AlertTriangle className="w-3.5 h-3.5 shrink-0" /> {fieldErrors.idTypeConge}
                </p>
              )}
            </div>

            {/* Motif Événement Familial (Si EXCEPTIONNEL) */}
            {isExceptionnel && (
              <div className="pl-3 border-l-2 border-warning-500 space-y-1.5">
                <label htmlFor="idMotifEvenementFamilial" className="block text-sm font-semibold text-[var(--color-fg)]">
                  Motif d'événement familial <span className="text-danger-500">*</span>
                </label>
                <select
                  id="idMotifEvenementFamilial"
                  value={idMotifEvenementFamilial}
                  onChange={(e) => {
                    setIdMotifEvenementFamilial(e.target.value);
                    setFieldErrors((prev) => ({ ...prev, idMotifEvenementFamilial: null }));
                  }}
                  aria-invalid={!!fieldErrors.idMotifEvenementFamilial}
                  className={`w-full px-3 py-2.5 bg-[var(--color-bg-subtle)] border rounded-none text-[var(--color-fg)] focus:outline-none cursor-pointer text-sm ${inputErrorClass(fieldErrors.idMotifEvenementFamilial) || 'border-[var(--color-border-subtle)] focus:border-warning-500'}`}
                >
                  <option value="">Sélectionner un motif…</option>
                  {motifsFamiliaux.map((m) => (
                    <option key={m.idMotif} value={m.idMotif}>
                      {m.libelle} ({m.nbJoursAutorises} jours autorisés)
                    </option>
                  ))}
                </select>
                {fieldErrors.idMotifEvenementFamilial && (
                  <p className="text-xs text-danger-500 flex items-center gap-1">
                    <AlertTriangle className="w-3.5 h-3.5 shrink-0" /> {fieldErrors.idMotifEvenementFamilial}
                  </p>
                )}
              </div>
            )}

            {/* ── Section 2 : Dates ────────────────────────────────────── */}
            <div className="space-y-3">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <label className="block text-sm font-semibold text-[var(--color-fg)]">
                    Date de début <span className="text-danger-500">*</span>
                  </label>
                  <Input
                    type="date"
                    value={dateDebut}
                    onChange={(e) => {
                      setDateDebut(e.target.value);
                      setFieldErrors((prev) => ({ ...prev, dateDebut: null }));
                    }}
                    required
                  />
                  {fieldErrors.dateDebut && (
                    <p className="text-xs text-danger-500 flex items-center gap-1">
                      <AlertTriangle className="w-3.5 h-3.5 shrink-0" /> {fieldErrors.dateDebut}
                    </p>
                  )}
                </div>
                <div className="space-y-1.5">
                  <label className="block text-sm font-semibold text-[var(--color-fg)]">
                    Date de fin <span className="text-danger-500">*</span>
                  </label>
                  <Input
                    type="date"
                    value={dateFin}
                    onChange={(e) => setDateFin(e.target.value)}
                    required
                  />
                  {fieldErrors.dateFin && (
                    <p className="text-xs text-danger-500 flex items-center gap-1">
                      <AlertTriangle className="w-3.5 h-3.5 shrink-0" /> {fieldErrors.dateFin}
                    </p>
                  )}
                </div>
              </div>

              {/* Compteur de jours — mis en avant, c'est l'info clé du formulaire */}
              {nbJoursDecomptes > 0 && (
                <div className="border border-info-500/30 bg-info-500/5">
                  <div className="flex items-center justify-between px-4 py-3">
                    <span className="text-xs font-semibold text-[var(--color-fg-muted)] uppercase tracking-wide">
                      Total décompté du solde
                    </span>
                    <span className="text-2xl font-extrabold text-info-600 dark:text-info-400 font-data">
                      {nbJoursDecomptes} <span className="text-sm font-semibold">jour{nbJoursDecomptes > 1 ? 's' : ''}</span>
                    </span>
                  </div>
                  <div className="grid grid-cols-2 divide-x divide-info-500/20 border-t border-info-500/20 bg-[var(--color-surface)]">
                    <div className="px-4 py-2.5 text-center">
                      <div className="text-[11px] text-[var(--color-fg-subtle)] uppercase tracking-wide">Jours en semaine</div>
                      <div className="text-base font-bold text-[var(--color-fg)] font-data">{nbJoursOuvres}</div>
                    </div>
                    <div className="px-4 py-2.5 text-center">
                      <div className="text-[11px] text-[var(--color-fg-subtle)] uppercase tracking-wide">Week-ends inclus</div>
                      <div className="text-base font-bold text-warning-600 dark:text-warning-500 font-data">{nbJoursWeekEnd}</div>
                    </div>
                  </div>
                  <p className="px-4 py-2 text-xs text-[var(--color-fg-subtle)] flex items-start gap-1.5 border-t border-info-500/20">
                    <Info className="w-3.5 h-3.5 shrink-0 mt-0.5 text-info-500" />
                    Tous les jours de l'intervalle, y compris samedis, dimanches et jours fériés chômés, sont intégralement déduits du solde.
                  </p>
                </div>
              )}
            </div>

            {/* Banner d'information : bascule maladie > 30j */}
            {selectedType && selectedType.code === 'MALADIE' && nbJoursDecomptes > 30 && (
              <div className="pl-3 border-l-2 border-warning-500 py-1 space-y-1">
                <div className="flex items-center gap-1.5 text-warning-600 dark:text-warning-500 font-semibold text-sm">
                  <ShieldAlert className="w-4 h-4 shrink-0" />
                  Bascule automatique (arrêt maladie &gt; 30 jours)
                </div>
                <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                  Cette demande de <strong>{nbJoursDecomptes} jours</strong> sera automatiquement scindée à l'enregistrement :
                  <strong className="block mt-1">30 jours</strong> en maladie (maintien de salaire 100 %)
                  <strong className="block">{nbJoursDecomptes - 30} jours</strong> en maladie sans solde (prise en charge CNaPS)
                </p>
              </div>
            )}

            {/* Extension pathologique maternité */}
            {isMaternite && (
              <label className="flex items-start gap-3 pl-3 border-l-2 border-warning-500 py-1 cursor-pointer">
                <input
                  type="checkbox"
                  checked={extensionPathologiqueMaternite}
                  onChange={(e) => setExtensionPathologiqueMaternite(e.target.checked)}
                  className="w-4 h-4 mt-0.5 accent-warning-500 cursor-pointer shrink-0"
                />
                <span className="text-sm text-[var(--color-fg)]">
                  <span className="font-semibold text-warning-600 dark:text-warning-500">Extension pathologique</span>
                  <span className="block text-xs text-[var(--color-fg-muted)] mt-0.5">
                    Porte le congé maternité à 119 jours maximum (17 semaines).
                  </span>
                </span>
              </label>
            )}

            {/* ── Section 3 : Justificatif & commentaire ─────────────────── */}
            <div className="space-y-4 pt-1 border-t border-[var(--color-border-subtle)]">
              <label className="flex items-start gap-3 pt-4 cursor-pointer">
                <input
                  type="checkbox"
                  id="justificatifFourni"
                  checked={justificatifFourni}
                  onChange={(e) => setJustificatifFourni(e.target.checked)}
                  className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0"
                />
                <span className="text-sm text-[var(--color-fg)]">
                  Justificatif fourni
                  <span className="block text-xs text-[var(--color-fg-muted)] mt-0.5">
                    Certificat médical, acte d'état civil, convocation, etc.
                  </span>
                </span>
              </label>

              <div className="space-y-1.5">
                <label className="block text-sm font-semibold text-[var(--color-fg)]">
                  Commentaire <span className="text-[var(--color-fg-subtle)] font-normal">(optionnel)</span>
                </label>
                <Textarea
                  rows="2"
                  placeholder="Remarques éventuelles…"
                  value={commentaire}
                  onChange={(e) => setCommentaire(e.target.value)}
                />
              </div>
            </div>

            {/* ── Section 4 : Options avancées RH ─────────────────────────── */}
            <div className="border border-[var(--color-border-subtle)]">
              <button
                type="button"
                onClick={() => setShowOptionsAvancees((v) => !v)}
                aria-expanded={showOptionsAvancees}
                className="w-full flex items-center justify-between px-4 py-3 text-left"
              >
                <span className="text-xs font-bold uppercase tracking-wide text-[var(--color-fg-muted)] flex items-center gap-1.5">
                  <ShieldAlert className="w-4 h-4 text-warning-500" />
                  Options avancées — RH uniquement
                </span>
                <ChevronDown className={`w-4 h-4 text-[var(--color-fg-muted)] transition-transform ${showOptionsAvancees ? 'rotate-180' : ''}`} />
              </button>
              {showOptionsAvancees && (
                <div className="px-4 pb-4 pt-1 border-t border-[var(--color-border-subtle)] space-y-3">
                  <label className="flex items-start gap-3 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={derogationSoldeNegatif}
                      onChange={(e) => setDerogationSoldeNegatif(e.target.checked)}
                      className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0"
                    />
                    <span className="text-sm text-[var(--color-fg)]">
                      Autoriser un solde négatif
                      <span className="block text-xs text-[var(--color-fg-muted)] mt-0.5">
                        La demande sera acceptée même si le solde de congés devient négatif.
                      </span>
                    </span>
                  </label>
                  <label className="flex items-start gap-3 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={derogationDelaiPreavis}
                      onChange={(e) => setDerogationDelaiPreavis(e.target.checked)}
                      className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0"
                    />
                    <span className="text-sm text-[var(--color-fg)]">
                      Ignorer le délai de préavis
                      <span className="block text-xs text-[var(--color-fg-muted)] mt-0.5">
                        Permet de soumettre la demande même si le préavis réglementaire n'est pas respecté.
                      </span>
                    </span>
                  </label>
                </div>
              )}
            </div>
          </div>

          {/* Actions — fixées en bas, toujours visibles */}
          <div className="flex items-center justify-between gap-3 px-6 py-4 border-t border-[var(--color-border-subtle)] shrink-0 bg-[var(--color-surface)]">
            <span className="text-xs text-[var(--color-fg-subtle)]">
              <span className="text-danger-500">*</span> Champs obligatoires
            </span>
            <div className="flex items-center gap-3">
              <Button type="button" variant="ghost" size="sm" onClick={onClose} className="rounded-none">
                Annuler
              </Button>
              <Button
                type="submit"
                variant="primary"
                size="sm"
                loading={loading}
                className="rounded-none font-bold"
              >
                {loading ? 'Enregistrement…' : 'Soumettre la demande'}
              </Button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}