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
    Users,
    Banknote,
    ArrowRight,
    ShieldCheck,
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
import { fmtDate } from '@/utils/dateUtils';
import organisationService from '@/features/organization/api/organisationService';
import periodePaieService from '@/features/attendance/api/periodePaieService';
import usePointageAnomalies from '@/features/attendance/hooks/usePointageAnomalies';
import Pagination from '@/components/ui/navigation/Pagination';
import { getErrorMessage } from '@/api/apiClient';

const AnomalieRegularisationModal = lazy(() => import('@/features/attendance/components/modals/AnomalieRegularisationModal'));

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
    const [isSemainesCollapsed, setIsSemainesCollapsed] = useState(false);
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
        stats: _stats,
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
    const nbBloquantes = useMemo(() => {
        return (anomalies || []).filter(a => a.bloquantTransmission && a.statutAnomalie !== 'done').length;
    }, [anomalies]);

    // Semaines restantes non saisies / non clôturées
    const nbSemainesRestantes = useMemo(() => {
        return (semaines || []).filter(s => s.statutSemaine !== 'CLOTUREE' && (s.progressPercent || 0) < 100).length;
    }, [semaines]);

    const hasIncompleteWeeks = nbSemainesRestantes > 0;
    const canTransmitDaf = actions.SUBMIT?.allowed === true;

    // Raison du blocage de transmission pour le tooltip ou le message d'aide
    const transmissionBlockReason = (actions.SUBMIT?.reasonCodes || []).join(' · ');

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
            // Filtre par statut de traitement (À régulariser vs Régularisé)
            if (filterStatutTraitement === 'TODO' && ano.statutAnomalie === 'done') {
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

            {/* ---------- KPI CARDS — 5 INDICATEURS CLÉS ---------- */}
            <div className="grid grid-cols-2 sm:grid-cols-5 gap-2.5">
                {[
                    { label: 'Salariés suivis', value: kpis?.collaborateursSuivis || 0, icon: Users, color: 'text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]' },
                    { label: 'Jours pointés', value: `${kpis?.joursSaisis || 0} / ${kpis?.joursTheoriques || 0} j`, icon: Calendar, color: 'text-[var(--color-info-600)] dark:text-[var(--color-info-400)]' },
                    { label: 'Heures travaillées', value: `${Math.round(kpis?.heuresCumulees || 0)}h`, icon: Clock, color: 'text-[var(--color-brand-500)] dark:text-[var(--color-brand-300)]' },
                    { label: 'Anomalies actives', value: `${kpis?.anomaliesActives || 0}`, badge: kpis?.anomaliesCritiques > 0 ? `${kpis.anomaliesCritiques} crit.` : null, icon: AlertTriangle, color: 'text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)]' },
                    { label: 'Indemnités prév.', value: `${Math.round(kpis?.indemnitesPrevisionnelles || 0).toLocaleString('fr-FR')} Ar`, icon: Banknote, color: 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]' },
                ].map(kpi => (
                    <div key={kpi.label} className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 flex items-center gap-3">
                        <kpi.icon size={20} className={`${kpi.color} shrink-0`} />
                        <div className="min-w-0">
                            <div className="text-[11px] font-semibold text-[var(--color-fg-muted)] truncate">{kpi.label}</div>
                            <div className="text-base sm:text-lg font-bold font-mono text-[var(--color-fg)] flex items-center gap-1.5">
                                <span>{kpi.value}</span>
                                {kpi.badge && (
                                    <span className="text-[10px] font-sans px-1.5 py-0.2 bg-[var(--color-danger-500)]/10 text-[var(--color-danger-700)] dark:text-[var(--color-danger-300)] font-bold border border-[var(--color-danger-500)]/30">
                                        {kpi.badge}
                                    </span>
                                )}
                            </div>
                        </div>
                    </div>
                ))}
            </div>

            {/* ---------- VUE HEBDOMADAIRE (S1 - S5) PLIABLE ---------- */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] overflow-hidden transition-all">
                <div 
                    onClick={() => setIsSemainesCollapsed(prev => !prev)}
                    className="p-3 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] flex items-center justify-between flex-wrap gap-2 cursor-pointer select-none hover:bg-[var(--color-surface-hover)] transition-colors"
                >
                    <div className="flex items-center gap-2">
                        <Layers size={14} className="text-[var(--color-brand-500)]" />
                        <div>
                            <span className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                                Progression hebdomadaire (S1 — S5)
                            </span>
                            <span className="text-[11px] text-[var(--color-fg-muted)] ml-2.5 hidden sm:inline">
                                {isSemainesCollapsed 
                                    ? `— Cliquez pour afficher le détail (${semaines.length - nbSemainesRestantes}/${semaines.length} semaine(s) saisie(s))` 
                                    : '— Avancement réel de la saisie par semaine'}
                            </span>
                        </div>
                    </div>
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
                            Anomalies de pointage &amp; Saisies incomplètes
                        </h2>
                        <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                            {filteredAnomalies.length} anomalie{filteredAnomalies.length > 1 ? 's' : ''} détectée{filteredAnomalies.length > 1 ? 's' : ''}
                            {nbBloquantes > 0 && ` — dont ${nbBloquantes} bloquante(s) avant transmission`}
                        </p>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-[minmax(220px,1.5fr)_repeat(4,minmax(0,1fr))] gap-2 items-end">
                        {/* Recherche Employé */}
                        <div className="w-full">
                            <Input
                                icon={<Search size={13} className="text-[var(--color-fg-muted)]" />}
                                placeholder="Rechercher nom, matricule…"
                                value={searchQuery}
                                onChange={(e) => {
                                    setSearchQuery(e.target.value);
                                    setCurrentPage(1);
                                }}
                                className="text-xs h-[28px]"
                            />
                        </div>

                        {/* Filtre Type */}
                        <Select
                            value={filterType}
                            onChange={(val) => {
                                setFilterType(val);
                                setCurrentPage(1);
                            }}
                            options={[
                                { value: 'ALL', label: 'Tous les types' },
                                { value: 'ABSENCE', label: 'Absence non justifiée' },
                                { value: 'INCOMPLET', label: 'Pointage incomplet' },
                                { value: 'RETARD', label: 'Retards' },
                            ]}
                            className="text-xs w-full h-[32px]"
                        />

                        {/* Filtre Sévérité */}
                        <Select
                            value={filterSeverite}
                            onChange={(val) => {
                                setFilterSeverite(val);
                                setCurrentPage(1);
                            }}
                            options={[
                                { value: 'ALL', label: 'Toutes sévérités' },
                                { value: 'CRITIQUE', label: 'Critique (Bloquant)' },
                                { value: 'HAUTE', label: 'Élevée' },
                                { value: 'MOYENNE', label: 'Moyenne' },
                                { value: 'FAIBLE', label: 'Faible' },
                            ]}
                            className="text-xs w-full h-[32px]"
                        />

                        {/* Filtre Statut de Traitement */}
                        <Select
                            value={filterStatutTraitement}
                            onChange={(val) => {
                                setFilterStatutTraitement(val);
                                setCurrentPage(1);
                            }}
                            options={[
                                { value: 'ALL', label: 'Tous les statuts' },
                                { value: 'TODO', label: 'À régulariser' },
                                { value: 'DONE', label: 'Déjà régularisés' },
                            ]}
                            className="text-xs w-full h-[32px]"
                        />

                        {/* Filtre Date précise */}
                        <div className="relative flex items-center w-full">
                            <Calendar size={13} className="absolute left-2.5 text-[var(--color-fg-muted)] pointer-events-none" />
                            <input
                                type="date"
                                value={filterDate}
                                min={periodeData?.dateDebut || undefined}
                                max={periodeData?.dateFin || undefined}
                                onChange={(e) => {
                                    setFilterDate(e.target.value);
                                    setCurrentPage(1);
                                }}
                                title="Filtrer par date précise"
                                className="w-full text-xs pl-7 pr-2 py-1 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] text-[var(--color-fg)] outline-none focus:border-[var(--color-brand-500)] cursor-pointer h-[32px]"
                            />
                        </div>

                        {/* Badge Reset filtre date si actif */}
                        {filterDate && (
                            <button
                                type="button"
                                onClick={() => setFilterDate('')}
                                title="Effacer le filtre de date"
                                className="inline-flex items-center gap-1 text-[11px] font-bold px-2 py-0.5 bg-brand-50 dark:bg-brand-950/40 text-[var(--color-brand-500)] border border-[var(--color-brand-500)] cursor-pointer h-[28px]"
                            >
                                <span>{fmtDate(filterDate)}</span>
                                <span className="text-xs">×</span>
                            </button>
                        )}

                        {/* Reset filtre semaine si actif */}
                        {filterSemaine !== 'ALL' && (
                            <button
                                type="button"
                                onClick={() => setFilterSemaine('ALL')}
                                title="Effacer le filtre de semaine"
                                className="inline-flex items-center gap-1 text-[11px] font-bold px-2 py-0.5 bg-brand-50 dark:bg-brand-950/40 text-[var(--color-brand-500)] border border-[var(--color-brand-500)] cursor-pointer h-[28px]"
                            >
                                <span>{filterSemaine}</span>
                                <span className="text-xs">×</span>
                            </button>
                        )}

                        {/* Reset global si filtres combinés */}
                        {(filterDate || filterSemaine !== 'ALL' || filterType !== 'ALL' || filterSeverite !== 'ALL' || filterStatutTraitement !== 'ALL' || searchQuery) && (
                            <button
                                type="button"
                                onClick={() => {
                                    setFilterDate('');
                                    setFilterSemaine('ALL');
                                    setFilterType('ALL');
                                    setFilterSeverite('ALL');
                                    setFilterStatutTraitement('ALL');
                                    setSearchQuery('');
                                    setCurrentPage(1);
                                }}
                                title="Réinitialiser tous les filtres"
                                className="text-[11px] text-[var(--color-fg-muted)] hover:text-danger-600 underline ml-1 cursor-pointer"
                            >
                                Réinitialiser
                            </button>
                        )}
                    </div>
                </div>

                {/* Légende de sévérité */}
                <div className="px-3 py-2 bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] flex items-center gap-5 text-[11px] text-[var(--color-fg-muted)] flex-wrap">
                    <span className="flex items-center gap-1.5">
                        <span className="w-2 h-2 bg-[#B23B34]" />
                        <strong className="text-[var(--color-fg)]">Critique :</strong> Bloque la transmission pour validation (oubli d'horaire ou absence sans statut)
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
                <div className="overflow-x-auto border-b border-[var(--color-border-subtle)]">
                    <Table size="sm" divided>
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
                                                    <span className="text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)] font-semibold inline-flex items-center gap-1 text-[11px]">
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
                                                    disabled={isLocked}
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
            </div>

            {/* ---------- BARRE D'ACTION BAS DE PAGE (DANS LE FLUX DE LA CARTE, AUCUN CHEVAUCHEMENT) ---------- */}
            <div className="bg-[var(--color-surface)] text-[var(--color-fg)] shadow-sm border border-[var(--color-border-subtle)] border-t-2 border-t-[var(--color-brand-500)] p-3 sm:px-4">
                <div className="flex items-center justify-between flex-wrap gap-4">
                    <div className="flex items-center gap-5 flex-wrap">
                        {/* Statut des points bloquants ou prérequis */}
                        {nbBloquantes > 0 ? (
                            <div className="flex items-center gap-2 text-xs font-semibold text-rose-600 dark:text-rose-400">
                                <span className="w-2.5 h-2.5 rounded-full bg-rose-500 animate-pulse" />
                                <span>{nbBloquantes} anomalie(s) bloquante(s) non régularisée(s) (pointages incomplets ou absences sans motif)</span>
                            </div>
                        ) : hasIncompleteWeeks ? (
                            <div className="flex items-center gap-2 text-xs font-semibold text-amber-600 dark:text-amber-400">
                                <AlertTriangle size={15} className="text-amber-500" />
                                <span>Saisie en cours : {nbSemainesRestantes} semaine(s) incomplète(s) avant clôture de période</span>
                            </div>
                        ) : !canTransmitDaf ? (
                            <div className="flex items-center gap-2 text-xs font-semibold text-sky-600 dark:text-sky-400">
                                <Clock size={15} className="text-sky-500" />
                                <span>{transmissionBlockReason || 'Transmission indisponible pour cette période.'}</span>
                            </div>
                        ) : (
                            <div className="flex items-center gap-2 text-xs font-semibold text-emerald-600 dark:text-emerald-400">
                                <CheckCircle2 size={15} className="text-emerald-500" />
                                <span>Période échue et conforme — Prête pour contrôle et validation</span>
                            </div>
                        )}

                        {/* Semaines saisies */}
                        <div className="text-xs text-[var(--color-fg-muted)] flex items-center gap-2 border-l border-[var(--color-border-subtle)] pl-4">
                            <span>Progression :</span>
                            <span className="font-mono font-bold text-[var(--color-fg)]">
                                {semaines.length - nbSemainesRestantes} / {semaines.length} semaine(s)
                            </span>
                        </div>
                    </div>

                    {/* Actions selon le profil connecté */}
                    <div className="flex items-center gap-2.5">
                        {isDaf ? (
                            // Rôle Validateur / Administrateur : Navigation directe vers Contrôle & Validation
                            <div className="flex items-center gap-2">
                                {canTransmitDaf && (
                                    <Button
                                        variant="outline"
                                        size="md"
                                        onClick={() => setShowTransmissionConfirm(true)}
                                        disabled={transmitting}
                                        loading={transmitting}
                                        icon={<Send size={14} />}
                                        title="Transmettre officiellement la période pour validation"
                                    >
                                        Transmettre
                                    </Button>
                                )}
                                <Button
                                    variant="primary"
                                    size="md"
                                    onClick={() => onTabChange ? onTabChange('arbitrage') : null}
                                    icon={<ShieldCheck size={15} />}
                                    className="font-bold text-xs"
                                    title="Accéder directement à l'étape 3 : Contrôle & Validation"
                                >
                                    <span>Accéder au Contrôle &amp; Validation</span>
                                    <ArrowRight size={14} className="ml-1" />
                                </Button>
                            </div>
                        ) : (
                            // Profil Agent de site : Revue Synthèse ou Transmission directe
                            <div className="flex items-center gap-2">
                                {canTransmitDaf && (
                                    <Button
                                        variant="outline"
                                        size="md"
                                        onClick={() => setShowTransmissionConfirm(true)}
                                        disabled={transmitting}
                                        loading={transmitting}
                                        icon={<Send size={14} />}
                                        title={transmissionBlockReason || 'Transmettre la synthèse pour validation finale.'}
                                    >
                                        Transmettre
                                    </Button>
                                )}
                                <Button
                                    variant="primary"
                                    size="md"
                                    onClick={() => onTabChange ? onTabChange('arbitrage') : null}
                                    icon={<ShieldCheck size={15} />}
                                    className="font-bold text-xs"
                                    title="Consulter la synthèse consolidée des heures et indemnités"
                                >
                                    <span>Consulter la Synthèse &amp; Contrôle</span>
                                    <ArrowRight size={14} className="ml-1" />
                                </Button>
                            </div>
                        )}
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
