import { memo, useMemo } from 'react';
import { Search, RotateCcw } from 'lucide-react';
import Input from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
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
    totalCollaborateurs,
    totalContrats,
    onResetFilters,
    hasActiveFilters
}) {
    // Données dérivées : options présentées dans les filtres.
    const siteOptions = useMemo(() => [
        { value: '', label: 'Tous les sites d\'affectation' },
        ...sites
            .filter(s => s.estActif !== false && s.actif !== false)
            .map(s => ({
                value: String(s.idSite),
                label: `${s.libelle || s.nomSite || s.codeSite || `Site #${s.idSite}`}${s.codeSite ? ` (${s.codeSite})` : ''}`
            }))
    ], [sites]);

    const statutOptions = useMemo(() => [
        { value: 'ALL', label: 'Tous les statuts de contrat' },
        { value: 'ACTIF', label: 'Contrat actif' },
        { value: 'PENDING', label: 'Planifié / En attente de signature' },
        { value: 'A_SIGNER', label: 'En attente de signature' },
        { value: 'BROUILLON', label: 'Brouillon' },
        { value: 'SUSPENDU', label: 'Contrat suspendu' },
        { value: 'CLOTURE', label: 'Contrat arrivé à terme (Clôturé)' },
        { value: 'ROMPU', label: 'Contrat rompu anticipativement' },
        { value: 'ANNULE', label: 'Contrat annulé' },
    ], []);

    const echeanceOptions = useMemo(() => [
        { value: '', label: 'Toutes les échéances' },
        { value: '30J', label: 'Moins de 30 jours' },
        { value: '60J', label: 'Moins de 60 jours' },
        { value: 'ESSAI', label: 'Période d\'essai en cours' },
    ], []);

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
                <div className="min-w-[170px]">
                    <Select
                        value={selectedSite}
                        onChange={(val) => onSiteChange(val)}
                        options={siteOptions}
                        className="py-1.5 text-xs"
                    />
                </div>

                {/* Filtre Statut */}
                <div className="min-w-[170px]">
                    <Select
                        value={selectedStatut}
                        onChange={(val) => onStatutChange(val)}
                        options={statutOptions}
                        className="py-1.5 text-xs"
                    />
                </div>

                {/* Filtre Échéance */}
                <div className="min-w-[160px]">
                    <Select
                        value={selectedEcheance}
                        onChange={(val) => onEcheanceChange(val)}
                        options={echeanceOptions}
                        className="py-1.5 text-xs"
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
