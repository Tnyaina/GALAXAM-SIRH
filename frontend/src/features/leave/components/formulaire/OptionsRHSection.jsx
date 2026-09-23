import { ShieldAlert, ChevronDown } from 'lucide-react';

export default function OptionsRHSection({
  showOptionsAvancees, setShowOptionsAvancees,
  derogationSoldeNegatif, setDerogationSoldeNegatif,
  derogationDelaiPreavis, setDerogationDelaiPreavis,
  derogationContinuiteService, setDerogationContinuiteService,
  estRemunere, setEstRemunere
}) {
  return (
    <div className="border border-[var(--color-border-subtle)] mt-4">
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
          {setEstRemunere && (
            <label className="flex items-start gap-3 cursor-pointer p-2 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
              <input
                type="checkbox"
                checked={estRemunere ?? true}
                onChange={(e) => setEstRemunere(e.target.checked)}
                className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0"
              />
              <span className="text-sm font-medium text-[var(--color-fg)]">
                Congé rémunéré (indemnisé sur les droits acquis)
                <span className="block text-xs text-[var(--color-fg-muted)] mt-0.5 font-normal">
                  Coché : l'absence donne droit à l'indemnité légale de congés payés. Décoché : congé sans solde (retenue sur paie au taux d'absence et droits du mois proratisés).
                </span>
              </span>
            </label>
          )}
          <label className="flex items-start gap-3 cursor-pointer">
            <input
              type="checkbox"
              checked={derogationSoldeNegatif}
              onChange={(e) => setDerogationSoldeNegatif(e.target.checked)}
              className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0"
            />
            <span className="text-sm text-[var(--color-fg)]">
              Autoriser le dépassement de solde (prise par anticipation)
              <span className="block text-xs text-[var(--color-fg-muted)] mt-0.5">
                Autorise la demande même si le solde actuel est insuffisant ou si l'ancienneté est &lt; 1 an. Le solde négatif temporaire sera régularisé par les prochains droits acquis.
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
              Dérogation de préavis
              <span className="block text-xs text-[var(--color-fg-muted)] mt-0.5">
                Autorise le dépôt ou la validation de la demande même si le délai de préavis habituel n'est pas respecté.
              </span>
            </span>
          </label>
          <label className="flex items-start gap-3 cursor-pointer">
            <input
              type="checkbox"
              checked={derogationContinuiteService}
              onChange={(e) => setDerogationContinuiteService(e.target.checked)}
              className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0"
            />
            <span className="text-sm text-[var(--color-fg)]">
              Continuité d'activité : autoriser les absences simultanées
              <span className="block text-xs text-[var(--color-fg-muted)] mt-0.5">
                Autorise le congé même si d'autres collaborateurs occupant le même poste sont déjà planifiés absents sur cette période.
              </span>
            </span>
          </label>
        </div>
      )}
    </div>
  );
}
