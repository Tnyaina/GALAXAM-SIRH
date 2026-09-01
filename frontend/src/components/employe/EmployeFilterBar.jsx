import { Search, RotateCcw, X, Filter, Building, MapPin, CheckCircle2 } from 'lucide-react';

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
    // Filtrer les services selon le département sélectionné
    const filteredServices = serviceFilter || departementFilter
        ? services.filter(s => !departementFilter || String(s.idDepartement || s.id_departement) === String(departementFilter))
        : services;

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

    return (
        <div className="p-3 sm:p-4 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-3 font-sans shadow-2xs">
            {/* Grille principale parfaitement équilibrée (6 colonnes symétriques sur desktop) */}
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-6 gap-2.5">
                {/* 1. Recherche globale avec bouton clear */}
                <div className="relative">
                    <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)] pointer-events-none" />
                    <input
                        type="text"
                        value={searchQuery}
                        onChange={(e) => onSearchChange(e.target.value)}
                        placeholder="Recherche (nom, matricule)..."
                        className="w-full pl-8 pr-7 py-1.5 text-xs bg-[var(--color-bg)] border border-[var(--color-border)] focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)] placeholder:text-[var(--color-fg-subtle)] rounded-none"
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
                <div>
                    <select
                        value={statusFilter}
                        onChange={(e) => onStatusFilterChange(e.target.value)}
                        className="w-full py-1.5 px-2.5 text-xs bg-[var(--color-bg)] border border-[var(--color-border)] focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)] cursor-pointer rounded-none"
                    >
                        <option value="ALL">Statut : Tous</option>
                        <option value="ACTIVE">Actifs uniquement</option>
                        <option value="INACTIVE">Inactifs uniquement</option>
                    </select>
                </div>

                {/* 3. Filtre Site */}
                <div>
                    <select
                        value={siteFilter}
                        onChange={(e) => onSiteFilterChange(e.target.value)}
                        className="w-full py-1.5 px-2.5 text-xs bg-[var(--color-bg)] border border-[var(--color-border)] focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)] cursor-pointer rounded-none"
                    >
                        <option value="">Site : Tous les sites</option>
                        {sites.map(s => (
                            <option key={s.idSite || s.id_site} value={s.idSite || s.id_site}>
                                {s.nomSite || s.nom_site || s.libelle}
                            </option>
                        ))}
                    </select>
                </div>

                {/* 4. Filtre Département */}
                <div>
                    <select
                        value={departementFilter}
                        onChange={(e) => {
                            onDepartementFilterChange(e.target.value);
                            onServiceFilterChange('');
                        }}
                        className="w-full py-1.5 px-2.5 text-xs bg-[var(--color-bg)] border border-[var(--color-border)] focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)] cursor-pointer rounded-none"
                    >
                        <option value="">Département : Tous</option>
                        {departements.map(d => (
                            <option key={d.idDepartement || d.id_departement} value={d.idDepartement || d.id_departement}>
                                {d.nomDepartement || d.nom_departement || d.libelle}
                            </option>
                        ))}
                    </select>
                </div>

                {/* 5. Filtre Service */}
                <div>
                    <select
                        value={serviceFilter}
                        onChange={(e) => onServiceFilterChange(e.target.value)}
                        className="w-full py-1.5 px-2.5 text-xs bg-[var(--color-bg)] border border-[var(--color-border)] focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)] cursor-pointer rounded-none"
                    >
                        <option value="">Service : Tous</option>
                        {filteredServices.map(srv => (
                            <option key={srv.idService || srv.id_service} value={srv.idService || srv.id_service}>
                                {srv.nomService || srv.nom_service || srv.libelle}
                            </option>
                        ))}
                    </select>
                </div>

                {/* 6. Ordre de Tri */}
                <div>
                    <select
                        value={sortPreset}
                        onChange={(e) => onSortPresetChange(e.target.value)}
                        className="w-full py-1.5 px-2.5 text-xs bg-[var(--color-bg)] border border-[var(--color-border)] focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)] cursor-pointer rounded-none"
                    >
                        <option value="HIERARCHICAL">Tri : Dépt → Service → Nom</option>
                        <option value="SITE_SERVICE">Tri : Site → Service → Nom</option>
                        <option value="NOM_ASC">Tri : Nom (A → Z)</option>
                        <option value="NOM_DESC">Tri : Nom (Z → A)</option>
                        <option value="MATRICULE_ASC">Tri : Matricule (0 → 9)</option>
                        <option value="MATRICULE_DESC">Tri : Matricule (9 → 0)</option>
                        <option value="ANCIENNETE_DESC">Tri : Ancienneté (Anciens)</option>
                        <option value="ANCIENNETE_ASC">Tri : Ancienneté (Récents)</option>
                    </select>
                </div>
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
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 text-[11px] bg-[var(--color-bg-subtle)] border border-[var(--color-border)] text-[var(--color-fg)] rounded-none">
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
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 text-[11px] bg-[var(--color-bg-subtle)] border border-[var(--color-border)] text-[var(--color-fg)] rounded-none">
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
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 text-[11px] bg-[var(--color-bg-subtle)] border border-[var(--color-border)] text-[var(--color-fg)] rounded-none">
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
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 text-[11px] bg-[var(--color-bg-subtle)] border border-[var(--color-border)] text-[var(--color-fg)] rounded-none">
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
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 text-[11px] bg-[var(--color-bg-subtle)] border border-[var(--color-border)] text-[var(--color-fg)] rounded-none">
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
                        <button
                            type="button"
                            onClick={onResetFilters}
                            className="inline-flex items-center gap-1 px-2 py-0.5 text-[11px] text-[var(--color-brand-500)] hover:underline font-semibold cursor-pointer ml-1"
                        >
                            <RotateCcw size={11} />
                            <span>Réinitialiser tout</span>
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
}
