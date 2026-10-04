import { Suspense, useState, useEffect, useCallback, useMemo } from 'react';
import { getErrorMessage } from '@/api/apiClient';
import { Briefcase, RefreshCw, Download, FileText, TrendingUp, Clock, AlertTriangle, FileCheck, CalendarClock, FileEdit } from 'lucide-react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import contratService from '@/features/employees/api/contratService';
import organisationService from '@/features/organization/api/organisationService';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Tabs from '@/components/ui/navigation/Tabs';
import Button from '@/components/ui/forms/Button';
import StatCard from '@/components/ui/data-display/StatCard';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';
import { SkeletonTable, SkeletonStatCard } from '@/components/ui/feedback/Skeleton';
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
        essaisEnCours: 0,
        enAttenteSignature: 0,
        echeances60Jours: 0,
        doublonsPotentiels: 0
    });
    const [statsLoading, setStatsLoading] = useState(true);
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
            setStatsLoading(true);
            const [st, al] = await Promise.all([
                contratService.getStatsSupervision().catch(() => ({})),
                contratService.getAlertesPrioritaires().catch(() => ([]))
            ]);
            if (st) setStats(st);
            if (Array.isArray(al)) setAlertesPrioritaires(al);
        } catch (err) {
            console.error('Erreur chargement stats/alertes:', err);
        } finally {
            setStatsLoading(false);
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
            label: 'Registre & Échéances',
            icon: FileText,
            badge: stats.totalContrats || undefined
        },
        {
            id: 'mouvements',
            label: 'Journal des Mouvements',
            icon: TrendingUp
        }
    ];

    return (
        <div className="space-y-3.5 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans pb-16">
            <SEO
                title="Supervision Contrats & Carrières | GALAXAM GRH"
                description="Pilotage centralisé des contrats, périodes d'essai, mouvements et évolutions de carrière."
            />

            {/* En-tête Format Standard Galaxam */}
            <PageHeader
                title="Tableau de bord Contrats & Carrières"
                subtitle="Pilotage centralisé, suivi des échéances, périodes d'essai et mouvements RH des collaborateurs."
                icon={Briefcase}
                breadcrumbs={[
                    { label: 'Tableau de bord', href: '/dashboard' },
                    { label: 'Collaborateurs', href: '/employes' },
                    { label: 'Contrats & Carrières' }
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

                    {/* Grille des Indicateurs Clés / Dashboard KPIs interactifs */}
                    <div className="grid grid-cols-2 lg:grid-cols-5 gap-3.5" aria-label="Indicateurs clés et filtres rapides">
                        {statsLoading ? (
                            Array.from({ length: 5 }).map((_, i) => <SkeletonStatCard key={i} />)
                        ) : (
                            <>
                                <StatCard
                                    title="Contrats Actifs"
                                    value={stats.contratsActifs ?? 0}
                                    subtext={`Sur ${stats.totalContrats ?? 0} au registre`}
                                    icon={<FileCheck size={18} />}
                                    color="brand"
                                    borderTop
                                    active={selectedStatut === 'ACTIF' && !doublonsOnly && !selectedEcheance}
                                    onClick={() => handleKpiFilterClick({ statut: 'ACTIF', doublons: false, echeance: '' })}
                                />
                                <StatCard
                                    title="Périodes d'essai"
                                    value={stats.essaisEnCours ?? 0}
                                    subtext="En cours de titularisation"
                                    icon={<Clock size={18} />}
                                    color="info"
                                    borderTop
                                    active={selectedEcheance === 'ESSAI'}
                                    onClick={() => handleKpiFilterClick({ statut: 'ALL', doublons: false, echeance: 'ESSAI' })}
                                />
                                <StatCard
                                    title="Échéances < 60j"
                                    value={stats.echeances60Jours ?? 0}
                                    subtext="Fins de CDD & préavis"
                                    icon={<CalendarClock size={18} />}
                                    color={stats.echeances60Jours > 0 ? 'warning' : 'brand'}
                                    borderTop
                                    active={selectedEcheance === '60J'}
                                    onClick={() => handleKpiFilterClick({ statut: 'ALL', doublons: false, echeance: '60J' })}
                                />
                                <StatCard
                                    title="À préparer / Visa"
                                    value={stats.enAttenteSignature ?? 0}
                                    subtext="Brouillons & signatures"
                                    icon={<FileEdit size={18} />}
                                    color={stats.enAttenteSignature > 0 ? 'warning' : 'neutral'}
                                    borderTop
                                    active={selectedStatut === 'PENDING' && !doublonsOnly}
                                    onClick={() => handleKpiFilterClick({ statut: 'PENDING', doublons: false, echeance: '' })}
                                />
                                <StatCard
                                    title="Doublons & Alertes"
                                    value={stats.doublonsPotentiels ?? 0}
                                    subtext={stats.doublonsPotentiels > 0 ? 'À vérifier en priorité' : 'Aucun doublon détecté'}
                                    icon={<AlertTriangle size={18} />}
                                    color={stats.doublonsPotentiels > 0 ? 'danger' : 'neutral'}
                                    borderTop
                                    active={doublonsOnly}
                                    onClick={() => handleKpiFilterClick({ statut: 'ALL', doublons: true, echeance: '' })}
                                />
                            </>
                        )}
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
