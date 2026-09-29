import { Suspense, useState, useEffect, useCallback, useMemo } from 'react';
import { getErrorMessage } from '@/api/apiClient';
import { Briefcase, RefreshCw, Download, FileText, TrendingUp } from 'lucide-react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import contratService from '@/features/employees/api/contratService';
import organisationService from '@/features/organization/api/organisationService';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Tabs from '@/components/ui/navigation/Tabs';
import Button from '@/components/ui/forms/Button';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Pagination from '@/components/ui/navigation/Pagination';
import SEO from '@/components/system/SEO';
import Loading from '@/components/ui/feedback/Loading';
import useDebounce from '@/hooks/useDebounce';

import SupervisionAlertsPanel from '@/features/employees/components/carriere/supervision/SupervisionAlertsPanel';
import SupervisionFilterBar from '@/features/employees/components/carriere/supervision/SupervisionFilterBar';
import SupervisionEmployeeGroup from '@/features/employees/components/carriere/supervision/SupervisionEmployeeGroup';
import SupervisionCompareModal from '@/features/employees/components/carriere/supervision/SupervisionCompareModal';

import SupervisionMouvementsTab from '@/features/employees/components/carriere/supervision/SupervisionMouvementsTab';

const VALID_MAIN_TABS = ['contrats', 'mouvements'];

export default function CarrieresSupervisionPage() {
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();

    // Onglet principal synchronisé avec l'URL (?tab=contrats | ?tab=mouvements)
    const tabFromUrl = searchParams.get('tab');
    const activeMainTab = VALID_MAIN_TABS.includes(tabFromUrl) ? tabFromUrl : 'contrats';

    const handleMainTabChange = (newTab) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('tab', newTab);
            return next;
        });
    };

    // Données Contrats
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [contrats, setContrats] = useState([]);
    const [totalElements, setTotalElements] = useState(0);
    const [totalContratsFiltres, setTotalContratsFiltres] = useState(0);
    const [stats, setStats] = useState({
        totalContrats: 0,
        contratsActifs: 0,
        enAttenteSignature: 0,
        echeances60Jours: 0,
        doublonsPotentiels: 0
    });
    const [alertesPrioritaires, setAlertesPrioritaires] = useState([]);

    // Filtres Contrats initialisés depuis l'URL si présents
    const [searchQuery, setSearchQuery] = useState(searchParams.get('q') || '');
    const debouncedSearch = useDebounce(searchQuery, 350);
    const [selectedSite, setSelectedSite] = useState(searchParams.get('site') || '');
    const [selectedStatut, setSelectedStatut] = useState(searchParams.get('statut') || 'ALL');
    const [selectedEcheance, setSelectedEcheance] = useState(searchParams.get('echeance') || '');
    const [doublonsOnly, setDoublonsOnly] = useState(searchParams.get('doublonsOnly') === 'true');
    const [currentPage, setCurrentPage] = useState(Number(searchParams.get('page')) || 1);
    const pageSize = 15;

    // État d'accordéon (groupes pliés/dépliés)
    const [expandedGroups, setExpandedGroups] = useState(new Set());

    // Modale de comparaison des doublons
    const [compareModalOpen, setCompareModalOpen] = useState(false);
    const [selectedEmployeeToCompare, setSelectedEmployeeToCompare] = useState(null);

    // Référentiels
    const [sites, setSites] = useState([]);
    const [contractStatuses, setContractStatuses] = useState([]);
    const [contractDeadlines, setContractDeadlines] = useState([]);

    useEffect(() => {
        Promise.all([organisationService.getSites(), contratService.getFiltresContrats()])
            .then(([siteList, filters]) => {
                setSites(Array.isArray(siteList) ? siteList : []);
                setContractStatuses(Array.isArray(filters?.statuts) ? filters.statuts : []);
                setContractDeadlines(Array.isArray(filters?.echeances) ? filters.echeances : []);
            })
            .catch(() => setError('Référentiels de recherche indisponibles.'));
    }, []);

    const fetchStatsAndAlerts = useCallback(async () => {
        try {
            const [st, al] = await Promise.all([
                contratService.getStatsSupervision().catch(() => ({})),
                contratService.getAlertesPrioritaires().catch(() => ([]))
            ]);
            if (st) setStats(st);
            if (Array.isArray(al)) setAlertesPrioritaires(al);
        } catch (err) {
            console.error('Erreur chargement stats/alertes:', err);
        }
    }, []);

    const fetchContrats = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const params = {
                page: currentPage,
                size: pageSize,
                q: debouncedSearch.trim() || undefined,
                idSite: selectedSite ? Number(selectedSite) : undefined,
                statut: selectedStatut !== 'ALL' ? selectedStatut : undefined,
                echeance: selectedEcheance || undefined,
                doublonsOnly: doublonsOnly ? true : undefined,
            };
            const data = await contratService.getContratsGlobal(params);
            setContrats(data?.content || []);
            setTotalElements(data?.totalElements || 0);
            setTotalContratsFiltres(data?.totalContrats || 0);
        } catch (err) {
            console.error('Erreur chargement contrats global:', err);
            setError("Impossible de charger le registre des contrats et carrières.");
            setContrats([]);
            setTotalContratsFiltres(0);
        } finally {
            setLoading(false);
        }
    }, [currentPage, pageSize, debouncedSearch, selectedSite, selectedStatut, selectedEcheance, doublonsOnly]);

    useEffect(() => {
        fetchStatsAndAlerts();
    }, [fetchStatsAndAlerts]);

    useEffect(() => {
        if (activeMainTab === 'contrats') {
            fetchContrats();
        }
    }, [fetchContrats, activeMainTab]);

    // Synchronisation des filtres actifs avec les paramètres URL
    useEffect(() => {
        const next = new URLSearchParams(searchParams);
        if (selectedStatut && selectedStatut !== 'ALL') next.set('statut', selectedStatut);
        else next.delete('statut');
        if (selectedEcheance) next.set('echeance', selectedEcheance);
        else next.delete('echeance');
        if (doublonsOnly) next.set('doublonsOnly', 'true');
        else next.delete('doublonsOnly');
        if (selectedSite) next.set('site', selectedSite);
        else next.delete('site');
        if (debouncedSearch.trim()) next.set('q', debouncedSearch.trim());
        else next.delete('q');
        if (currentPage > 1) next.set('page', String(currentPage));
        else next.delete('page');

        if (next.toString() !== searchParams.toString()) {
            setSearchParams(next, { replace: true });
        }
    }, [selectedStatut, selectedEcheance, doublonsOnly, selectedSite, debouncedSearch, currentPage, searchParams, setSearchParams]);

    // Regroupement des contrats par collaborateur
    const groupedByEmployee = useMemo(() => {
        const groups = new Map();
        contrats.forEach((c) => {
            const key = `emp-${c.idEmploye}`;
            if (!groups.has(key)) {
                groups.set(key, {
                    key,
                    idEmploye: c.idEmploye,
                    matricule: c.matricule,
                    nomPrenom: c.nomPrenom,
                    nomSite: c.nomSite,
                    nomFonction: c.nomFonction,
                    contrats: [],
                });
            }
            groups.get(key).contrats.push(c);
        });
        return Array.from(groups.values());
    }, [contrats]);

    const toggleGroupCollapse = (key) => {
        setExpandedGroups((prev) => {
            const next = new Set(prev);
            if (next.has(key)) next.delete(key);
            else next.add(key);
            return next;
        });
    };

    const handleKpiFilterClick = ({ statut, doublons, echeance }) => {
        setSelectedStatut(statut);
        setDoublonsOnly(doublons);
        setSelectedEcheance(echeance);
        setCurrentPage(1);
    };

    const handleResetFilters = () => {
        setSearchQuery('');
        setSelectedSite('');
        setSelectedStatut('ALL');
        setSelectedEcheance('');
        setDoublonsOnly(false);
        setCurrentPage(1);
    };

    const handleExportCsv = async () => {
        try {
            const blob = await contratService.exporterContratsCsv();
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = `registre_contrats_${new Date().toISOString().slice(0, 10)}.csv`;
            document.body.appendChild(link);
            link.click();
            link.remove();
            URL.revokeObjectURL(url);
        } catch (exportError) {
            setError(getErrorMessage(exportError, 'Export des contrats impossible.'));
        }
    };

    const handleOpenCompare = async (empGroup) => {
        try {
            const fullHistory = [];
            let page = 1;
            let totalPages = 1;
            do {
                const data = await contratService.getContratsGlobal({ idEmploye: empGroup.idEmploye, page, size: 50 });
                fullHistory.push(...(data?.content || []));
                totalPages = data?.totalPages || 1;
                page += 1;
            } while (page <= totalPages);
            setSelectedEmployeeToCompare({ ...empGroup, contrats: fullHistory });
            setCompareModalOpen(true);
        } catch {
            setError('Impossible de charger tous les contrats du salarié pour la comparaison.');
        }
    };

    const hasActiveFilters = Boolean(
        searchQuery || selectedSite || selectedStatut !== 'ALL' || selectedEcheance || doublonsOnly
    );

    const tabsList = [
        {
            id: 'contrats',
            label: 'Contrats',
            icon: FileText,
            badge: stats.totalContrats || undefined
        },
        {
            id: 'mouvements',
            label: 'Mouvements',
            icon: TrendingUp
        }
    ];

    return (
        <div className="space-y-3.5 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans pb-16">
            <SEO
                title="Supervision Contrats & Carrières | GALAXAM SIRH"
                description="Pilotage centralisé des contrats, périodes d'essai, mouvements et évolutions de carrière."
            />

            {/* En-tête Format Standard Galaxam */}
            <PageHeader
                title="Contrats et carrières"
                subtitle="Contrats, échéances et mouvements RH des collaborateurs."
                icon={Briefcase}
                breadcrumbs={[
                    { label: 'Tableau de bord', href: '/dashboard' },
                    { label: 'Collaborateurs', href: '/employes' },
                    { label: 'Supervision des Carrières' }
                ]}
                actions={
                    activeMainTab === 'contrats' ? <>
                        <Button
                            variant="outline"
                            size="sm"
                            iconLeft={<Download size={14} />}
                            onClick={handleExportCsv}
                        >
                            Exporter tous les contrats
                        </Button>
                        <Button
                            variant="outline"
                            size="sm"
                            iconLeft={<RefreshCw size={14} className={loading ? 'animate-spin' : ''} />}
                            onClick={() => { fetchStatsAndAlerts(); fetchContrats(); }}
                        >
                            Actualiser
                        </Button>
                    </> : null
                }
            />

            {/* Navigation par Onglets avec persistance URL (?tab=contrats | ?tab=mouvements) */}
            <Tabs
                tabs={tabsList}
                activeTab={activeMainTab}
                onChange={handleMainTabChange}
                variant="underline"
            />

            {/* ONGLET 1 : REGISTRE DES CONTRATS & ÉCHÉANCES */}
            {activeMainTab === 'contrats' && (
                <div className="space-y-3.5">
                    {/* Notifications d'Erreur */}
                    {error && <Alert type="error" onClose={() => setError(null)}>{error}</Alert>}

                    {/* Repères compacts : les chiffres globaux restent distincts des résultats filtrés. */}
                    <p className="text-[11px] font-semibold uppercase tracking-wider text-[var(--color-fg-muted)]">Repères globaux · cliquez pour filtrer</p>
                    <div className="flex flex-wrap gap-2" aria-label="Filtres rapides des contrats">
                        {[
                            { label: 'Tous', value: stats.totalContrats, active: selectedStatut === 'ALL' && !doublonsOnly && !selectedEcheance, filter: { statut: 'ALL', doublons: false, echeance: '' } },
                            { label: 'Actifs', value: stats.contratsActifs, active: selectedStatut === 'ACTIF' && !doublonsOnly && !selectedEcheance, filter: { statut: 'ACTIF', doublons: false, echeance: '' } },
                            { label: 'À préparer', value: stats.enAttenteSignature, active: selectedStatut === 'PENDING' && !doublonsOnly, filter: { statut: 'PENDING', doublons: false, echeance: '' } },
                            { label: 'Échéance < 60 j', value: stats.echeances60Jours, active: selectedEcheance === '60J', filter: { statut: 'ALL', doublons: false, echeance: '60J' } },
                            { label: 'Doublons à vérifier', value: stats.doublonsPotentiels, active: doublonsOnly, filter: { statut: 'ALL', doublons: true, echeance: '' } },
                        ].map(item => (
                            <Button key={item.label} type="button" size="xs" variant={item.active ? 'primary' : 'outline'}
                                aria-pressed={item.active} onClick={() => handleKpiFilterClick(item.filter)}>
                                {item.label} <strong className="ml-1 font-data">{item.value ?? 0}</strong>
                            </Button>
                        ))}
                    </div>

                    {/* Barre de Filtres Complète */}
                    <SupervisionFilterBar
                        searchQuery={searchQuery}
                        onSearchChange={(q) => { setSearchQuery(q); setCurrentPage(1); }}
                        selectedSite={selectedSite}
                        onSiteChange={(site) => { setSelectedSite(site); setCurrentPage(1); }}
                        selectedStatut={selectedStatut}
                        onStatutChange={(st) => { setSelectedStatut(st); setCurrentPage(1); }}
                        selectedEcheance={selectedEcheance}
                        onEcheanceChange={(ech) => { setSelectedEcheance(ech); setCurrentPage(1); }}
                        doublonsOnly={doublonsOnly}
                        onDoublonsToggle={(val) => { setDoublonsOnly(val); setCurrentPage(1); }}
                        sites={sites}
                        statuses={contractStatuses}
                        deadlines={contractDeadlines}
                        totalCollaborateurs={totalElements}
                        totalContrats={totalContratsFiltres}
                        onResetFilters={handleResetFilters}
                        hasActiveFilters={hasActiveFilters}
                    />

                    <SupervisionAlertsPanel
                        alertes={alertesPrioritaires}
                        onActionClick={(url) => navigate(url)}
                        onVerifyDoublons={() => { setDoublonsOnly(true); setCurrentPage(1); }}
                    />

                    {/* Chargement Skeleton */}
                    {loading && <SkeletonTable rows={8} columns={6} />}

                    {/* État vide */}
                    {!loading && !error && contrats.length === 0 && (
                        <EmptyState
                            icon={Briefcase}
                            title="Aucun contrat correspondant"
                            description="Modifiez vos critères de recherche ou réinitialisez les filtres pour afficher l'ensemble des contrats."
                            action={
                                <Button variant="outline" size="sm" onClick={handleResetFilters}>
                                    Réinitialiser les filtres
                                </Button>
                            }
                        />
                    )}

                    {/* Liste Groupée par Collaborateur */}
                    {!loading && !error && contrats.length > 0 && (
                        <div className="space-y-3.5">
                            {groupedByEmployee.map((group) => (
                                <SupervisionEmployeeGroup
                                    key={group.key}
                                    group={group}
                                    isCollapsed={!expandedGroups.has(group.key)}
                                    onToggleCollapse={() => toggleGroupCollapse(group.key)}
                                    onOpenCompare={handleOpenCompare}
                                    onNavigateCarriere={(idEmp) => navigate(`/employes/${idEmp}?tab=carriere`)}
                                />
                            ))}
                        </div>
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

                    {/* Légende locale : elle n'est utile que sur cet écran de supervision. */}
                    <div className="flex items-center gap-4 flex-wrap text-xs text-[var(--color-fg-muted)] px-1 pt-2">
                        <span className="font-extrabold uppercase tracking-wider text-[var(--color-fg)]">
                            Repère couleur :
                        </span>
                        <span className="inline-flex items-center gap-1.5">
                            <span className="w-3 h-1 bg-[var(--color-brand-500)] inline-block" />
                            En attente de signature
                        </span>
                        <span className="inline-flex items-center gap-1.5">
                            <span className="w-3 h-1 bg-[var(--color-warning-500)] inline-block" />
                            Échéance proche (&lt; 60 j)
                        </span>
                        <span className="inline-flex items-center gap-1.5">
                            <span className="w-3 h-1 bg-[var(--color-danger-500)] inline-block" />
                            Doublon potentiel à vérifier
                        </span>
                    </div>

                    {/* Modale de Comparaison des Doublons */}
                    <SupervisionCompareModal
                        isOpen={compareModalOpen}
                        onClose={() => setCompareModalOpen(false)}
                        employeeGroup={selectedEmployeeToCompare}
                        onNavigateCarriere={(idEmp) => navigate(`/employes/${idEmp}?tab=carriere`)}
                    />
                </div>
            )}

            {/* ONGLET 2 : JOURNAL DES MOUVEMENTS & ÉVOLUTIONS CARRIÈRE */}
            {activeMainTab === 'mouvements' && (
                <Suspense fallback={<Loading display="page" label="Chargement des mouvements…" />}>
                    <SupervisionMouvementsTab />
                </Suspense>
            )}
        </div>
    );
}
