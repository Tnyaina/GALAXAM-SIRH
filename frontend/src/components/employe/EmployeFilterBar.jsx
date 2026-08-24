import { Search, RotateCcw, ArrowUpDown, Building, MapPin, Briefcase } from 'lucide-react';
import Input, { Select } from '@/components/ui/forms/Input';
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
}) {
    // Filtrer les services selon le département sélectionné (si applicable)
    const filteredServices = serviceFilter || departementFilter
        ? services.filter(s => !departementFilter || String(s.idDepartement || s.id_departement) === String(departementFilter))
        : services;

    const hasActiveFilters = searchQuery || statusFilter !== 'ALL' || siteFilter || departementFilter || serviceFilter;

    return (
        <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 shadow-sm p-4 space-y-4 font-sans">
            {/* Ligne 1 : Recherche principale + Statuts + Preset Tri */}
            <div className="flex flex-col lg:flex-row gap-3 justify-between items-stretch lg:items-center">
                {/* Recherche globale */}
                <div className="flex-1 min-w-[280px]">
                    <Input
                        type="text"
                        value={searchQuery}
                        onChange={(e) => onSearchChange(e.target.value)}
                        placeholder="Rechercher par nom, matricule, CIN, téléphone..."
                        icon={<Search size={18} />}
                    />
                </div>

                {/* Filtre Statut (Tous, Actifs, Inactifs) */}
                <div className="flex items-center gap-1 bg-ink-50 dark:bg-ink-950 border border-ink-200 dark:border-ink-700 p-1 shrink-0">
                    {[
                        { key: 'ALL', label: 'Tous' },
                        { key: 'ACTIVE', label: 'Actifs' },
                        { key: 'INACTIVE', label: 'Inactifs' },
                    ].map(f => (
                        <Button
                            key={f.key}
                            size="xs"
                            variant={statusFilter === f.key ? 'primary' : 'ghost'}
                            onClick={() => onStatusFilterChange(f.key)}
                        >
                            {f.label}
                        </Button>
                    ))}
                </div>

                {/* Preset de Tri logique / hiérarchique */}
                <div className="flex items-center gap-2 shrink-0">
                    <ArrowUpDown size={15} className="text-brand-500 shrink-0" />
                    <Select
                        value={sortPreset}
                        onChange={(e) => onSortPresetChange(e.target.value)}
                        className="text-xs py-1.5 font-semibold bg-ink-50 dark:bg-ink-950"
                    >
                        <option value="HIERARCHICAL">Tri : Département → Service → Nom</option>
                        <option value="SITE_SERVICE">Tri : Site → Service → Nom</option>
                        <option value="NOM_ASC">Tri : Nom (A-Z)</option>
                        <option value="NOM_DESC">Tri : Nom (Z-A)</option>
                        <option value="MATRICULE_ASC">Tri : Matricule (Croissant)</option>
                        <option value="MATRICULE_DESC">Tri : Matricule (Décroissant)</option>
                        <option value="ANCIENNETE_DESC">Tri : Ancienneté (Plus anciens)</option>
                        <option value="ANCIENNETE_ASC">Tri : Ancienneté (Plus récents)</option>
                    </Select>
                </div>
            </div>

            {/* Ligne 2 : Filtres d'organisation (Site, Département, Service) */}
            <div className="pt-3 border-t border-ink-100 dark:border-ink-800 grid grid-cols-1 sm:grid-cols-3 lg:grid-cols-4 gap-3 items-center">
                {/* Filtre Site */}
                <div className="flex items-center gap-2">
                    <MapPin size={14} className="text-ink-400 shrink-0" />
                    <Select
                        value={siteFilter}
                        onChange={(e) => onSiteFilterChange(e.target.value)}
                        className="text-xs py-1.5 w-full"
                    >
                        <option value="">Tous les sites / agences</option>
                        {sites.map(s => (
                            <option key={s.idSite || s.id_site} value={s.idSite || s.id_site}>
                                {s.nomSite || s.nom_site || s.libelle}
                            </option>
                        ))}
                    </Select>
                </div>

                {/* Filtre Département */}
                <div className="flex items-center gap-2">
                    <Building size={14} className="text-ink-400 shrink-0" />
                    <Select
                        value={departementFilter}
                        onChange={(e) => {
                            onDepartementFilterChange(e.target.value);
                            onServiceFilterChange(''); // Reset service si département change
                        }}
                        className="text-xs py-1.5 w-full"
                    >
                        <option value="">Tous les départements</option>
                        {departements.map(d => (
                            <option key={d.idDepartement || d.id_departement} value={d.idDepartement || d.id_departement}>
                                {d.nomDepartement || d.nom_departement || d.libelle}
                            </option>
                        ))}
                    </Select>
                </div>

                {/* Filtre Service */}
                <div className="flex items-center gap-2">
                    <Briefcase size={14} className="text-ink-400 shrink-0" />
                    <Select
                        value={serviceFilter}
                        onChange={(e) => onServiceFilterChange(e.target.value)}
                        className="text-xs py-1.5 w-full"
                    >
                        <option value="">Tous les services</option>
                        {filteredServices.map(srv => (
                            <option key={srv.idService || srv.id_service} value={srv.idService || srv.id_service}>
                                {srv.nomService || srv.nom_service || srv.libelle}
                            </option>
                        ))}
                    </Select>
                </div>

                {/* Bouton Réinitialiser si au moins un filtre est actif */}
                {hasActiveFilters && (
                    <div className="flex justify-end sm:col-span-3 lg:col-span-1">
                        <Button
                            variant="ghost"
                            size="xs"
                            onClick={onResetFilters}
                            iconLeft={<RotateCcw size={13} />}
                            className="text-danger-500 hover:text-danger-700 font-semibold"
                        >
                            Réinitialiser filtres
                        </Button>
                    </div>
                )}
            </div>
        </div>
    );
}
