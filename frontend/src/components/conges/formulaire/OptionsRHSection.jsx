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
                Maintien de la rémunération (Rémunéré à 100%)
                <span className="block text-xs text-[var(--color-fg-muted)] mt-0.5 font-normal">
                  Coché : le salaire est intégralement maintenu. Décoché : absence non rémunérée / sans solde, décomptée au temps de travail sur la paie.
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
              Dérogation RH : Prise par anticipation / Solde négatif
              <span className="block text-xs text-[var(--color-fg-muted)] mt-0.5">
                Autorise la pose même si l'ancienneté est &lt; 12 mois (Art. 85) ou si le solde disponible est insuffisant.
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
          <label className="flex items-start gap-3 cursor-pointer">
            <input
              type="checkbox"
              checked={derogationContinuiteService}
              onChange={(e) => setDerogationContinuiteService(e.target.checked)}
              className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0"
            />
            <span className="text-sm text-[var(--color-fg)]">
              Dérogation de continuité de service (Poste commun)
              <span className="block text-xs text-[var(--color-fg-muted)] mt-0.5">
                Autorise l'absence même si un ou plusieurs collaborateurs du même poste sont déjà absents sur cette période.
              </span>
            </span>
          </label>
        </div>
      )}
    </div>
  );
}
