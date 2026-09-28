import { useState, useEffect, useMemo, useCallback, useRef, lazy, Suspense } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
    CheckCircle, XCircle, AlertTriangle, CheckCircle2, Clock,
    Users, TrendingUp, Moon, Award, Search,
    Edit3, Calendar, Building2, X, GanttChartSquare,
    LayoutGrid, ShieldCheck, Banknote, RefreshCw, Scale, Eye, Send
} from 'lucide-react';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Button from '@/components/ui/forms/Button';
import Select from '@/components/ui/forms/Select';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Input, { Textarea } from '@/components/ui/forms/Input';
import Pagination from '@/components/ui/navigation/Pagination';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Modal from '@/components/ui/overlays/Modal';
import SEO from '@/components/system/SEO';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/ui/feedback/Toast';
import organisationService from '@/features/organization/api/organisationService';
import { periodePaieService, pointageSyntheseService, attendanceService } from '@/features/attendance/api/attendanceApi';
import { formatMonnaie, formatHeuresMinutes, formatHeuresDecimal, getAttendanceBadgeClass } from '@/features/attendance/utils/attendanceDisplayUtils';
import { isDateInFuture } from '@/utils/dateUtils';
import { getErrorMessage } from '@/api/apiClient';
import useDebounce from '@/hooks/useDebounce';

const DafAjustementPointageModal = lazy(() => import('@/features/attendance/components/modals/DafAjustementPointageModal'));
const PointageDetailHebdoModal = lazy(() => import('@/features/attendance/components/modals/PointageDetailHebdoModal'));
const PointageSoldePeriodeModal = lazy(() => import('@/features/attendance/components/modals/PointageSoldePeriodeModal'));
const PointageGanttView = lazy(() => import('@/features/attendance/components/views/PointageGanttView'));

/**
 * Arbitrage DAF — Etape 3 du cockpit Pointage (DAF uniquement).
 * Synthese mensuelle + Vue Gantt pour investigation + Actions : Valider / Rejeter.
 */
export default function ArbitrageDafPage({ activePeriode = null, onSelectPeriode = null }) {
    // Contexte, navigation et hook métier
    const [searchParams, setSearchParams] = useSearchParams();
    const navigate = useNavigate();
    const { hasPermission } = useAuth();
    const { toast } = useToast();
    const isDaf = hasPermission('POINTAGE_VALIDATE');

    // État local
    const [periodes, setPeriodes] = useState([]);
    const [selectedPeriode, setSelectedPeriode] = useState(() => searchParams.get('periode') || (activePeriode?.idPeriodePaie ? String(activePeriode.idPeriodePaie) : ''));
    const [selectedEmployeToAdjust, setSelectedEmployeToAdjust] = useState(null);
    const [selectedEmployeForDetail, setSelectedEmployeForDetail] = useState(null);
    const [selectedEmployeForSolde, setSelectedEmployeForSolde] = useState(null);
    const [showAuditColumns, setShowAuditColumns] = useState(false);
    const [adjustInitialDate, setAdjustInitialDate] = useState(null);
    const [sites, setSites] = useState([]);
    const [selectedSite, setSelectedSite] = useState(() => Number(searchParams.get('site') || 0));
    const [syntheseData, setSyntheseData] = useState([]);
    const [semaines, setSemaines] = useState([]);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [preflight, setPreflight] = useState(null);
    const loadSequence = useRef(0);
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
    const [showTransmissionModal, setShowTransmissionModal] = useState(false);
    const [transmitting, setTransmitting] = useState(false);
    const [motifRejet, setMotifRejet] = useState('');
    const [processing, setProcessing] = useState(false);

    // Vue : 'synthese' | 'gantt'
    const [viewMode, setViewMode] = useState('synthese');
    const [selectedSemaine, setSelectedSemaine] = useState(null);
    const [ganttData, setGanttData] = useState({});
    const [loadingGantt, setLoadingGantt] = useState(false);

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
                    const activeP = (activePeriode ? resPeriodes.find(p => p.idPeriodePaie === activePeriode.idPeriodePaie) : null)
                        || (paramPeriode ? resPeriodes.find(p => String(p.idPeriodePaie) === String(paramPeriode)) : null)
                        || (context?.selectedPeriod?.id ? resPeriodes.find(p => String(p.idPeriodePaie) === String(context.selectedPeriod.id)) : null)
                        || resPeriodes.find(p => ['EN_COURS', 'A_VALIDER', 'TRANSMIS_DAF'].includes(p.statutCode))
                        || resPeriodes[0];
                    if (activeP) {
                        const pid = String(activeP.idPeriodePaie);
                        setSelectedPeriode(pid);
                        if (!paramPeriode) {
                            setSearchParams(prev => { const n = new URLSearchParams(prev); n.set('periode', pid); return n; }, { replace: true });
                        }
                    }
                } else {
                    setLoading(false);
                }
                if (resSites) {
                    setSites(resSites);
                    const paramSite = searchParams.get('site');
                    if (paramSite) setSelectedSite(Number(paramSite));
                }
            } catch (err) {
                console.error('Erreur init ArbitrageDafPage:', err);
                setLoading(false);
            }
        };
        init();
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    // Synchronisation réactive avec activePeriode provenant du parent
    useEffect(() => {
        if (activePeriode?.idPeriodePaie && String(activePeriode.idPeriodePaie) !== String(selectedPeriode)) {
            setSelectedPeriode(String(activePeriode.idPeriodePaie));
            setCurrentPage(1);
        }
    }, [activePeriode?.idPeriodePaie]); // eslint-disable-line react-hooks/exhaustive-deps

    const loadData = useCallback(async () => {
        if (!selectedPeriode) {
            setLoading(false);
            setSyntheseData([]);
            setSemaines([]);
            setServerKpis(null);
            setServerAlertCounts(null);
            setTotalElements(0);
            return;
        }
        const sequence = ++loadSequence.current;
        setLoading(true);
        setLoadError('');
        setPreflight(null);
        try {
            const siteParam = Number(selectedSite) > 0 ? Number(selectedSite) : undefined;
            const [resSemaines, resSynthese, resPreflight, resPeriodes] = await Promise.all([
                pointageSyntheseService.getSemaines({ idPeriodePaie: selectedPeriode, idSite: siteParam }),
                pointageSyntheseService.getSynthese({
                    idPeriodePaie: selectedPeriode,
                    idSite: siteParam,
                    search: debouncedSearch.trim() || undefined,
                    filtre: alertFilter !== 'ALL' ? alertFilter : undefined,
                    page: currentPage,
                    size: pageSize
                }),
                pointageSyntheseService.getPreflight({ idPeriodePaie: selectedPeriode }),
                periodePaieService.getAllPeriodes()
            ]);
            if (sequence !== loadSequence.current) return;
            if (resSemaines?.semaines) setSemaines(resSemaines.semaines);
            if (resSynthese?.synthese) setSyntheseData(resSynthese.synthese);
            if (resSynthese?.totalElements !== undefined) setTotalElements(resSynthese.totalElements);
            if (resSynthese?.kpis) setServerKpis(resSynthese.kpis);
            if (resSynthese?.alertCounts) setServerAlertCounts(resSynthese.alertCounts);
            setPreflight(resPreflight);
            if (resPeriodes) setPeriodes(resPeriodes);
        } catch (err) {
            if (sequence !== loadSequence.current) return;
            setLoadError(getErrorMessage(err, 'Impossible de charger le contrôle du pointage.'));
            setSyntheseData([]);
            setSemaines([]);
            setServerKpis(null);
            setServerAlertCounts(null);
            setTotalElements(0);
        } finally {
            if (sequence === loadSequence.current) setLoading(false);
        }
    }, [selectedPeriode, selectedSite, debouncedSearch, alertFilter, currentPage, pageSize]);

    useEffect(() => { loadData(); }, [loadData]);

    // Données dérivées
    const currentPeriodeObj = useMemo(() =>
        periodes.find(p => p.idPeriodePaie === Number(selectedPeriode)) || null,
        [periodes, selectedPeriode]);

    const isPeriodLocked = useMemo(() => {
        const st = (currentPeriodeObj?.statutCode || currentPeriodeObj?.state || currentPeriodeObj?.statut_code || '').toUpperCase();
        return st === 'VALIDEE' || st === 'PAIE_VALIDEE' || st === 'CLOTUREE';
    }, [currentPeriodeObj]);

    const periodActions = currentPeriodeObj?.availableActions || {};
    const isPendingDaf = ['A_VALIDER', 'TRANSMIS_DAF'].includes(currentPeriodeObj?.statutCode);
    const canApprove = !loading && !loadError && preflight?.pret === true && periodActions.APPROVE?.allowed === true;
    const canReject = periodActions.REJECT?.allowed === true;
    const approvalBlockCode = periodActions.APPROVE?.reasonCodes?.[0];
    const approvalBlockMessage = {
        PERIOD_NOT_ENDED: 'La période doit être terminée avant validation.',
        POINTAGE_EMPTY: 'Aucun pointage n’a été saisi pour cette période.',
        BLOCKING_ANOMALIES: 'Des anomalies de pointage doivent être régularisées.',
        POINTAGE_POPULATION_EMPTY: 'Aucun collaborateur n’est présent dans la synthèse.',
        POINTAGE_JOURNEES_MANQUANTES: 'Des journées ouvrables n’ont aucun statut de pointage.',
        POINTAGE_HS_SANS_SEMAINE: 'Des HS finales n’ont pas de semaine de rattachement.'
    }[approvalBlockCode];
    const canSubmit = periodActions.SUBMIT?.allowed === true;
    const transmissionBlockReason = periodActions.SUBMIT?.reason || null;

    const kpis = serverKpis;

    const defaultAlertCounts = useMemo(() => ({
        all: 0,
        hs: 0,
        nuit: 0,
        retard: 0,
        compense: 0,
        absence: 0
    }), []);

    const alertCounts = useMemo(() => ({
        ...defaultAlertCounts,
        ...(serverAlertCounts || {})
    }), [defaultAlertCounts, serverAlertCounts]);

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
                    attendanceService.getGrilleSaisie({ idSite: siteParam, date })
                        .then(res => {
                            const rows = res?.grilleData || res?.employes || (Array.isArray(res) ? res : []);
                            const definitions = res?.siteIndemnites || [];
                            return {
                                date,
                                rows: rows.map((row) => ({
                                    ...row,
                                    indemnites: Object.fromEntries(definitions.map((definition) => {
                                        const selection = row.dynamicIndemnites?.[String(definition.idIndemnite)];
                                        return [definition.code, {
                                            code: definition.code,
                                            accordee: Boolean(selection?.accordee),
                                            montant: selection?.montantApplique ?? 0,
                                        }];
                                    })),
                                })),
                            };
                        })
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

    // Actions
    const handleValiderPeriode = async () => {
        if (!selectedPeriode) return;
        setProcessing(true);
        try {
            const res = await periodePaieService.executeCommand(currentPeriodeObj.idPeriodePaie, 'APPROVE', {
                version: currentPeriodeObj.version
            });
            if (res?.decision === 'ALLOWED') {
                toast({ title: 'Période validée avec succès', message: 'Les données de pointage sont validées et figées.', type: 'success' });
                window.dispatchEvent(new Event('refresh-notifications'));
                window.dispatchEvent(new Event('pointage-period-updated'));
                setShowValiderModal(false);
                const list = await periodePaieService.getAllPeriodes();
                if (list) setPeriodes(list);
                await loadData();
            }
        } catch (err) {
            toast({ title: 'Erreur', message: getErrorMessage(err, 'Impossible de valider la période.'), type: 'error' });
        } finally { setProcessing(false); }
    };

    const handleRejeterPeriode = async () => {
        if (!selectedPeriode || !motifRejet.trim()) return;
        setProcessing(true);
        try {
            const res = await periodePaieService.executeCommand(currentPeriodeObj.idPeriodePaie, 'REJECT', {
                version: currentPeriodeObj.version,
                motif: motifRejet.trim()
            });
            if (res?.decision === 'ALLOWED') {
                toast({ title: 'Période renvoyée', message: 'La période a été renvoyée pour correction.', type: 'warning' });
                window.dispatchEvent(new Event('refresh-notifications'));
                window.dispatchEvent(new Event('pointage-period-updated'));
                setShowRejetModal(false);
                setMotifRejet('');
                const list = await periodePaieService.getAllPeriodes();
                if (list) setPeriodes(list);
                await loadData();
            }
        } catch (err) {
            toast({ title: 'Erreur', message: getErrorMessage(err, 'Impossible de renvoyer la période.'), type: 'error' });
        } finally { setProcessing(false); }
    };

    const handleTransmettreDaf = async () => {
        if (!selectedPeriode || !canSubmit) return;
        setTransmitting(true);
        try {
            const siteParam = Number(selectedSite) > 0 ? Number(selectedSite) : undefined;
            const res = await periodePaieService.executeCommand(currentPeriodeObj.idPeriodePaie, 'SUBMIT', {
                version: currentPeriodeObj.version,
                idSite: siteParam
            });
            if (res?.decision === 'ALLOWED') {
                toast({ title: 'Période transmise', message: 'La synthèse consolidée a été transmise avec succès pour arbitrage.', type: 'success' });
                setShowTransmissionModal(false);
                window.dispatchEvent(new Event('refresh-notifications'));
                window.dispatchEvent(new Event('pointage-period-updated'));
                const updatedList = await periodePaieService.getAllPeriodes();
                if (updatedList) setPeriodes(updatedList);
                await loadData();
            } else {
                toast({ title: 'Erreur', message: res?.message || 'Erreur lors de la transmission.', type: 'error' });
            }
        } catch (err) {
            console.error('Erreur transmission validation:', err);
            toast({ title: 'Erreur', message: getErrorMessage(err, 'Impossible de transmettre la période pour validation.'), type: 'error' });
        } finally {
            setTransmitting(false);
        }
    };

    const [recalculating, setRecalculating] = useState(false);
    const handleRecalculerTout = async () => {
        if (!selectedPeriode || isPeriodLocked) return;
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
                title: 'Erreur de recalcul',
                message: getErrorMessage(err, 'Erreur lors du recalcul des pointages.'),
                type: 'error'
            });
        } finally {
            setRecalculating(false);
        }
    };

    // Rendu
    return (
        <>
            <SEO title="Contrôle & Validation — Synthèse Mensuelle" description="Contrôle et validation des heures supplémentaires, indemnités et synthèse de pointage" />

            <div className="space-y-4">
                {/* Header avec sélecteurs de périmètre */}
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 shadow-xs">
                    <div className="flex items-center gap-3 flex-wrap">
                        {/* Période */}
                        <div className="flex items-center gap-1.5 min-w-[210px]">
                            <Calendar size={14} className="text-[var(--color-brand-500)] shrink-0" />
                            <SearchSelect
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
                                    label: [p.code, p.libelle].filter(Boolean).join(' — '),
                                    sublabel: p.statutCode || p.statut || ''
                                }))}
                                placeholder="Sélectionner une période..."
                                className="w-full"
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
                        {isPeriodLocked ? (
                            <>
                                {hasPermission('PAIE_READ') && currentPeriodeObj?.statutCode === 'VALIDEE' && (
                                    <Button type="button" variant="primary" size="sm"
                                        onClick={() => navigate(`/paie/periodes/${selectedPeriode}/preparation`)}>
                                        Continuer vers la paie
                                    </Button>
                                )}
                                <Button
                                    type="button"
                                    variant="outline"
                                    size="sm"
                                    loading={loading}
                                    onClick={loadData}
                                    icon={<RefreshCw size={13} className={loading ? 'animate-spin' : ''} />}
                                    className="text-xs h-[32px] font-semibold"
                                    title="Rafraîchir l'affichage (la période est validée ou clôturée, les calculs sont figés)"
                                >
                                    <span className="hidden md:inline">Rafraîchir l'affichage</span>
                                    <span className="md:hidden">Rafraîchir</span>
                                </Button>
                            </>
                        ) : (
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
                        )}
                        {canSubmit && (
                            <Button
                                type="button"
                                variant="primary"
                                size="sm"
                                disabled={transmitting}
                                onClick={() => setShowTransmissionModal(true)}
                                icon={<Send size={14} className={transmitting ? 'animate-spin' : ''} />}
                                className="text-xs h-[32px] font-bold"
                                title={transmissionBlockReason || 'Transmettre la synthèse consolidée pour arbitrage'}
                            >
                                Transmettre pour arbitrage
                            </Button>
                        )}
                        {isDaf && isPendingDaf && (
                            <>
                                {canReject && <Button type="button" variant="danger" size="sm" onClick={() => setShowRejetModal(true)}
                                    icon={<XCircle size={14} />} className="text-xs h-[32px] font-bold">
                                    Renvoyer pour correction
                                </Button>}
                                <Button type="button" variant="primary" size="sm" disabled={!canApprove} onClick={() => setShowValiderModal(true)}
                                    icon={<CheckCircle size={14} />} className="text-xs h-[32px] font-bold">
                                    Valider l'arbitrage & Sceller les heures
                                </Button>
                            </>
                        )}
                    </div>
                </div>

                {loadError && <div role="alert" className="border border-rose-300 bg-rose-50 p-3 text-sm text-rose-800 flex items-center justify-between gap-3">
                    <span>{loadError} Les données précédentes ont été masquées.</span>
                    <Button type="button" variant="outline" size="sm" onClick={loadData}>Réessayer</Button>
                </div>}
                {!loadError && isPendingDaf && preflight && !preflight.pret && (
                    <div role="alert" className="border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900 space-y-2">
                        <strong>Validation en attente de correction</strong>
                        {preflight.nombreEmployes === 0 && <p>Aucun collaborateur dans la synthèse de période.</p>}
                        {preflight.journeesManquantes?.length > 0 && <>
                            <p>{preflight.journeesManquantes.length} journée(s) ouvrable(s) sans pointage ni statut explicite. Saisissez une présence, une absence ou un congé avant le gel.</p>
                            <div className="flex flex-wrap gap-2">{preflight.journeesManquantes.slice(0, 8).map((jour) => (
                                <button key={`${jour.idEmploye}-${jour.date}`} type="button"
                                    className="border border-amber-400 bg-white px-2 py-1 hover:bg-amber-100"
                                    onClick={() => navigate(`/pointages?tab=saisie&periode=${selectedPeriode}`)}>
                                    {jour.matricule} · {jour.date} · ouvrir la saisie
                                </button>
                            ))}</div>
                            {preflight.journeesManquantes.length > 8 && <p>+ {preflight.journeesManquantes.length - 8} autres journées à traiter.</p>}
                        </>}
                        {preflight.blocages?.length > 0 && <>
                            <p>{preflight.blocages.length} collaborateur(s) ont des HS sans semaine de rattachement. Corrigez leur journée avant de sceller le pointage.</p>
                            <div className="flex flex-wrap gap-2">{preflight.blocages.map((blocker) => (
                                <button key={blocker.idEmploye} type="button" className="border border-amber-400 bg-white px-2 py-1 hover:bg-amber-100"
                                    onClick={() => { setSearchTerm(blocker.matricule || `${blocker.nom} ${blocker.prenom}`); setCurrentPage(1); }}>
                                    {blocker.matricule} · {blocker.nom} {blocker.prenom} · {(blocker.minutesHsSansSemaine / 60).toFixed(2)} h à rattacher
                                </button>
                            ))}</div>
                        </>}
                    </div>
                )}
                {!loadError && isDaf && isPendingDaf && preflight?.pret && !canApprove && approvalBlockMessage && (
                    <div role="alert" className="border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
                        Validation en attente : {approvalBlockMessage}
                    </div>
                )}

                {/* KPI Cards — 5 indicateurs clés */}
                <div className="grid grid-cols-2 sm:grid-cols-5 gap-2.5">
                    {[
                        { label: 'Salariés', value: kpis?.totalSalaries ?? '—', icon: Users, color: 'text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]' },
                        { label: 'Heures totales', value: kpis ? `${formatHeuresDecimal(kpis.totalHeures)} (${formatHeuresMinutes(kpis.totalHeures)})` : '—', icon: Clock, color: 'text-[var(--color-info-600)] dark:text-[var(--color-info-400)]' },
                        { label: 'HS30 + HS50', value: kpis ? `${formatHeuresDecimal(kpis.totalHs30 + kpis.totalHs50)} (${formatHeuresMinutes(kpis.totalHs30 + kpis.totalHs50)})` : '—', icon: TrendingUp, color: 'text-[var(--color-brand-500)] dark:text-[var(--color-brand-300)]' },
                        { label: 'Retards cumulés', value: kpis ? `${formatHeuresDecimal(kpis.totalRetardMins / 60)} (${formatHeuresMinutes(kpis.totalRetardMins / 60)})` : '—', icon: AlertTriangle, color: 'text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)]' },
                        { label: 'Indemnités totales', value: kpis ? formatMonnaie(kpis.totalIndemnites) : '—', icon: Banknote, color: 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]' },
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
                                {isDaf ? 'Contrôle & Validation' : 'Synthèse mensuelle & Auto-Contrôle'} — {currentPeriodeObj?.libelle || 'Période active'}
                            </h3>
                            {/* Filtres alerte (synthèse seulement) */}
                            {viewMode === 'synthese' && (
                                <div className="flex items-center gap-1 flex-wrap">
                                    {[
                                        { v: 'ALL', label: `Tous (${alertCounts.all ?? 0})` },
                                        { v: 'HS', label: `HS (${alertCounts.hs ?? 0})`, icon: TrendingUp },
                                        { v: 'NUIT', label: `Nuit (${alertCounts.nuit ?? 0})`, icon: Moon },
                                        { v: 'RETARD', label: `Retards (${alertCounts.retard ?? 0})`, icon: Clock },
                                        { v: 'COMPENSE', label: `Compensés (${alertCounts.compense ?? 0})`, icon: Scale },
                                        { v: 'ABSENCE', label: `Retenues (${alertCounts.absence ?? 0})`, icon: AlertTriangle },
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
                            <div className="w-48 sm:w-56">
                                <Input
                                    icon={<Search size={13} className="text-[var(--color-fg-muted)]" />}
                                    iconRight={searchTerm ? (
                                        <button
                                            type="button"
                                            onClick={() => { setSearchTerm(''); setCurrentPage(1); }}
                                            className="hover:text-[var(--color-fg)] cursor-pointer"
                                        >
                                            <X size={13} />
                                        </button>
                                    ) : null}
                                    value={searchTerm}
                                    onChange={(e) => { setSearchTerm(e.target.value); setCurrentPage(1); }}
                                    placeholder="Nom ou matricule..."
                                    className="h-[32px] text-xs"
                                />
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

                            {/* Toggle colonnes audit (Surplus / Compensé) */}
                            {viewMode === 'synthese' && (
                                <button
                                    type="button"
                                    onClick={() => setShowAuditColumns(prev => !prev)}
                                    className={`flex items-center gap-1.5 px-2.5 py-1.5 text-[11px] font-semibold border transition-all cursor-pointer ${
                                        showAuditColumns
                                            ? 'border-[var(--color-brand-500)] bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)]'
                                            : 'border-[var(--color-border-subtle)] text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'
                                    }`}
                                    title="Afficher ou masquer les colonnes techniques de calcul (Surplus journalier et Compensé)"
                                >
                                    <Scale size={13} />
                                    <span className="hidden md:inline">{showAuditColumns ? 'Masquer calculs' : 'Mode audit (Surplus/Compensé)'}</span>
                                </button>
                            )}
                        </div>
                    </div>

                    {/* Vue Synthese — tableau mensuel par employe */}
                    {viewMode === 'synthese' && (
                        loading ? (
                            <div className="flex items-center justify-center gap-2 py-12 text-xs text-[var(--color-fg-muted)]">
                                <Clock size={14} className="animate-spin text-brand-500" />Chargement de la synthese...
                            </div>
                        ) : !selectedPeriode ? (
                            <div className="flex flex-col items-center justify-center gap-2 py-12 text-xs text-[var(--color-fg-muted)]">
                                <Calendar size={20} className="text-[var(--color-fg-muted)] opacity-50" />
                                <span>Veuillez sélectionner une période pour afficher le contrôle.</span>
                            </div>
                        ) : displayedData.length === 0 ? (
                            <div className="flex items-center justify-center gap-3 py-12 text-xs text-[var(--color-fg-muted)]">
                                <CheckCircle2 size={18} className="text-[var(--color-success-600)]" />
                                <span>Aucun collaborateur ne correspond aux critères sélectionnés.</span>
                            </div>
                        ) : (
                            <div className="space-y-0 overflow-x-auto">
                                {/* Bannière explicative règle de compensation */}
                                <div className="attendance-info mb-3 p-2.5 text-xs flex items-center gap-2">
                                    <Scale size={15} className="shrink-0" />
                                    <div>
                                        <strong>Compensation hebdomadaire :</strong> les heures en plus sur une journée compensent d&apos;abord les heures manquantes de la même semaine. L&apos;objectif est automatiquement ajusté pour les congés et jours fériés. Le solde devient une <span className="font-semibold text-[var(--color-purple-700)] dark:text-[var(--color-purple-200)]">heure supplémentaire</span> ou une <span className="font-semibold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">retenue sur salaire</span>. Les retards restent suivis séparément.
                                    </div>
                                </div>

                                <Table size="sm" divided>
                                    <Thead>
                                        <Tr>
                                            <Th className="w-[190px] max-w-[210px]">Collaborateur</Th>
                                            <Th className="text-center w-16">Jours</Th>
                                            <Th className="text-center w-20">Heures</Th>
                                                    <Th className="text-center w-16">
                                                        <span className="inline-flex items-center gap-1 text-[var(--color-purple-700)] dark:text-[var(--color-purple-200)]">
                                                    <TrendingUp size={11} /> HS 30% finales
                                                </span>
                                            </Th>
                                                    <Th className="text-center w-16">
                                                        <span className="inline-flex items-center gap-1 text-[var(--color-purple-700)] dark:text-[var(--color-purple-200)]">
                                                    <Award size={11} /> HS 50%
                                                </span>
                                            </Th>
                                                    <Th className="text-center w-14">
                                                        <span className="inline-flex items-center gap-1 text-[var(--color-info-700)] dark:text-[var(--color-info-200)]">
                                                    <Moon size={11} /> Nuit
                                                </span>
                                            </Th>
                                                    <Th className="text-center w-28">
                                                        <span className="inline-flex items-center gap-1 text-[var(--color-orange-700)] dark:text-[var(--color-orange-200)]">
                                                    <Banknote size={11} /> Indemnités
                                                </span>
                                            </Th>
                                                    <Th className="text-center w-20" title="Retards constatés sur la journée (matin et après-midi)">
                                                        <span className="inline-flex items-center gap-1 text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">
                                                    <Clock size={11} /> Retards
                                                </span>
                                            </Th>
                                            {showAuditColumns && (
                                                <>
                                                    <Th className="text-center w-20" title="Heures réalisées au-delà de la durée normale d'une journée">
                                                        <span className="inline-flex items-center gap-1 text-[var(--color-info-700)] dark:text-[var(--color-info-200)]">
                                                            <TrendingUp size={11} /> En plus
                                                        </span>
                                                    </Th>
                                                    <Th className="text-center w-22" title="Heures en plus utilisées pour compenser un manque de la même semaine">
                                                        <span className="inline-flex items-center gap-1 text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">
                                                            <Scale size={11} /> Utilisées
                                                        </span>
                                                    </Th>
                                                </>
                                            )}
                                            <Th className="text-center w-20" title="Absence horaire finale après rapprochement de période, hors congés">
                                                <span className="inline-flex items-center gap-1 text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">
                                                    <AlertTriangle size={11} /> Absence finale
                                                </span>
                                            </Th>
                                            <Th className="text-center w-16" title="Jours d'absence non justifiée">
                                                Absences
                                            </Th>
                                            <Th className="text-center w-20">Actions</Th>
                                        </Tr>
                                    </Thead>
                                    <Tbody>
                                        {displayedData.map((row) => {
                                            const presents = row.nbJoursPresents ?? row.joursPresents;
                                            const profilLabel = row.badgeProfilLabel || (row.dispensePointage ? 'AUTONOME' : row.estCadre ? 'CADRE' : null);
                                            const profilVariant = row.badgeProfilVariant || (row.dispensePointage ? 'neutral' : row.estCadre ? 'info' : null);
                                            const allowanceEntries = Object.entries(row.montantsIndemnites || {});
                                            const hs30Finales = row.minutesHs30Finales != null ? row.minutesHs30Finales / 60 : (row.heuresHs30 || 0);
                                            const hs50Finales = row.minutesHs50Finales != null ? row.minutesHs50Finales / 60 : (row.heuresHs50 || 0);
                                            const absenceFinale = row.minutesAbsenceFinale != null ? row.minutesAbsenceFinale / 60 : (row.heuresAbsenceRetenues || 0);
                                            const totalIndRow = allowanceEntries.reduce((total, [, amount]) => total + Number(amount || 0), 0);
                                            const allowanceDetails = allowanceEntries.map(([code, amount]) =>
                                                `• ${code} : ${formatMonnaie(amount || 0)}`).join('\n');

                                            return (
                                                <Tr key={row.idEmploye || row.matricule}>
                                                    <Td className="w-[190px] max-w-[210px]">
                                                        <div className="font-semibold text-xs text-[var(--color-fg)] truncate" title={`${row.nom} ${row.prenom}`}>
                                                            <button
                                                                type="button"
                                                                onClick={() => setSelectedEmployeForDetail(row)}
                                                                className="hover:text-[var(--color-brand-600)] hover:underline text-left cursor-pointer transition-colors"
                                                                title={`Consulter le détail pointage & compensation de ${row.nom} ${row.prenom}`}
                                                            >
                                                                {row.nom} {row.prenom}
                                                            </button>
                                                        </div>
                                                        <div className="text-[10px] font-mono text-[var(--color-fg-muted)] flex items-center gap-1.5 mt-0.5">
                                                            <span>{row.matricule}</span>
                                                            {profilLabel && (
                                                                <span className={`px-1.5 py-0 text-[8.5px] font-bold border ${getAttendanceBadgeClass(profilVariant)}`}>
                                                                    {profilLabel}
                                                                </span>
                                                            )}
                                                        </div>
                                                    </Td>
                                                    <Td className="text-center font-mono text-xs">
                                                        <div className="font-semibold">{presents != null && presents > 0 ? `${presents}j` : presents === 0 ? '0j' : '—'}</div>
                                                        {((row.nbJoursConge || 0) > 0 || (row.nbJoursFerieChome || 0) > 0) && (
                                                            <div className="flex flex-wrap items-center justify-center gap-1 mt-0.5 font-sans">
                                                                {(row.nbJoursConge || 0) > 0 && (
                                                                    <span className="text-[9px] px-1 py-0.2 rounded bg-[var(--color-info-500)]/15 text-[var(--color-info-700)] dark:text-[var(--color-info-300)] font-medium" title={`${row.nbJoursConge}j de congé payé`}>
                                                                        +{row.nbJoursConge}j congé
                                                                    </span>
                                                                )}
                                                                {(row.nbJoursFerieChome || 0) > 0 && (
                                                                    <span className="text-[9px] px-1 py-0.2 rounded bg-[var(--color-warning-500)]/15 text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] font-medium" title={`${row.nbJoursFerieChome}j férié chômé`}>
                                                                        +{row.nbJoursFerieChome}j férié
                                                                    </span>
                                                                )}
                                                            </div>
                                                        )}
                                                    </Td>
                                                    <Td className="text-center font-mono text-xs font-semibold">
                                                        {row.totalHeuresTravaillees != null ? (
                                                            <div>
                                                                <div>{formatHeuresMinutes(row.totalHeuresTravaillees)}</div>
                                                                <div className="attendance-duration-secondary">({formatHeuresDecimal(row.totalHeuresTravaillees)})</div>
                                                            </div>
                                                        ) : '—'}
                                                    </Td>
                                                    <Td className="text-center">
                                                        {hs30Finales > 0 ? (
                                                            <span className="attendance-overtime inline-block attendance-duration text-xs font-bold px-1.5 py-0.5" title="HS 30 % après rapprochement de période">
                                                                {formatHeuresMinutes(hs30Finales)}
                                                                <span className="attendance-duration-secondary block">({formatHeuresDecimal(hs30Finales)})</span>
                                                            </span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)] text-xs font-mono">—</span>
                                                        )}
                                                    </Td>
                                                    <Td className="text-center">
                                                        {hs50Finales > 0 ? (
                                                            <span className="attendance-overtime inline-block attendance-duration text-xs font-bold px-1.5 py-0.5" title={`${formatHeuresMinutes(hs50Finales)}`}>
                                                                {formatHeuresMinutes(hs50Finales)}
                                                                <span className="attendance-duration-secondary block">({formatHeuresDecimal(hs50Finales)})</span>
                                                            </span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)] text-xs font-mono">—</span>
                                                        )}
                                                    </Td>
                                                    <Td className="text-center">
                                                        {(row.heuresNuitTotal || 0) > 0 ? (
                                                            <span className="attendance-info inline-block attendance-duration text-xs font-bold px-1.5 py-0.5" title={`${formatHeuresMinutes(row.heuresNuitTotal)}`}>
                                                                {formatHeuresMinutes(row.heuresNuitTotal)}
                                                                <span className="attendance-duration-secondary block">({formatHeuresDecimal(row.heuresNuitTotal)})</span>
                                                            </span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)] text-xs font-mono">—</span>
                                                        )}
                                                    </Td>
                                                    {/* Total Indemnités regroupé */}
                                                    <Td
                                                        className="attendance-allowance text-center attendance-duration text-xs cursor-help"
                                                        title={`Détail des indemnités :\n${allowanceDetails || 'Aucune'}\n• Total : ${formatMonnaie(totalIndRow)}`}
                                                    >
                                                        {totalIndRow > 0 ? (
                                                            <div className="flex flex-col items-center">
                                                                <span className="font-bold">{formatMonnaie(totalIndRow)}</span>
                                                                <span className="text-[9px] text-[var(--color-fg-muted)] mt-0.5">
                                                                    {allowanceEntries.map(([code]) => code).join(' · ')}
                                                                </span>
                                                            </div>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)] text-xs font-mono">—</span>
                                                        )}
                                                    </Td>
                                                    {/* Retards (Rouge) */}
                                                    <Td className="text-center">
                                                        {(row.totalMinutesRetard || 0) > 0 ? (
                                                            <span
                                                                className="attendance-retention inline-block attendance-duration text-xs font-bold px-1.5 py-0.5 cursor-help"
                                                                title={`Retards constatés (matin et après-midi) : ${formatHeuresDecimal(row.totalMinutesRetard / 60)} (${formatHeuresMinutes(row.totalMinutesRetard / 60)})`}
                                                            >
                                                                {formatHeuresMinutes(row.totalMinutesRetard / 60)}
                                                                <span className="attendance-duration-secondary block">({formatHeuresDecimal(row.totalMinutesRetard / 60)})</span>
                                                            </span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)] text-xs font-mono">—</span>
                                                        )}
                                                    </Td>
                                                    {/* Colonnes d'audit conditionnelles */}
                                                    {showAuditColumns && (
                                                        <>
                                                            {/* Surplus brut */}
                                                            <Td className="text-center">
                                                                {(row.surplusBrut || 0) > 0 ? (
                                                                    <span
                                                                        className="attendance-info inline-block attendance-duration text-xs font-bold px-1.5 py-0.5 cursor-help"
                                                                        title={`Heures en plus sur la journée : +${formatHeuresMinutes(row.surplusBrut)} (+${formatHeuresDecimal(row.surplusBrut)})`}
                                                                    >
                                                                        +{formatHeuresMinutes(row.surplusBrut)}
                                                                        <span className="attendance-duration-secondary block">(+{formatHeuresDecimal(row.surplusBrut)})</span>
                                                                    </span>
                                                                ) : (
                                                                    <span className="text-[var(--color-fg-subtle)] text-xs font-mono">—</span>
                                                                )}
                                                            </Td>
                                                            {/* Compensé */}
                                                            <Td className="text-center">
                                                                {(row.retardCompense || 0) > 0 ? (
                                                                    <span
                                                                        className="attendance-compensation inline-block attendance-duration text-xs font-bold px-1.5 py-0.5 cursor-help"
                                                                        title={`Heures manquantes comblées par les surplus intra-semaine : -${formatHeuresDecimal(row.retardCompense)} (-${formatHeuresMinutes(row.retardCompense)})`}
                                                                    >
                                                                        -{formatHeuresMinutes(row.retardCompense)}
                                                                        <span className="attendance-duration-secondary block">(-{formatHeuresDecimal(row.retardCompense)})</span>
                                                                    </span>
                                                                ) : (
                                                                    <span className="text-[var(--color-fg-subtle)] text-xs font-mono">—</span>
                                                                )}
                                                            </Td>
                                                        </>
                                                    )}
                                                    {/* Retenue Paie (Net) */}
                                                    <Td className="text-center">
                                                        {absenceFinale > 0 ? (
                                                            <span
                                                                className="attendance-retention inline-block attendance-duration text-xs font-bold px-1.5 py-0.5 cursor-help"
                                                                title={`Absence horaire finale hors congés : -${formatHeuresDecimal(absenceFinale)} (-${formatHeuresMinutes(absenceFinale)})`}
                                                            >
                                                                -{formatHeuresMinutes(absenceFinale)}
                                                                <span className="attendance-duration-secondary block">(-{formatHeuresDecimal(absenceFinale)})</span>
                                                            </span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)] text-xs font-mono">—</span>
                                                        )}
                                                    </Td>
                                                    {/* Absences (Jours) */}
                                                    <Td className="text-center">
                                                        {(row.nbJoursAbsence || 0) > 0 ? (
                                                            <span
                                                                className="inline-block font-mono text-xs text-rose-700 dark:text-rose-400 font-bold bg-rose-500/10 px-1.5 py-0.5 border border-rose-500/20 cursor-help"
                                                                title={`Absence non justifiée : ${row.nbJoursAbsence} jour(s)`}
                                                            >
                                                                {row.nbJoursAbsence}j
                                                            </span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)] text-xs font-mono">—</span>
                                                        )}
                                                    </Td>
                                                    {/* Actions : Voir détail & Ajuster */}
                                                    <Td className="text-center">
                                                        <div className="flex items-center justify-center gap-1">
                                                            <button type="button" onClick={() => setSelectedEmployeForSolde(row)}
                                                                className="inline-flex items-center justify-center w-7 h-7 border border-[var(--color-border-subtle)] hover:border-[var(--color-brand-500)] hover:bg-[var(--color-brand-500)]/10 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] transition-all cursor-pointer"
                                                                title="Voir le solde horaire de période et les ajustements">
                                                                <Scale size={13} />
                                                            </button>
                                                            <button
                                                                type="button"
                                                                onClick={() => setSelectedEmployeForDetail(row)}
                                                                className="inline-flex items-center justify-center w-7 h-7 border border-[var(--color-border-subtle)] hover:border-[var(--color-brand-500)] hover:bg-[var(--color-brand-500)]/10 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] transition-all cursor-pointer"
                                                                title="Voir le détail des pointages & la compensation hebdomadaire"
                                                            >
                                                                <Eye size={13} />
                                                            </button>
                                                            {isDaf && (
                                                                <button
                                                                    type="button"
                                                                    disabled={isPeriodLocked}
                                                                    onClick={() => {
                                                                        if (isPeriodLocked) {
                                                                            toast({
                                                                                title: 'Période verrouillée',
                                                                                message: 'La période est validée ou clôturée : les modifications de pointage ne sont plus autorisées.',
                                                                                type: 'warning'
                                                                            });
                                                                            return;
                                                                        }
                                                                        setAdjustInitialDate(null);
                                                                        setSelectedEmployeToAdjust(row);
                                                                    }}
                                                                    className={`inline-flex items-center justify-center w-7 h-7 border border-[var(--color-border-subtle)] ${isPeriodLocked ? 'opacity-40 cursor-not-allowed' : 'hover:border-[var(--color-brand-500)] hover:bg-[var(--color-brand-500)]/10 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] cursor-pointer'} transition-all`}
                                                                    title={isPeriodLocked ? "Période validée/clôturée : modifications impossibles" : "Ajuster le pointage de ce collaborateur"}
                                                                >
                                                                    <Edit3 size={13} />
                                                                </button>
                                                            )}
                                                        </div>
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

                    {/* Vue Gantt hebdomadaire pour la DAF (Lazy Loaded) */}
                    {viewMode === 'gantt' && (
                        <Suspense fallback={<div className="p-8 text-center text-xs font-bold text-[var(--color-fg-muted)]">Chargement de la vue Gantt…</div>}>
                            <PointageGanttView
                                semaines={semaines}
                                selectedSemaine={selectedSemaine}
                                onSelectSemaine={(sem) => setSelectedSemaine(sem)}
                                ganttData={ganttData}
                                loadingGantt={loadingGantt}
                                isReadOnly={isPeriodLocked}
                                onCellClick={(emp, day, _cellData) => {
                                    if (isPeriodLocked) {
                                        toast({
                                            title: 'Période verrouillée',
                                            message: 'La période est validée ou clôturée : les modifications de pointage ne sont plus autorisées.',
                                            type: 'warning'
                                        });
                                        return;
                                    }
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
                        </Suspense>
                    )}
                </div>
            </div>

            {/* Modales d'actions de validation */}
            <ConfirmDialog
                isOpen={showTransmissionModal}
                onClose={() => setShowTransmissionModal(false)}
                onConfirm={handleTransmettreDaf}
                title={`Transmettre : ${currentPeriodeObj?.libelle || ''} ?`}
                message="Cette action transmet officiellement la synthèse consolidée de pointage pour arbitrage et validation finale. La saisie quotidienne sera figée pour les agents de site."
                confirmLabel="Confirmer et transmettre"
                variant="primary"
                loading={transmitting}
            />

            <ConfirmDialog
                isOpen={showValiderModal}
                onClose={() => setShowValiderModal(false)}
                onConfirm={handleValiderPeriode}
                title={`Arbitrage & Gel : ${currentPeriodeObj?.libelle || ''} ?`}
                message="Cette action fige les données de pointage de la période. La paie utilisera le solde final d’absence horaire et de HS 30 %, ainsi que les autres qualifications et indemnités figées. Vous pourrez ensuite contrôler les bulletins avant validation. Confirmer l’arbitrage et le scellement ?"
                confirmLabel="Valider l'arbitrage & Sceller le pointage"
                variant="success"
                loading={processing}
            />

            <Modal
                isOpen={showRejetModal}
                onClose={() => setShowRejetModal(false)}
                title="Renvoyer pour correction"
                size="md"
                footer={
                    <div className="flex justify-end gap-2 w-full">
                        <Button type="button" variant="ghost" size="sm" onClick={() => setShowRejetModal(false)}>Annuler</Button>
                        <Button type="button" variant="danger" size="sm" loading={processing} onClick={handleRejeterPeriode}
                            disabled={!motifRejet.trim()}>
                            Renvoyer pour correction
                        </Button>
                    </div>
                }
            >
                <div className="space-y-3 text-xs">
                    <p className="text-[var(--color-fg-muted)]">
                        Indiquez le motif du renvoi pour correction. Les agents de site pourront rectifier les données puis re-transmettre la période.
                    </p>
                    <div>
                        <label className="block text-[10.5px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] mb-1">Motif (obligatoire)</label>
                        <Textarea
                            value={motifRejet}
                            onChange={e => setMotifRejet(e.target.value)}
                            rows={3}
                            placeholder="Ex: Incohérences sur les heures supplémentaires ou justificatifs d'absence..."
                            required
                        />
                    </div>
                </div>
            </Modal>

            {/* Modale ajustement DAF individuel (Lazy Loaded) */}
            {selectedEmployeToAdjust && (
                <Suspense fallback={null}>
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
                </Suspense>
            )}

            {/* Modale détail pointages, compensation et semaines (Lazy Loaded) */}
            {selectedEmployeForDetail && (
                <Suspense fallback={null}>
                    <PointageDetailHebdoModal
                        isOpen={Boolean(selectedEmployeForDetail)}
                        onClose={() => setSelectedEmployeForDetail(null)}
                        employe={selectedEmployeForDetail}
                        idPeriodePaie={selectedPeriode}
                        periodeLibelle={currentPeriodeObj?.libelle || currentPeriodeObj?.codePeriode}
                        onAjusterPointage={(emp) => {
                            setSelectedEmployeForDetail(null);
                            setAdjustInitialDate(null);
                            setSelectedEmployeToAdjust(emp);
                        }}
                    />
                </Suspense>
            )}
            {selectedEmployeForSolde && (
                <Suspense fallback={null}>
                    <PointageSoldePeriodeModal
                        employe={selectedEmployeForSolde}
                        idPeriodePaie={Number(selectedPeriode)}
                        readOnly={!isDaf || isPeriodLocked}
                        onClose={() => setSelectedEmployeForSolde(null)}
                        onChanged={loadData}
                    />
                </Suspense>
            )}
        </>
    );
}
