import { useState, useEffect, useCallback, useMemo } from 'react';
import { Briefcase, RefreshCw, Download, FileText, TrendingUp } from 'lucide-react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import contratService from '@/api/employe/contratService';
import organisationService from '@/api/admin/organisationService';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Tabs from '@/components/ui/navigation/Tabs';
import Button from '@/components/ui/forms/Button';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Pagination from '@/components/ui/navigation/Pagination';
import SEO from '@/components/system/SEO';
import useDebounce from '@/hooks/useDebounce';

import SupervisionKpiCards from '@/components/carriere/supervision/SupervisionKpiCards';
import SupervisionAlertsPanel from '@/components/carriere/supervision/SupervisionAlertsPanel';
import SupervisionFilterBar from '@/components/carriere/supervision/SupervisionFilterBar';
import SupervisionEmployeeGroup from '@/components/carriere/supervision/SupervisionEmployeeGroup';
import SupervisionCompareModal from '@/components/carriere/supervision/SupervisionCompareModal';
import SupervisionLegend from '@/components/carriere/supervision/SupervisionLegend';
import SupervisionMouvementsTab from '@/components/carriere/supervision/SupervisionMouvementsTab';

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
    const [stats, setStats] = useState({
        totalContrats: 0,
        contratsActifs: 0,
        enAttenteSignature: 0,
        echeances60Jours: 0,
        doublonsPotentiels: 0
    });
    const [alertesPrioritaires, setAlertesPrioritaires] = useState([]);

    // Filtres Contrats
    const [searchQuery, setSearchQuery] = useState('');
    const debouncedSearch = useDebounce(searchQuery, 350);
    const [selectedSite, setSelectedSite] = useState('');
    const [selectedStatut, setSelectedStatut] = useState('ALL');
    const [selectedEcheance, setSelectedEcheance] = useState('');
    const [doublonsOnly, setDoublonsOnly] = useState(false);
    const [currentPage, setCurrentPage] = useState(1);
    const pageSize = 15;

    // État d'accordéon (groupes pliés/dépliés)
    const [collapsedGroups, setCollapsedGroups] = useState(new Set());

    // Modale de comparaison des doublons
    const [compareModalOpen, setCompareModalOpen] = useState(false);
    const [selectedEmployeeToCompare, setSelectedEmployeeToCompare] = useState(null);

    // Référentiels
    const [sites, setSites] = useState([]);

    useEffect(() => {
        organisationService.getSites()
            .then(data => setSites(Array.isArray(data) ? data : []))
            .catch(err => console.error('Erreur chargement sites:', err));
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
        } catch (err) {
            console.error('Erreur chargement contrats global:', err);
            setError("Impossible de charger le registre des contrats et carrières.");
            setContrats([]);
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
        setCollapsedGroups((prev) => {
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

    const handleExportCsv = () => {
        const url = `${import.meta.env.VITE_API_URL || 'http://localhost:8080/api'}/contrats/export`;
        window.open(url, '_blank');
    };

    const handleOpenCompare = (empGroup) => {
        setSelectedEmployeeToCompare(empGroup);
        setCompareModalOpen(true);
    };

    const hasActiveFilters = Boolean(
        searchQuery || selectedSite || selectedStatut !== 'ALL' || selectedEcheance || doublonsOnly
    );

    const tabsList = [
        {
            id: 'contrats',
            label: 'Registre des Contrats & Échéances',
            icon: FileText,
            badge: stats.totalContrats || undefined
        },
        {
            id: 'mouvements',
            label: 'Journal des Mouvements & Évolutions',
            icon: TrendingUp
        }
    ];

    return (
        <div className="space-y-6 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans pb-16">
            <SEO
                title="Supervision Contrats & Carrières | GALAXAM SIRH"
                description="Pilotage centralisé des contrats, périodes d'essai, mouvements et évolutions de carrière."
            />

            {/* En-tête Format Standard Galaxam */}
            <PageHeader
                title="Supervision Globale des Contrats & Carrières"
                subtitle="Pilotage centralisé des contrats de travail, des périodes d'essai, des évolutions et mouvements de carrière."
                icon={Briefcase}
                backLink="/employes"
                backLabel="Retour au répertoire du personnel"
                actions={
                    <>
                        <Button
                            variant="outline"
                            size="sm"
                            iconLeft={<Download size={14} />}
                            onClick={handleExportCsv}
                        >
                            Exporter
                        </Button>
                        <Button
                            variant="outline"
                            size="sm"
                            iconLeft={<RefreshCw size={14} className={loading ? 'animate-spin' : ''} />}
                            onClick={() => { fetchStatsAndAlerts(); fetchContrats(); }}
                        >
                            Actualiser
                        </Button>
                    </>
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
                <div className="space-y-6">
                    {/* Notifications d'Erreur */}
                    {error && <Alert type="error" onClose={() => setError(null)}>{error}</Alert>}

                    {/* Bandeau KPI Cliquables (Filtres Rapides) */}
                    <SupervisionKpiCards
                        stats={stats}
                        selectedStatut={selectedStatut}
                        selectedEcheance={selectedEcheance}
                        doublonsOnly={doublonsOnly}
                        onFilterClick={handleKpiFilterClick}
                    />

                    {/* Alertes Prioritaires (« À TRAITER EN PRIORITÉ ») */}
                    <SupervisionAlertsPanel
                        alertes={alertesPrioritaires}
                        onActionClick={(url) => navigate(url)}
                        onVerifyDoublons={() => { setDoublonsOnly(true); setCurrentPage(1); }}
                    />

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
                        totalCollaborateurs={groupedByEmployee.length}
                        totalContrats={totalElements}
                        onResetFilters={handleResetFilters}
                        hasActiveFilters={hasActiveFilters}
                    />

                    {/* Chargement Skeleton */}
                    {loading && <SkeletonTable rows={8} />}

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
                                    isCollapsed={collapsedGroups.has(group.key)}
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

                    {/* Légende Repère Couleur */}
                    <SupervisionLegend />

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
                <SupervisionMouvementsTab />
            )}
        </div>
    );
}