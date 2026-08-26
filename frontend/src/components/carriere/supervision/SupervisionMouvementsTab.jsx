import { useState, useEffect, useCallback, memo } from 'react';
import { Search, ArrowRight, TrendingUp, RefreshCw, Layers } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import contratService from '@/api/employe/contratService';
import organisationService from '@/api/admin/organisationService';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Pagination from '@/components/ui/navigation/Pagination';
import { fmtDate, fmtAriary } from '@/components/dashboard/DashboardUtils';
import useDebounce from '@/hooks/useDebounce';

function SupervisionMouvementsTab() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [workflows, setWorkflows] = useState([]);
    const [totalElements, setTotalElements] = useState(0);

    // Filtres
    const [searchQuery, setSearchQuery] = useState('');
    const debouncedSearch = useDebounce(searchQuery, 350);
    const [selectedTypeEvenement, setSelectedTypeEvenement] = useState('ALL');
    const [selectedStatut, setSelectedStatut] = useState('ALL');
    const [selectedSite, setSelectedSite] = useState('');
    const [currentPage, setCurrentPage] = useState(1);
    const pageSize = 15;

    // Référentiels
    const [typesEvenements, setTypesEvenements] = useState([]);
    const [sites, setSites] = useState([]);

    useEffect(() => {
        Promise.all([
            contratService.getTypesEvenementRh().catch(() => []),
            organisationService.getSites().catch(() => [])
        ]).then(([evts, st]) => {
            setTypesEvenements(Array.isArray(evts) ? evts : []);
            setSites(Array.isArray(st) ? st : []);
        });
    }, []);

    const fetchWorkflows = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const params = {
                page: currentPage,
                size: pageSize,
                q: debouncedSearch.trim() || undefined,
                codeTypeEvenement: selectedTypeEvenement !== 'ALL' ? selectedTypeEvenement : undefined,
                codeStatut: selectedStatut !== 'ALL' ? selectedStatut : undefined,
                idSite: selectedSite ? Number(selectedSite) : undefined,
            };
            const data = await contratService.getWorkflowsGlobal(params);
            setWorkflows(data?.content || []);
            setTotalElements(data?.totalElements || 0);
        } catch (err) {
            console.error('Erreur chargement journal des mouvements carrières:', err);
            setError("Impossible de charger le journal des mouvements de carrière.");
            setWorkflows([]);
        } finally {
            setLoading(false);
        }
    }, [currentPage, pageSize, debouncedSearch, selectedTypeEvenement, selectedStatut, selectedSite]);

    useEffect(() => {
        fetchWorkflows();
    }, [fetchWorkflows]);

    const handleResetFilters = () => {
        setSearchQuery('');
        setSelectedTypeEvenement('ALL');
        setSelectedStatut('ALL');
        setSelectedSite('');
        setCurrentPage(1);
    };

    const getEventBadgeVariant = (code) => {
        switch (code) {
            case 'EMBAUCHE_INITIALE': return 'primary';
            case 'TITULARISATION':
            case 'CONFIRMATION_ESSAI': return 'success';
            case 'PROMOTION': return 'brand';
            case 'MUTATION': return 'info';
            case 'REVISION_SALAIRE': return 'warning';
            case 'RUPTURE':
            case 'FIN_CONTRAT': return 'danger';
            default: return 'default';
        }
    };

    const getStatutBadgeVariant = (code, dateEffet) => {
        if (code === 'VALIDE') {
            if (dateEffet && new Date(dateEffet) > new Date()) return 'primary'; // Planifié
            return 'success';
        }
        if (code === 'BROUILLON') return 'default';
        if (code === 'ANNULE') return 'danger';
        return 'default';
    };

    const hasActiveFilters = Boolean(searchQuery || selectedTypeEvenement !== 'ALL' || selectedStatut !== 'ALL' || selectedSite);

    return (
        <div className="space-y-4">
            {/* Barre de Filtres */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 flex flex-wrap items-center gap-3">
                {/* Recherche */}
                <div className="relative min-w-[240px] flex-1">
                    <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-fg-subtle)]" />
                    <input
                        type="text"
                        value={searchQuery}
                        onChange={(e) => { setSearchQuery(e.target.value); setCurrentPage(1); }}
                        placeholder="Recherche matricule, collaborateur, motif..."
                        className="w-full pl-9 pr-3 py-2 text-xs bg-[var(--color-bg)] border border-[var(--color-border)] focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)] placeholder:text-[var(--color-fg-subtle)]"
                    />
                </div>

                {/* Filtre Type d'Événement */}
                <select
                    value={selectedTypeEvenement}
                    onChange={(e) => { setSelectedTypeEvenement(e.target.value); setCurrentPage(1); }}
                    className="py-2 px-3 text-xs bg-[var(--color-bg)] border border-[var(--color-border)] focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)] min-w-[190px]"
                >
                    <option value="ALL">Tous les types d'évolution</option>
                    {typesEvenements.map((t, idx) => (
                        <option key={t.idTypeEvenementRh || t.code || idx} value={t.code}>{t.libelle}</option>
                    ))}
                </select>

                {/* Filtre Statut */}
                <select
                    value={selectedStatut}
                    onChange={(e) => { setSelectedStatut(e.target.value); setCurrentPage(1); }}
                    className="py-2 px-3 text-xs bg-[var(--color-bg)] border border-[var(--color-border)] focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)] min-w-[150px]"
                >
                    <option value="ALL">Tous les statuts</option>
                    <option value="ACTIF">Actif / Validé</option>
                    <option value="PLANIFIE">Planifié (effet futur)</option>
                    <option value="BROUILLON">Brouillon</option>
                    <option value="ANNULE">Annulé</option>
                </select>

                {/* Filtre Site */}
                <select
                    value={selectedSite}
                    onChange={(e) => { setSelectedSite(e.target.value); setCurrentPage(1); }}
                    className="py-2 px-3 text-xs bg-[var(--color-bg)] border border-[var(--color-border)] focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)] min-w-[150px]"
                >
                    <option value="">Tous les sites</option>
                    {sites.map((s, idx) => (
                        <option key={s.idSite || idx} value={s.idSite}>
                            {s.libelle || s.nomSite || s.codeSite || `Site #${s.idSite}`}{s.codeSite ? ` (${s.codeSite})` : ''}
                        </option>
                    ))}
                </select>

                <Button
                    variant="outline"
                    size="sm"
                    iconLeft={<RefreshCw size={14} className={loading ? 'animate-spin' : ''} />}
                    onClick={fetchWorkflows}
                >
                    Actualiser
                </Button>
            </div>

            {/* Ligne des Résultats & Reset */}
            <div className="flex items-center justify-between text-xs text-[var(--color-fg-muted)] px-1">
                <span>
                    <strong className="text-[var(--color-fg)]">{totalElements}</strong> mouvement(s) de carrière enregistré(s)
                </span>
                {hasActiveFilters && (
                    <button
                        type="button"
                        onClick={handleResetFilters}
                        className="text-xs font-bold text-[var(--color-brand-600)] hover:underline cursor-pointer"
                    >
                        Réinitialiser tous les filtres
                    </button>
                )}
            </div>

            {/* Erreur */}
            {error && <Alert variant="danger" title="Erreur">{error}</Alert>}

            {/* Skeleton */}
            {loading && <SkeletonTable rows={8} />}

            {/* État vide */}
            {!loading && !error && workflows.length === 0 && (
                <EmptyState
                    icon={Layers}
                    title="Aucun mouvement de carrière trouvé"
                    description="Aucun événement ne correspond aux critères de filtre sélectionnés."
                    action={
                        <Button variant="outline" size="sm" onClick={handleResetFilters}>
                            Réinitialiser les filtres
                        </Button>
                    }
                />
            )}

            {/* Table du Journal des Mouvements */}
            {!loading && !error && workflows.length > 0 && (
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] overflow-x-auto">
                    <table className="w-full text-xs text-left">
                        <thead className="bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] font-bold uppercase tracking-wider text-[10px] text-[var(--color-fg-muted)]">
                            <tr>
                                <th className="py-3 px-4">Date d'Effet</th>
                                <th className="py-3 px-4">Collaborateur</th>
                                <th className="py-3 px-4">Type de Mouvement</th>
                                <th className="py-3 px-4">Poste &amp; Affectation</th>
                                <th className="py-3 px-4">Rémunération</th>
                                <th className="py-3 px-4">Statut</th>
                                <th className="py-3 px-4 text-right">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-[var(--color-border-subtle)] text-[var(--color-fg)]">
                            {workflows.map((wf) => {
                                const isFuture = wf.dateEffet && new Date(wf.dateEffet) > new Date();
                                const isPlanifie = wf.codeStatutWorkflow === 'VALIDE' && isFuture;

                                return (
                                    <tr key={wf.idWorkflow} className="hover:bg-[var(--color-bg-subtle)]/50 transition-colors">
                                        {/* Date d'effet */}
                                        <td className="py-3.5 px-4 font-data">
                                            <div className="font-bold text-[var(--color-fg)]">
                                                {fmtDate(wf.dateEffet || wf.dateDemande)}
                                            </div>
                                            {isPlanifie && (
                                                <div className="text-[10px] font-bold text-[var(--color-brand-600)]">
                                                    Prise d'effet future
                                                </div>
                                            )}
                                        </td>

                                        {/* Collaborateur */}
                                        <td className="py-3.5 px-4">
                                            <div className="flex items-center gap-2.5">
                                                <div className="w-7 h-7 bg-[var(--color-fg)] text-[var(--color-surface)] font-bold flex items-center justify-center text-[11px] shrink-0">
                                                    {wf.nomPrenomEmploye?.charAt(0) || 'E'}
                                                </div>
                                                <div className="min-w-0">
                                                    <div className="font-extrabold text-[var(--color-fg)] truncate">
                                                        {wf.nomPrenomEmploye}
                                                    </div>
                                                    <div className="font-data text-[11px] text-[var(--color-fg-subtle)]">
                                                        #{wf.matriculeEmploye}
                                                    </div>
                                                </div>
                                            </div>
                                        </td>

                                        {/* Type d'événement */}
                                        <td className="py-3.5 px-4">
                                            <Badge variant={getEventBadgeVariant(wf.codeTypeEvenement)} size="xs">
                                                {wf.libelleTypeEvenement || wf.codeTypeEvenement}
                                            </Badge>
                                            {wf.motif && (
                                                <div className="text-[11px] text-[var(--color-fg-muted)] mt-0.5 truncate max-w-[180px]">
                                                    {wf.motif}
                                                </div>
                                            )}
                                        </td>

                                        {/* Poste & Affectation */}
                                        <td className="py-3.5 px-4">
                                            <div className="font-semibold text-[var(--color-fg)]">
                                                {wf.libelleFonction || 'Fonction inchangée'}
                                            </div>
                                            <div className="text-[11px] text-[var(--color-fg-muted)]">
                                                {wf.libelleSite || 'Site'} {wf.libelleDepartement ? `· ${wf.libelleDepartement}` : ''}
                                            </div>
                                        </td>

                                        {/* Rémunération */}
                                        <td className="py-3.5 px-4 font-data">
                                            {wf.salaireBase ? (
                                                <span className="font-bold text-[var(--color-fg)]">
                                                    {fmtAriary(wf.salaireBase)} Ar
                                                </span>
                                            ) : (
                                                <span className="text-[var(--color-fg-subtle)]">—</span>
                                            )}
                                        </td>

                                        {/* Statut */}
                                        <td className="py-3.5 px-4">
                                            <StatusBadge status={wf.codeStatutWorkflow} label={wf.libelleStatutWorkflow} size="xs" />
                                        </td>

                                        {/* Actions */}
                                        <td className="py-3.5 px-4 text-right">
                                            <Button
                                                variant="outline"
                                                size="xs"
                                                onClick={() => navigate(`/employes/${wf.idEmploye}?tab=carriere`)}
                                            >
                                                Fiche Carrière
                                            </Button>
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
            )}

            {/* Pagination */}
            {totalElements > pageSize && (
                <div className="p-4 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] flex justify-between items-center">
                    <span className="text-xs text-[var(--color-fg-muted)]">
                        Page {currentPage} sur {Math.ceil(totalElements / pageSize)}
                    </span>
                    <Pagination
                        currentPage={currentPage}
                        totalElements={totalElements}
                        pageSize={pageSize}
                        onPageChange={setCurrentPage}
                    />
                </div>
            )}
        </div>
    );
}

export default memo(SupervisionMouvementsTab);
