import { useState, useEffect, useMemo, lazy, Suspense } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import {
    Clock,
    AlertTriangle,
    CheckCircle2,
    Calendar,
    Search,
    Shield,
    Building2,
    Layers,
    ArrowRight,
    ChevronDown,
    ChevronUp
} from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import SEO from '@/components/system/SEO';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/ui/feedback/Toast';
import { Avatar } from '@/components/ui/data-display/Avatar';
import Badge from '@/components/ui/data-display/Badge';
import StatCard from '@/components/ui/data-display/StatCard';
import { fmtDate } from '@/utils/dateUtils';
import organisationService from '@/features/organization/api/organisationService';
import { periodePaieService, pointageSyntheseService } from '@/features/attendance/api/attendanceApi';
import usePointageAnomalies from '@/features/attendance/hooks/usePointageAnomalies';
import Pagination from '@/components/ui/navigation/Pagination';

const AnomalieRegularisationModal = lazy(() => import('@/features/attendance/components/modals/AnomalieRegularisationModal'));

const SEVERITY_PRESENTATION = {
    CRITIQUE: { label: 'Critique', badge: 'danger', border: 'border-l-[var(--color-danger-500)]' },
    HAUTE: { label: 'Élevée', badge: 'warning', border: 'border-l-[var(--color-warning-500)]' },
    MOYENNE: { label: 'Moyenne', badge: 'info', border: 'border-l-[var(--color-info-500)]' },
    FAIBLE: { label: 'Faible', badge: 'neutral', border: 'border-l-[var(--color-border)]' },
};

export default function SuiviAnomaliesPage({ activePeriode, onSelectPeriode, onTabChange, onAnomaliesCountChange, periodesDisponibles = null, sitesDisponibles = null }) {
    // Contexte, navigation et hook métier
    const [searchParams, setSearchParams] = useSearchParams();
    const { hasPermission } = useAuth();
    const toastContext = useToast();
    const toast = toastContext?.toast || toastContext;
    const isDaf = hasPermission('POINTAGE_VALIDATE');

    // État local
    const [periodes, setPeriodes] = useState([]);
    const [selectedPeriodeId, setSelectedPeriodeId] = useState(() => (
        activePeriode?.idPeriodePaie ? String(activePeriode.idPeriodePaie) : (searchParams.get('periode') || '')
    ));
    const [sites, setSites] = useState([]);
    const [selectedSiteId, setSelectedSiteId] = useState(() => (
        searchParams.get('site') ? Number(searchParams.get('site')) : 0
    ));

    // Filtres anomalies & Pagination
    const [searchQuery, setSearchQuery] = useState('');
    const [filterType, setFilterType] = useState('ALL');
    const [filterSeverite, setFilterSeverite] = useState('ALL');
    const [filterSemaine, setFilterSemaine] = useState('ALL'); // 'ALL', 'S1', 'S2', ...
    const [filterDate, setFilterDate] = useState('');
    const [filterStatutTraitement, setFilterStatutTraitement] = useState('TODO'); // 'ALL', 'TODO', 'DONE'
    const [isSemainesCollapsed, setIsSemainesCollapsed] = useState(true);
    const [isMissingDaysCollapsed, setIsMissingDaysCollapsed] = useState(true);
    const [showAdvancedFilters, setShowAdvancedFilters] = useState(false);
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(8);

    // Modale de régularisation
    const [selectedAnomalie, setSelectedAnomalie] = useState(null);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [selectedRetards, setSelectedRetards] = useState(() => new Map());
    const [isBulkConfirmOpen, setIsBulkConfirmOpen] = useState(false);
    const [bulkRegularising, setBulkRegularising] = useState(false);

    // Hook unifié des anomalies & semaines
    const {
        anomalies,
        stats,
        semaines,
        kpis,
        periode: periodeData,
        loading,
        updateFilters,
        loadAnomalies,
        regulariser
    } = usePointageAnomalies({
        idPeriodePaie: activePeriode?.idPeriodePaie || (searchParams.get('periode') ? Number(searchParams.get('periode')) : 0),
        idSite: searchParams.get('site') ? Number(searchParams.get('site')) : 0
    });

    // Effets
    useEffect(() => {
        const init = async () => {
            try {
                const paramPeriode = searchParams.get('periode');
                const [resPeriodes, resSites, context] = await Promise.all([
                    periodesDisponibles ?? periodePaieService.getAllPeriodes(),
                    sitesDisponibles ?? organisationService.getSites(),
                    activePeriode ? null : periodePaieService.getContext(paramPeriode ? Number(paramPeriode) : null)
                ]);
                if (resPeriodes && resPeriodes.length > 0) {
                    setPeriodes(resPeriodes);
                    const active = (activePeriode ? resPeriodes.find(p => p.idPeriodePaie === activePeriode.idPeriodePaie) : null)
                        || (paramPeriode ? resPeriodes.find(p => String(p.idPeriodePaie) === String(paramPeriode)) : null)
                        || resPeriodes.find(p => String(p.idPeriodePaie) === String(context?.selectedPeriod?.id))
                        || resPeriodes[0];
                    if (active) {
                        const pid = String(active.idPeriodePaie);
                        setSelectedPeriodeId(pid);
                        if (!paramPeriode || String(paramPeriode) !== pid) {
                            setSearchParams(prev => { const n = new URLSearchParams(prev); n.set('periode', pid); return n; }, { replace: true });
                        }
                    }
                }
                if (resSites) {
                    setSites(resSites);
                    const paramSite = searchParams.get('site');
                    if (paramSite) setSelectedSiteId(Number(paramSite));
                }
            } catch (err) {
                console.error('Erreur initialisation SuiviAnomaliesPage:', err);
            }
        };
        init();
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    // Synchronisation si le parent change activePeriode
    useEffect(() => {
        if (activePeriode?.idPeriodePaie && String(activePeriode.idPeriodePaie) !== String(selectedPeriodeId)) {
            setSelectedPeriodeId(String(activePeriode.idPeriodePaie));
        }
    }, [activePeriode?.idPeriodePaie]); // eslint-disable-line react-hooks/exhaustive-deps

    // Synchronisation filtres avec hook
    useEffect(() => {
        if (selectedPeriodeId) {
            updateFilters({
                idPeriodePaie: Number(selectedPeriodeId),
                idSite: Number(selectedSiteId) > 0 ? Number(selectedSiteId) : 0,
                search: searchQuery
            });
        }
    }, [selectedPeriodeId, selectedSiteId, searchQuery]); // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => {
        setSelectedRetards(new Map());
    }, [selectedPeriodeId, selectedSiteId]);

    // Données dérivées
    const currentPeriodeObj = useMemo(() =>
        periodes.find(p => String(p.idPeriodePaie) === String(selectedPeriodeId)) || null,
    [periodes, selectedPeriodeId]);

    // Preflight global de la période pour détecter précisément les journées manquantes par site
    const [preflight, setPreflight] = useState(null);
    const [preflightError, setPreflightError] = useState(false);
    const [preflightRevision, setPreflightRevision] = useState(0);

    useEffect(() => {
        if (!selectedPeriodeId) return;
        let cancelled = false;
        setPreflight(null);
        setPreflightError(false);
        pointageSyntheseService.getPreflight({ idPeriodePaie: Number(selectedPeriodeId) })
            .then(res => { if (!cancelled) setPreflight(res); })
            .catch(() => { if (!cancelled) { setPreflight(null); setPreflightError(true); } });
        return () => { cancelled = true; };
    }, [selectedPeriodeId, preflightRevision]);

    const isSiteFiltered = Number(selectedSiteId) > 0;
    const selectedSiteObj = useMemo(() =>
        sites.find(s => String(s.idSite || s.id) === String(selectedSiteId)) || null,
    [sites, selectedSiteId]);
    const selectedSiteCode = selectedSiteObj?.codeSite || selectedSiteObj?.code || '';

    // Analyse des journées ouvrables manquantes par site
    const sitesWithMissingDays = useMemo(() => {
        if (!preflight?.journeesManquantes?.length) return [];
        const missingBySite = {};
        preflight.journeesManquantes.forEach(m => {
            const sid = m.idSite ? String(m.idSite) : 'unknown';
            missingBySite[sid] = (missingBySite[sid] || 0) + 1;
        });
        return sites
            .filter(s => (missingBySite[String(s.idSite || s.id)] || 0) > 0)
            .map(s => ({
                ...s,
                nbManquantes: missingBySite[String(s.idSite || s.id)]
            }));
    }, [preflight, sites]);

    const currentSiteMissingCount = useMemo(() => {
        if (!isSiteFiltered || !preflight?.journeesManquantes?.length) return 0;
        return preflight.journeesManquantes.filter(m => String(m.idSite) === String(selectedSiteId)).length;
    }, [isSiteFiltered, preflight, selectedSiteId]);

    useEffect(() => {
        if (onAnomaliesCountChange) {
            onAnomaliesCountChange((Number(kpis.anomaliesActives) || 0)
                + (isSiteFiltered ? currentSiteMissingCount : (preflight?.journeesManquantes?.length || 0)));
        }
    }, [kpis.anomaliesActives, isSiteFiltered, currentSiteMissingCount, preflight, onAnomaliesCountChange]);

    const missingDays = useMemo(() => (preflight?.journeesManquantes || [])
        .filter(day => !isSiteFiltered || String(day.idSite) === String(selectedSiteId))
        .filter(day => {
            const query = searchQuery.trim().toLowerCase();
            return !query || `${day.nom || ''} ${day.prenom || ''} ${day.matricule || ''}`.toLowerCase().includes(query);
        })
        .sort((a, b) => String(a.date).localeCompare(String(b.date))),
    [preflight, isSiteFiltered, selectedSiteId, searchQuery]);

    const employeeDayUrl = (idEmploye, date) => `/pointages?tab=saisie&mode=employe&periode=${selectedPeriodeId}&employe=${idEmploye}&date=${date}`;

    const otherIncompleteSites = useMemo(() => {
        return sitesWithMissingDays.filter(s => String(s.idSite || s.id) !== String(selectedSiteId));
    }, [sitesWithMissingDays, selectedSiteId]);

    const handleSwitchSite = (targetSiteId) => {
        const sid = Number(targetSiteId);
        setSelectedSiteId(sid);
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            if (sid > 0) next.set('site', String(sid));
            else next.delete('site');
            return next;
        }, { replace: true });
    };

    const actions = Object.keys(currentPeriodeObj?.availableActions || {}).length > 0
        ? currentPeriodeObj.availableActions
        : (String(activePeriode?.idPeriodePaie) === String(selectedPeriodeId) ? activePeriode.availableActions || {} : {});
    const isPendingDaf = ['A_VALIDER', 'TRANSMIS_DAF'].includes(currentPeriodeObj?.statutCode || activePeriode?.statutCode);
    const canRegularize = actions.REGULARIZE_ANOMALY?.allowed === true || (isDaf && isPendingDaf);
    const isLocked = !canRegularize;

    // Anomalies bloquantes pour la transmission DAF
    const nbBloquantes = Number(stats?.bloquantes || 0);

    // Semaines restantes non saisies / non clôturées
    const nbSemainesRestantes = useMemo(() => {
        return (semaines || []).filter(s => s.statutSemaine !== 'CLOTUREE' && (s.progressPercent || 0) < 100).length;
    }, [semaines]);

    const canTransmitDaf = actions.SUBMIT?.allowed === true;

    // Traduction de présentation des codes de blocage fournis par le workflow backend.
    const transmissionBlockReason = (actions.SUBMIT?.reasonCodes || []).map(code => ({
        PERIOD_NOT_ENDED: 'La période n’est pas encore terminée.',
        POINTAGE_EMPTY: 'Aucun pointage n’a été saisi pour cette période.',
        POINTAGE_POPULATION_EMPTY: 'Aucun collaborateur éligible n’a été trouvé pour cette période.',
        POINTAGE_JOURNEES_MANQUANTES: 'Des journées ouvrables restent sans pointage. Complétez la saisie avant de transmettre.',
        POINTAGE_CROSS_PERIOD_SHIFT: 'Une vacation du dernier jour déborde sur la période suivante. Scindez sa saisie par date avant de transmettre.',
        POINTAGE_HS_ELIGIBILITY_CHANGE: 'L’éligibilité aux HS change pendant une semaine. La DAF doit confirmer la date d’effet de l’affectation.',
        BLOCKING_ANOMALIES: 'Des anomalies bloquantes doivent être régularisées.',
        INVALID_STATE: 'L’état actuel de la période ne permet pas encore cette action.',
        FORBIDDEN: 'Vous ne disposez pas des droits nécessaires pour transmettre cette période.',
    }[code] || code)).join(' · ');

    const hasJourneesManquantes = (actions.SUBMIT?.reasonCodes || []).includes('POINTAGE_JOURNEES_MANQUANTES');
    const isCurrentSiteClean = isSiteFiltered && currentSiteMissingCount === 0;

    const transmissionBlockTitle = useMemo(() => {
        if (isPendingDaf) return 'Période transmise à la DAF pour arbitrage.';
        if (canTransmitDaf) return 'La période est prête à être transmise.';
        if (hasJourneesManquantes) {
            if (isCurrentSiteClean) {
                return `Pointages complets pour le site ${selectedSiteCode || 'sélectionné'} · Transmission globale en attente`;
            }
            if (isSiteFiltered) {
                return `Des journées ouvrables (${currentSiteMissingCount} j) restent sans pointage sur ${selectedSiteCode}.`;
            }
            return 'Des journées ouvrables restent sans pointage sur la période.';
        }
        return transmissionBlockReason || 'Le workflow ne permet pas encore la transmission.';
    }, [isPendingDaf, canTransmitDaf, hasJourneesManquantes, isCurrentSiteClean, isSiteFiltered, currentSiteMissingCount, selectedSiteCode, transmissionBlockReason]);

    const transmissionBlockSubtitle = useMemo(() => {
        if (isPendingDaf) return 'La DAF peut encore corriger ou régulariser les journées ici, puis valider ou renvoyer la période depuis « Contrôle & Validation ».';
        if (canTransmitDaf) return 'Pour transmettre la période à la DAF, utilisez le bouton de l’étape « Synthèse & Contrôle ». ';
        if (hasJourneesManquantes && isCurrentSiteClean) {
            const otherList = otherIncompleteSites.map(s => `${s.codeSite || s.code || s.libelle} (${s.nbManquantes} j)`).join(', ');
            return `La transmission pour contrôle s'applique à l'ensemble des sites de la période. Des pointages restent à finaliser sur : ${otherList || 'd’autres sites (ex. TMM)'}.`;
        }
        if (hasJourneesManquantes && !isSiteFiltered && sitesWithMissingDays.length > 0) {
            const list = sitesWithMissingDays.map(s => `${s.codeSite || s.code || s.libelle} (${s.nbManquantes} j)`).join(', ');
            return `Répartition des journées à compléter : ${list}.`;
        }
        if (nbBloquantes > 0) {
            return `${nbBloquantes} anomalie(s) bloquante(s) recensée(s) par le contrôle.`;
        }
        return null;
    }, [isPendingDaf, canTransmitDaf, hasJourneesManquantes, isCurrentSiteClean, otherIncompleteSites, isSiteFiltered, sitesWithMissingDays, nbBloquantes]);

    // Filtrage dynamique des anomalies
    const filteredAnomalies = useMemo(() => {
        return (anomalies || []).filter(ano => {
            // Filtre par semaine sélectionnée dans les cartes S1..S5
            if (filterSemaine !== 'ALL' && ano.semaineCode !== filterSemaine) {
                return false;
            }
            // Filtre par date précise
            if (filterDate && ano.datePointage !== filterDate) {
                return false;
            }
            if (filterStatutTraitement === 'TODO' && ano.statutAnomalie !== 'todo') {
                return false;
            }
            if (filterStatutTraitement === 'DONE' && ano.statutAnomalie !== 'done') {
                return false;
            }
            // Filtre sévérité
            if (filterSeverite !== 'ALL' && ano.severite !== filterSeverite) {
                return false;
            }
            // Filtre type
            if (filterType !== 'ALL') {
                if (filterType === 'ABSENCE' && ano.typeAnomalie !== 'ABSENCE') return false;
                if (filterType === 'INCOMPLET' && ano.typeAnomalie !== 'INCOMPLET') return false;
                if (filterType === 'RETARD' && ano.typeAnomalie !== 'RETARD') return false;
            }
            // Recherche textuelle
            if (searchQuery.trim()) {
                const q = searchQuery.toLowerCase();
                const nomComplet = `${ano.nom || ''} ${ano.prenom || ''}`.toLowerCase();
                const mat = (ano.matricule || '').toLowerCase();
                const desc = (ano.descriptionAnomalie || '').toLowerCase();
                if (!nomComplet.includes(q) && !mat.includes(q) && !desc.includes(q)) {
                    return false;
                }
            }
            return true;
        });
    }, [anomalies, filterSemaine, filterDate, filterStatutTraitement, filterSeverite, filterType, searchQuery]);

    // Pagination
    const _totalPages = Math.ceil(filteredAnomalies.length / pageSize) || 1;
    const paginatedAnomalies = useMemo(() => {
        const start = (currentPage - 1) * pageSize;
        return filteredAnomalies.slice(start, start + pageSize);
    }, [filteredAnomalies, currentPage, pageSize]);

    const isBulkRetardEligible = (anomalie) => anomalie?.typeAnomalie === 'RETARD'
        && anomalie.statutAnomalie === 'todo'
        && Number(anomalie.minutesRetard) > 0
        && Number(anomalie.idPointage) > 0
        && Number.isInteger(Number(anomalie.version));
    const visibleRetardsEligible = paginatedAnomalies.filter(isBulkRetardEligible);
    const allVisibleRetardsSelected = visibleRetardsEligible.length > 0
        && visibleRetardsEligible.every(ano => selectedRetards.has(String(ano.idPointage)));

    const toggleRetardSelection = (anomalie) => {
        const key = String(anomalie.idPointage);
        setSelectedRetards(previous => {
            const next = new Map(previous);
            if (next.has(key)) next.delete(key);
            else next.set(key, anomalie);
            return next;
        });
    };

    const toggleVisibleRetards = () => {
        setSelectedRetards(previous => {
            const next = new Map(previous);
            if (allVisibleRetardsSelected) visibleRetardsEligible.forEach(ano => next.delete(String(ano.idPointage)));
            else visibleRetardsEligible.forEach(ano => next.set(String(ano.idPointage), ano));
            return next;
        });
    };

    const handleBulkConfirmRetards = async () => {
        const items = [...selectedRetards.values()];
        if (!items.length || bulkRegularising) return;
        setBulkRegularising(true);
        let succeeded = 0;
        const failed = [];
        try {
            for (const ano of items) {
                const result = await regulariser(ano.idPointage, {
                    typeRegularisation: 'RETARD_REEL',
                    version: Number(ano.version),
                }, { reload: false });
                if (result?.success || result === true) succeeded += 1;
                else failed.push(ano);
            }
            setSelectedRetards(new Map());
            setIsBulkConfirmOpen(false);
            await loadAnomalies();
            setPreflightRevision(value => value + 1);
            if (succeeded > 0) toast.success(`${succeeded} retard${succeeded > 1 ? 's' : ''} confirmé${succeeded > 1 ? 's' : ''} comme réel${succeeded > 1 ? 's' : ''}.`);
            if (failed.length > 0) toast.error(`${failed.length} retard${failed.length > 1 ? 's' : ''} n'ont pas pu être régularisés. Rechargez la liste et vérifiez-les individuellement.`);
        } finally {
            setBulkRegularising(false);
        }
    };

    // Gestion de l'ouverture de la modale de régularisation
    // Actions
    const handleOpenRegularisation = (anomalie) => {
        setSelectedAnomalie(anomalie);
        setIsModalOpen(true);
    };

    // Callback de succès après régularisation
    const handleSaveRegularisation = async (arg1, arg2) => {
        const payload = arg2 !== undefined ? arg2 : arg1;
        const idPtg = (arg2 !== undefined ? arg1 : selectedAnomalie?.idPointage) || selectedAnomalie?.idPointage;
        if (!idPtg || !payload) return false;

        const res = await regulariser(idPtg, payload);
        if (res && (res.success || res === true)) {
            setIsModalOpen(false);
            setSelectedAnomalie(null);
            toast.success(res.message || 'Pointage régularisé avec succès.');
            const updatedPeriodes = await periodePaieService.getAllPeriodes();
            if (updatedPeriodes) setPeriodes(updatedPeriodes);
            setPreflightRevision(value => value + 1);
            return true;
        } else {
            toast.error(res?.error || res?.message || 'Erreur lors de la régularisation.');
            return res || false;
        }
    };


    // Rendu
    return (
        <div className="space-y-4 font-sans animate-in fade-in duration-300">
            <SEO
                title="Suivi &amp; Anomalies — Pointage | GALAXAM GRH"
                description="Suivi de la progression hebdomadaire, détection proactive et régularisation d'anomalies de pointage."
            />

            {/* ---------- HEADER CONTEXTE & ACTIONS (STICKY TOP) ---------- */}
            <div className="sticky top-0 z-20 flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 shadow-sm">
                <div className="flex items-center gap-3 flex-wrap">
                    {/* Période */}
                    <div className="flex items-center gap-1.5 min-w-[220px]">
                        <Calendar size={14} className="text-[var(--color-brand-500)] shrink-0" />
                        <SearchSelect
                            value={String(selectedPeriodeId || '')}
                            onChange={(val) => {
                                const pid = String(val);
                                setSelectedPeriodeId(pid);
                                setFilterDate('');
                                setFilterSemaine('ALL');
                                setCurrentPage(1);
                                if (onSelectPeriode) {
                                    onSelectPeriode(Number(pid));
                                } else {
                                    setSearchParams(prev => {
                                        const next = new URLSearchParams(prev);
                                        next.set('periode', pid);
                                        return next;
                                    }, { replace: true });
                                }
                            }}
                            options={periodes.map(p => ({
                                value: String(p.idPeriodePaie),
                                label: [p.code, p.libelle].filter(Boolean).join(' — '),
                                sublabel: p.statutCode || p.statut || ''
                            }))}
                            className="w-full"
                        />
                    </div>

                    {/* Site */}
                    <div className="flex items-center gap-1.5 min-w-[200px]">
                        <Building2 size={14} className="text-[var(--color-brand-500)] shrink-0" />
                        <SearchSelect
                            value={String(selectedSiteId)}
                            onChange={(val) => {
                                const sid = Number(val || 0);
                                setSelectedSiteId(sid);
                                setCurrentPage(1);
                                setSearchParams(prev => {
                                    const next = new URLSearchParams(prev);
                                    if (sid > 0) next.set('site', String(sid));
                                    else next.delete('site');
                                    return next;
                                }, { replace: true });
                            }}
                            options={[
                                { value: '0', label: 'Tous les sites (Global)' },
                                ...sites.map(s => {
                                    const code = s.codeSite || s.code;
                                    const name = s.libelle || s.nom || s.nomSite || '';
                                    return {
                                        value: String(s.idSite || s.id),
                                        label: code ? `${code} — ${name}` : name
                                    };
                                })
                            ]}
                            placeholder="Tous les sites (Global)"
                            className="text-xs min-w-[200px]"
                        />
                    </div>
                </div>
                <Input
                    icon={<Search size={13} className="text-[var(--color-fg-muted)]" />}
                    placeholder="Rechercher un salarié ou matricule…"
                    aria-label="Rechercher un salarié ou matricule"
                    value={searchQuery}
                    onChange={(event) => { setSearchQuery(event.target.value); setCurrentPage(1); }}
                    className="w-full sm:w-64 text-xs"
                />
            </div>

            {/* Les repères prioritaires avant le détail et les filtres. */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                <StatCard
                    title="À traiter"
                    value={(Number(kpis?.anomaliesActives) || 0) + (isSiteFiltered ? currentSiteMissingCount : (preflight?.journeesManquantes?.length || 0))}
                    subtext="Journées sans statut et anomalies actives"
                    icon={<AlertTriangle size={17} />}
                    color={(Number(kpis?.anomaliesActives) || 0) + (preflight?.journeesManquantes?.length || 0) > 0 ? 'warning' : 'neutral'}
                />
                <StatCard
                    title="Journées sans statut"
                    value={isSiteFiltered ? currentSiteMissingCount : (preflight?.journeesManquantes?.length ?? '—')}
                    subtext="Jours ouvrables à compléter avant transmission"
                    icon={<Calendar size={17} />}
                    color={(isSiteFiltered ? currentSiteMissingCount : (preflight?.journeesManquantes?.length || 0)) > 0 ? 'warning' : 'success'}
                />
                <StatCard
                    title="Anomalies bloquantes"
                    value={nbBloquantes}
                    subtext="Pointages enregistrés qui demandent une décision"
                    icon={<Shield size={17} />}
                    color={nbBloquantes > 0 ? 'danger' : 'success'}
                />
            </div>

            {/* Les journées absentes du pointage ne figurent pas dans la liste des anomalies enregistrées. */}
            {filterStatutTraitement !== 'DONE' && (
                <section className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)]" aria-labelledby="missing-days-title">
                    <Button
                        type="button"
                        variant="ghost"
                        size="md"
                        wrapLabel
                        aria-expanded={!isMissingDaysCollapsed}
                        aria-controls="missing-days-content"
                        onClick={() => setIsMissingDaysCollapsed(previous => !previous)}
                        iconRight={isMissingDaysCollapsed
                            ? <ChevronDown size={16} className="shrink-0 text-[var(--color-fg-muted)]" />
                            : <ChevronUp size={16} className="shrink-0 text-[var(--color-fg-muted)]" />}
                        className="w-full justify-between p-4 text-left normal-case tracking-normal"
                    >
                        <span className="flex min-w-0 flex-1 flex-col items-start text-left normal-case tracking-normal">
                            <span id="missing-days-title" className="block text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                                Journées ouvrables sans statut · {missingDays.length}
                            </span>
                            {!isMissingDaysCollapsed && (
                                <span className="mt-1 block text-xs font-normal normal-case tracking-normal text-[var(--color-fg-muted)]">
                                    Ouvrez la journée pour saisir le pointage ou signaler une absence ou un congé. Aucun retrait de salaire n’est appliqué par cette liste.
                                </span>
                            )}
                        </span>
                    </Button>
                    {!isMissingDaysCollapsed && (
                        <div id="missing-days-content" className="border-t border-[var(--color-border-subtle)]">
                            {missingDays.length > 0 ? (
                                <div className="max-h-72 overflow-y-auto divide-y divide-[var(--color-border-subtle)]">
                                    {missingDays.map(day => (
                                        <div key={`${day.idEmploye}-${day.date}`} className="flex flex-wrap items-center justify-between gap-2 px-4 py-2 text-xs">
                                            <div><strong>{day.nom} {day.prenom}</strong> <span className="text-[var(--color-fg-muted)]">· {day.matricule} · {fmtDate(day.date)}</span></div>
                                            <Link to={employeeDayUrl(day.idEmploye, day.date)} className="font-semibold text-[var(--color-brand-600)] hover:underline">Ouvrir la journée →</Link>
                                        </div>
                                    ))}
                                </div>
                            ) : <p className="p-4 text-xs text-[var(--color-fg-muted)]">{preflightError ? 'Contrôle des journées indisponible pour le moment.' : preflight ? 'Aucune journée sans statut pour cette sélection.' : 'Chargement des journées à traiter…'}</p>}
                        </div>
                    )}
                </section>
            )}

            {/* ---------- VUE HEBDOMADAIRE (S1 - S5) PLIABLE ---------- */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] overflow-hidden transition-all">
                <div className="p-3 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] flex items-center justify-between flex-wrap gap-2">
                    <button
                        type="button"
                        aria-expanded={!isSemainesCollapsed}
                        onClick={() => setIsSemainesCollapsed(previous => !previous)}
                        className="flex min-w-0 items-center gap-2 text-left hover:text-[var(--color-brand-700)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-focus-ring)]"
                    >
                        <Layers size={14} className="text-[var(--color-brand-500)] shrink-0" />
                        <span className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                            Progression hebdomadaire
                        </span>
                        <span className="text-[11px] text-[var(--color-fg-muted)] hidden sm:inline">
                            {semaines.length - nbSemainesRestantes}/{semaines.length} semaine(s) complétée(s)
                        </span>
                    </button>
                    <div className="flex items-center gap-2">
                        {filterSemaine !== 'ALL' && (
                            <Button
                                variant="ghost"
                                size="xs"
                                onClick={(e) => { e.stopPropagation(); setFilterSemaine('ALL'); }}
                                className="text-xs text-[var(--color-brand-500)] font-bold"
                            >
                                Annuler filtre ({filterSemaine})
                            </Button>
                        )}
                        <button
                            type="button"
                            aria-expanded={!isSemainesCollapsed}
                            onClick={() => setIsSemainesCollapsed(previous => !previous)}
                            className="flex items-center gap-1 text-xs font-semibold text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] px-2 py-1 bg-[var(--color-surface)] border border-[var(--color-border-subtle)]"
                        >
                            <span>{isSemainesCollapsed ? 'Déplier' : 'Plier'}</span>
                            {isSemainesCollapsed ? <ChevronDown size={13} /> : <ChevronUp size={13} />}
                        </button>
                    </div>
                </div>

                {!isSemainesCollapsed && (

                <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-5 divide-y md:divide-y-0 md:divide-x divide-[var(--color-border-subtle)]">
                    {semaines && semaines.length > 0 ? (
                        semaines.map((sem) => {
                            const isSelected = filterSemaine === sem.semaineCode;
                            return (
                                <div
                                    key={sem.semaineCode}
                                    role="button"
                                    tabIndex={0}
                                    aria-pressed={isSelected}
                                    onClick={() => setFilterSemaine(prev => prev === sem.semaineCode ? 'ALL' : sem.semaineCode)}
                                    onKeyDown={(event) => {
                                        if (event.key === 'Enter' || event.key === ' ') {
                                            event.preventDefault();
                                            setFilterSemaine(prev => prev === sem.semaineCode ? 'ALL' : sem.semaineCode);
                                        }
                                    }}
                                    className={`p-3.5 transition-all cursor-pointer relative group focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--color-focus-ring)] ${
                                        sem.isCurrent ? 'bg-[var(--color-brand-50)]/20 dark:bg-[var(--color-brand-950)]/10' : ''
                                    } ${isSelected ? 'bg-[var(--color-brand-50)]/50 dark:bg-[var(--color-brand-950)]/30 border-l-4 border-l-[var(--color-brand-500)]' : 'hover:bg-[var(--color-bg-subtle)]'}`}
                                >
                                    <div className="flex items-center justify-between">
                                        <span className="font-bold text-xs text-[var(--color-fg)]">
                                            {sem.titre}
                                        </span>
                                        {sem.isCurrent && (
                                            <span className="text-[10px] font-bold text-[var(--color-brand-500)] uppercase bg-[var(--color-brand-50)] dark:bg-[var(--color-brand-950)]/40 px-1.5 py-0.5 border border-[var(--color-brand-500)]/30">
                                                En cours
                                            </span>
                                        )}
                                    </div>
                                    <div className="text-xs text-[var(--color-fg-muted)] mt-0.5 mb-2.5 font-data">
                                        {sem.dates}
                                    </div>

                                    {/* Ligne Saisis */}
                                    <div className="flex items-center justify-between text-xs mb-1">
                                        <span className="text-[var(--color-fg-muted)]">Saisis</span>
                                        <span className="font-data font-semibold text-[var(--color-fg)]">
                                            {sem.joursSaisis} / {sem.joursTheoriques} j
                                        </span>
                                    </div>

                                    {/* Barre de progression */}
                                    <div className="w-full h-1.5 bg-[var(--color-border-subtle)] overflow-hidden mb-2.5">
                                        <div 
                                            className="h-full bg-[var(--color-brand-500)] transition-all duration-300"
                                            style={{ width: `${sem.progressPercent || 0}%` }}
                                        />
                                    </div>

                                    {/* Ligne Heures */}
                                    <div className="flex items-center justify-between text-xs mb-2.5">
                                        <span className="text-[var(--color-fg-muted)]">Heures</span>
                                        <span className="font-data font-semibold text-[var(--color-fg)]">
                                            {sem.heuresCumulees}h
                                        </span>
                                    </div>

                                    {/* Badge statut anomalies */}
                                    <div className="flex items-center justify-between pt-1 border-t border-[var(--color-border-subtle)]">
                                        {sem.nbAnomalies > 0 ? (
                                            <span className="inline-flex items-center gap-1 text-[10px] font-bold px-1.5 py-0.5 bg-[var(--color-warning-500)]/10 text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] border border-[var(--color-warning-500)]/30">
                                                <AlertTriangle size={10} />
                                                {sem.nbAnomalies} anomalie{sem.nbAnomalies > 1 ? 's' : ''}
                                            </span>
                                        ) : sem.joursSaisis > 0 ? (
                                            <span className="inline-flex items-center gap-1 text-[10px] font-bold px-1.5 py-0.5 bg-[var(--color-success-500)]/10 text-[var(--color-success-700)] dark:text-[var(--color-success-300)] border border-[var(--color-success-500)]/30">
                                                <CheckCircle2 size={10} />
                                                Conforme
                                            </span>
                                        ) : (
                                            <span className="text-[10px] text-[var(--color-fg-muted)] font-medium">
                                                À venir
                                            </span>
                                        )}

                                        <span className="text-[10px] font-bold text-[var(--color-brand-500)] group-hover:underline">
                                            {isSelected ? 'Filtré ✓' : 'Filtrer →'}
                                        </span>
                                    </div>
                                </div>
                            );
                        })
                    ) : (
                        <div className="col-span-5 p-6 text-center text-xs text-[var(--color-fg-muted)]">
                            Aucune donnée de semaine disponible pour cette sélection.
                        </div>
                    )}
                </div>
                )}
            </div>

            {/* ---------- SECTION ANOMALIES DE POINTAGE ---------- */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] overflow-hidden" id="anomalies">
                {/* Header & Filtres */}
                <div className="p-4 border-b border-[var(--color-border-subtle)] space-y-3">
                    <div>
                        <h2 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                            <Shield size={14} className="text-[var(--color-brand-500)]" />
                            Pointages à vérifier et régularisations
                        </h2>
                        <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                            {filteredAnomalies.length} élément{filteredAnomalies.length > 1 ? 's' : ''} de suivi
                            {nbBloquantes > 0 && ` — dont ${nbBloquantes} bloquante(s) avant transmission`}
                        </p>
                    </div>

                    <div className="flex flex-wrap items-center gap-2">
                        <SearchSelect
                            aria-label="Filtrer les anomalies par traitement"
                            value={filterStatutTraitement}
                            onChange={(val) => {
                                setFilterStatutTraitement(val || 'ALL');
                                setCurrentPage(1);
                            }}
                            placeholder="Tous les statuts"
                            options={[
                                { value: 'TODO', label: 'À traiter' },
                                { value: 'DONE', label: 'Traités' },
                                { value: 'ALL', label: 'Tous' },
                            ]}
                            className="text-xs w-full sm:w-48"
                        />
                        <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            aria-expanded={showAdvancedFilters}
                            onClick={() => setShowAdvancedFilters(previous => !previous)}
                            iconLeft={<Layers size={14} />}
                        >
                            Filtres complémentaires
                            {(filterDate || filterSemaine !== 'ALL' || filterType !== 'ALL' || filterSeverite !== 'ALL') && ' · actifs'}
                        </Button>
                    </div>

                    {showAdvancedFilters && (
                        <>
                        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-2 border-t border-[var(--color-border-subtle)] pt-3">
                            <SearchSelect
                                aria-label="Filtrer par type d'anomalie"
                                value={filterType}
                                onChange={(val) => { setFilterType(val || 'ALL'); setCurrentPage(1); }}
                                placeholder="Tous les types"
                                options={[
                                    { value: 'ALL', label: 'Tous les types' },
                                    { value: 'ABSENCE', label: 'ABSENCE — Absence non justifiée' },
                                    { value: 'INCOMPLET', label: 'INCOMPLET — Pointage incomplet' },
                                    { value: 'RETARD', label: 'RETARD — Retards' },
                                ]}
                                className="text-xs w-full"
                            />
                            <SearchSelect
                                aria-label="Filtrer par sévérité"
                                value={filterSeverite}
                                onChange={(val) => { setFilterSeverite(val || 'ALL'); setCurrentPage(1); }}
                                placeholder="Toutes les sévérités"
                                options={[
                                    { value: 'ALL', label: 'Toutes les sévérités' },
                                    { value: 'CRITIQUE', label: 'CRITIQUE — Critique · bloquante' },
                                    { value: 'HAUTE', label: 'HAUTE — Élevée' },
                                    { value: 'MOYENNE', label: 'MOYENNE — Moyenne' },
                                    { value: 'FAIBLE', label: 'FAIBLE — Faible' },
                                ]}
                                className="text-xs w-full"
                            />
                            <div className="relative flex items-center w-full">
                                <Calendar size={13} className="absolute left-2.5 text-[var(--color-fg-muted)] pointer-events-none" />
                                <input
                                    type="date"
                                    value={filterDate}
                                    min={periodeData?.dateDebut || undefined}
                                    max={periodeData?.dateFin || undefined}
                                    onChange={(e) => { setFilterDate(e.target.value); setCurrentPage(1); }}
                                    aria-label="Filtrer par date précise"
                                    className="w-full text-xs pl-7 pr-2 py-1 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] text-[var(--color-fg)] outline-none focus:border-[var(--color-brand-500)] cursor-pointer h-[32px]"
                                />
                            </div>
                            <Select
                                aria-label="Filtrer par semaine"
                                value={filterSemaine}
                                onChange={(val) => { setFilterSemaine(val); setCurrentPage(1); }}
                                options={[
                                    { value: 'ALL', label: 'Toutes les semaines' },
                                    ...semaines.map(semaine => ({ value: semaine.semaineCode, label: semaine.titre || semaine.semaineCode }))
                                ]}
                                className="text-xs w-full"
                            />
                        </div>
                        {(filterDate || filterSemaine !== 'ALL' || filterType !== 'ALL' || filterSeverite !== 'ALL' || filterStatutTraitement !== 'ALL' || searchQuery) && (
                            <div className="flex justify-end">
                                <Button
                                    type="button"
                                    variant="ghost"
                                    size="sm"
                                    onClick={() => {
                                        setFilterDate('');
                                        setFilterSemaine('ALL');
                                        setFilterType('ALL');
                                        setFilterSeverite('ALL');
                                        setFilterStatutTraitement('ALL');
                                        setSearchQuery('');
                                        setCurrentPage(1);
                                    }}
                                >
                                    Réinitialiser les filtres
                                </Button>
                            </div>
                        )}
                        </>
                    )}
                </div>

                <p className="px-4 py-2 border-b border-[var(--color-border-subtle)] text-xs text-[var(--color-fg-muted)]">
                    Un retard peut être corrigé si la saisie est erronée, ou confirmé comme réel. Dans les deux cas, il reste tracé et passe à « Régularisé ».
                </p>

                {selectedRetards.size > 0 && (
                    <div className="flex flex-col gap-2 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/60 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
                        <p className="text-xs text-[var(--color-fg-muted)]">
                            <strong className="text-[var(--color-fg)]">{selectedRetards.size}</strong> retard{selectedRetards.size > 1 ? 's' : ''} sélectionné{selectedRetards.size > 1 ? 's' : ''} pour confirmation comme réel.
                        </p>
                        <div className="flex items-center gap-2">
                            <Button type="button" variant="ghost" size="sm" onClick={() => setSelectedRetards(new Map())}>
                                Effacer la sélection
                            </Button>
                            <Button
                                type="button"
                                variant="primary"
                                size="sm"
                                iconLeft={<CheckCircle2 size={14} />}
                                disabled={isLocked || bulkRegularising}
                                onClick={() => setIsBulkConfirmOpen(true)}
                            >
                                Confirmer les retards sélectionnés
                            </Button>
                        </div>
                    </div>
                )}

                {/* Tableau des anomalies */}
                <div className="overflow-x-auto border-b border-[var(--color-border-subtle)]">
                    <Table density="compact" bordered={false}>
                        <Thead className="sticky top-0 bg-[var(--color-surface)] z-10 shadow-xs">
                            <Tr>
                                <Th className="w-10 text-center">
                                    <input
                                        type="checkbox"
                                        aria-label="Sélectionner tous les retards vérifiables de cette page"
                                        checked={allVisibleRetardsSelected}
                                        disabled={isLocked || visibleRetardsEligible.length === 0}
                                        onChange={toggleVisibleRetards}
                                        className="accent-[var(--color-brand-500)] cursor-pointer disabled:cursor-not-allowed"
                                    />
                                </Th>
                                <Th className="min-w-[200px]">Salarié</Th>
                                <Th className="text-center w-20">Semaine</Th>
                                <Th className="w-24">Date</Th>
                                <Th className="min-w-[220px]">Type &amp; Description</Th>
                                <Th className="w-24 text-center">Sévérité</Th>
                                <Th className="w-28 text-center">Statut</Th>
                                <Th className="w-24 text-right">Action</Th>
                            </Tr>
                        </Thead>
                        <Tbody>
                            {paginatedAnomalies && paginatedAnomalies.length > 0 ? (
                                paginatedAnomalies.map((ano) => {
                                    const severity = SEVERITY_PRESENTATION[ano.severite] || SEVERITY_PRESENTATION.FAIBLE;

                                    const isDone = ano.statutAnomalie === 'done';

                                        return (
                                        <Tr
                                            key={`${ano.idPointage}-${ano.idEmploye}-${ano.datePointage}`}
                                            className={`border-l-4 ${severity.border}`}
                                        >
                                            <Td className="text-center">
                                                {isBulkRetardEligible(ano) && (
                                                    <input
                                                        type="checkbox"
                                                        aria-label={`Sélectionner le retard de ${ano.nom || ''} ${ano.prenom || ''} du ${fmtDate(ano.datePointage)}`}
                                                        checked={selectedRetards.has(String(ano.idPointage))}
                                                        disabled={isLocked || bulkRegularising}
                                                        onChange={() => toggleRetardSelection(ano)}
                                                        className="accent-[var(--color-brand-500)] cursor-pointer disabled:cursor-not-allowed"
                                                    />
                                                )}
                                            </Td>
                                            {/* Employé */}
                                            <Td>
                                                <div className="flex items-center gap-2">
                                                    <Avatar
                                                        src={ano.photoUrl}
                                                        name={`${ano.nom || ''} ${ano.prenom || ''}`}
                                                        size="sm"
                                                    />
                                                    <div>
                                                        <div className="font-bold text-[12px] text-[var(--color-fg)]">
                                                            <Link
                                                                to={`/employes/${ano.idEmploye}?tab=pointage`}
                                                                className="hover:text-[var(--color-brand-600)] hover:underline transition-colors"
                                                                title={`Consulter le détail pointage de ${ano.nom} ${ano.prenom}`}
                                                            >
                                                                {ano.nom} {ano.prenom}
                                                            </Link>
                                                        </div>
                                                <div className="text-xs font-mono text-[var(--color-fg-muted)]">
                                                            {ano.matricule}
                                                        </div>
                                                    </div>
                                                </div>
                                            </Td>

                                            {/* Semaine */}
                                            <Td className="text-center font-bold text-xs">
                                                <span className="inline-block px-1.5 py-0.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-[10px] font-mono">
                                                    {ano.semaineCode || '—'}
                                                </span>
                                            </Td>

                                            {/* Date */}
                                            <Td className="text-xs font-data">
                                                {fmtDate(ano.datePointage)}
                                            </Td>

                                            {/* Type & Description */}
                                            <Td>
                                                <div className="font-semibold text-xs text-[var(--color-fg)]">
                                                    {ano.typeAnomalie === 'INCOMPLET' ? 'Pointage incomplet' :
                                                     ano.typeAnomalie === 'RETARD' ? `Retard (${ano.minutesRetard} min)` :
                                                     ano.typeAnomalie === 'ABSENCE' ? (ano.demandeEnAttenteLibelle ? `Absence (${ano.demandeEnAttenteLibelle} en attente)` : 'Absence non justifiée') :
                                                     ano.typeAnomalie}
                                                </div>
                                                <div className="text-[11px] text-[var(--color-fg-muted)] truncate max-w-xs">
                                                    {ano.motifAjustement ? (
                                                        <span className="text-[var(--color-brand-500)] font-medium">
                                                            Motif : {ano.motifAjustement}
                                                        </span>
                                                    ) : (
                                                        ano.descriptionAnomalie
                                                    )}
                                                </div>
                                            </Td>

                                            {/* Sévérité Chip */}
                                            <Td className="text-center">
                                                <Badge variant={severity.badge} size="xs">{severity.label}</Badge>
                                            </Td>

                                            {/* Statut */}
                                            <Td className="text-center">
                                                {isDone ? (
                                                    <span className="text-[var(--color-brand-500)] font-semibold inline-flex items-center gap-1 text-[11px]">
                                                        <CheckCircle2 size={12} />
                                                        Régularisé
                                                    </span>
                                                ) : (
                                                    <span className="text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)] font-semibold inline-flex items-center gap-1 text-[11px]">
                                                        <Clock size={12} />
                                                        {ano.typeAnomalie === 'RETARD' || ano.statutCode === 'ABSENCE_JUSTIFIEE'
                                                            ? 'À vérifier' : 'À régulariser'}
                                                    </span>
                                                )}
                                            </Td>

                                            {/* Action */}
                                            <Td className="text-right">
                                                <Link
                                                    to={employeeDayUrl(ano.idEmploye, ano.datePointage)}
                                                    className="mr-2 inline-flex min-h-[44px] items-center text-xs font-semibold text-[var(--color-brand-600)] hover:underline"
                                                >
                                                    Voir la journée
                                                </Link>
                                                <Button
                                                    variant={isDone ? 'ghost' : 'outline'}
                                                    size="sm"
                                                    onClick={() => handleOpenRegularisation(ano)}
                                                    disabled={isLocked}
                                                    className="font-bold text-xs min-h-[44px] inline-flex items-center justify-center"
                                                    aria-label={`${isDone ? 'Modifier la régularisation de' : 'Régulariser l\'anomalie de'} ${ano.nomComplet || `${ano.nom || ''} ${ano.prenom || ''}`.trim() || 'ce collaborateur'} du ${ano.datePointage || ano.date || ''}`.trim()}
                                                >
                                                    {isDone ? 'Modifier' : ano.typeAnomalie === 'RETARD' || ano.statutCode === 'ABSENCE_JUSTIFIEE'
                                                        ? 'Vérifier' : 'Régulariser'}
                                                </Button>
                                            </Td>
                                        </Tr>
                                    );
                                })
                            ) : (
                                <Tr>
                                    <Td colSpan={8} className="py-12 text-center text-xs text-[var(--color-fg-muted)]">
                                        {loading ? 'Chargement des anomalies…' : 'Aucune anomalie détectée pour cette sélection.'}
                                    </Td>
                                </Tr>
                            )}
                        </Tbody>
                    </Table>
                </div>

                {/* Pagination officielle Galaxam */}
                <Pagination
                    currentPage={currentPage}
                    totalItems={filteredAnomalies.length}
                    pageSize={pageSize}
                    pageSizeOptions={[8, 15, 25, 50]}
                    onPageChange={(p) => setCurrentPage(p)}
                    onPageSizeChange={(sz) => { setPageSize(sz); setCurrentPage(1); }}
                />
            {/* La décision de transmission reste au pied du tableau, dans sa même surface. */}
                <div className="border-t border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/50 p-3 sm:p-4">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                        <div className="flex min-w-0 items-start gap-2 text-sm">
                            {!canTransmitDaf ? (
                                <AlertTriangle size={16} className={`mt-0.5 shrink-0 ${nbBloquantes > 0 ? 'text-[var(--color-danger-600)]' : 'text-[var(--color-warning-600)]'}`} />
                            ) : (
                                <CheckCircle2 size={16} className="mt-0.5 shrink-0 text-[var(--color-success-600)]" />
                            )}
                            <div>
                                <p className="font-semibold text-[var(--color-fg)]">
                                    {transmissionBlockTitle}
                                </p>
                                {transmissionBlockSubtitle && (
                                    <p className="mt-0.5 text-xs text-[var(--color-fg-muted)]">
                                        {transmissionBlockSubtitle}
                                    </p>
                                )}
                                {hasJourneesManquantes && isCurrentSiteClean && otherIncompleteSites.length > 0 && (
                                    <div className="flex flex-wrap items-center gap-2 mt-2">
                                        <button
                                            type="button"
                                            onClick={() => handleSwitchSite(0)}
                                            className="text-xs font-semibold text-[var(--color-brand-600)] hover:underline"
                                        >
                                            Afficher tous les sites (Vue globale)
                                        </button>
                                        {otherIncompleteSites[0] && (
                                            <>
                                                <span className="text-xs text-[var(--color-fg-subtle)]">·</span>
                                                <button
                                                    type="button"
                                                    onClick={() => handleSwitchSite(otherIncompleteSites[0].idSite || otherIncompleteSites[0].id)}
                                                    className="text-xs font-semibold text-[var(--color-brand-600)] hover:underline"
                                                >
                                                    Basculer sur {otherIncompleteSites[0].codeSite || otherIncompleteSites[0].code}
                                                </button>
                                            </>
                                        )}
                                    </div>
                                )}
                            </div>
                        </div>

                        <div className="flex flex-wrap items-center justify-end gap-2">
                            <Button
                                type="button"
                                variant="primary"
                                size="sm"
                                onClick={() => onTabChange?.('arbitrage')}
                                iconRight={<ArrowRight size={14} />}
                            >
                                {isDaf ? 'Ouvrir le contrôle DAF' : 'Consulter la synthèse'}
                            </Button>
                        </div>
                    </div>
                </div>
            </div>

            {/* Modale de régularisation intelligente (Lazy Loaded) */}
            {isModalOpen && (
                <Suspense fallback={null}>
                    <AnomalieRegularisationModal
                        isOpen={isModalOpen}
                        onClose={() => {
                            setIsModalOpen(false);
                            setSelectedAnomalie(null);
                        }}
                        anomalie={selectedAnomalie}
                        onSuccess={handleSaveRegularisation}
                    />
                </Suspense>
            )}

            {/* Dialogue de confirmation de transmission pour validation */}
            <ConfirmDialog
                isOpen={isBulkConfirmOpen}
                onClose={() => !bulkRegularising && setIsBulkConfirmOpen(false)}
                onConfirm={handleBulkConfirmRetards}
                title="Confirmer plusieurs retards réels"
                message={`Confirmer comme réels les ${selectedRetards.size} retard${selectedRetards.size > 1 ? 's' : ''} sélectionnés ? Les horaires et les minutes de retard ne seront pas modifiés. Chaque pointage sera vérifié avec sa version actuelle.`}
                confirmLabel="Confirmer les retards"
                cancelLabel="Annuler"
                variant="primary"
                loading={bulkRegularising}
            />

        </div>
    );
}
