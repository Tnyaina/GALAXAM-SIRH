import { AlertTriangle, Info, ShieldAlert, CheckCircle2 } from 'lucide-react';
import Input from '@/components/ui/forms/Input';
import FieldError from '@/components/ui/forms/FieldError';
import { getDateFinFromRepriseInput, getRepriseInputValue } from '@/features/leave/utils/leaveDateRange';
import { shiftIsoDate } from '@/utils/dateUtils';

export default function DatesCongeSection({
  dateDebut, setDateDebut,
  dateFin, setDateFin,
  estDemiJournee, setEstDemiJournee,
  momentDemiJournee, setMomentDemiJournee,
  nbJoursDecomptes, nbJoursOuvres, nbJoursWeekEnd, nbJoursFeries,
  selectedType,
  isMaternite,
  extensionPathologiqueMaternite, setExtensionPathologiqueMaternite,
  preavisInfo,
  derogationDelaiPreavis,
  setDerogationDelaiPreavis,
  simulationReport,
  _derogationSoldeNegatif,
  _setDerogationSoldeNegatif,
  setShowOptionsAvancees,
  fieldErrors, setFieldErrors
}) {
  const soldeNum = Number(simulationReport?.soldeAvant || 0);
  const isSoldeInsuffisant = simulationReport?.violations?.includes('LEAVE_BALANCE_INSUFFICIENT')
    || simulationReport?.warnings?.includes('LEAVE_BALANCE_INSUFFICIENT');

  const repriseInput = getRepriseInputValue({ estDemiJournee, dateDebut, dateFin });

  const handleRepriseChange = (e) => {
    const val = e.target.value;
    setFieldErrors((prev) => ({ ...prev, dateFin: null }));
    setDateFin(getDateFinFromRepriseInput(val));
  };

  return (
    <>
      {/* ── Section 2 : Dates ────────────────────────────────────── */}
      <div className="space-y-3">
        {/* Checkbox Demi-journée */}
        <div className="flex items-center justify-between p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
          <label className="flex items-center gap-2.5 cursor-pointer">
            <input
              type="checkbox"
              checked={estDemiJournee}
              onChange={(e) => {
                const checked = e.target.checked;
                setEstDemiJournee(checked);
                if (checked && dateDebut) {
                  setDateFin(dateDebut);
                }
              }}
              className="w-4 h-4 accent-[var(--color-brand-500)] cursor-pointer shrink-0"
            />
            <span className="text-sm font-semibold text-[var(--color-fg)]">
              Demande en demi-journée (0,5 jour)
            </span>
          </label>
          {estDemiJournee && (
            <span className="text-xs font-bold text-[var(--color-info-600)] bg-[var(--color-info-500)]/10 px-2 py-0.5 border border-[var(--color-info-500)]/20">
              0,5 J
            </span>
          )}
        </div>

        {/* Sélection du moment si demi-journée */}
        {estDemiJournee && (
          <div className="p-3 bg-info-500/5 border border-info-500/20 space-y-2">
            <p id="leave-moment-label" className="block text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
              Moment de la journée <span className="text-danger-500">*</span>
            </p>
            <div className="grid grid-cols-2 gap-3">
              <label className={`flex items-center gap-2 p-2.5 border cursor-pointer text-sm font-medium transition-colors ${
                momentDemiJournee === 'MATIN' 
                  ? 'border-[var(--color-brand-500)] bg-[var(--color-surface)] text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]' 
                  : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] hover:bg-[var(--color-surface-hover)]'
              }`}>
                <input
                  type="radio"
                  name="momentDemiJournee"
                  value="MATIN"
                  checked={momentDemiJournee === 'MATIN'}
                  onChange={(e) => setMomentDemiJournee(e.target.value)}
                  aria-labelledby="leave-moment-label"
                  aria-invalid={!!fieldErrors.momentDemiJournee}
                  aria-describedby={fieldErrors.momentDemiJournee ? 'leave-moment-error' : undefined}
                  className="accent-[var(--color-brand-500)]"
                />
                Matin (Matinée)
              </label>
              <label className={`flex items-center gap-2 p-2.5 border cursor-pointer text-sm font-medium transition-colors ${
                momentDemiJournee === 'APRES_MIDI' 
                  ? 'border-[var(--color-brand-500)] bg-[var(--color-surface)] text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]' 
                  : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] hover:bg-[var(--color-surface-hover)]'
              }`}>
                <input
                  type="radio"
                  name="momentDemiJournee"
                  value="APRES_MIDI"
                  checked={momentDemiJournee === 'APRES_MIDI'}
                  onChange={(e) => setMomentDemiJournee(e.target.value)}
                  aria-labelledby="leave-moment-label"
                  aria-invalid={!!fieldErrors.momentDemiJournee}
                  aria-describedby={fieldErrors.momentDemiJournee ? 'leave-moment-error' : undefined}
                  className="accent-[var(--color-brand-500)]"
                />
                Après-midi (Deuxième partie)
              </label>
            </div>
            <FieldError id="leave-moment-error" className="mt-1">{fieldErrors.momentDemiJournee}</FieldError>
          </div>
        )}

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <label htmlFor="leave-date-start" className="block text-sm font-semibold text-[var(--color-fg)]">
              Date {estDemiJournee ? 'du congé' : 'de début'} <span className="text-danger-500">*</span>
            </label>
            <Input
              id="leave-date-start"
              type="date"
              error={fieldErrors.dateDebut}
              value={dateDebut}
              onChange={(e) => {
                const val = e.target.value;
                setDateDebut(val);
                if (estDemiJournee) {
                  setDateFin(val);
                }
                setFieldErrors((prev) => ({ ...prev, dateDebut: null }));
              }}
              required
            />
          </div>
          <div className="space-y-1.5">
            <label htmlFor="leave-date-end" className="block text-sm font-semibold text-[var(--color-fg)]">
              Date de reprise prévue <span className="text-danger-500">*</span>
            </label>
            <Input
              id="leave-date-end"
              type="date"
              error={fieldErrors.dateFin}
              value={estDemiJournee ? shiftIsoDate(dateDebut, 1) : repriseInput}
              disabled={estDemiJournee}
              min={shiftIsoDate(dateDebut, 1) || undefined}
              onChange={handleRepriseChange}
              required
            />
          </div>
        </div>
        {dateFin && <p className="mt-2 text-xs text-[var(--color-fg-muted)]">Dernier jour de congé couvert : <strong>{dateFin}</strong>. La date de reprise n&apos;est pas débitée.</p>}

        {/* Alerte Préavis / Rétroactivité explicite */}
        {preavisInfo?.isDepasse && (
          <div className="p-3 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 space-y-2 mt-2">
            <div className="flex items-center gap-2 text-xs font-bold text-[var(--color-warning-500)]">
              <ShieldAlert className="w-4 h-4 shrink-0" />
              {preavisInfo.isRetroactif 
                ? 'Demande de congé rétroactive' 
                : 'Délai de préavis réglementaire non respecté'}
            </div>
            <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
              {preavisInfo.isRetroactif ? (
                <>La date de début sélectionnée est passée ({Math.abs(preavisInfo.joursConstates)} jours de retard). Une dérogation de préavis est requise.</>
              ) : (
                <>Le préavis standard pour ce type de congé est de <strong>{preavisInfo.preavisRequis} jours</strong> ({preavisInfo.joursConstates} jours constatés entre aujourd'hui et le début du congé). Une dérogation est requise pour autoriser cette soumission.</>
              )}
            </p>
            {derogationDelaiPreavis ? (
              <div className="flex items-center justify-between gap-2 pt-1.5 border-t border-[var(--color-warning-500)]/20">
                <div className="flex items-center gap-1.5 text-xs font-semibold text-success-600 dark:text-success-400">
                  <CheckCircle2 className="w-3.5 h-3.5 shrink-0" />
                  <span>Dérogation de préavis activée {preavisInfo.isRetroactif ? '(saisie rétroactive / régularisation)' : ''}</span>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    if (setDerogationDelaiPreavis) setDerogationDelaiPreavis(false);
                  }}
                  className="text-[11px] text-[var(--color-fg-muted)] hover:text-danger-500 underline cursor-pointer"
                >
                  Désactiver
                </button>
              </div>
            ) : (
              <button
                type="button"
                onClick={() => {
                  if (setDerogationDelaiPreavis) setDerogationDelaiPreavis(true);
                  if (setShowOptionsAvancees) setShowOptionsAvancees(true);
                }}
                className="text-xs font-bold underline text-[var(--color-warning-500)] hover:text-[var(--color-warning-700)] cursor-pointer block"
              >
                Activer la dérogation de préavis
              </button>
            )}
          </div>
        )}

        {/* Compteur de jours — mis en avant, c'est l'info clé du formulaire */}
        {nbJoursDecomptes > 0 && (
          <div className="border border-info-500/30 bg-info-500/5 mt-4">
            <div className="flex items-center justify-between px-4 py-3">
              <span className="text-xs font-semibold text-[var(--color-fg-muted)] uppercase tracking-wide">
                Total décompté du solde
              </span>
              <span className="text-2xl font-extrabold text-info-600 dark:text-info-400 font-data">
                {nbJoursDecomptes} <span className="text-sm font-semibold">jour{nbJoursDecomptes > 1 ? 's' : ''}</span>
              </span>
            </div>
            <div className={`grid divide-x divide-info-500/20 border-t border-info-500/20 bg-[var(--color-surface)] ${
              nbJoursFeries > 0 ? 'grid-cols-3' : 'grid-cols-2'
            }`}>
              <div className="px-4 py-2.5 text-center">
                <div className="text-[11px] text-[var(--color-fg-subtle)] uppercase tracking-wide">Jours en semaine</div>
                <div className="text-base font-bold text-[var(--color-fg)] font-data">{nbJoursOuvres}</div>
              </div>
              <div className="px-4 py-2.5 text-center">
                <div className="text-[11px] text-[var(--color-fg-subtle)] uppercase tracking-wide">Week-ends inclus</div>
                <div className="text-base font-bold text-[var(--color-warning-600)] dark:text-[var(--color-warning-500)] font-data">{nbJoursWeekEnd}</div>
              </div>
              {nbJoursFeries > 0 && (
                <div className="px-4 py-2.5 text-center">
                  <div className="text-[11px] text-[var(--color-fg-subtle)] uppercase tracking-wide">Fériés traversés</div>
                  <div className="text-base font-bold text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] font-data">{nbJoursFeries}</div>
                </div>
              )}
            </div>
            <p className="px-4 py-2 text-xs text-[var(--color-fg-subtle)] flex items-start gap-1.5 border-t border-info-500/20">
              <Info className="w-3.5 h-3.5 shrink-0 mt-0.5 text-info-500" />
              Tous les jours de l'intervalle, y compris samedis, dimanches et jours fériés chômés, sont intégralement déduits du solde.
            </p>
          </div>
        )}

        {/* ── Contrôle Solde Disponible en Temps Réel ──────────────────── */}
        {selectedType?.decompteSoldeAcquis && simulationReport?.soldeAvant !== undefined && (
          <div className={`mt-3 p-3 border ${
            isSoldeInsuffisant
              ? 'border-[var(--color-warning-500)]/40 bg-[var(--color-warning-500)]/10'
              : 'border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]'
          }`}>
            <div className="flex items-center justify-between text-xs">
              <span className="text-[var(--color-fg-muted)] font-medium">
                Solde actuel disponible :
              </span>
              <span className={`font-data font-bold ${
                isSoldeInsuffisant
                  ? 'text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)]'
                  : 'text-success-600 dark:text-success-400'
              }`}>
                {soldeNum.toFixed(2)} jour(s)
              </span>
            </div>

            {isSoldeInsuffisant && (
              <div className="mt-2 pt-2 border-t border-[var(--color-warning-500)]/20 space-y-2">
                <div className="flex items-start gap-1.5 text-xs text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] font-medium">
                  <AlertTriangle className="w-4 h-4 text-[var(--color-warning-500)] shrink-0 mt-0.5" />
                  <span>
                    Solde insuffisant pour couvrir cette demande ({soldeNum.toFixed(2)} j disponibles pour {nbJoursDecomptes} j demandés).
                  </span>
                </div>
                <p className="text-xs text-[var(--color-warning-700)]">
                  Les jours non couverts restent une absence à traiter dans le pointage.
                </p>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Banner d'information : bascule maladie > 30j */}
      {selectedType && selectedType.code === 'MALADIE' && nbJoursDecomptes > 30 && (
        <div className="pl-3 border-l-2 border-[var(--color-warning-500)] py-1 mt-4 space-y-1">
          <div className="flex items-center gap-1.5 text-[var(--color-warning-600)] dark:text-[var(--color-warning-500)] font-semibold text-sm">
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
        <label className="flex items-start gap-3 pl-3 border-l-2 border-[var(--color-warning-500)] py-1 mt-4 cursor-pointer">
          <input
            type="checkbox"
            checked={extensionPathologiqueMaternite}
            onChange={(e) => setExtensionPathologiqueMaternite(e.target.checked)}
            className="w-4 h-4 mt-0.5 accent-[var(--color-warning-500)] cursor-pointer shrink-0"
          />
          <span className="text-sm text-[var(--color-fg)]">
            <span className="font-semibold text-[var(--color-warning-600)]">Extension pathologique</span>
            <span className="block text-xs text-[var(--color-fg-muted)] mt-0.5">
              Porte le congé maternité à 126 jours maximum (18 semaines), selon justification médicale.
            </span>
          </span>
        </label>
      )}
    </>
  );
}
