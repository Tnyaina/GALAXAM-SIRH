import { memo } from 'react';
import { Search } from 'lucide-react';

function SupervisionFilterBar({
    searchQuery,
    onSearchChange,
    selectedSite,
    onSiteChange,
    selectedStatut,
    onStatutChange,
    selectedEcheance,
    onEcheanceChange,
    doublonsOnly,
    onDoublonsToggle,
    sites = [],
    totalCollaborateurs,
    totalContrats,
    onResetFilters,
    hasActiveFilters
}) {
    return (
        <div className="space-y-2">
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 flex flex-wrap items-center gap-3">
                {/* Recherche globale */}
                <div className="relative min-w-[240px] flex-1">
                    <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-fg-subtle)]" />
                    <input
                        type="text"
                        value={searchQuery}
                        onChange={(e) => onSearchChange(e.target.value)}
                        placeholder="Matricule, nom, prénom, N° contrat..."
                        className="w-full pl-9 pr-3 py-2 text-xs bg-[var(--color-bg)] border border-[var(--color-border)] focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)] placeholder:text-[var(--color-fg-subtle)]"
                    />
                </div>

                {/* Filtre Site */}
                <select
                    value={selectedSite}
                    onChange={(e) => onSiteChange(e.target.value)}
                    className="py-2 px-3 text-xs bg-[var(--color-bg)] border border-[var(--color-border)] focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)] min-w-[170px]"
                >
                    <option value="">Tous les sites d'affectation</option>
                    {sites
                        .filter(s => s.estActif !== false && s.actif !== false)
                        .map((s, idx) => (
                            <option key={s.idSite || idx} value={s.idSite}>
                                {s.libelle || s.nomSite || s.codeSite || `Site #${s.idSite}`}{s.codeSite ? ` (${s.codeSite})` : ''}
                            </option>
                        ))}
                </select>

                {/* Filtre Statut */}
                <select
                    value={selectedStatut}
                    onChange={(e) => onStatutChange(e.target.value)}
                    className="py-2 px-3 text-xs bg-[var(--color-bg)] border border-[var(--color-border)] focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)] min-w-[170px]"
                >
                    <option value="ALL">Tous les statuts de contrat</option>
                    <option value="ACTIF">Contrat actif</option>
                    <option value="PENDING">Planifié / En attente de signature</option>
                    <option value="A_SIGNER">En attente de signature</option>
                    <option value="BROUILLON">Brouillon</option>
                    <option value="SUSPENDU">Contrat suspendu</option>
                    <option value="CLOTURE">Contrat arrivé à terme (Clôturé)</option>
                    <option value="ROMPU">Contrat rompu anticipativement</option>
                    <option value="ANNULE">Contrat annulé</option>
                </select>

                {/* Filtre Échéance */}
                <select
                    value={selectedEcheance}
                    onChange={(e) => onEcheanceChange(e.target.value)}
                    className="py-2 px-3 text-xs bg-[var(--color-bg)] border border-[var(--color-border)] focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)] min-w-[170px]"
                >
                    <option value="">Toutes les échéances</option>
                    <option value="30J">Moins de 30 jours</option>
                    <option value="60J">Moins de 60 jours</option>
                    <option value="ESSAI">Période d'essai en cours</option>
                </select>

                {/* Toggle Doublons uniquement */}
                <label className={`inline-flex items-center gap-2 px-3 py-2 text-xs font-bold border cursor-pointer select-none transition-colors ml-auto ${
                    doublonsOnly
                        ? 'bg-danger-500/10 border-danger-500 text-danger-700 dark:text-danger-400'
                        : 'border-dashed border-warning-500/80 bg-warning-500/5 text-warning-800 dark:text-warning-300 hover:bg-warning-500/10'
                }`}>
                    <input
                        type="checkbox"
                        checked={doublonsOnly}
                        onChange={(e) => onDoublonsToggle(e.target.checked)}
                        className="accent-danger-500 cursor-pointer"
                    />
                    Doublons uniquement
                </label>
            </div>

            {/* Ligne des Résultats & Reset */}
            <div className="flex items-center justify-between text-xs text-[var(--color-fg-muted)] px-1">
                <span>
                    <strong className="text-[var(--color-fg)]">{totalCollaborateurs}</strong> collaborateur(s) · <strong className="text-[var(--color-fg)]">{totalContrats}</strong> contrat(s) trouvé(s)
                </span>
                {hasActiveFilters && (
                    <button
                        type="button"
                        onClick={onResetFilters}
                        className="text-xs font-bold text-[var(--color-brand-600)] hover:underline cursor-pointer"
                    >
                        Réinitialiser tous les filtres
                    </button>
                )}
            </div>
        </div>
    );
}

export default memo(SupervisionFilterBar);
