import { useState, useEffect, useCallback, memo } from 'react';
import { Search, RefreshCw, Layers, ChevronRight } from 'lucide-react';
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
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { Avatar } from '@/components/ui/data-display/Avatar';
import { fmtDate, fmtAriary } from '@/components/dashboard/DashboardUtils';
import HasPermission from '@/components/auth/HasPermission';
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
            {error && <Alert type="error" title="Erreur">{error}</Alert>}

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
                <Table>
                    <Thead>
                        <Tr>
                            <Th>Date d'Effet</Th>
                            <Th>Collaborateur</Th>
                            <Th>Type de Mouvement</Th>
                            <Th>Type &amp; N° Contrat</Th>
                            <Th>Poste &amp; Affectation</Th>
                            <Th>Rémunération</Th>
                            <Th>Statut</Th>
                            <Th align="right">Action</Th>
                        </Tr>
                    </Thead>
                    <Tbody>
                        {workflows.map((wf) => {
                            const isFuture = wf.dateEffet && new Date(wf.dateEffet) > new Date();
                            const isAnnule = Boolean(wf.isAnnule);
                            const isPlanifie = Boolean(wf.isPlanifie) || (isFuture && !isAnnule);
                            const motifAnnulation = wf.motif || wf.commentaire;

                            return (
                                <Tr
                                    key={wf.idWorkflow}
                                    onClick={() => navigate(`/employes/${wf.idEmploye}?tab=carriere${wf.idWorkflow ? `&wf=${wf.idWorkflow}` : ''}`)}
                                    className="cursor-pointer group"
                                    title="Cliquer pour ouvrir la fiche carrière du collaborateur"
                                >
                                    {/* Date d'effet */}
                                    <Td className="font-data">
                                        <div className="font-bold text-[var(--color-fg)]">
                                            {fmtDate(wf.dateEffet || wf.dateDemande)}
                                        </div>
                                        {isPlanifie && (
                                            <div className="text-[10px] font-bold text-[var(--color-brand-600)]">
                                                Prise d'effet future
                                            </div>
                                        )}
                                    </Td>

                                    {/* Collaborateur */}
                                    <Td>
                                        <div className="flex items-center gap-2.5">
                                            <Avatar name={wf.nomPrenomEmploye} size="sm" shape="square" />
                                            <div className="min-w-0">
                                                <div className="font-extrabold text-[var(--color-fg)] group-hover:text-brand-500 transition-colors truncate">
                                                    {wf.nomPrenomEmploye}
                                                </div>
                                                <div className="font-data text-[11px] text-[var(--color-fg-subtle)]">
                                                    #{wf.matriculeEmploye}
                                                </div>
                                            </div>
                                        </div>
                                    </Td>

                                    {/* Type d'événement */}
                                    <Td>
                                        <Badge variant={getEventBadgeVariant(wf.codeTypeEvenement)} size="xs">
                                            {wf.libelleTypeEvenement || wf.codeTypeEvenement}
                                        </Badge>
                                        {wf.motif && !isAnnule && (
                                            <div className="text-[11px] text-[var(--color-fg-muted)] mt-0.5 truncate max-w-[180px]">
                                                {wf.motif}
                                            </div>
                                        )}
                                    </Td>

                                    {/* Type & N° Contrat */}
                                    <Td>
                                        <div className="flex items-center gap-1.5 flex-wrap">
                                            {wf.libelleTypeContrat && (
                                                <Badge variant={getEventBadgeVariant(wf.libelleTypeContrat)} size="xs">
                                                    {wf.libelleTypeContrat}
                                                </Badge>
                                            )}
                                            <span className="font-mono font-bold text-[var(--color-brand-600)]">
                                                {wf.numeroContratCible || wf.numeroContratSource || '—'}
                                            </span>
                                        </div>
                                    </Td>

                                    {/* Poste & Affectation */}
                                    <Td>
                                        <div className="font-semibold text-[var(--color-fg)]">
                                            {wf.libelleFonction || 'Fonction inchangée'}
                                        </div>
                                        <div className="text-[11px] text-[var(--color-fg-muted)]">
                                            {wf.libelleSite || 'Site'} {wf.libelleDepartement ? `· ${wf.libelleDepartement}` : ''}
                                        </div>
                                    </Td>

                                    {/* Rémunération (protégée par PAIE_READ) */}
                                    <Td className="font-data">
                                        <HasPermission authority="PAIE_READ" fallback={<span className="text-[var(--color-fg-subtle)] text-[11px] ">Confidentiel</span>}>
                                            {wf.salaireBase ? (
                                                <span className="font-bold text-[var(--color-fg)]">
                                                    {fmtAriary(wf.salaireBase)} Ar
                                                </span>
                                            ) : (
                                                <span className="text-[var(--color-fg-subtle)]">—</span>
                                            )}
                                        </HasPermission>
                                    </Td>

                                    {/* Statut */}
                                    <Td>
                                        <StatusBadge status={wf.codeStatutWorkflow} label={wf.libelleStatutWorkflow} size="xs" />
                                        {isAnnule && motifAnnulation && (
                                            <div className="text-[10px] text-danger-500 font-normal mt-0.5 max-w-[180px] truncate" title={`Motif : ${motifAnnulation}`}>
                                                Motif : {motifAnnulation}
                                            </div>
                                        )}
                                    </Td>

                                    {/* Actions */}
                                    <Td align="right">
                                        <span className="text-xs font-semibold text-[var(--color-brand-600)] group-hover:text-[var(--color-brand-700)] inline-flex items-center gap-0.5">
                                            Carrière <ChevronRight size={14} className="group-hover:translate-x-0.5 transition-transform" />
                                        </span>
                                    </Td>
                                </Tr>
                            );
                        })}
                    </Tbody>
                </Table>
            )}

            {/* Pagination */}
            {totalElements > pageSize && (
                <div className="border border-[var(--color-border-subtle)]">
                    <Pagination
                        currentPage={currentPage}
                        totalItems={totalElements}
                        pageSize={pageSize}
                        onPageChange={setCurrentPage}
                    />
                </div>
            )}
        </div>
    );
}

export default memo(SupervisionMouvementsTab);
