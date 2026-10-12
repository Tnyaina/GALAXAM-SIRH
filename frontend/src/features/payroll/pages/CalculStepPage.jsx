import { useState, useCallback, useEffect, lazy, Suspense } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
    Calculator, Play, CheckCircle2, AlertTriangle, RefreshCw, Eye,
    Users, ArrowRight, Sparkles, ChevronDown, ChevronUp, Search, XCircle
} from 'lucide-react';
import SEO from '@/components/system/SEO';
import PayrollCampaignShell from '@/features/payroll/components/workflow/PayrollCampaignShell';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import { useToast } from '@/components/ui/feedback/Toast';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Badge from '@/components/ui/data-display/Badge';
import { Card } from '@/components/ui/data-display/Card';
import StatCard from '@/components/ui/data-display/StatCard';
import Pagination from '@/components/ui/navigation/Pagination';
import { Tr, Td } from '@/components/ui/data-display/Table';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import paieService from '@/features/payroll/api/paieService';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { getErrorMessage } from '@/api/apiClient';
import PayrollStepTable, { PayrollEmployeeCell, PayrollMoneyCell } from '@/features/payroll/components/workflow/PayrollStepTable';

const BulletinPaieDrawer = lazy(() => import('@/features/payroll/components/views/BulletinPaieDrawer'));

const summarizeBatch = (batch, isSimulation) => {
    const outcomes = Array.isArray(batch.employeeOutcomes) ? batch.employeeOutcomes : [];
    const successful = outcomes.filter(outcome => outcome.status === 'OK' && outcome.result?.bulletin);
    return {
        isSimulation,
        outcomes,
        blocked: outcomes.filter(outcome => outcome.status !== 'OK'),
        bulletins: batch.nbBulletinsCalcules ?? successful.length,
        brut: batch.totalMasseSalarialeBrute,
        cotisationsSalariales: batch.totalCotisationsSalariales,
        irsa: batch.totalIrsa,
        net: batch.totalNetAPayer,
        patronales: batch.totalChargesPatronales,
        coutGlobal: batch.totalCoutGlobal,
    };
};

/**
 * CalculStepPage — Étape 4 : Calcul de la paie (Maquette V1 Full V3).
 *
 * États opérationnels :
 * 1. AVANT CALCUL : Campagne prête à calculer (X salariés, 0 blocker, actions [Simuler] et [Calculer])
 * 2. APRES CALCUL : Calcul vN (X/X calculés, compteurs, KPIs financiers consolidés, [Voir les résultats])
 * 3. STALE (Calcul obsolète) : Signal clair, liste des salariés affectés (ex: snapshot pointage), validation verrouillée, action [Recalculer]
 *
 * Règle stricte : Zéro recalcul mathématique dans React.
 */
export default function CalculStepPage() {
    // Contexte et hook métier
    const { periodId } = useParams();
    const navigate = useNavigate();
    const toast = useToast();
    const numericPeriodId = Number(periodId) || null;
    const { periodes, context, loading: ctxLoading, refreshSilent } = usePayrollCampaign(numericPeriodId);

    // État local
    const [calcLoading, setCalcLoading] = useState(false);
    const [simLoading, setSimLoading] = useState(false);
    const [result, setResult] = useState(null);
    const [existingKpis, setExistingKpis] = useState(null);
    const [cockpitPopulationCount, setCockpitPopulationCount] = useState(null);
    const [existingBulletins, setExistingBulletins] = useState([]);
    const [bulletinPage, setBulletinPage] = useState(1);
    const [bulletinTotal, setBulletinTotal] = useState(0);
    const [showResultsTable, setShowResultsTable] = useState(true);
    const [selectedBulletinId, setSelectedBulletinId] = useState(null);
    const [cockpitError, setCockpitError] = useState(null);
    const [cockpitLoaded, setCockpitLoaded] = useState(false);
    const [isCalcConfirmOpen, setIsCalcConfirmOpen] = useState(false);
    // Recherche & filtres
    const [tableSearch, setTableSearch] = useState('');
    const [blockedSearch, setBlockedSearch] = useState('');
    const [blockedPage, setBlockedPage] = useState(1);
    const BLOCKED_PAGE_SIZE = 5;
    const TABLE_PAGE_SIZE = 20;

    // Données dérivées
    const actions = context?.availableActions || {};
    const canCalculate = actions?.CALCULATE?.allowed === true;
    const canSimulate = actions?.SIMULATE?.allowed === true;
    const calculationReasons = actions?.CALCULATE?.reasonCodes || [];
    const pendingVariables = calculationReasons.includes('PAYROLL_VARIABLES_PENDING_APPROVAL');
    const recalcRequired = context?.recalculationRequired === true;
    const recalcReason = context?.recalculationReason;
    const isStale = recalcRequired;
    // isReadOnly : dérivé du campaign step CALCULATION fourni par le backend.
    // Si state === 'LOCKED', la période est clôturée et on ne peut plus rien modifier.
    // Ne pas réimplémenter la liste des statuts ici : le backend est la source de vérité.
    const calcStep = context?.campaignSteps?.find(s => s.code === 'CALCULATION');
    // BLOCKED signifie qu'un prérequis manque, pas que la campagne est archivée.
    // Seul l'état métier fourni par le backend "LOCKED" rend cette étape en lecture seule.
    const isReadOnly = calcStep?.state === 'LOCKED';

    // Effets
    const loadExistingCalculation = useCallback(async () => {
        if (!numericPeriodId) return;
        try {
            const cockpit = await paieService.getCockpitPeriode(numericPeriodId, { page: bulletinPage, size: 20 });
            setCockpitPopulationCount(cockpit?.populationPage?.totalElements ?? cockpit?.readiness?.effectifAttendu ?? null);
            if (cockpit?.kpis && (cockpit.kpis.totalBulletins > 0 || cockpit.kpis.bulletinsCount > 0 || cockpit.kpis.effectifCalcule > 0)) {
                setExistingKpis({
                    bulletins: cockpit.kpis.totalBulletins ?? cockpit.kpis.bulletinsCount ?? cockpit.kpis.effectifCalcule ?? 0,
                    brut: cockpit.kpis.totalBrut ?? cockpit.kpis.masseSalarialeBrute ?? 0,
                    cotisationsSalariales: cockpit.kpis.totalCotisationsSalariales ?? 0,
                    irsa: cockpit.kpis.totalIrsa ?? 0,
                    net: cockpit.kpis.totalNetAPayer ?? cockpit.kpis.masseNetAPayer ?? 0,
                    patronales: cockpit.kpis.totalCotisationsPatronales ?? 0,
                    coutGlobal: cockpit.kpis.coutGlobalEmployeur,
                });
            } else {
                setExistingKpis(null);
            }

            if (Array.isArray(cockpit?.bulletins) && cockpit.bulletins.length > 0) {
                setExistingBulletins(cockpit.bulletins);
            } else {
                setExistingBulletins([]);
            }
            setBulletinTotal(cockpit?.bulletinsPage?.totalElements ?? cockpit?.bulletins?.length ?? 0);
            setCockpitError(null);
        } catch (err) {
            setCockpitError(getErrorMessage(err, 'Impossible de charger les résultats de calcul.'));
        } finally {
            setCockpitLoaded(true);
        }
    }, [numericPeriodId, bulletinPage]);

    useEffect(() => {
        loadExistingCalculation();
    }, [loadExistingCalculation]);

    useEffect(() => {
        const refreshIfVisible = () => {
            if (document.visibilityState !== 'visible') return;
            refreshSilent();
            loadExistingCalculation();
        };
        document.addEventListener('visibilitychange', refreshIfVisible);
        return () => document.removeEventListener('visibilitychange', refreshIfVisible);
    }, [refreshSilent, loadExistingCalculation]);

    // Actions
    const handleSimuler = useCallback(async () => {
        if (!numericPeriodId) return;
        setSimLoading(true);
        try {
            const res = await paieService.calculerPaiePeriode(numericPeriodId, true);
            setBulletinPage(1);
            setResult(summarizeBatch(res, true));
            setShowResultsTable(true);
            toast.info('Simulation terminée.');
        } catch (err) {
            toast.error(getErrorMessage(err, 'Erreur lors de la simulation.'));
        } finally {
            setSimLoading(false);
        }
    }, [numericPeriodId, toast]);

    const executeCalculation = useCallback(async () => {
        if (!numericPeriodId) return;
        setCalcLoading(true);
        try {
            const res = await paieService.calculerPaiePeriode(numericPeriodId, false);
            setBulletinPage(1);
            const summary = summarizeBatch(res, false);
            setResult(summary);
            toast.success('Calcul réel exécuté avec succès.');
            await refreshSilent();
            await loadExistingCalculation();
        } catch (err) {
            toast.error(getErrorMessage(err, "Erreur lors de l'exécution du calcul."));
        } finally {
            setCalcLoading(false);
            setIsCalcConfirmOpen(false);
        }
    }, [numericPeriodId, refreshSilent, loadExistingCalculation, toast]);

    const hasPersistedBulletins = Boolean((existingKpis?.bulletins ?? 0) > 0 || (result && !result.isSimulation && (result?.bulletins ?? 0) > 0));
    const isSimulationActive = result?.isSimulation === true;
    const hasBulletins = hasPersistedBulletins || result !== null;
    const displayKpis = result || existingKpis;
    const simulationOutcomes = isSimulationActive ? result.outcomes : [];
    const simulationBulletins = simulationOutcomes.filter(outcome => outcome.status === 'OK' && outcome.result?.bulletin).map(outcome => outcome.result.bulletin);
    const displayBulletins = isSimulationActive
        ? simulationBulletins.slice((bulletinPage - 1) * 20, bulletinPage * 20)
        : existingBulletins;
    const populationCount = cockpitPopulationCount ?? context?.populationCount ?? context?.population?.eligibleCount ?? context?.periodContext?.nbSalariesTotal;
    const calculationVersion = context?.periodContext?.calculationSnapshotVersion ?? context?.snapshotVersion;
    const controlStep = context?.campaignSteps?.find(step => step.code === 'CONTROL');
    const canOpenControl = Boolean(controlStep?.destination && controlStep.state !== 'BLOCKED');

    // Salariés affectés par une obsolescence STALE (si fournis par le contexte)
    const affectedEmployees = context?.staleAffectedEmployees || context?.affectedEmployees || [];

    return (
        <PayrollCampaignShell
            context={context}
            periodes={periodes}
            activeStep="CALCULATION"
            loading={ctxLoading}
            title="Calcul de la paie"
            subtitle="Vérifiez les autorisations de la campagne, lancez une simulation ou calculez les bulletins."
        >
            <SEO
                title="Calcul de la paie | GALAXAM GRH"
                description="Calcul et simulation du moteur de paie GALAXAM."
            />

            <div className="space-y-6">
                {cockpitError && <Alert type="error" title="Résultats indisponibles" message={cockpitError} />}
                {!cockpitLoaded && <Card className="border border-[var(--color-border)] p-5 text-sm text-[var(--color-fg-muted)]">Chargement des résultats de la campagne…</Card>}
                {pendingVariables && !hasPersistedBulletins && (
                    <div className="flex flex-wrap items-center justify-between gap-3 border border-[var(--color-warning-500)]/40 bg-[var(--color-warning-500)]/10 p-3">
                        <p className="text-sm text-[var(--color-fg)]">Des variables attendent une décision avant le calcul réel.</p>
                        <Button variant="secondary" size="sm" onClick={() => navigate(`/paie/periodes/${numericPeriodId}/variables`)}>Traiter les variables</Button>
                    </div>
                )}

                {/* 1. ÉTAT STALE (CALCUL OBSOLÈTE) */}
                {isStale && (
                    <div className="p-4 bg-[var(--color-warning-500)]/10 border-2 border-[var(--color-warning-500)] rounded-none space-y-3">
                        <div className="flex items-start gap-3">
                            <AlertTriangle size={20} className="text-[var(--color-warning-600)] shrink-0 mt-0.5" />
                            <div className="flex-1">
                                <div className="flex items-center gap-2">
                                    <h3 className="text-sm font-bold text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] uppercase tracking-wide">
                                        Calcul à relancer
                                    </h3>
                                    <Badge variant="warning" size="xs">Résultats obsolètes</Badge>
                                </div>
                                <p className="text-xs text-[var(--color-fg-muted)] mt-1">
                                    {recalcReason === 'CALCULATION_INPUTS_CHANGED'
                                        ? 'Des modifications ont été apportées aux variables ou aux snapshots de pointage depuis le dernier calcul. Un recalcul complet est requis avant validation.'
                                        : 'Les données sources ont changé depuis le dernier calcul. Un nouveau calcul est nécessaire avant validation.'}
                                </p>
                            </div>
                        </div>

                        {affectedEmployees.length > 0 && (
                            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 rounded text-xs space-y-2">
                                <span className="font-semibold text-[var(--color-fg)]">
                                    {affectedEmployees.length} collaborateur(s) directement affecté(s) :
                                </span>
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                                    {affectedEmployees.map((emp, idx) => (
                                        <div key={idx} className="flex items-center justify-between p-2 bg-[var(--color-bg-subtle)] rounded border border-[var(--color-border-subtle)]">
                                            <span className="font-medium text-[var(--color-fg)]">{emp.nomPrenom || emp.matricule}</span>
                                            <span className="text-[11px] text-[var(--color-fg-muted)] font-mono">
                                                {[emp.source, emp.detail].filter(Boolean).join(' · ')}
                                            </span>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}

                        <div className="flex items-center justify-end gap-2 pt-1">
                            <Button
                                variant="primary"
                                size="sm"
                                onClick={() => setIsCalcConfirmOpen(true)}
                                loading={calcLoading}
                                disabled={!canCalculate || !cockpitLoaded}
                                iconLeft={<RefreshCw size={14} />}
                            >
                                Recalculer la paie
                            </Button>
                        </div>
                    </div>
                )}

                {/* 2. AVANT CALCUL : CAMPAGNE PRÊTE */}
                {!hasBulletins && !isStale && cockpitLoaded && !cockpitError && (
                    <Card className="border border-[var(--color-border)] p-5 space-y-5">
                        <div className="flex flex-wrap items-start justify-between gap-4">
                            <div className="flex items-start gap-3">
                                <div className="w-10 h-10 bg-[var(--color-brand-50)] dark:bg-[var(--color-brand-950)] text-[var(--color-brand-600)] flex items-center justify-center shrink-0">
                                    <Calculator size={20} />
                                </div>
                                <div>
                                    <h3 className="text-base font-bold text-[var(--color-fg)]">{canCalculate ? 'Calcul autorisé' : 'Calcul en attente'}</h3>
                                    <p className="text-sm text-[var(--color-fg-muted)] mt-1">
                                        {canCalculate ? 'La campagne peut être calculée selon les contrôles du serveur.' : 'Consultez les prérequis de la campagne avant de lancer le calcul.'}
                                    </p>
                                </div>
                            </div>
                            <Badge variant={canCalculate ? 'success' : 'warning'} size="sm">{canCalculate ? 'Prêt à calculer' : 'Prérequis à traiter'}</Badge>
                        </div>
                        <div className="flex flex-wrap items-center gap-x-6 gap-y-2 border-y border-[var(--color-border-subtle)] py-3 text-sm">
                            {populationCount != null && <span className="flex items-center gap-2 text-[var(--color-fg)]"><Users size={16} />{populationCount} salariés dans la campagne</span>}
                            <span className="text-[var(--color-fg-muted)]">Simulation : résultat provisoire sans enregistrement</span>
                            <span className="text-[var(--color-fg-muted)]">Calcul réel : bulletins enregistrés</span>
                        </div>
                        {!isReadOnly && <div className="flex flex-wrap gap-2">
                            <Button variant="primary" size="sm" onClick={() => setIsCalcConfirmOpen(true)} loading={calcLoading} disabled={!canCalculate || simLoading} iconLeft={<Play size={15} />}>Calculer la paie</Button>
                            <Button variant="secondary" size="sm" onClick={handleSimuler} loading={simLoading} disabled={!canSimulate || calcLoading} iconLeft={<Sparkles size={15} />}>Simuler sans enregistrer</Button>
                        </div>}
                    </Card>
                )}

                {/* 3. APRÈS CALCUL : SYNTHÈSE DES RÉSULTATS */}
                {hasBulletins && (
                    <div className="space-y-4">
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 rounded-none flex flex-col md:flex-row md:items-center md:justify-between gap-4">
                            <div className="flex items-center gap-3">
                                <div className={`w-10 h-10 flex items-center justify-center shrink-0 ${result?.blocked?.length ? 'bg-[var(--color-warning-500)]/10 text-[var(--color-warning-600)]' : 'bg-[var(--color-success-50)] dark:bg-[var(--color-success-950)] text-[var(--color-success-600)]'}`}>
                                    {result?.blocked?.length ? <AlertTriangle size={20} /> : <CheckCircle2 size={20} />}
                                </div>
                                <div>
                                    <div className="flex items-center gap-2">
                                        <h3 className="text-sm font-bold text-[var(--color-fg)]">
                                            {isSimulationActive
                                                ? (result.blocked.length ? 'Simulation partielle' : 'Simulation terminée')
                                                : (hasPersistedBulletins ? `Calcul ${calculationVersion ? calculationVersion : 'effectué'}` : 'Aucun bulletin calculé')}
                                        </h3>
                                        {isSimulationActive && <Badge variant={result.blocked.length ? 'warning' : 'secondary'} size="xs">Non enregistré</Badge>}
                                        {!isStale && !isSimulationActive && hasPersistedBulletins && <Badge variant="success" size="xs">Résultats enregistrés</Badge>}
                                    </div>
                                    <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                                        {displayKpis?.bulletins ?? existingBulletins.length} calculé(s){result?.blocked?.length ? ` · ${result.blocked.length} non calculé(s)` : ''} · {isSimulationActive ? (hasPersistedBulletins ? 'Simulation non enregistrée, bulletins précédents inchangés' : 'Simulation non enregistrée') : (hasPersistedBulletins ? 'Bulletins enregistrés' : 'Aucun bulletin enregistré')}
                                    </p>
                                </div>
                            </div>

                            <div className="flex flex-wrap items-center gap-2">
                                {isSimulationActive && canSimulate && <Button
                                    variant="secondary"
                                    size="sm"
                                    onClick={handleSimuler}
                                    loading={simLoading}
                                    disabled={calcLoading}
                                    iconLeft={<RefreshCw size={14} />}
                                >
                                    Relancer la simulation
                                </Button>}
                                {isSimulationActive ? (
                                    <Button
                                        variant="primary"
                                        size="sm"
                                        onClick={() => setIsCalcConfirmOpen(true)}
                                        loading={calcLoading}
                                        disabled={!canCalculate || simLoading}
                                        iconLeft={<Play size={14} />}
                                    >
                                        Calculer la paie
                                    </Button>
                                ) : (
                                    <>
                                        {/* Bouton Recalculer toujours accessible si la campagne n'est pas verrouillée.
                                            Indispensable quand le calcul est incomplet (certains salariés en erreur)
                                            mais que le backend ne signale pas isStale. */}
                                        {!isReadOnly && canCalculate && (
                                            <Button
                                                variant="secondary"
                                                size="sm"
                                                onClick={() => setIsCalcConfirmOpen(true)}
                                                loading={calcLoading}
                                                disabled={simLoading}
                                                iconLeft={<RefreshCw size={14} />}
                                            >
                                                Recalculer
                                            </Button>
                                        )}
                                        {!isStale && canOpenControl && (
                                            <Button
                                                variant="primary"
                                                size="sm"
                                                onClick={() => navigate(controlStep.destination)}
                                                iconRight={<ArrowRight size={14} />}
                                            >
                                                {result?.blocked?.length ? 'Voir les résultats enregistrés' : 'Passer au contrôle'}
                                            </Button>
                                        )}
                                    </>
                                )}
                            </div>
                        </div>

                        {result?.blocked?.length > 0 && (() => {
                            const blockedFiltered = result.blocked.filter(o => {
                                const q = blockedSearch.trim().toLowerCase();
                                if (!q) return true;
                                return (
                                    (o.employeeName || '').toLowerCase().includes(q) ||
                                    (o.matricule || '').toLowerCase().includes(q) ||
                                    (o.reasonCode || '').toLowerCase().includes(q)
                                );
                            });
                            const blockedPageCount = Math.max(1, Math.ceil(blockedFiltered.length / BLOCKED_PAGE_SIZE));
                            const blockedPageSafe = Math.min(blockedPage, blockedPageCount);
                            const blockedSlice = blockedFiltered.slice((blockedPageSafe - 1) * BLOCKED_PAGE_SIZE, blockedPageSafe * BLOCKED_PAGE_SIZE);
                            return (
                                <section className="border border-[var(--color-warning-500)]/50 bg-[var(--color-warning-500)]/10" aria-labelledby="blocked-payroll-title">
                                    {/* En-tête + recherche */}
                                    <div className="flex flex-wrap items-center justify-between gap-3 p-4">
                                        <div className="flex items-start gap-3">
                                            <AlertTriangle size={18} className="shrink-0 mt-0.5 text-[var(--color-warning-600)]" />
                                            <div>
                                                <h4 id="blocked-payroll-title" className="text-sm font-bold text-[var(--color-fg)]">
                                                    {result.blocked.length} salarié(s) non calculé(s)
                                                    {blockedSearch && <span className="ml-2 text-xs font-normal text-[var(--color-warning-600)]">· {blockedFiltered.length} résultat(s)</span>}
                                                </h4>
                                                <p className="text-xs text-[var(--color-fg-muted)] mt-1">Consultez les dossiers concernés avant une nouvelle exécution.</p>
                                            </div>
                                        </div>
                                        {/* Champ de recherche */}
                                        <div className="relative">
                                            <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)] pointer-events-none" />
                                            <input
                                                type="text"
                                                placeholder="Nom, matricule, erreur…"
                                                value={blockedSearch}
                                                onChange={e => { setBlockedSearch(e.target.value); setBlockedPage(1); }}
                                                className="pl-8 pr-8 py-1.5 text-xs border border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-fg)] placeholder:text-[var(--color-fg-subtle)] focus:outline-none focus:ring-1 focus:ring-[var(--color-brand-500)] w-52"
                                            />
                                            {blockedSearch && (
                                                <button type="button" onClick={() => { setBlockedSearch(''); setBlockedPage(1); }} className="absolute right-2 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] cursor-pointer">
                                                    <XCircle size={13} />
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                    {/* Liste */}
                                    <div className="divide-y divide-[var(--color-border-subtle)] border-t border-[var(--color-warning-500)]/30 bg-[var(--color-surface)]">
                                        {blockedSlice.length === 0 ? (
                                            <p className="px-4 py-6 text-sm text-center text-[var(--color-fg-muted)]">Aucun résultat pour « {blockedSearch} »</p>
                                        ) : blockedSlice.map(outcome => (
                                            <div key={outcome.employeeId} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 text-sm">
                                                <div className="min-w-0">
                                                    <p className="font-semibold text-[var(--color-fg)]">
                                                        {outcome.employeeName || `Salarié ${outcome.employeeId}`}
                                                        {outcome.matricule && <span className="ml-2 font-normal font-mono text-[var(--color-fg-muted)] text-xs">{outcome.matricule}</span>}
                                                    </p>
                                                    <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                                                        {outcome.reasonCode === 'LEAVE_RATE_MISSING'
                                                            ? 'Taux journalier de congé manquant à la date du calcul.'
                                                            : 'Le calcul de ce salarié a été bloqué.'}
                                                    </p>
                                                    {(outcome.message || outcome.reasonCode) && (
                                                        <details className="mt-1 text-xs text-[var(--color-fg-muted)]">
                                                            <summary className="cursor-pointer">Détail technique</summary>
                                                            <p className="mt-1 break-words font-mono">{outcome.message || outcome.reasonCode}</p>
                                                        </details>
                                                    )}
                                                </div>
                                                {outcome.employeeId && (
                                                    <Button
                                                        variant="outline"
                                                        size="xs"
                                                        onClick={() => navigate(outcome.reasonCode === 'LEAVE_RATE_MISSING'
                                                            ? `/employes/${outcome.employeeId}/conges`
                                                            : `/employes/${outcome.employeeId}`)}
                                                    >
                                                        {outcome.reasonCode === 'LEAVE_RATE_MISSING' ? 'Voir les congés' : 'Voir le dossier'}
                                                    </Button>
                                                )}
                                            </div>
                                        ))}
                                    </div>
                                    {/* Pagination salariés bloqués */}
                                    {blockedPageCount > 1 && (
                                        <Pagination
                                            currentPage={blockedPageSafe}
                                            totalItems={blockedFiltered.length}
                                            pageSize={BLOCKED_PAGE_SIZE}
                                            onPageChange={setBlockedPage}
                                        />
                                    )}
                                </section>
                            );
                        })()}

                        {/* 4 KPIs Clés consolidés avec StatCard */}
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                            <StatCard
                                title="Masse Salariale Brute"
                                value={formatMoneyAr(displayKpis?.brut)}
                                subtext="Salaires bruts imposables"
                                color="brand"
                            />
                            <StatCard
                                title="Cotisations Salariales"
                                value={formatMoneyAr(displayKpis?.cotisationsSalariales)}
                                subtext="Charges sociales déduites"
                                color="info"
                            />
                            <StatCard
                                title="Retenue IRSA (DGI)"
                                value={formatMoneyAr(displayKpis?.irsa)}
                                subtext="Impôt sur les revenus salariaux"
                                color="warning"
                            />
                            <StatCard
                                title="Total Net à Payer"
                                value={formatMoneyAr(displayKpis?.net)}
                                subtext="Masse nette des virements"
                                color="success"
                            />
                        </div>

                        <div className="flex justify-end">
                            <Button
                                variant="outline"
                                size="sm"
                                aria-expanded={showResultsTable}
                                onClick={() => setShowResultsTable(value => !value)}
                                iconRight={showResultsTable ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                            >
                                {showResultsTable ? 'Masquer les résultats individuels' : 'Voir les résultats individuels'}
                            </Button>
                        </div>

                        {/* Décomposition technique des résultats */}
                        {showResultsTable && (() => {
                            // Filtrage côté client pour la simulation ; côté serveur pour les bulletins persistés.
                            const allBulletinsForFilter = isSimulationActive ? simulationBulletins : existingBulletins;
                            const tableFiltered = tableSearch.trim()
                                ? allBulletinsForFilter.filter(b => {
                                    const q = tableSearch.trim().toLowerCase();
                                    return (
                                        (b.nomPrenom || b.intitulePoste || '').toLowerCase().includes(q) ||
                                        (b.matricule || '').toLowerCase().includes(q)
                                    );
                                })
                                : allBulletinsForFilter;

                            const totalBulletinsCount = isSimulationActive ? simulationBulletins.length : (bulletinTotal || existingBulletins.length);
                            const clientPageCount = Math.max(1, Math.ceil(tableFiltered.length / TABLE_PAGE_SIZE));
                            const clientPage = Math.min(bulletinPage, clientPageCount);
                            const clientSlice = isSimulationActive
                                ? tableFiltered.slice((clientPage - 1) * TABLE_PAGE_SIZE, clientPage * TABLE_PAGE_SIZE)
                                : (tableSearch.trim() ? tableFiltered.slice((bulletinPage - 1) * TABLE_PAGE_SIZE, bulletinPage * TABLE_PAGE_SIZE) : displayBulletins);
                            const rowsToShow = isSimulationActive || tableSearch.trim() ? clientSlice : displayBulletins;
                            const currentTotalItems = tableSearch.trim() ? tableFiltered.length : totalBulletinsCount;

                            const columns = [
                                { key: 'salarie', label: 'Salarié', width: 'w-60' },
                                { key: 'base', label: 'Salaire Base', align: 'right' },
                                { key: 'brut', label: 'Brut', align: 'right' },
                                { key: 'cotisations', label: 'Cotisations', align: 'right' },
                                { key: 'irsa', label: 'IRSA', align: 'right' },
                                { key: 'net', label: 'Net à Payer', align: 'right' },
                                ...(!isSimulationActive ? [{ key: 'action', label: 'Détail', align: 'center', width: 'w-20' }] : []),
                            ];

                            return (
                                <PayrollStepTable
                                    title="Décomposition individuelle"
                                    badgeCount={currentTotalItems}
                                    subtitle={!isSimulationActive ? "Cliquez sur Voir pour ouvrir le bulletin" : null}
                                    searchValue={tableSearch}
                                    onSearchChange={val => { setTableSearch(val); setBulletinPage(1); }}
                                    searchPlaceholder="Nom ou matricule…"
                                    columns={columns}
                                    loading={calcLoading && !isSimulationActive}
                                    emptyMessage={tableSearch ? `Aucun bulletin correspondant à « ${tableSearch} »` : 'Aucun bulletin calculé pour cette exécution.'}
                                    pagination={{
                                        currentPage: bulletinPage,
                                        totalItems: currentTotalItems,
                                        pageSize: TABLE_PAGE_SIZE,
                                        onPageChange: (p) => setBulletinPage(p),
                                    }}
                                    ariaLabel="Décomposition des bulletins calculés"
                                >
                                    {rowsToShow.map((b) => (
                                        <Tr key={b.idBulletin || b.idEmploye}>
                                            <Td>
                                                <PayrollEmployeeCell
                                                    name={b.nomPrenom || b.intitulePoste}
                                                    matricule={b.matricule}
                                                    subtitle={b.nomSite}
                                                />
                                            </Td>
                                            <Td className="text-right">
                                                <PayrollMoneyCell value={b.salaireBase} type="muted" />
                                            </Td>
                                            <Td className="text-right">
                                                <PayrollMoneyCell value={b.totalGainsBruts} />
                                            </Td>
                                            <Td className="text-right">
                                                <PayrollMoneyCell value={b.totalCotisationsSalariales} type="deduction" />
                                            </Td>
                                            <Td className="text-right">
                                                <PayrollMoneyCell value={b.irsaNet} type="deduction" />
                                            </Td>
                                            <Td className="text-right">
                                                <PayrollMoneyCell value={b.netAPayer} type="net" />
                                            </Td>
                                            {!isSimulationActive && (
                                                <Td className="text-center">
                                                    <Button
                                                        variant="outline"
                                                        size="xs"
                                                        onClick={() => setSelectedBulletinId(b.idBulletin)}
                                                        iconLeft={<Eye size={13} />}
                                                        aria-label={`Voir le bulletin de ${b.nomPrenom || b.matricule}`}
                                                    >
                                                        Voir
                                                    </Button>
                                                </Td>
                                            )}
                                        </Tr>
                                    ))}
                                </PayrollStepTable>
                            );
                        })()}
                    </div>
                )}
            </div>

            {/* Modal Drawer du bulletin de paie complet */}
            <Suspense fallback={null}>
                {selectedBulletinId && (
                    <BulletinPaieDrawer
                        isOpen={Boolean(selectedBulletinId)}
                        onClose={() => setSelectedBulletinId(null)}
                        idBulletin={selectedBulletinId}
                        onRecalculateSuccess={async () => {
                            await refreshSilent();
                            await loadExistingCalculation();
                        }}
                    />
                )}
            </Suspense>

            <ConfirmDialog
                isOpen={isCalcConfirmOpen}
                onClose={() => setIsCalcConfirmOpen(false)}
                onConfirm={executeCalculation}
                title={isStale ? 'Recalculer la paie' : 'Calculer la paie'}
                message="Le calcul réel enregistrera les bulletins produits pour cette campagne."
                confirmLabel={isStale ? 'Recalculer la paie' : 'Lancer le calcul réel'}
                variant={result?.isSimulation && result.blocked.length > 0 ? 'warning' : 'primary'}
                loading={calcLoading}
            >
                <div className="space-y-2 text-xs">
                    <p className="text-[var(--color-fg)]">Période <strong>{context?.selectedPeriod?.code || context?.currentPeriod?.code || periodId}</strong>{populationCount != null ? ` · ${populationCount} salariés dans la campagne` : ''}</p>
                    {result?.isSimulation && <p className="text-[var(--color-fg-muted)]">Dernière simulation : {result.bulletins} calculé(s), {result.blocked.length} non calculé(s).</p>}
                    {result?.isSimulation && result.blocked.length > 0 && <div className="border border-[var(--color-warning-500)]/50 bg-[var(--color-warning-500)]/10 p-3 text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)]">
                        Le calcul réel peut être partiel. Consultez les dossiers des salariés bloqués avant de poursuivre.
                    </div>}
                </div>
            </ConfirmDialog>
        </PayrollCampaignShell>
    );
}
