import { memo, useMemo } from 'react';
import { Search, RotateCcw } from 'lucide-react';
import Input from '@/components/ui/forms/Input';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Button from '@/components/ui/forms/Button';

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
    statuses = [],
    deadlines = [],
    totalCollaborateurs,
    totalContrats,
    onResetFilters,
    hasActiveFilters
}) {
    // Données dérivées : options présentées dans les filtres avec code et libellé.
    const siteOptions = useMemo(() => [
        { value: '', label: 'Tous les sites d\'affectation' },
        ...sites
            .filter(s => s.estActif !== false && s.actif !== false)
            .map(s => {
                const code = s.codeSite || s.code;
                const libelle = s.libelle || s.nomSite || `Site #${s.idSite}`;
                return {
                    value: String(s.idSite),
                    label: code ? `${code} — ${libelle}` : libelle,
                };
            })
    ], [sites]);

    const statutOptions = useMemo(() => [
        { value: 'ALL', label: 'Tous les états' },
        ...statuses.map(s => ({
            value: s.code,
            label: s.code && s.code !== s.libelle ? `${s.code} — ${s.libelle}` : s.libelle
        }))
    ], [statuses]);

    const echeanceOptions = useMemo(() => [
        { value: '', label: 'Toutes les échéances' },
        ...deadlines.map(item => ({
            value: item.code === 'ALL' ? '' : item.code,
            label: item.code && item.code !== 'ALL' && item.code !== item.libelle ? `${item.code} — ${item.libelle}` : item.libelle,
        }))
    ], [deadlines]);

    // Rendu
    return (
        <div className="space-y-2">
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 sm:p-4 flex flex-wrap items-center gap-2.5">
                {/* Recherche globale */}
                <div className="min-w-[220px] flex-1">
                    <Input
                        type="text"
                        value={searchQuery}
                        onChange={(e) => onSearchChange(e.target.value)}
                        placeholder="Matricule, nom, prénom, N° contrat..."
                        iconLeft={<Search size={14} />}
                        className="py-1.5 pl-8 text-xs"
                    />
                </div>

                {/* Filtre Site */}
                <div className="min-w-[200px] flex-1 sm:flex-initial">
                    <SearchSelect
                        value={selectedSite}
                        onChange={(val) => onSiteChange(val || '')}
                        options={siteOptions}
                        placeholder="Tous les sites d'affectation"
                        className="w-full text-xs"
                    />
                </div>

                {/* Filtre Statut */}
                <div className="min-w-[180px] flex-1 sm:flex-initial">
                    <SearchSelect
                        value={selectedStatut}
                        onChange={(val) => onStatutChange(val || 'ALL')}
                        options={statutOptions}
                        placeholder="Tous les états"
                        className="w-full text-xs"
                    />
                </div>

                {/* Filtre Échéance */}
                <div className="min-w-[180px] flex-1 sm:flex-initial">
                    <SearchSelect
                        value={selectedEcheance}
                        onChange={(val) => onEcheanceChange(val || '')}
                        options={echeanceOptions}
                        placeholder="Toutes les échéances"
                        className="w-full text-xs"
                    />
                </div>

                {/* Toggle Doublons uniquement */}
                <label className={`inline-flex items-center gap-2 px-3 py-1.5 text-xs font-bold border cursor-pointer select-none transition-colors ml-auto ${
                    doublonsOnly
                        ? 'bg-[var(--color-danger-500)]/10 border-[var(--color-danger-500)] text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]'
                        : 'border-dashed border-[var(--color-warning-500)]/80 bg-[var(--color-warning-500)]/5 text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] hover:bg-[var(--color-warning-500)]/10'
                }`}>
                    <input
                        type="checkbox"
                        checked={doublonsOnly}
                        onChange={(e) => onDoublonsToggle(e.target.checked)}
                        className="accent-[var(--color-danger-500)] cursor-pointer"
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
                    <Button
                        type="button"
                        variant="ghost"
                        size="xs"
                        iconLeft={<RotateCcw size={12} />}
                        onClick={onResetFilters}
                        className="text-[var(--color-brand-600)] hover:text-[var(--color-brand-700)] font-semibold"
                    >
                        Réinitialiser tous les filtres
                    </Button>
                )}
            </div>
        </div>
    );
}

export default memo(SupervisionFilterBar);
