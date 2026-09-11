import { useState, useEffect, useMemo, useCallback } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import {
    CheckCircle, XCircle, Lock, AlertTriangle, CheckCircle2, Clock,
    Users, TrendingUp, Moon, Sun, Award, Search, ClipboardCheck,
    Edit3, Calendar, Building2, X, GanttChartSquare,
    LayoutGrid, ShieldCheck, Car, Banknote, RefreshCw, Scale
} from 'lucide-react';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Button from '@/components/ui/forms/Button';
import Select from '@/components/ui/forms/Select';
import Pagination from '@/components/ui/navigation/Pagination';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Modal from '@/components/ui/overlays/Modal';
import SEO from '@/components/system/SEO';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/ui/feedback/Toast';
import organisationService from '@/api/admin/organisationService';
import periodePaieService from '@/services/periodePaieService';
import pointageSyntheseService from '@/api/pointage/pointageSyntheseService';
import pointageSaisieService from '@/api/pointage/pointageSaisieService';
import { formatMonnaie, formatHeuresMinutes, formatHeuresDecimal } from '@/utils/pointageUtils';
import DafAjustementPointageModal from '@/components/pointage/DafAjustementPointageModal';
import PointageGanttView from '@/components/pointage/PointageGanttView';
import { isDateInFuture } from '@/utils/dateUtils';
import useDebounce from '@/hooks/useDebounce';

/**
 * Arbitrage DAF — Etape 3 du cockpit Pointage (DAF uniquement).
 * Synthese mensuelle + Vue Gantt pour investigation + Actions : Valider / Rejeter / Cloture.
 */
export default function ArbitrageDafPage({ activePeriode = null, onSelectPeriode = null }) {
    const [searchParams, setSearchParams] = useSearchParams();
    const { hasPermission } = useAuth();
    const { toast } = useToast();
    const isDaf = hasPermission('POINTAGE_VALIDATE');

    const [periodes, setPeriodes] = useState([]);
    const [selectedPeriode, setSelectedPeriode] = useState(() => searchParams.get('periode') || (activePeriode?.idPeriodePaie ? String(activePeriode.idPeriodePaie) : ''));
    const [selectedEmployeToAdjust, setSelectedEmployeToAdjust] = useState(null);
    const [adjustInitialDate, setAdjustInitialDate] = useState(null);
    const [sites, setSites] = useState([]);
    const [selectedSite, setSelectedSite] = useState(() => Number(searchParams.get('site') || 0));
    const [syntheseData, setSyntheseData] = useState([]);
    const [semaines, setSemaines] = useState([]);
    const [loading, setLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState('');
    const debouncedSearch = useDebounce(searchTerm, 300);
    const [alertFilter, setAlertFilter] = useState('ALL');

    // Pagination serveur & Lazy loading
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(25);
    const [totalElements, setTotalElements] = useState(0);
    const [serverKpis, setServerKpis] = useState(null);
    const [serverAlertCounts, setServerAlertCounts] = useState(null);

    const [showValiderModal, setShowValiderModal] = useState(false);
    const [showRejetModal, setShowRejetModal] = useState(false);
    const [showCloturerModal, setShowCloturerModal] = useState(false);
    const [motifRejet, setMotifRejet] = useState('');
    const [processing, setProcessing] = useState(false);

    // Vue : 'synthese' | 'gantt'
    const [viewMode, setViewMode] = useState('synthese');
    const [selectedSemaine, setSelectedSemaine] = useState(null);
    const [ganttData, setGanttData] = useState({});
    const [loadingGantt, setLoadingGantt] = useState(false);

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
                    const activeP = (activePeriode ? resPeriodes.find(p => p.idPeriodePaie === activePeriode.idPeriodePaie) : null)
                        || found
                        || resPeriodes.find(p => p.statutCode === 'A_VALIDER' || p.statutCode === 'TRANSMIS_DAF')
                        || resPeriodes.find(p => p.statutCode === 'EN_COURS')
                        || resPeriodes[0];
                    if (activeP) {
                        const pid = String(activeP.idPeriodePaie);
                        setSelectedPeriode(pid);
                        if (!paramPeriode) {
                            setSearchParams(prev => { const n = new URLSearchParams(prev); n.set('periode', pid); return n; }, { replace: true });
                        }
                    }
                }
                if (resSites) {
                    setSites(resSites);
                    const paramSite = searchParams.get('site');
                    if (paramSite) setSelectedSite(Number(paramSite));
                }
            } catch (err) {
                console.error('Erreur init ArbitrageDafPage:', err);
            }
        };
        init();
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    // Sécurité : auto-sélection dès que la liste des périodes est disponible
    useEffect(() => {
        if (periodes.length > 0 && !selectedPeriode) {
            const activeP = (activePeriode ? periodes.find(p => p.idPeriodePaie === activePeriode.idPeriodePaie) : null)
                || periodes.find(p => p.statutCode === 'A_VALIDER' || p.statutCode === 'TRANSMIS_DAF')
                || periodes.find(p => p.statutCode === 'EN_COURS')
                || periodes[0];
            if (activeP) {
                const pid = String(activeP.idPeriodePaie);
                setSelectedPeriode(pid);
                setSearchParams(prev => { const n = new URLSearchParams(prev); n.set('periode', pid); return n; }, { replace: true });
            }
        }
    }, [periodes, selectedPeriode, activePeriode, setSearchParams]);

    // Synchronisation réactive avec activePeriode provenant du parent
    useEffect(() => {
        if (activePeriode?.idPeriodePaie && String(activePeriode.idPeriodePaie) !== String(selectedPeriode)) {
            setSelectedPeriode(String(activePeriode.idPeriodePaie));
            setCurrentPage(1);
        }
    }, [activePeriode?.idPeriodePaie]); // eslint-disable-line react-hooks/exhaustive-deps

    const loadData = useCallback(async () => {
        if (!selectedPeriode) return;
        setLoading(true);
        try {
            const siteParam = Number(selectedSite) > 0 ? Number(selectedSite) : undefined;
            const [resSemaines, resSynthese] = await Promise.all([
                pointageSyntheseService.getSemaines({ idPeriodePaie: selectedPeriode, idSite: siteParam }),
                pointageSyntheseService.getSynthese({
                    idPeriodePaie: selectedPeriode,
                    idSite: siteParam,
                    search: debouncedSearch.trim() || undefined,
                    filtre: alertFilter !== 'ALL' ? alertFilter : undefined,
                    page: currentPage,
                    size: pageSize
                })
            ]);
            if (resSemaines?.semaines) setSemaines(resSemaines.semaines);
            if (resSynthese?.synthese) setSyntheseData(resSynthese.synthese);
            if (resSynthese?.totalElements !== undefined) setTotalElements(resSynthese.totalElements);
            if (resSynthese?.kpis) setServerKpis(resSynthese.kpis);
            if (resSynthese?.alertCounts) setServerAlertCounts(resSynthese.alertCounts);
        } catch (err) {
            console.error('Erreur chargement ArbitrageDafPage:', err);
        } finally {
            setLoading(false);
        }
    }, [selectedPeriode, selectedSite, debouncedSearch, alertFilter, currentPage, pageSize]);

    useEffect(() => { loadData(); }, [loadData]);

    const currentPeriodeObj = useMemo(() =>
        periodes.find(p => p.idPeriodePaie === Number(selectedPeriode)) || null,
        [periodes, selectedPeriode]);

    const stCode = currentPeriodeObj?.statutCode;
    const isAValider = stCode === 'A_VALIDER' || stCode === 'TRANSMIS_DAF';
    const isValidee = stCode === 'VALIDEE';
    const _isCloturee = stCode === 'CLOTUREE';

    const kpis = useMemo(() => {
        const totalInd = Math.round(syntheseData.reduce((a, r) => a + ((r.montantRepas || 0) + (r.montantDeplacement || 0)), 0));
        if (serverKpis) {
            return {
                ...serverKpis,
                totalIndemnites: (serverKpis.totalIndemnites && serverKpis.totalIndemnites > 0) ? Math.round(serverKpis.totalIndemnites) : totalInd
            };
        }
        return {
            totalSalaries: totalElements || syntheseData.length,
            totalHeures: Math.round(syntheseData.reduce((a, r) => a + (r.totalHeuresTravaillees || 0), 0)),
            totalHs30: Math.round(syntheseData.reduce((a, r) => a + (r.heuresHs30 || 0), 0)),
            totalHs50: Math.round(syntheseData.reduce((a, r) => a + (r.heuresHs50 || 0), 0)),
            totalRetardMins: syntheseData.reduce((a, r) => a + (r.totalMinutesRetard || 0), 0),
            totalIndemnites: totalInd
        };
    }, [serverKpis, totalElements, syntheseData]);

    const alertCounts = useMemo(() => {
        if (serverAlertCounts) return serverAlertCounts;
        return {
            all: totalElements || syntheseData.length,
            hs: syntheseData.filter(r => (r.heuresHs30 || 0) > 0 || (r.heuresHs50 || 0) > 0).length,
            nuit: syntheseData.filter(r => (r.heuresNuitTotal || 0) > 0).length,
            retard: syntheseData.filter(r => (r.totalMinutesRetard || 0) > 0).length,
            absence: syntheseData.filter(r => (r.heuresAbsenceRetenues || 0) > 0).length,
        };
    }, [serverAlertCounts, totalElements, syntheseData]);

    // Données affichées : déjà filtrées et paginées par le serveur
    const displayedData = syntheseData;

    // Chargement Gantt
    const loadGanttData = useCallback(async (semaine) => {
        if (!semaine?.dateDebut || !semaine?.dateFin) return;
        setLoadingGantt(true);
        try {
            const days = [];
            let curr = new Date(semaine.dateDebut + 'T00:00:00');
            const end = new Date(semaine.dateFin + 'T00:00:00');
            while (curr <= end) {
                const yyyy = curr.getFullYear();
                const mm = String(curr.getMonth() + 1).padStart(2, '0');
                const dd = String(curr.getDate()).padStart(2, '0');
                days.push(`${yyyy}-${mm}-${dd}`);
                curr.setDate(curr.getDate() + 1);
            }
            const siteParam = Number(selectedSite) > 0 ? Number(selectedSite) : undefined;
            const results = await Promise.all(
                days.map(date =>
                    pointageSaisieService.getGrilleSaisie({ idSite: siteParam, date })
                        .then(res => ({ date, rows: res?.grilleData || res?.employes || (Array.isArray(res) ? res : []) }))
                        .catch(() => ({ date, rows: [] }))
                )
            );
            const dataMap = {};
            results.forEach(({ date, rows }) => { dataMap[date] = Array.isArray(rows) ? rows : []; });
            setGanttData(dataMap);
        } catch (err) {
            console.error('Erreur chargement Gantt DAF:', err);
            setGanttData({});
        } finally {
            setLoadingGantt(false);
        }
    }, [selectedSite]);

    const handleSetViewMode = useCallback((mode) => {
        setViewMode(mode);
        if (mode === 'gantt' && semaines.length > 0 && !selectedSemaine) {
            setSelectedSemaine(semaines[0]);
        }
    }, [semaines, selectedSemaine]);

    useEffect(() => {
        if (viewMode === 'gantt' && selectedSemaine) {
            loadGanttData(selectedSemaine);
        }
    }, [selectedSemaine, viewMode, loadGanttData]);

    const handleValiderPeriode = async () => {
        if (!selectedPeriode) return;
        setProcessing(true);
        try {
            const res = await pointageSyntheseService.validerDaf({ idPeriodePaie: selectedPeriode });
            if (res?.success) {
                toast({ title: 'Periode validee par la DAF', message: 'Les donnees sont gelees.', type: 'success' });
                window.dispatchEvent(new Event('refresh-notifications'));
                setShowValiderModal(false);
                const list = await periodePaieService.getAllPeriodes();
                if (list) setPeriodes(list);
                await loadData();
            }
        } catch (err) {
            toast({ title: 'Erreur', message: err?.response?.data?.error || 'Impossible de valider.', type: 'error' });
        } finally { setProcessing(false); }
    };

    const handleRejeterPeriode = async () => {
        if (!selectedPeriode || !motifRejet.trim()) return;
        setProcessing(true);
        try {
            const res = await pointageSyntheseService.rejeterDaf({ idPeriodePaie: selectedPeriode, motif: motifRejet.trim() });
            if (res?.success) {
                toast({ title: 'Periode renvoyee', message: 'Les agents ont ete notifies.', type: 'warning' });
                window.dispatchEvent(new Event('refresh-notifications'));
                setShowRejetModal(false);
                setMotifRejet('');
                const list = await periodePaieService.getAllPeriodes();
                if (list) setPeriodes(list);
                await loadData();
            }
        } catch (err) {
            toast({ title: 'Erreur', message: err?.response?.data?.error || 'Impossible de renvoyer.', type: 'error' });
        } finally { setProcessing(false); }
    };

    const [recalculating, setRecalculating] = useState(false);
    const handleRecalculerTout = async () => {
        if (!selectedPeriode) return;
        setRecalculating(true);
        try {
            const siteParam = Number(selectedSite) > 0 ? Number(selectedSite) : undefined;
            const res = await pointageSyntheseService.calculerTout({
                idPeriodePaie: selectedPeriode,
                idSite: siteParam
            });
            if (res?.success) {
                toast({
                    title: 'Calculs actualisés',
                    message: res.message || 'Toutes les semaines et totaux ont été recalculés.',
                    type: 'success'
                });
                await loadData();
                if (selectedSemaine) {
                    await loadGanttData(selectedSemaine);
                }
            }
        } catch (err) {
            toast({
                title: 'Erreur',
                message: err?.response?.data?.error || err?.message || 'Erreur lors du recalcul.',
                type: 'error'
            });
        } finally {
            setRecalculating(false);
        }
    };

    const handleCloturerPeriode = async () => {
        if (!selectedPeriode) return;
        setProcessing(true);
        try {
            await periodePaieService.updateStatut(selectedPeriode, 'CLOTUREE');
            toast({ title: 'Periode cloturee', message: 'Les bulletins peuvent etre generes.', type: 'success' });
            window.dispatchEvent(new Event('refresh-notifications'));
            setShowCloturerModal(false);
            const list = await periodePaieService.getAllPeriodes();
            if (list) setPeriodes(list);
            await loadData();
        } catch (err) {
            toast({ title: 'Erreur', message: err?.response?.data?.error || 'Impossible de cloturer.', type: 'error' });
        } finally { setProcessing(false); }
    };

    if (!isDaf) {
        return (
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-12 text-center">
                <Lock size={32} className="mx-auto mb-3 text-[var(--color-fg-subtle)] opacity-40" />
                <p className="text-sm font-semibold text-[var(--color-fg)]">Acces restreint</p>
                <p className="text-xs text-[var(--color-fg-muted)] mt-1">Cette etape est reservee a la Direction Administrative et Financiere (DAF).</p>
            </div>
        );
    }

    return (
        <>
            <SEO title="Arbitrage DAF — Synthese & Validation Mensuelle" description="Arbitrage DAF, controle des heures sup, indemnites et validation du pointage mensuel" />

            <div className="space-y-4">
                {/* Header DAF avec sélecteurs de périmètre */}
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 shadow-xs">
                    <div className="flex items-center gap-3 flex-wrap">
                        {/* Période */}
                        <div className="flex items-center gap-1.5 min-w-[210px]">
                            <Calendar size={14} className="text-[var(--color-brand-500)] shrink-0" />
                            <Select
                                value={selectedPeriode}
                                onChange={(val) => {
                                    setSelectedPeriode(val);
                                    setCurrentPage(1);
                                    if (onSelectPeriode) {
                                        onSelectPeriode(val);
                                    } else {
                                        setSearchParams(prev => { const n = new URLSearchParams(prev); n.set('periode', val); return n; });
                                    }
                                }}
                                options={periodes.map(p => ({
                                    value: String(p.idPeriodePaie || p.id),
                                    label: p.libelle || p.code
                                }))}
                                placeholder="Sélectionner une période..."
                                className="text-xs h-[32px] font-semibold"
                            />
                        </div>

                        <div className="h-5 w-px bg-[var(--color-border-subtle)] hidden sm:block shrink-0" />

                        {/* Site */}
                        <div className="flex items-center gap-1.5 min-w-[170px]">
                            <Building2 size={14} className="text-[var(--color-fg-muted)] shrink-0" />
                            <Select
                                value={String(selectedSite)}
                                onChange={(val) => {
                                    const sId = Number(val);
                                    setSelectedSite(sId);
                                    setCurrentPage(1);
                                    setSearchParams(prev => {
                                        const n = new URLSearchParams(prev);
                                        if (sId > 0) n.set('site', String(sId));
                                        else n.delete('site');
                                        return n;
                                    });
                                }}
                                options={[
                                    { value: '0', label: 'Tous les sites' },
                                    ...sites.map(s => ({ value: String(s.idSite || s.id), label: s.libelle || s.nom }))
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
                            loading={recalculating}
                            onClick={handleRecalculerTout}
                            icon={<RefreshCw size={13} className={recalculating ? 'animate-spin' : ''} />}
                            className="text-xs h-[32px] font-semibold"
                            title="Recalculer et actualiser l'ensemble des semaines et cumuls"
                        >
                            <span className="hidden md:inline">Actualiser les calculs</span>
                            <span className="md:hidden">Actualiser</span>
                        </Button>
                        {isAValider && (
                            <>
                                <Button type="button" variant="danger" size="sm" onClick={() => setShowRejetModal(true)}
                                    icon={<XCircle size={14} />} className="text-xs h-[32px] font-bold">
                                    Renvoyer
                                </Button>
                                <Button type="button" variant="primary" size="sm" onClick={() => setShowValiderModal(true)}
                                    icon={<CheckCircle size={14} />} className="text-xs h-[32px] font-bold">
                                    Valider
                                </Button>
                            </>
                        )}
                        {isValidee && (
                            <Button type="button" variant="success" size="sm" onClick={() => setShowCloturerModal(true)}
                                icon={<ClipboardCheck size={14} />} className="text-xs h-[32px] font-bold">
                                Clôturer
                            </Button>
                        )}
                    </div>
                </div>

                {/* KPI Cards — 5 indicateurs clés */}
                <div className="grid grid-cols-2 sm:grid-cols-5 gap-2.5">
                    {[
                        { label: 'Salariés', value: kpis.totalSalaries, icon: Users, color: 'text-brand-600 dark:text-brand-400' },
                        { label: 'Heures totales', value: `${formatHeuresDecimal(kpis.totalHeures)} (${formatHeuresMinutes(kpis.totalHeures)})`, icon: Clock, color: 'text-sky-600 dark:text-sky-400' },
                        { label: 'HS30 + HS50', value: `${formatHeuresDecimal(kpis.totalHs30 + kpis.totalHs50)} (${formatHeuresMinutes(kpis.totalHs30 + kpis.totalHs50)})`, icon: TrendingUp, color: 'text-violet-600 dark:text-violet-400' },
                        { label: 'Retards cumulés', value: `${formatHeuresDecimal(kpis.totalRetardMins / 60)} (${formatHeuresMinutes(kpis.totalRetardMins / 60)})`, icon: AlertTriangle, color: 'text-orange-600 dark:text-orange-400' },
                        { label: 'Indemnités totales', value: formatMonnaie(kpis.totalIndemnites), icon: Banknote, color: 'text-emerald-600 dark:text-emerald-400' },
                    ].map(kpi => (
                        <div key={kpi.label} className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 flex items-center gap-3">
                            <kpi.icon size={20} className={`${kpi.color} shrink-0`} />
                            <div>
                                <div className="text-[11px] text-[var(--color-fg-muted)] font-medium">{kpi.label}</div>
                                <div className="text-base font-bold font-mono text-[var(--color-fg)]">{kpi.value}</div>
                            </div>
                        </div>
                    ))}
                </div>

                {/* Toggle Synthèse / Gantt */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-4">
                    <div className="flex flex-col lg:flex-row lg:items-center justify-between mb-4 gap-3">
                        <div className="flex items-center gap-3 flex-wrap">
                            <h3 className="text-sm font-bold text-[var(--color-fg)] flex items-center gap-2 shrink-0">
                                <ShieldCheck size={16} className="text-[var(--color-brand-500)]" />
                                Arbitrage DAF — {currentPeriodeObj?.libelle || 'Période active'}
                            </h3>
                            {/* Filtres alerte (synthèse seulement) */}
                            {viewMode === 'synthese' && (
                                <div className="flex items-center gap-1 flex-wrap">
                                    {[
                                        { v: 'ALL', label: `Tous (${alertCounts.all})` },
                                        { v: 'HS', label: `HS (${alertCounts.hs})`, icon: TrendingUp },
                                        { v: 'NUIT', label: `Nuit (${alertCounts.nuit})`, icon: Moon },
                                        { v: 'RETARD', label: `Retards (${alertCounts.retard})`, icon: Clock },
                                        { v: 'ABSENCE', label: `Retenues / Abs. (${alertCounts.absence})`, icon: AlertTriangle },
                                    ].map(opt => (
                                        <button
                                            key={opt.v}
                                            type="button"
                                            onClick={() => { setAlertFilter(opt.v); setCurrentPage(1); }}
                                            className={`flex items-center gap-1 px-2.5 py-1 text-[10.5px] font-bold border transition-all cursor-pointer ${alertFilter === opt.v
                                                    ? 'bg-[var(--color-brand-500)] text-white border-[var(--color-brand-500)] shadow-xs'
                                                    : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border-[var(--color-border-subtle)] hover:border-[var(--color-border)] hover:text-[var(--color-fg)]'
                                                }`}
                                        >
                                            {opt.icon && <opt.icon size={11} />}
                                            {opt.label}
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>

                        <div className="flex items-center gap-2.5 shrink-0 flex-wrap">
                            {/* Recherche salarié dans la table */}
                            <div className="relative w-48 sm:w-56">
                                <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" />
                                <input
                                    type="text"
                                    value={searchTerm}
                                    onChange={(e) => { setSearchTerm(e.target.value); setCurrentPage(1); }}
                                    placeholder="Nom ou matricule..."
                                    className="w-full pl-8 pr-7 text-xs border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] focus:outline-none focus:border-[var(--color-brand-500)] h-[32px] rounded-none placeholder:text-[var(--color-fg-subtle)]"
                                />
                                {searchTerm && (
                                    <button
                                        type="button"
                                        onClick={() => { setSearchTerm(''); setCurrentPage(1); }}
                                        className="absolute right-2 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] cursor-pointer"
                                    >
                                        <X size={13} />
                                    </button>
                                )}
                            </div>

                            {/* Toggle vue */}
                            <div className="flex items-center border border-[var(--color-border-subtle)] overflow-hidden">
                                <button
                                    type="button"
                                    onClick={() => handleSetViewMode('synthese')}
                                    className={`flex items-center gap-1.5 px-3 py-1.5 text-[11px] font-semibold transition-all cursor-pointer ${viewMode === 'synthese' ? 'bg-[var(--color-brand-500)] text-white font-bold' : 'text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'
                                        }`}
                                    title="Vue synthèse mensuelle"
                                >
                                    <LayoutGrid size={13} />
                                    <span className="hidden sm:inline">Synthèse</span>
                                </button>
                                <button
                                    type="button"
                                    onClick={() => handleSetViewMode('gantt')}
                                    className={`flex items-center gap-1.5 px-3 py-1.5 text-[11px] font-semibold transition-all cursor-pointer ${viewMode === 'gantt' ? 'bg-[var(--color-brand-500)] text-white font-bold' : 'text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'
                                        }`}
                                    title="Vue détaillée par semaine"
                                >
                                    <GanttChartSquare size={13} />
                                    <span className="hidden sm:inline">Détail Semaine</span>
                                </button>
                            </div>
                        </div>
                    </div>

                    {/* Vue Synthese — tableau mensuel par employe */}
                    {viewMode === 'synthese' && (
                        loading ? (
                            <div className="flex items-center justify-center gap-2 py-12 text-xs text-[var(--color-fg-muted)]">
                                <Clock size={14} className="animate-spin text-brand-500" />Chargement de la synthese...
                            </div>
                        ) : displayedData.length === 0 ? (
                            <div className="flex items-center justify-center gap-3 py-12 text-xs text-[var(--color-fg-muted)]">
                                <CheckCircle2 size={18} className="text-[var(--color-success-600)]" />
                                <span>Aucun employe ne correspond aux criteres selectionnes.</span>
                            </div>
                        ) : (
                            <div className="space-y-0 overflow-x-auto">
                                <Table size="sm" divided>
                                    <Thead>
                                        <Tr>
                                            <Th className="w-[180px] max-w-[200px]">Collaborateur</Th>
                                            <Th className="text-center w-14">Jours</Th>
                                            <Th className="text-center w-16">Heures</Th>
                                            <Th className="text-center w-16">
                                                <span className="inline-flex items-center gap-1 text-violet-700 dark:text-violet-300">
                                                    <TrendingUp size={11} /> HS 30%
                                                </span>
                                            </Th>
                                            <Th className="text-center w-16">
                                                <span className="inline-flex items-center gap-1 text-violet-700 dark:text-violet-300">
                                                    <Award size={11} /> HS 50%
                                                </span>
                                            </Th>
                                            <Th className="text-center w-14">
                                                <span className="inline-flex items-center gap-1 text-sky-700 dark:text-sky-300">
                                                    <Moon size={11} /> Nuit
                                                </span>
                                            </Th>
                                            <Th className="text-center w-20">
                                                <span className="inline-flex items-center gap-1 text-amber-700 dark:text-amber-300">
                                                    <Sun size={11} /> Repas
                                                </span>
                                            </Th>
                                            <Th className="text-center w-20">
                                                <span className="inline-flex items-center gap-1 text-emerald-700 dark:text-emerald-300">
                                                    <Car size={11} /> Déplac.
                                                </span>
                                            </Th>
                                            <Th className="text-center w-24">
                                                <span className="inline-flex items-center gap-1 text-teal-700 dark:text-teal-300">
                                                    <Banknote size={11} /> Indemnités
                                                </span>
                                            </Th>
                                            <Th className="text-center w-20" title="Total des retards bruts">
                                                <span className="inline-flex items-center gap-1 text-orange-700 dark:text-orange-300">
                                                    <Clock size={11} /> Retards
                                                </span>
                                            </Th>
                                            <Th className="text-center w-20" title="Heures supplémentaires brutes avant compensation">
                                                <span className="inline-flex items-center gap-1 text-indigo-700 dark:text-indigo-300">
                                                    <TrendingUp size={11} /> Surplus
                                                </span>
                                            </Th>
                                            <Th className="text-center w-22" title="Retards absorbés par le surplus ">
                                                <span className="inline-flex items-center gap-1 text-emerald-700 dark:text-emerald-300">
                                                    <Scale size={11} /> Compensé
                                                </span>
                                            </Th>
                                            <Th className="text-center w-24" title="Retenue nette paie : retards résiduels non compensés">
                                                <span className="inline-flex items-center gap-1 text-rose-700 dark:text-rose-300">
                                                    <AlertTriangle size={11} /> Retenue Paie
                                                </span>
                                            </Th>
                                            <Th className="text-center w-16" title="Nombre de jours d'absence injustifiée">
                                                Absences
                                            </Th>
                                            <Th className="text-center w-12">Action</Th>
                                        </Tr>
                                    </Thead>
                                    <Tbody>
                                        {displayedData.map((row) => {
                                            const presents = row.nbJoursPresents ?? row.joursPresents;
                                            const profilLabel = row.badgeProfilLabel || (row.dispensePointage ? 'AUTONOME' : row.estCadre ? 'CADRE' : null);
                                            const profilVariant = row.badgeProfilVariant || (row.dispensePointage ? 'neutral' : row.estCadre ? 'info' : null);
                                            const totalIndRow = (row.montantRepas || 0) + (row.montantDeplacement || 0);

                                            return (
                                                <Tr key={row.idEmploye || row.matricule}>
                                                    <Td className="w-[180px] max-w-[200px]">
                                                        <div className="font-semibold text-xs text-[var(--color-fg)] truncate" title={`${row.nom} ${row.prenom}`}>
                                                            <Link
                                                                to={`/employes/${row.idEmploye}?tab=pointage`}
                                                                className="hover:text-brand-600 hover:underline transition-colors"
                                                                title={`Consulter le détail pointage de ${row.nom} ${row.prenom}`}
                                                            >
                                                                {row.nom} {row.prenom}
                                                            </Link>
                                                        </div>
                                                        <div className="text-[10px] font-mono text-[var(--color-fg-muted)] flex items-center gap-1.5 mt-0.5">
                                                            <span>{row.matricule}</span>
                                                            {profilLabel && (
                                                                <span className={`px-1.5 py-0 text-[8.5px] font-bold border ${getBadgeStyle(profilVariant)}`}>
                                                                    {profilLabel}
                                                                </span>
                                                            )}
                                                        </div>
                                                    </Td>
                                                    <Td className="text-center font-mono text-xs font-semibold">
                                                        {presents != null && presents > 0 ? `${presents}j` : presents === 0 ? '0j' : '—'}
                                                    </Td>
                                                    <Td className="text-center font-mono text-xs font-semibold">
                                                        {row.totalHeuresTravaillees != null ? (
                                                            <div>
                                                                <div>{formatHeuresDecimal(row.totalHeuresTravaillees)}</div>
                                                                <div className="text-[10px] text-slate-500 font-normal">({formatHeuresMinutes(row.totalHeuresTravaillees)})</div>
                                                            </div>
                                                        ) : '—'}
                                                    </Td>
                                                    <Td className="text-center">
                                                        {(row.heuresHs30 || 0) > 0 ? (
                                                            <span className="inline-block font-mono text-xs text-violet-700 dark:text-violet-300 font-bold bg-violet-500/10 px-1.5 py-0.5 border border-violet-500/20" title={`${formatHeuresMinutes(row.heuresHs30)}`}>
                                                                {formatHeuresDecimal(row.heuresHs30)}
                                                                <span className="block text-[9px] font-normal opacity-80 font-mono">({formatHeuresMinutes(row.heuresHs30)})</span>
                                                            </span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)] text-xs font-mono">—</span>
                                                        )}
                                                    </Td>
                                                    <Td className="text-center">
                                                        {(row.heuresHs50 || 0) > 0 ? (
                                                            <span className="inline-block font-mono text-xs text-violet-700 dark:text-violet-300 font-bold bg-violet-500/10 px-1.5 py-0.5 border border-violet-500/20" title={`${formatHeuresMinutes(row.heuresHs50)}`}>
                                                                {formatHeuresDecimal(row.heuresHs50)}
                                                                <span className="block text-[9px] font-normal opacity-80 font-mono">({formatHeuresMinutes(row.heuresHs50)})</span>
                                                            </span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)] text-xs font-mono">—</span>
                                                        )}
                                                    </Td>
                                                    <Td className="text-center">
                                                        {(row.heuresNuitTotal || 0) > 0 ? (
                                                            <span className="inline-block font-mono text-xs text-sky-700 dark:text-sky-300 font-bold bg-sky-500/10 px-1.5 py-0.5 border border-sky-500/20" title={`${formatHeuresMinutes(row.heuresNuitTotal)}`}>
                                                                {formatHeuresDecimal(row.heuresNuitTotal)}
                                                                <span className="block text-[9px] font-normal opacity-80 font-mono">({formatHeuresMinutes(row.heuresNuitTotal)})</span>
                                                            </span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)] text-xs font-mono">—</span>
                                                        )}
                                                    </Td>
                                                    {/* Repas */}
                                                    <Td
                                                        className="text-center font-mono text-xs cursor-help"
                                                        title={`${row.nbJoursRepas || 0} jour(s) de repas accordés • Montant: ${formatMonnaie(row.montantRepas || 0)}`}
                                                    >
                                                        {(row.nbJoursRepas || 0) > 0 ? (
                                                            <div className="flex flex-col items-center">
                                                                <span className="font-semibold text-[var(--color-fg)]">{row.nbJoursRepas}j</span>
                                                                <span className="text-[9px] text-amber-700 dark:text-amber-300 font-bold leading-tight">{formatMonnaie(row.montantRepas || 0)}</span>
                                                            </div>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)] text-xs font-mono">—</span>
                                                        )}
                                                    </Td>
                                                    {/* Déplacement */}
                                                    <Td
                                                        className="text-center font-mono text-xs cursor-help"
                                                        title={`${row.nbJoursDeplacement || 0} jour(s) de déplacement • Montant: ${formatMonnaie(row.montantDeplacement || 0)}`}
                                                    >
                                                        {(row.nbJoursDeplacement || 0) > 0 ? (
                                                            <div className="flex flex-col items-center">
                                                                <span className="font-semibold text-[var(--color-fg)]">{row.nbJoursDeplacement}j</span>
                                                                <span className="text-[9px] text-emerald-700 dark:text-emerald-300 font-bold leading-tight">{formatMonnaie(row.montantDeplacement || 0)}</span>
                                                            </div>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)] text-xs font-mono">—</span>
                                                        )}
                                                    </Td>
                                                    {/* Total Indemnités */}
                                                    <Td
                                                        className="text-center font-mono text-xs cursor-help bg-teal-500/5 border-x border-teal-500/15"
                                                        title={`Détail des indemnités:\n• Repas: ${formatMonnaie(row.montantRepas || 0)} (${row.nbJoursRepas || 0}j)\n• Déplacement: ${formatMonnaie(row.montantDeplacement || 0)} (${row.nbJoursDeplacement || 0}j)\n• Total: ${formatMonnaie(totalIndRow)}`}
                                                    >
                                                        {totalIndRow > 0 ? (
                                                            <span className="font-bold text-teal-700 dark:text-teal-300">{formatMonnaie(totalIndRow)}</span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)] text-xs font-mono">—</span>
                                                        )}
                                                    </Td>
                                                    {/* Retards bruts */}
                                                    <Td className="text-center">
                                                        {(row.totalMinutesRetard || 0) > 0 ? (
                                                            <span
                                                                className="inline-block font-mono text-xs text-orange-700 dark:text-orange-300 font-bold bg-orange-500/10 px-1.5 py-0.5 border border-orange-500/20"
                                                                title={`${formatHeuresDecimal(row.totalMinutesRetard / 60)} de retard brut (${formatHeuresMinutes(row.totalMinutesRetard / 60)})`}
                                                            >
                                                                {formatHeuresDecimal(row.totalMinutesRetard / 60)}
                                                                <span className="block text-[9px] font-normal opacity-80 font-mono">({formatHeuresMinutes(row.totalMinutesRetard / 60)})</span>
                                                            </span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)] text-xs font-mono">—</span>
                                                        )}
                                                    </Td>
                                                    {/* Surplus brut */}
                                                    <Td className="text-center">
                                                        {(row.surplusBrut || 0) > 0 ? (
                                                            <span
                                                                className="inline-block font-mono text-xs text-indigo-700 dark:text-indigo-300 font-bold bg-indigo-500/10 px-1.5 py-0.5 border border-indigo-500/20"
                                                                title={`Surplus brut total : ${formatHeuresDecimal(row.surplusBrut)} (${formatHeuresMinutes(row.surplusBrut)})`}
                                                            >
                                                                +{formatHeuresDecimal(row.surplusBrut)}
                                                                <span className="block text-[9px] font-normal opacity-80 font-mono">(+{formatHeuresMinutes(row.surplusBrut)})</span>
                                                            </span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)] text-xs font-mono">—</span>
                                                        )}
                                                    </Td>
                                                    {/* Compensé  */}
                                                    <Td className="text-center">
                                                        {(row.retardCompense || 0) > 0 ? (
                                                            <span
                                                                className="inline-block font-mono text-xs text-emerald-700 dark:text-emerald-300 font-bold bg-emerald-500/10 px-1.5 py-0.5 border border-emerald-500/20"
                                                                title={`Retards absorbés par le surplus : ${formatHeuresDecimal(row.retardCompense)} (${formatHeuresMinutes(row.retardCompense)})`}
                                                            >
                                                                -{formatHeuresDecimal(row.retardCompense)}
                                                                <span className="block text-[9px] font-normal opacity-80 font-mono">(-{formatHeuresMinutes(row.retardCompense)})</span>
                                                            </span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)] text-xs font-mono">—</span>
                                                        )}
                                                    </Td>
                                                    {/* Retenue Paie (Net) */}
                                                    <Td className="text-center">
                                                        {(row.heuresAbsenceRetenues || 0) > 0 ? (
                                                            <span
                                                                className="inline-block font-mono text-xs text-rose-700 dark:text-rose-300 font-bold bg-rose-500/10 px-1.5 py-0.5 border border-rose-500/20 cursor-help"
                                                                title={`Retards nets non compensés à retenir en paie : ${formatHeuresDecimal(row.heuresAbsenceRetenues)} (${formatHeuresMinutes(row.heuresAbsenceRetenues)})`}
                                                            >
                                                                -{formatHeuresDecimal(row.heuresAbsenceRetenues)}
                                                                <span className="block text-[9px] font-normal opacity-80 font-mono">(-{formatHeuresMinutes(row.heuresAbsenceRetenues)})</span>
                                                            </span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)] text-xs font-mono">—</span>
                                                        )}
                                                    </Td>
                                                    {/* Absences (Jours) */}
                                                    <Td className="text-center">
                                                        {(row.nbJoursAbsence || 0) > 0 ? (
                                                            <span
                                                                className="inline-block font-mono text-xs text-rose-700 dark:text-rose-300 font-bold bg-rose-500/10 px-1.5 py-0.5 border border-rose-500/20 cursor-help"
                                                                title={`Absence non justifiée : ${row.nbJoursAbsence} jour(s)`}
                                                            >
                                                                {row.nbJoursAbsence}j
                                                            </span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)] text-xs font-mono">—</span>
                                                        )}
                                                    </Td>
                                                    <Td className="text-center">
                                                        <button
                                                            type="button"
                                                            onClick={() => {
                                                                setAdjustInitialDate(null);
                                                                setSelectedEmployeToAdjust(row);
                                                            }}
                                                            className="inline-flex items-center justify-center w-7 h-7 border border-[var(--color-border-subtle)] hover:border-[var(--color-brand-500)] hover:bg-[var(--color-brand-500)]/10 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] transition-all cursor-pointer"
                                                            title="Ajuster le pointage de ce collaborateur"
                                                        >
                                                            <Edit3 size={13} />
                                                        </button>
                                                    </Td>
                                                </Tr>
                                            );
                                        })}
                                    </Tbody>
                                </Table>

                                <Pagination
                                    currentPage={currentPage}
                                    totalElements={totalElements}
                                    pageSize={pageSize}
                                    pageSizeOptions={[10, 25, 50, 100]}
                                    onPageChange={setCurrentPage}
                                    onPageSizeChange={(sz) => { setPageSize(sz); setCurrentPage(1); }}
                                />
                            </div>
                        )
                    )}

                    {/* Vue Gantt hebdomadaire pour la DAF */}
                    {viewMode === 'gantt' && (
                        <PointageGanttView
                            semaines={semaines}
                            selectedSemaine={selectedSemaine}
                            onSelectSemaine={(sem) => setSelectedSemaine(sem)}
                            ganttData={ganttData}
                            loadingGantt={loadingGantt}
                            isReadOnly={false}
                            onCellClick={(emp, day, _cellData) => {
                                if (isDateInFuture(day?.date)) {
                                    toast({
                                        title: 'Action impossible',
                                        message: 'Impossible d\'ajuster un pointage pour une date future.',
                                        type: 'warning'
                                    });
                                    return;
                                }
                                setAdjustInitialDate(day.date);
                                setSelectedEmployeToAdjust(emp);
                            }}
                        />
                    )}
                </div>
            </div>

            {/* Modales d'actions DAF */}
            <ConfirmDialog
                isOpen={showValiderModal}
                onClose={() => setShowValiderModal(false)}
                onConfirm={handleValiderPeriode}
                title={`Valider ${currentPeriodeObj?.libelle || ''} ?`}
                message="Les pointages seront geles. Les agents ne pourront plus modifier leurs saisies. Cette action est irreversible."
                confirmLabel="Valider la periode"
                variant="primary"
                loading={processing}
            />

            <Modal isOpen={showRejetModal} onClose={() => setShowRejetModal(false)} title="Renvoyer pour correction" size="md">
                <div className="space-y-3 text-xs">
                    <p className="text-[var(--color-fg-muted)]">
                        Indiquez aux agents le motif du renvoi. Ils pourront corriger et re-transmettre apres regularisation.
                    </p>
                    <div>
                        <label className="block text-[10.5px] font-semibold text-[var(--color-fg-muted)] mb-1">Motif (obligatoire)</label>
                        <textarea
                            value={motifRejet}
                            onChange={e => setMotifRejet(e.target.value)}
                            rows={3}
                            placeholder="Ex: Incoherences sur les heures supplementaires ou justificatifs d'absence..."
                            className="w-full px-2 py-1.5 text-xs border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] text-[var(--color-fg)] placeholder:text-[var(--color-fg-muted)] focus:outline-none focus:border-[var(--color-brand-500)] resize-none"
                        />
                    </div>
                    <div className="flex justify-end gap-2 pt-1">
                        <Button type="button" variant="ghost" size="sm" onClick={() => setShowRejetModal(false)}>Annuler</Button>
                        <Button type="button" variant="danger" size="sm" loading={processing} onClick={handleRejeterPeriode}
                            disabled={!motifRejet.trim()}>
                            Renvoyer pour correction
                        </Button>
                    </div>
                </div>
            </Modal>

            <ConfirmDialog
                isOpen={showCloturerModal}
                onClose={() => setShowCloturerModal(false)}
                onConfirm={handleCloturerPeriode}
                title={`Cloture definitive ${currentPeriodeObj?.libelle || ''} ?`}
                message="La periode sera archivee. Les bulletins de paie pourront etre generes. Cette action est irreversible."
                confirmLabel="Cloture definitif"
                variant="danger"
                loading={processing}
            />

            {/* Modale ajustement DAF individuel */}
            {selectedEmployeToAdjust && (
                <DafAjustementPointageModal
                    isOpen={Boolean(selectedEmployeToAdjust)}
                    onClose={() => {
                        setSelectedEmployeToAdjust(null);
                        setAdjustInitialDate(null);
                    }}
                    employe={selectedEmployeToAdjust}
                    idEmploye={selectedEmployeToAdjust?.idEmploye}
                    employeNom={selectedEmployeToAdjust ? `${selectedEmployeToAdjust.nom || ''} ${selectedEmployeToAdjust.prenom || ''}`.trim() : ''}
                    matricule={selectedEmployeToAdjust?.matricule}
                    idPeriode={selectedPeriode}
                    idPeriodePaie={selectedPeriode}
                    initialDate={adjustInitialDate}
                    onSuccess={() => {
                        setSelectedEmployeToAdjust(null);
                        setAdjustInitialDate(null);
                        loadData();
                        if (selectedSemaine) loadGanttData(selectedSemaine);
                    }}
                />
            )}
        </>
    );
}

function getBadgeStyle(variant) {
    switch (variant) {
        case 'violet': return 'bg-violet-500/10 border-violet-500/40 text-violet-700 dark:text-violet-300';
        case 'warning': return 'bg-amber-500/10 border-amber-500/40 text-amber-700 dark:text-amber-300';
        case 'info': return 'bg-sky-500/10 border-sky-500/40 text-sky-700 dark:text-sky-300';
        default: return 'bg-[var(--color-bg-subtle)] border-[var(--color-border)] text-[var(--color-fg-muted)]';
    }
}
