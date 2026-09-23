import { useMemo } from 'react';
import { Search, RotateCcw, X } from 'lucide-react';
import Select from '@/components/ui/forms/Select';
import Input from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';

export default function EmployeFilterBar({
    searchQuery = '',
    onSearchChange,
    statusFilter = 'ALL',
    onStatusFilterChange,
    siteFilter = '',
    onSiteFilterChange,
    departementFilter = '',
    onDepartementFilterChange,
    serviceFilter = '',
    onServiceFilterChange,
    sites = [],
    departements = [],
    services = [],
    sortPreset = 'HIERARCHICAL',
    onSortPresetChange,
    onResetFilters,
    totalFound = 0,
}) {
    // Données dérivées : services compatibles, libellés et options de filtres.
    const filteredServices = useMemo(() => {
        if (!serviceFilter && !departementFilter) return services;
        return services.filter(s => !departementFilter || String(s.idDepartement || s.id_departement) === String(departementFilter));
    }, [services, departementFilter, serviceFilter]);

    const hasActiveFilters = Boolean(
        searchQuery || 
        statusFilter !== 'ALL' || 
        siteFilter || 
        departementFilter || 
        serviceFilter
    );

    // Labels pour les badges actifs
    const selectedSite = sites.find(s => String(s.idSite || s.id_site) === String(siteFilter));
    const selectedDept = departements.find(d => String(d.idDepartement || d.id_departement) === String(departementFilter));
    const selectedService = services.find(srv => String(srv.idService || srv.id_service) === String(serviceFilter));

    // Options pour les composants Select
    const statusOptions = useMemo(() => [
        { value: 'ALL', label: 'Statut : Tous' },
        { value: 'ACTIVE', label: 'Actifs uniquement' },
        { value: 'INACTIVE', label: 'Inactifs uniquement' },
    ], []);

    const siteOptions = useMemo(() => [
        { value: '', label: 'Site : Tous les sites' },
        ...sites
            .filter(s => s.estActif !== false && s.actif !== false)
            .map(s => ({
                value: String(s.idSite || s.id_site),
                label: s.nomSite || s.nom_site || s.libelle
            }))
    ], [sites]);

    const deptOptions = useMemo(() => [
        { value: '', label: 'Département : Tous' },
        ...departements
            .filter(d => d.estActif !== false && d.actif !== false)
            .map(d => ({
                value: String(d.idDepartement || d.id_departement),
                label: d.nomDepartement || d.nom_departement || d.libelle
            }))
    ], [departements]);

    const serviceOptions = useMemo(() => [
        { value: '', label: 'Service : Tous' },
        ...filteredServices
            .filter(srv => srv.estActif !== false && srv.actif !== false)
            .map(srv => ({
                value: String(srv.idService || srv.id_service),
                label: srv.nomService || srv.nom_service || srv.libelle
            }))
    ], [filteredServices]);

    const sortOptions = useMemo(() => [
        { value: 'HIERARCHICAL', label: 'Tri : Dépt → Service → Nom' },
        { value: 'SITE_SERVICE', label: 'Tri : Site → Service → Nom' },
        { value: 'NOM_ASC', label: 'Tri : Nom (A → Z)' },
        { value: 'NOM_DESC', label: 'Tri : Nom (Z → A)' },
        { value: 'MATRICULE_ASC', label: 'Tri : Matricule (0 → 9)' },
        { value: 'MATRICULE_DESC', label: 'Tri : Matricule (9 → 0)' },
        { value: 'ANCIENNETE_DESC', label: 'Tri : Ancienneté (Anciens)' },
        { value: 'ANCIENNETE_ASC', label: 'Tri : Ancienneté (Récents)' },
    ], []);

    // Rendu
    return (
        <div className="p-3 sm:p-4 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-3 font-sans shadow-2xs">
            {/* Grille principale : 6 colonnes symétriques sur desktop */}
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-6 gap-2.5 items-center">
                {/* 1. Recherche globale avec bouton clear */}
                <div className="relative">
                    <Input
                        type="text"
                        value={searchQuery}
                        onChange={(e) => onSearchChange(e.target.value)}
                        placeholder="Recherche (nom, matricule)..."
                        iconLeft={<Search size={14} />}
                        className="py-1.5 pl-8 pr-7 text-xs"
                    />
                    {searchQuery && (
                        <button
                            type="button"
                            onClick={() => onSearchChange('')}
                            className="absolute right-2 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] p-0.5 rounded-none cursor-pointer"
                            title="Effacer la recherche"
                            aria-label="Effacer la recherche"
                        >
                            <X size={12} />
                        </button>
                    )}
                </div>

                {/* 2. Filtre Statut */}
                <Select
                    value={statusFilter}
                    onChange={(val) => onStatusFilterChange(val)}
                    options={statusOptions}
                    className="py-1.5 text-xs"
                />

                {/* 3. Filtre Site */}
                <Select
                    value={siteFilter}
                    onChange={(val) => onSiteFilterChange(val)}
                    options={siteOptions}
                    className="py-1.5 text-xs"
                />

                {/* 4. Filtre Département */}
                <Select
                    value={departementFilter}
                    onChange={(val) => {
                        onDepartementFilterChange(val);
                        onServiceFilterChange('');
                    }}
                    options={deptOptions}
                    className="py-1.5 text-xs"
                />

                {/* 5. Filtre Service */}
                <Select
                    value={serviceFilter}
                    onChange={(val) => onServiceFilterChange(val)}
                    options={serviceOptions}
                    className="py-1.5 text-xs"
                />

                {/* 6. Ordre de Tri */}
                <Select
                    value={sortPreset}
                    onChange={(val) => onSortPresetChange(val)}
                    options={sortOptions}
                    className="py-1.5 text-xs"
                />
            </div>

            {/* Barre de synthèse & Badges de filtres actifs amovibles */}
            <div className="flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-[var(--color-border-subtle)] text-xs">
                {/* Compteur d'employés trouvés */}
                <div className="flex items-center gap-2 text-[var(--color-fg-muted)]">
                    <span className="w-2 h-2 rounded-full bg-[var(--color-brand-500)] inline-block shrink-0" />
                    <span className="font-semibold text-[var(--color-fg)]">
                        {totalFound}
                    </span>
                    <span>
                        {totalFound > 1 ? 'collaborateurs trouvés' : 'collaborateur trouvé'}
                    </span>
                </div>

                {/* Zone des Badges/Chips de filtres actifs */}
                {hasActiveFilters && (
                    <div className="flex items-center gap-1.5 flex-wrap">
                        {searchQuery && (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 text-[11px] bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-[var(--color-fg)]">
                                Recherche: <strong className="font-mono">{searchQuery}</strong>
                                <button
                                    type="button"
                                    onClick={() => onSearchChange('')}
                                    className="hover:text-[var(--color-danger-500)] ml-0.5 cursor-pointer"
                                    title="Retirer le filtre"
                                >
                                    <X size={10} />
                                </button>
                            </span>
                        )}

                        {statusFilter !== 'ALL' && (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 text-[11px] bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-[var(--color-fg)]">
                                Statut: <strong>{statusFilter === 'ACTIVE' ? 'Actifs' : 'Inactifs'}</strong>
                                <button
                                    type="button"
                                    onClick={() => onStatusFilterChange('ALL')}
                                    className="hover:text-[var(--color-danger-500)] ml-0.5 cursor-pointer"
                                    title="Retirer le filtre"
                                >
                                    <X size={10} />
                                </button>
                            </span>
                        )}

                        {selectedSite && (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 text-[11px] bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-[var(--color-fg)]">
                                Site: <strong>{selectedSite.nomSite || selectedSite.libelle}</strong>
                                <button
                                    type="button"
                                    onClick={() => onSiteFilterChange('')}
                                    className="hover:text-[var(--color-danger-500)] ml-0.5 cursor-pointer"
                                    title="Retirer le filtre"
                                >
                                    <X size={10} />
                                </button>
                            </span>
                        )}

                        {selectedDept && (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 text-[11px] bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-[var(--color-fg)]">
                                Dépt: <strong>{selectedDept.nomDepartement || selectedDept.libelle}</strong>
                                <button
                                    type="button"
                                    onClick={() => {
                                        onDepartementFilterChange('');
                                        onServiceFilterChange('');
                                    }}
                                    className="hover:text-[var(--color-danger-500)] ml-0.5 cursor-pointer"
                                    title="Retirer le filtre"
                                >
                                    <X size={10} />
                                </button>
                            </span>
                        )}

                        {selectedService && (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 text-[11px] bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-[var(--color-fg)]">
                                Service: <strong>{selectedService.nomService || selectedService.libelle}</strong>
                                <button
                                    type="button"
                                    onClick={() => onServiceFilterChange('')}
                                    className="hover:text-[var(--color-danger-500)] ml-0.5 cursor-pointer"
                                    title="Retirer le filtre"
                                >
                                    <X size={10} />
                                </button>
                            </span>
                        )}

                        {/* Bouton pour tout réinitialiser */}
                        <Button
                            type="button"
                            variant="ghost"
                            size="xs"
                            iconLeft={<RotateCcw size={12} />}
                            onClick={onResetFilters}
                            className="text-[var(--color-brand-600)] hover:text-[var(--color-brand-700)] ml-1 font-semibold"
                        >
                            Réinitialiser tout
                        </Button>
                    </div>
                )}
            </div>
        </div>
    );
}
