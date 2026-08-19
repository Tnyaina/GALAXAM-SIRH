import { AlertTriangle, Info, ShieldAlert } from 'lucide-react';
import Input from '@/components/ui/forms/Input';

export default function DatesCongeSection({
  dateDebut, setDateDebut,
  dateFin, setDateFin,
  nbJoursDecomptes, nbJoursOuvres, nbJoursWeekEnd,
  selectedType,
  isMaternite,
  extensionPathologiqueMaternite, setExtensionPathologiqueMaternite,
  fieldErrors, setFieldErrors
}) {
  return (
    <>
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
          <div className="border border-info-500/30 bg-info-500/5 mt-4">
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
        <div className="pl-3 border-l-2 border-warning-500 py-1 mt-4 space-y-1">
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
        <label className="flex items-start gap-3 pl-3 border-l-2 border-warning-500 py-1 mt-4 cursor-pointer">
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
    </>
  );
}
