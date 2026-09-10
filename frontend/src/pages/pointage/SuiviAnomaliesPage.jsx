import { useState, useEffect, useMemo, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
    Clock,
    AlertTriangle,
    CheckCircle2,
    Calendar,
    Send,
    Search,
    Shield,
    RefreshCw,
    Building2,
    Layers,
    Filter,
    Users,
    Banknote,
    Lock,
    Eye,
    XCircle
} from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Select from '@/components/ui/forms/Select';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Badge from '@/components/ui/data-display/Badge';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import SEO from '@/components/system/SEO';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/ui/feedback/Toast';
import { Avatar } from '@/components/ui/data-display/Avatar';
import { getFullPhotoUrl } from '@/utils/fileUtils';
import { fmtDate } from '@/utils/carriereUtils';
import organisationService from '@/api/admin/organisationService';
import periodePaieService from '@/services/periodePaieService';
import pointageSyntheseService from '@/api/pointage/pointageSyntheseService';
import usePointageAnomalies from '@/hooks/pointage/usePointageAnomalies';
import Pagination from '@/components/ui/navigation/Pagination';
import AnomalieRegularisationModal from '@/components/pointage/AnomalieRegularisationModal';

export default function SuiviAnomaliesPage({ activePeriode, onSelectPeriode, onTabChange }) {
    const [searchParams, setSearchParams] = useSearchParams();
    const { hasPermission } = useAuth();
    const toastContext = useToast();
    const toast = toastContext?.toast || toastContext;
    const canWrite = hasPermission('POINTAGE_WRITE');

    // Sélecteurs de contexte (Période & Site)
    const [periodes, setPeriodes] = useState([]);
    const [selectedPeriodeId, setSelectedPeriodeId] = useState('');
    const [sites, setSites] = useState([]);
    const [selectedSiteId, setSelectedSiteId] = useState(0);

    // Filtres anomalies & Pagination
    const [searchQuery, setSearchQuery] = useState('');
    const [filterType, setFilterType] = useState('ALL');
    const [filterSeverite, setFilterSeverite] = useState('ALL');
    const [filterSemaine, setFilterSemaine] = useState('ALL'); // 'ALL', 'S1', 'S2', ...
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(8);

    // Modale de régularisation
    const [selectedAnomalie, setSelectedAnomalie] = useState(null);
    const [isModalOpen, setIsModalOpen] = useState(false);

    // État de transmission DAF & calculs
    const [transmitting, setTransmitting] = useState(false);
    const [calculating, setCalculating] = useState(false);
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

    // Horodatage de dernière mise à jour
    const [lastUpdated, setLastUpdated] = useState(() => new Date().toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' }));

    // Chargement initial des référentiels (périodes & sites)
    useEffect(() => {
        const init = async () => {
            try {
                const [resPeriodes, resSites] = await Promise.all([
                    periodePaieService.getAllPeriodes(),
                    organisationService.getSites()
                ]);
                if (resPeriodes && resPeriodes.length > 0) {
                    setPeriodes(resPeriodes);
                    const paramPeriode = searchParams.get('periode');
                    const found = paramPeriode ? resPeriodes.find(p => String(p.idPeriodePaie) === paramPeriode) : null;
                    const active = (activePeriode ? resPeriodes.find(p => p.idPeriodePaie === activePeriode.idPeriodePaie) : null)
                        || found
                        || resPeriodes.find(p => p.statutCode === 'EN_COURS')
                        || resPeriodes[0];
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
            setLastUpdated(new Date().toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' }));
        }
    }, [selectedPeriodeId, selectedSiteId, searchQuery]); // eslint-disable-line react-hooks/exhaustive-deps

    // Période courante
    const currentPeriodeObj = useMemo(() =>
        periodes.find(p => String(p.idPeriodePaie) === String(selectedPeriodeId)) || null,
    [periodes, selectedPeriodeId]);

    const stCode = currentPeriodeObj?.statutCode;
    const isEnCours = stCode === 'EN_COURS' || stCode === 'OUVERTE' || !stCode;
    const isAValider = stCode === 'A_VALIDER' || stCode === 'TRANSMIS_DAF';
    const isValidee = stCode === 'VALIDEE_DAF';
    const isCloturee = stCode === 'CLOTUREE';
    const isLocked = isAValider || isValidee || isCloturee;

    // Anomalies bloquantes pour la transmission DAF
    const nbBloquantes = useMemo(() => {
        return (anomalies || []).filter(a => a.bloquantTransmission && a.statutAnomalie !== 'done').length;
    }, [anomalies]);

    // Semaines restantes non saisies / non clôturées
    const nbSemainesRestantes = useMemo(() => {
        return (semaines || []).filter(s => s.statutSemaine !== 'CLOTUREE' && (s.progressPercent || 0) < 100).length;
    }, [semaines]);

    // Filtrage dynamique des anomalies
    const filteredAnomalies = useMemo(() => {
        return (anomalies || []).filter(ano => {
            // Filtre par semaine sélectionnée dans les cartes S1..S5
            if (filterSemaine !== 'ALL' && ano.semaineCode !== filterSemaine) {
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
    }, [anomalies, filterSemaine, filterSeverite, filterType, searchQuery]);

    // Pagination
    const totalPages = Math.ceil(filteredAnomalies.length / pageSize) || 1;
    const paginatedAnomalies = useMemo(() => {
        const start = (currentPage - 1) * pageSize;
        return filteredAnomalies.slice(start, start + pageSize);
    }, [filteredAnomalies, currentPage, pageSize]);

    // Gestion de l'ouverture de la modale de régularisation
    const handleOpenRegularisation = (anomalie) => {
        setSelectedAnomalie(anomalie);
        setIsModalOpen(true);
    };

    // Callback de succès après régularisation
    const handleSaveRegularisation = async (regularisationData) => {
        if (!selectedAnomalie) return;
        const res = await regulariser(selectedAnomalie.idPointage, regularisationData);
        if (res && res.success) {
            setIsModalOpen(false);
            setSelectedAnomalie(null);
            toast.success(res.message || 'Anomalie régularisée avec succès.');
        } else {
            toast.error(res?.error || 'Erreur lors de la régularisation.');
        }
    };

    // Recalcul manuel des synthèses hebdomadaires
    const handleRecalculerSemaines = async () => {
        if (!selectedPeriodeId) return;
        setCalculating(true);
        try {
            const siteParam = Number(selectedSiteId) > 0 ? Number(selectedSiteId) : undefined;
            const res = await pointageSyntheseService.calculerTout({
                idPeriodePaie: Number(selectedPeriodeId),
                idSite: siteParam
            });
            await loadAnomalies();
            toast.success(res?.message || 'Synthèses hebdomadaires et anomalies recalculées avec succès.');
        } catch (err) {
            console.error('Erreur calcul semaines:', err);
            toast.error(err?.response?.data?.error || 'Erreur lors du recalcul des semaines.');
        } finally {
            setCalculating(false);
        }
    };

    // Transmission officielle à la DAF
    const handleTransmettreDaf = async () => {
        if (!selectedPeriodeId || nbBloquantes > 0) return;
        setTransmitting(true);
        try {
            const siteParam = Number(selectedSiteId) > 0 ? Number(selectedSiteId) : undefined;
            const res = await pointageSyntheseService.transmettreDaf({
                idPeriodePaie: Number(selectedPeriodeId),
                idSite: siteParam
            });
            if (res && res.success) {
                toast.success('Période transmise à la DAF avec succès.');
                setShowTransmissionConfirm(false);
                const updatedList = await periodePaieService.getAllPeriodes();
                if (updatedList) setPeriodes(updatedList);
                await loadAnomalies();
                if (onTabChange) {
                    onTabChange('arbitrage');
                }
            } else {
                toast.error(res?.message || 'Erreur lors de la transmission.');
            }
        } catch (err) {
            console.error('Erreur transmission DAF:', err);
            toast.error(err?.response?.data?.error || 'Impossible de transmettre la période à la DAF.');
        } finally {
            setTransmitting(false);
        }
    };

    return (
        <div className="space-y-4 pb-32 font-sans animate-in fade-in duration-300">
            <SEO
                title="Suivi &amp; Anomalies — Pointage | GALAXAM SIRH"
                description="Suivi de la progression hebdomadaire, détection proactive et régularisation d'anomalies de pointage."
            />

            {/* ---------- HEADER CONTEXTE & ACTIONS (COHÉRENCE DAF) ---------- */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 shadow-xs">
                <div className="flex items-center gap-3 flex-wrap">
                    {/* Période */}
                    <div className="flex items-center gap-1.5 min-w-[220px]">
                        <Calendar size={14} className="text-[var(--color-brand-500)] shrink-0" />
                        <Select
                            value={String(selectedPeriodeId || '')}
                            onChange={(val) => {
                                const pid = String(val);
                                setSelectedPeriodeId(pid);
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
                                label: p.libelle || p.code
                            }))}
                            className="text-xs h-[32px]"
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
                                ...sites.map(s => ({ value: String(s.idSite || s.id), label: `${s.code} — ${s.libelle || s.nom}` }))
                            ]}
                            className="text-xs h-[32px]"
                        />
                    </div>
                </div>

                <div className="flex items-center gap-2 shrink-0 ml-auto">
                    <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        loading={calculating}
                        onClick={handleRecalculerSemaines}
                        icon={<RefreshCw size={13} className={calculating ? 'animate-spin' : ''} />}
                        className="text-xs h-[32px] font-semibold"
                        title="Recalculer et actualiser l'ensemble des semaines et cumuls"
                    >
                        <span className="hidden md:inline">Actualiser les calculs</span>
                        <span className="md:hidden">Actualiser</span>
                    </Button>
                </div>
            </div>

            {/* ---------- KPI CARDS — 5 INDICATEURS CLÉS ---------- */}
            <div className="grid grid-cols-2 sm:grid-cols-5 gap-2.5">
                {[
                    { label: 'Salariés suivis', value: kpis?.collaborateursSuivis || 0, icon: Users, color: 'text-brand-600 dark:text-brand-400' },
                    { label: 'Jours pointés', value: `${kpis?.joursSaisis || 0} / ${kpis?.joursTheoriques || 0} j`, icon: Calendar, color: 'text-sky-600 dark:text-sky-400' },
                    { label: 'Heures travaillées', value: `${Math.round(kpis?.heuresCumulees || 0)}h`, icon: Clock, color: 'text-indigo-600 dark:text-indigo-400' },
                    { label: 'Anomalies actives', value: `${kpis?.anomaliesActives || 0}`, badge: kpis?.anomaliesCritiques > 0 ? `${kpis.anomaliesCritiques} crit.` : null, icon: AlertTriangle, color: 'text-amber-600 dark:text-amber-400' },
                    { label: 'Indemnités prév.', value: `${Math.round(kpis?.indemnitesPrevisionnelles || 0).toLocaleString('fr-FR')} Ar`, icon: Banknote, color: 'text-emerald-600 dark:text-emerald-400' },
                ].map(kpi => (
                    <div key={kpi.label} className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 flex items-center gap-3">
                        <kpi.icon size={20} className={`${kpi.color} shrink-0`} />
                        <div className="min-w-0">
                            <div className="text-[11px] font-semibold text-[var(--color-fg-muted)] truncate">{kpi.label}</div>
                            <div className="text-base sm:text-lg font-bold font-mono text-[var(--color-fg)] flex items-center gap-1.5">
                                <span>{kpi.value}</span>
                                {kpi.badge && (
                                    <span className="text-[10px] font-sans px-1.5 py-0.2 bg-rose-50 text-rose-700 dark:bg-rose-950/50 dark:text-rose-300 font-bold border border-rose-200">
                                        {kpi.badge}
                                    </span>
                                )}
                            </div>
                        </div>
                    </div>
                ))}
            </div>

            {/* ---------- VUE HEBDOMADAIRE (S1 - S5) INTERACTIVE ---------- */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] overflow-hidden">
                <div className="p-3 border-b border-[var(--color-border-subtle)] flex items-center justify-between flex-wrap gap-2">
                    <div>
                        <h2 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                            <Layers size={14} className="text-[var(--color-brand-500)]" />
                            Progression hebdomadaire (S1 — S5)
                        </h2>
                        <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                            Avancement réel de la saisie. Cliquez sur une semaine pour filtrer le tableau des anomalies.
                        </p>
                    </div>
                    {filterSemaine !== 'ALL' && (
                        <Button
                            variant="ghost"
                            size="xs"
                            onClick={() => setFilterSemaine('ALL')}
                            className="text-xs text-[var(--color-brand-500)]"
                        >
                            Afficher toutes les semaines (Annuler le filtre {filterSemaine})
                        </Button>
                    )}
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-5 divide-y md:divide-y-0 md:divide-x divide-[var(--color-border-subtle)]">
                    {semaines && semaines.length > 0 ? (
                        semaines.map((sem) => {
                            const isSelected = filterSemaine === sem.semaineCode;
                            return (
                                <div
                                    key={sem.semaineCode}
                                    onClick={() => setFilterSemaine(prev => prev === sem.semaineCode ? 'ALL' : sem.semaineCode)}
                                    className={`p-3.5 transition-all cursor-pointer relative group ${
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
                                            <span className="inline-flex items-center gap-1 text-[10px] font-bold px-1.5 py-0.5 bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 border border-amber-200 dark:border-amber-800">
                                                <AlertTriangle size={10} />
                                                {sem.nbAnomalies} anomalie{sem.nbAnomalies > 1 ? 's' : ''}
                                            </span>
                                        ) : sem.joursSaisis > 0 ? (
                                            <span className="inline-flex items-center gap-1 text-[10px] font-bold px-1.5 py-0.5 bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800">
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
            </div>

            {/* ---------- SECTION ANOMALIES DE POINTAGE ---------- */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] overflow-hidden" id="anomalies">
                {/* Header & Filtres */}
                <div className="p-3 border-b border-[var(--color-border-subtle)] flex items-center justify-between flex-wrap gap-3">
                    <div>
                        <h2 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                            <Shield size={14} className="text-[var(--color-brand-500)]" />
                            Anomalies de pointage &amp; Saisies incomplètes
                        </h2>
                        <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                            {filteredAnomalies.length} anomalie{filteredAnomalies.length > 1 ? 's' : ''} détectée{filteredAnomalies.length > 1 ? 's' : ''}
                            {nbBloquantes > 0 && ` — dont ${nbBloquantes} bloquante(s) avant transmission DAF`}
                        </p>
                    </div>

                    <div className="flex items-center gap-2 flex-wrap">
                        {/* Recherche Employé */}
                        <div className="relative">
                            <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" />
                            <input
                                type="text"
                                placeholder="Rechercher nom, matricule…"
                                value={searchQuery}
                                onChange={(e) => {
                                    setSearchQuery(e.target.value);
                                    setCurrentPage(1);
                                }}
                                className="text-xs pl-7 pr-3 py-1 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] text-[var(--color-fg)] w-44 focus:w-56 transition-all focus:border-[var(--color-brand-500)] outline-none"
                            />
                        </div>

                        {/* Filtre Type */}
                        <select
                            value={filterType}
                            onChange={(e) => {
                                setFilterType(e.target.value);
                                setCurrentPage(1);
                            }}
                            className="text-xs py-1 px-2.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] text-[var(--color-fg)] outline-none focus:border-[var(--color-brand-500)] cursor-pointer"
                        >
                            <option value="ALL">Tous les types</option>
                            <option value="ABSENCE">Absence non justifiée</option>
                            <option value="INCOMPLET">Pointage incomplet / Non saisi</option>
                            <option value="RETARD">Retards</option>
                        </select>

                        {/* Filtre Sévérité */}
                        <select
                            value={filterSeverite}
                            onChange={(e) => {
                                setFilterSeverite(e.target.value);
                                setCurrentPage(1);
                            }}
                            className="text-xs py-1 px-2.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] text-[var(--color-fg)] outline-none focus:border-[var(--color-brand-500)] cursor-pointer"
                        >
                            <option value="ALL">Toutes sévérités</option>
                            <option value="CRITIQUE">Critique (Bloquant DAF)</option>
                            <option value="HAUTE">Élevée</option>
                            <option value="MOYENNE">Moyenne</option>
                            <option value="FAIBLE">Faible</option>
                        </select>

                        {/* Reset filtre semaine si actif */}
                        {filterSemaine !== 'ALL' && (
                            <button
                                type="button"
                                onClick={() => setFilterSemaine('ALL')}
                                className="inline-flex items-center gap-1 text-[11px] font-bold px-2 py-0.5 bg-brand-50 dark:bg-brand-950/40 text-[var(--color-brand-500)] border border-[var(--color-brand-500)]"
                            >
                                <span>{filterSemaine}</span>
                                <span className="text-xs">×</span>
                            </button>
                        )}
                    </div>
                </div>

                {/* Légende de sévérité */}
                <div className="px-3 py-2 bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] flex items-center gap-5 text-[11px] text-[var(--color-fg-muted)] flex-wrap">
                    <span className="flex items-center gap-1.5">
                        <span className="w-2 h-2 bg-[#B23B34]" />
                        <strong className="text-[var(--color-fg)]">Critique :</strong> Bloque la transmission DAF (oubli d'horaire ou absence sans statut)
                    </span>
                    <span className="flex items-center gap-1.5">
                        <span className="w-2 h-2 bg-[#B5720A]" />
                        <strong className="text-[var(--color-fg)]">Élevée :</strong> Absence qualifiée à justifier
                    </span>
                    <span className="flex items-center gap-1.5">
                        <span className="w-2 h-2 bg-[#345C82]" />
                        <strong className="text-[var(--color-fg)]">Moyenne :</strong> Retard ou écart mineur
                    </span>
                </div>

                {/* Tableau des anomalies */}
                <Table size="sm" divided>
                    <Thead>
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
                                const sevBorderColor = 
                                    ano.severite === 'CRITIQUE' ? 'border-l-[#B23B34]' :
                                    ano.severite === 'HAUTE' ? 'border-l-[#B5720A]' :
                                    ano.severite === 'MOYENNE' ? 'border-l-[#345C82]' : 'border-l-[#6E7266]';

                                const isDone = ano.statutAnomalie === 'done';

                                return (
                                    <Tr
                                        key={`${ano.idPointage}-${ano.idEmploye}-${ano.datePointage}`}
                                        className={`border-l-4 ${sevBorderColor}`}
                                    >
                                        {/* Employé */}
                                        <Td>
                                            <div className="flex items-center gap-2">
                                                <Avatar
                                                    src={getFullPhotoUrl(ano.photoUrl)}
                                                    name={`${ano.nom || ''} ${ano.prenom || ''}`}
                                                    size="sm"
                                                />
                                                <div>
                                                    <div className="font-bold text-[var(--color-fg)]">
                                                        {ano.nom} {ano.prenom}
                                                    </div>
                                                    <div className="text-[10px] text-[var(--color-fg-muted)] font-mono">
                                                        {ano.matricule}
                                                    </div>
                                                </div>
                                            </div>
                                        </Td>

                                        {/* Semaine */}
                                        <Td className="text-center">
                                            <span className="font-mono font-bold px-1.5 py-0.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-[var(--color-fg)] text-[11px]">
                                                {ano.semaineCode || 'S1'}
                                            </span>
                                        </Td>

                                        {/* Date */}
                                        <Td className="font-mono font-medium text-[var(--color-fg)]">
                                            {fmtDate(ano.datePointage)}
                                        </Td>

                                        {/* Type & Description */}
                                        <Td>
                                            <div className="font-semibold text-[var(--color-fg)]">
                                                {ano.typeAnomalie === 'INCOMPLET' ? 'Pointage non saisi / Incomplet' :
                                                 ano.typeAnomalie === 'ABSENCE' ? 'Absence non justifiée' :
                                                 ano.typeAnomalie === 'RETARD' ? `Retard (${ano.minutesRetard || 0} min)` :
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
                                            <span className={`inline-block text-[10px] font-bold px-1.5 py-0.5 ${
                                                ano.severite === 'CRITIQUE' ? 'bg-[#FBEAE8] text-[#B23B34] dark:bg-red-950/40 dark:text-red-300 border border-red-200 dark:border-red-800' :
                                                ano.severite === 'HAUTE' ? 'bg-[#FCEFDD] text-[#B5720A] dark:bg-amber-950/40 dark:text-amber-300 border border-amber-200 dark:border-amber-800' :
                                                ano.severite === 'MOYENNE' ? 'bg-[#E8F0F7] text-[#345C82] dark:bg-blue-950/40 dark:text-blue-300 border border-blue-200 dark:border-blue-800' :
                                                'bg-[#EFEEE6] text-[#6E7266] dark:bg-zinc-800 dark:text-zinc-300 border border-zinc-200'
                                            }`}>
                                                {ano.severite === 'CRITIQUE' ? 'Critique' :
                                                 ano.severite === 'HAUTE' ? 'Élevée' :
                                                 ano.severite === 'MOYENNE' ? 'Moyenne' : 'Faible'}
                                            </span>
                                        </Td>

                                        {/* Statut */}
                                        <Td className="text-center">
                                            {isDone ? (
                                                <span className="text-[var(--color-brand-500)] font-semibold inline-flex items-center gap-1 text-[11px]">
                                                    <CheckCircle2 size={12} />
                                                    Régularisé
                                                </span>
                                            ) : (
                                                <span className="text-amber-600 dark:text-amber-400 font-semibold inline-flex items-center gap-1 text-[11px]">
                                                    <Clock size={12} />
                                                    À régulariser
                                                </span>
                                            )}
                                        </Td>

                                        {/* Action */}
                                        <Td className="text-right">
                                            <Button
                                                variant={isDone ? 'ghost' : 'outline'}
                                                size="xs"
                                                onClick={() => handleOpenRegularisation(ano)}
                                                disabled={isLocked || !canWrite}
                                                className="font-bold text-[11px] h-[28px]"
                                            >
                                                {isDone ? 'Modifier' : 'Régulariser'}
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

                {/* Pagination officielle Galaxam */}
                <Pagination
                    currentPage={currentPage}
                    totalItems={filteredAnomalies.length}
                    pageSize={pageSize}
                    pageSizeOptions={[8, 15, 25, 50]}
                    onPageChange={(p) => setCurrentPage(p)}
                    onPageSizeChange={(sz) => { setPageSize(sz); setCurrentPage(1); }}
                />
            </div>

            {/* ---------- BARRE DE TRANSMISSION FIXÉE EN BAS (STICKY) ---------- */}
            <div className="sticky bottom-0 z-20 bg-[var(--color-surface)] text-[var(--color-fg)] shadow-2xl border border-[var(--color-border-subtle)] border-t-2 border-t-[var(--color-brand-500)] p-3.5">
                <div className="flex items-center justify-between flex-wrap gap-4">
                    <div className="flex items-center gap-5 flex-wrap">
                        {/* Statut des points bloquants */}
                        {nbBloquantes > 0 ? (
                            <div className="flex items-center gap-2 text-xs font-semibold text-rose-600 dark:text-rose-400">
                                <span className="w-2.5 h-2.5 rounded-full bg-rose-500 animate-pulse" />
                                <span>{nbBloquantes} point(s) bloquant(s) avant transmission (pointages incomplets ou absences non justifiées)</span>
                            </div>
                        ) : (
                            <div className="flex items-center gap-2 text-xs font-semibold text-emerald-600 dark:text-emerald-400">
                                <CheckCircle2 size={15} className="text-emerald-500" />
                                <span>Toutes les anomalies bloquantes sont régularisées — Période prête pour transmission DAF</span>
                            </div>
                        )}

                        {/* Semaines restantes */}
                        <div className="text-xs text-[var(--color-fg-muted)] flex items-center gap-2 border-l border-[var(--color-border-subtle)] pl-4">
                            <span>Progression de saisie :</span>
                            <span className="font-mono font-bold text-[var(--color-fg)]">
                                {semaines.length - nbSemainesRestantes} / {semaines.length} semaine(s)
                            </span>
                        </div>
                    </div>

                    {/* Bouton de transmission DAF */}
                    <div className="flex items-center gap-3">
                        <Button
                            variant="primary"
                            size="md"
                            onClick={() => setShowTransmissionConfirm(true)}
                            disabled={nbBloquantes > 0 || isLocked || !canWrite || transmitting}
                            loading={transmitting}
                            icon={<Send size={14} />}
                            title={
                                nbBloquantes > 0
                                    ? `Résolvez les ${nbBloquantes} anomalie(s) bloquante(s) pour débloquer la transmission.`
                                    : isLocked
                                    ? 'Cette période est déjà transmise ou validée.'
                                    : 'Transmettre officiellement la synthèse à la DAF.'
                            }
                        >
                            Transmettre à la DAF
                        </Button>
                    </div>
                </div>
            </div>

            {/* Modale de régularisation intelligente */}
            <AnomalieRegularisationModal
                isOpen={isModalOpen}
                onClose={() => {
                    setIsModalOpen(false);
                    setSelectedAnomalie(null);
                }}
                anomalie={selectedAnomalie}
                onSuccess={handleSaveRegularisation}
            />

            {/* Dialogue de confirmation de transmission DAF */}
            <ConfirmDialog
                isOpen={showTransmissionConfirm}
                onClose={() => setShowTransmissionConfirm(false)}
                onConfirm={handleTransmettreDaf}
                title="Confirmer la transmission à la DAF"
                message="Êtes-vous sûr de vouloir transmettre la période de pointage à la DAF ? Cette action fige la saisie pour les agents de site et déclenche la notification officielle auprès de la DAF."
                confirmText="Confirmer et transmettre"
                cancelText="Annuler"
                variant="primary"
                loading={transmitting}
            />
        </div>
    );
}
