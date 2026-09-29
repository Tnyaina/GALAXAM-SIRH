import { useState, useEffect, useMemo, lazy, Suspense } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import {
    Clock,
    AlertTriangle,
    CheckCircle2,
    Calendar,
    Send,
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
import { periodePaieService } from '@/features/attendance/api/attendanceApi';
import usePointageAnomalies from '@/features/attendance/hooks/usePointageAnomalies';
import Pagination from '@/components/ui/navigation/Pagination';
import { getErrorMessage } from '@/api/apiClient';

const AnomalieRegularisationModal = lazy(() => import('@/features/attendance/components/modals/AnomalieRegularisationModal'));

const SEVERITY_PRESENTATION = {
    CRITIQUE: { label: 'Critique', badge: 'danger', border: 'border-l-[var(--color-danger-500)]' },
    HAUTE: { label: 'Élevée', badge: 'warning', border: 'border-l-[var(--color-warning-500)]' },
    MOYENNE: { label: 'Moyenne', badge: 'info', border: 'border-l-[var(--color-info-500)]' },
    FAIBLE: { label: 'Faible', badge: 'neutral', border: 'border-l-[var(--color-border)]' },
};

export default function SuiviAnomaliesPage({ activePeriode, onSelectPeriode, onTabChange }) {
    // Contexte, navigation et hook métier
    const [searchParams, setSearchParams] = useSearchParams();
    const { hasPermission } = useAuth();
    const toastContext = useToast();
    const toast = toastContext?.toast || toastContext;
    const isDaf = hasPermission('POINTAGE_VALIDATE');

    // État local
    const [periodes, setPeriodes] = useState([]);
    const [selectedPeriodeId, setSelectedPeriodeId] = useState('');
    const [sites, setSites] = useState([]);
    const [selectedSiteId, setSelectedSiteId] = useState(0);

    // Filtres anomalies & Pagination
    const [searchQuery, setSearchQuery] = useState('');
    const [filterType, setFilterType] = useState('ALL');
    const [filterSeverite, setFilterSeverite] = useState('ALL');
    const [filterSemaine, setFilterSemaine] = useState('ALL'); // 'ALL', 'S1', 'S2', ...
    const [filterDate, setFilterDate] = useState('');
    const [filterStatutTraitement, setFilterStatutTraitement] = useState('ALL'); // 'ALL', 'TODO', 'DONE'
    const [isSemainesCollapsed, setIsSemainesCollapsed] = useState(true);
    const [showAdvancedFilters, setShowAdvancedFilters] = useState(false);
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(8);

    // Modale de régularisation
    const [selectedAnomalie, setSelectedAnomalie] = useState(null);
    const [isModalOpen, setIsModalOpen] = useState(false);

    // État de transmission DAF
    const [transmitting, setTransmitting] = useState(false);
    const [showTransmissionConfirm, setShowTransmissionConfirm] = useState(false);

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
    } = usePointageAnomalies();

    // Effets
    useEffect(() => {
        const init = async () => {
            try {
                const paramPeriode = searchParams.get('periode');
                const [resPeriodes, resSites, context] = await Promise.all([
                    periodePaieService.getAllPeriodes(),
                    organisationService.getSites(),
                    periodePaieService.getContext(paramPeriode ? Number(paramPeriode) : null)
                ]);
                if (resPeriodes && resPeriodes.length > 0) {
                    setPeriodes(resPeriodes);
                    const active = (activePeriode ? resPeriodes.find(p => p.idPeriodePaie === activePeriode.idPeriodePaie) : null)
                        || resPeriodes.find(p => String(p.idPeriodePaie) === String(context?.selectedPeriod?.id));
                    if (active) {
                        const pid = String(active.idPeriodePaie);
                        setSelectedPeriodeId(pid);
                        updateFilters({ idPeriodePaie: active.idPeriodePaie });
                        if (!paramPeriode) {
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
            const pid = String(activePeriode.idPeriodePaie);
            setSelectedPeriodeId(pid);
            updateFilters({ idPeriodePaie: activePeriode.idPeriodePaie });
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

    // Période courante
    // Données dérivées
    const currentPeriodeObj = useMemo(() =>
        periodes.find(p => String(p.idPeriodePaie) === String(selectedPeriodeId)) || null,
    [periodes, selectedPeriodeId]);

    const actions = currentPeriodeObj?.availableActions || {};
    const isLocked = actions.REGULARIZE_ANOMALY?.allowed !== true;

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
        BLOCKING_ANOMALIES: 'Des anomalies bloquantes doivent être régularisées.',
    }[code] || code)).join(' · ');

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
            return true;
        } else {
            toast.error(res?.error || res?.message || 'Erreur lors de la régularisation.');
            return res || false;
        }
    };


    // Transmission officielle à la DAF
    const handleTransmettreDaf = async () => {
        if (!selectedPeriodeId || !canTransmitDaf) return;
        setTransmitting(true);
        try {
            const siteParam = Number(selectedSiteId) > 0 ? Number(selectedSiteId) : undefined;
            const res = await periodePaieService.executeCommand(currentPeriodeObj.idPeriodePaie, 'SUBMIT', {
                version: currentPeriodeObj.version,
                idSite: siteParam
            });
            if (res?.decision === 'ALLOWED') {
                toast.success('Période transmise pour validation avec succès.');
                window.dispatchEvent(new Event('pointage-period-updated'));
                setShowTransmissionConfirm(false);
                const updatedList = await periodePaieService.getAllPeriodes();
                if (updatedList) setPeriodes(updatedList);
                await loadAnomalies();
            } else {
                toast.error(res?.message || 'Erreur lors de la transmission.');
            }
        } catch (err) {
            console.error('Erreur transmission validation:', err);
            toast.error(getErrorMessage(err, 'Impossible de transmettre la période pour validation.'));
        } finally {
            setTransmitting(false);
        }
    };

    // Rendu
    return (
        <div className="space-y-4 font-sans animate-in fade-in duration-300">
            <SEO
                title="Suivi &amp; Anomalies — Pointage | GALAXAM SIRH"
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
                        <Select
                            value={String(selectedSiteId)}
                            onChange={(val) => {
                                const sid = Number(val);
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
                                    const name = s.libelle || s.nom || '';
                                    return {
                                        value: String(s.idSite || s.id),
                                        label: code ? `${code} — ${name}` : name
                                    };
                                })
                            ]}
                            className="text-xs h-[32px]"
                        />
                    </div>
                </div>
            </div>

            {/* Les repères prioritaires avant le détail et les filtres. */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                <StatCard
                    title="Anomalies actives"
                    value={kpis?.anomaliesActives ?? 0}
                    subtext="À examiner sur la période"
                    icon={<AlertTriangle size={17} />}
                    color={Number(kpis?.anomaliesActives || 0) > 0 ? 'warning' : 'neutral'}
                />
                <StatCard
                    title="Pointages saisis"
                    value={`${kpis?.joursSaisis ?? 0} / ${kpis?.joursTheoriques ?? 0}`}
                    subtext="Jours saisis / jours attendus"
                    icon={<Calendar size={17} />}
                    color="brand"
                />
                <StatCard
                    title="Bloquantes avant transmission"
                    value={nbBloquantes}
                    subtext="Valeur fournie par le contrôle des anomalies"
                    icon={<Shield size={17} />}
                    color={nbBloquantes > 0 ? 'danger' : 'success'}
                />
            </div>

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
                                        sem.isCurrent ? 'bg-brand-50/20 dark:bg-brand-950/10' : ''
                                    } ${isSelected ? 'bg-brand-50/50 dark:bg-brand-950/30 border-l-4 border-l-[var(--color-brand-500)]' : 'hover:bg-[var(--color-bg-subtle)]'}`}
                                >
                                    <div className="flex items-center justify-between">
                                        <span className="font-bold text-xs text-[var(--color-fg)]">
                                            {sem.titre}
                                        </span>
                                        {sem.isCurrent && (
                                            <span className="text-[10px] font-bold text-[var(--color-brand-500)] uppercase bg-brand-50 dark:bg-brand-950/40 px-1.5 py-0.5 border border-[var(--color-brand-500)]/30">
                                                En cours
                                            </span>
                                        )}
                                    </div>
                                    <div className="text-[11px] text-[var(--color-fg-muted)] mt-0.5 mb-2.5 font-mono">
                                        {sem.dates}
                                    </div>

                                    {/* Ligne Saisis */}
                                    <div className="flex items-center justify-between text-xs mb-1">
                                        <span className="text-[var(--color-fg-muted)]">Saisis</span>
                                        <span className="font-mono font-semibold text-[var(--color-fg)]">
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
                                        <span className="font-mono font-semibold text-[var(--color-fg)]">
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
                            Anomalies de pointage &amp; retards à vérifier
                        </h2>
                        <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                            {filteredAnomalies.length} élément{filteredAnomalies.length > 1 ? 's' : ''} de suivi
                            {nbBloquantes > 0 && ` — dont ${nbBloquantes} bloquante(s) avant transmission`}
                        </p>
                    </div>

                    <div className="grid grid-cols-1 gap-2 lg:grid-cols-[minmax(16rem,1fr)_13rem_auto] lg:items-center">
                        <div className="min-w-0">
                            <Input
                                icon={<Search size={13} className="text-[var(--color-fg-muted)]" />}
                                placeholder="Rechercher nom, matricule…"
                                value={searchQuery}
                                onChange={(e) => {
                                    setSearchQuery(e.target.value);
                                    setCurrentPage(1);
                                }}
                                className="text-xs"
                            />
                        </div>
                        <Select
                            aria-label="Filtrer les anomalies par traitement"
                            value={filterStatutTraitement}
                            onChange={(val) => {
                                setFilterStatutTraitement(val);
                                setCurrentPage(1);
                            }}
                            options={[
                                { value: 'ALL', label: 'Tous les statuts' },
                                { value: 'TODO', label: 'À vérifier / régulariser' },
                                { value: 'DONE', label: 'Déjà régularisés' },
                            ]}
                            className="text-xs w-full"
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
                            <Select
                                aria-label="Filtrer par type d'anomalie"
                                value={filterType}
                                onChange={(val) => { setFilterType(val); setCurrentPage(1); }}
                                options={[
                                    { value: 'ALL', label: 'Tous les types' },
                                    { value: 'ABSENCE', label: 'Absence non justifiée' },
                                    { value: 'INCOMPLET', label: 'Pointage incomplet' },
                                    { value: 'RETARD', label: 'Retards' },
                                ]}
                                className="text-xs w-full"
                            />
                            <Select
                                aria-label="Filtrer par sévérité"
                                value={filterSeverite}
                                onChange={(val) => { setFilterSeverite(val); setCurrentPage(1); }}
                                options={[
                                    { value: 'ALL', label: 'Toutes les sévérités' },
                                    { value: 'CRITIQUE', label: 'Critique · bloquante' },
                                    { value: 'HAUTE', label: 'Élevée' },
                                    { value: 'MOYENNE', label: 'Moyenne' },
                                    { value: 'FAIBLE', label: 'Faible' },
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

                {/* Tableau des anomalies */}
                <div className="overflow-x-auto border-b border-[var(--color-border-subtle)]">
                    <Table density="compact" divided bordered={false}>
                        <Thead className="sticky top-0 bg-[var(--color-surface)] z-10 shadow-xs">
                            <Tr>
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
                                                                className="hover:text-brand-600 hover:underline transition-colors"
                                                                title={`Consulter le détail pointage de ${ano.nom} ${ano.prenom}`}
                                                            >
                                                                {ano.nom} {ano.prenom}
                                                            </Link>
                                                        </div>
                                                        <div className="text-[10px] font-mono text-[var(--color-fg-muted)]">
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
                                                        {ano.typeAnomalie === 'RETARD' ? 'À vérifier' : 'À régulariser'}
                                                    </span>
                                                )}
                                            </Td>

                                            {/* Action */}
                                            <Td className="text-right">
                                                <Button
                                                    variant={isDone ? 'ghost' : 'outline'}
                                                    size="xs"
                                                    onClick={() => handleOpenRegularisation(ano)}
                                                    disabled={isLocked}
                                                    className="font-bold text-[11px] h-[28px]"
                                                >
                                                    {isDone ? 'Modifier' : ano.typeAnomalie === 'RETARD' ? 'Vérifier' : 'Régulariser'}
                                                </Button>
                                            </Td>
                                        </Tr>
                                    );
                                })
                            ) : (
                                <Tr>
                                    <Td colSpan={7} className="py-12 text-center text-xs text-[var(--color-fg-muted)]">
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
                                    {canTransmitDaf
                                        ? 'La période peut être transmise pour contrôle.'
                                        : transmissionBlockReason || 'Le workflow ne permet pas encore la transmission.'}
                                </p>
                                {nbBloquantes > 0 && !canTransmitDaf && (
                                    <p className="mt-0.5 text-xs text-[var(--color-fg-muted)]">
                                        {nbBloquantes} anomalie(s) bloquante(s) recensée(s) par le contrôle.
                                    </p>
                                )}
                            </div>
                        </div>

                        <div className="flex flex-wrap items-center justify-end gap-2">
                            {canTransmitDaf && (
                                <Button
                                    type="button"
                                    variant="outline"
                                    size="sm"
                                    onClick={() => setShowTransmissionConfirm(true)}
                                    disabled={transmitting}
                                    loading={transmitting}
                                    iconLeft={<Send size={14} />}
                                >
                                    Transmettre pour validation
                                </Button>
                            )}
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
                isOpen={showTransmissionConfirm}
                onClose={() => setShowTransmissionConfirm(false)}
                onConfirm={handleTransmettreDaf}
                title="Confirmer la transmission pour validation"
                message="Êtes-vous sûr de vouloir transmettre la période de pointage pour validation ? Cette action fige la saisie pour les agents de site et transmet le dossier pour validation finale."
                confirmLabel="Confirmer et transmettre"
                cancelLabel="Annuler"
                variant="primary"
                loading={transmitting}
            />
        </div>
    );
}
