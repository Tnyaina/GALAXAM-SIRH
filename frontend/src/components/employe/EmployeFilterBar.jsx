import { Search, RotateCcw } from 'lucide-react';

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

    const hasActiveFilters = Boolean(searchQuery || statusFilter !== 'ALL' || siteFilter || departementFilter || serviceFilter);

    return (
        <div className="p-3 sm:p-3.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-2.5 font-sans shadow-2xs">
            {/* Grille principale des filtres */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-2.5">
                {/* Recherche globale */}
                <div className="relative lg:col-span-2">
                    <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" />
                    <input
                        type="text"
                        value={searchQuery}
                        onChange={(e) => onSearchChange(e.target.value)}
                        placeholder="Recherche (nom, prénom, matricule, CIN, tél)..."
                        className="w-full pl-8 pr-3 py-1.5 text-xs bg-[var(--color-bg)] border border-[var(--color-border)] focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)] placeholder:text-[var(--color-fg-subtle)]"
                    />
                </div>

                {/* Filtre Statut */}
                <div>
                    <select
                        value={statusFilter}
                        onChange={(e) => onStatusFilterChange(e.target.value)}
                        className="w-full py-1.5 px-2.5 text-xs bg-[var(--color-bg)] border border-[var(--color-border)] focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)] cursor-pointer"
                    >
                        <option value="ALL">Tous les statuts</option>
                        <option value="ACTIVE">Actifs uniquement</option>
                        <option value="INACTIVE">Inactifs uniquement</option>
                    </select>
                </div>

                {/* Filtre Site */}
                <div>
                    <select
                        value={siteFilter}
                        onChange={(e) => onSiteFilterChange(e.target.value)}
                        className="w-full py-1.5 px-2.5 text-xs bg-[var(--color-bg)] border border-[var(--color-border)] focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)] cursor-pointer"
                    >
                        <option value="">Tous les sites</option>
                        {sites.map(s => (
                            <option key={s.idSite || s.id_site} value={s.idSite || s.id_site}>
                                {s.nomSite || s.nom_site || s.libelle}
                            </option>
                        ))}
                    </select>
                </div>

                {/* Preset de Tri */}
                <div>
                    <select
                        value={sortPreset}
                        onChange={(e) => onSortPresetChange(e.target.value)}
                        className="w-full py-1.5 px-2.5 text-xs bg-[var(--color-bg)] border border-[var(--color-border)] focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)] cursor-pointer"
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

            {/* Ligne secondaire : Département & Service (si applicable) */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 pt-2 border-t border-[var(--color-border-subtle)]">
                <div>
                    <select
                        value={departementFilter}
                        onChange={(e) => {
                            onDepartementFilterChange(e.target.value);
                            onServiceFilterChange('');
                        }}
                        className="w-full py-1.5 px-2.5 text-xs bg-[var(--color-bg)] border border-[var(--color-border)] focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)] cursor-pointer"
                    >
                        <option value="">Tous les départements</option>
                        {departements.map(d => (
                            <option key={d.idDepartement || d.id_departement} value={d.idDepartement || d.id_departement}>
                                {d.nomDepartement || d.nom_departement || d.libelle}
                            </option>
                        ))}
                    </select>
                </div>

                <div>
                    <select
                        value={serviceFilter}
                        onChange={(e) => onServiceFilterChange(e.target.value)}
                        className="w-full py-1.5 px-2.5 text-xs bg-[var(--color-bg)] border border-[var(--color-border)] focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)] cursor-pointer"
                    >
                        <option value="">Tous les services</option>
                        {filteredServices.map(srv => (
                            <option key={srv.idService || srv.id_service} value={srv.idService || srv.id_service}>
                                {srv.nomService || srv.nom_service || srv.libelle}
                            </option>
                        ))}
                    </select>
                </div>
            </div>

            {/* Barre d'état & reset */}
            <div className="flex items-center justify-between pt-1 text-[11px] text-[var(--color-fg-muted)]">
                <span className="font-semibold">
                    {totalFound} employé(s) trouvé(s)
                </span>
                {hasActiveFilters && (
                    <button
                        onClick={onResetFilters}
                        className="text-[11px] text-[var(--color-brand-500)] hover:underline font-semibold flex items-center gap-1 cursor-pointer"
                    >
                        <RotateCcw size={12} />
                        Réinitialiser tous les filtres
                    </button>
                )}
            </div>
        </div>
    );
}
