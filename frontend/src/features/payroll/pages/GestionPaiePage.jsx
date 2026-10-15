import { useEffect, useMemo, useState, useCallback, useRef, lazy, Suspense } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import {
    CalendarRange, FileText,
    SlidersHorizontal, ChevronRight, Users, Clock3
} from 'lucide-react';
import SEO from '@/components/system/SEO';
import Button from '@/components/ui/forms/Button';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Alert from '@/components/ui/feedback/Alert';
import PayrollPeriodStepper from '@/features/payroll/components/workflow/PayrollPeriodStepper';
import PayrollIssueBadge from '@/features/payroll/components/feedback/PayrollIssueBadge';
import PageHeader from '@/components/ui/navigation/PageHeader';
import { Skeleton, SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import paieService from '@/features/payroll/api/paieService';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { fmtDate } from '@/utils/dateUtils';
import PayrollWorkflowStatusBadges from '@/features/payroll/components/workflow/PayrollWorkflowStatusBadges';
import HasPermission from '@/features/auth/components/HasPermission';

const PeriodePaiePage = lazy(() => import('@/features/payroll/pages/PeriodePaiePage'));
const VariablesPaieTab = lazy(() => import('@/features/payroll/components/tabs/VariablesPaieTab'));

/**
 * Centre de Paie (/paie) — Tableau de bord opérationnel de la paie (Maquette V1 Full V3).
 *
 * Répond immédiatement :
 * - Quelle campagne est active ?
 * - Quel est son statut et sa progression ?
 * - Quels sont les blockers / avertissements ?
 * - Le calcul est-il à jour ?
 * - Combien de salariés sont concernés ?
 * - Quelle est la prochaine action logique (« À FAIRE MAINTENANT ») ?
 */
export default function GestionPaiePage() {
    // Contexte, navigation et hook métier
    const [searchParams] = useSearchParams();
    const location = useLocation();
    const navigate = useNavigate();
    const rawTab = searchParams.get('tab') || 'centre';
    const requestedPeriodId = Number(searchParams.get('periodId')) || null;
    const needsCampaignContext = rawTab === 'centre' || rawTab === 'variables';

    const { periodes, context, loading, error, selectPeriode } = usePayrollCampaign(requestedPeriodId, needsCampaignContext);

    // État local
    const [periodKpis, setPeriodKpis] = useState(null);
    const [kpiLoading, setKpiLoading] = useState(false);
    const kpiRequest = useRef(0);
    const [variableSummary, setVariableSummary] = useState({ count: 0, gains: 0, retenues: 0 });
    const [blockersExpanded, setBlockersExpanded] = useState(false);

    // Données dérivées
    const activePeriod = context?.selectedPeriod || context?.currentPeriod || periodes[0] || null;
    const campaignSteps = context?.campaignSteps || [];
    const currentStepCode = context?.currentStep;
    const currentStepObj = campaignSteps.find(s => s.code === currentStepCode);
    const periodStateLabel = activePeriod?.statutCode || activePeriod?.statut || activePeriod?.state;
    const recalcRequired = context?.recalculationRequired === true;
    // Les issues comme 'PAYROLL_NOT_CALCULATED' concernent la validation finale des bulletins.
    // À l'étape Préparation / Population / Variables, ce n'est pas un blocage actif de la campagne
    // mais le déroulement normal du workflow (la paie n'a tout simplement pas encore été calculée).
    const activeBlockers = useMemo(() => {
        // L'API expose un issue de contexte lorsqu'aucune période n'est sélectionnée.
        // Ce n'est pas un blocage de campagne et il ne doit pas alimenter le badge.
        if (!activePeriod) return [];
        const issues = context?.issues || [];
        return issues.filter(issue => {
            if (['PAYROLL_PERIOD_CONTEXT_REQUIRED', 'PAYROLL_NOT_CALCULATED', 'PAYROLL_ALREADY_VALIDATED'].includes(issue?.code)) {
                return currentStepCode === 'VALIDATION';
            }
            return true;
        });
    }, [activePeriod, context?.issues, currentStepCode]);

    useEffect(() => {
        setBlockersExpanded(false);
    }, [activePeriod?.id]);

    // Effets
    useEffect(() => {
        if (rawTab === 'validation') {
            const targetId = activePeriod?.id || requestedPeriodId;
            const dest = targetId ? `/paie/periodes/${targetId}/validation` : '/paie';
            navigate(dest, { replace: true });
        } else if (rawTab === 'bulletins') {
            const suffix = requestedPeriodId ? `/paie/periodes/${requestedPeriodId}/controle` : '/paie';
            navigate(suffix, { replace: true });
        } else if (rawTab === 'variables' && activePeriod?.id) {
            navigate(`/paie/periodes/${activePeriod.id}/variables`, { replace: true });
        }
    }, [rawTab, navigate, requestedPeriodId, activePeriod?.id]);

    // Chargements
    const loadPeriodKpis = useCallback(async (periodId) => {
        const requestId = ++kpiRequest.current;
        if (!periodId) {
            setPeriodKpis(null);
            return;
        }
        setKpiLoading(true);
        try {
            const cockpitData = await paieService.getCockpitPeriode(periodId, { size: 1 });
            if (requestId !== kpiRequest.current) return;
            if (cockpitData) {
                const popCount = cockpitData.populationPage?.totalElements
                    ?? cockpitData.readiness?.effectifAttendu
                    ?? cockpitData.validationSummary?.population
                    ?? (Array.isArray(cockpitData.population) ? cockpitData.population.length : null);
                setPeriodKpis({
                    totalBulletins: cockpitData.kpis?.totalBulletins ?? cockpitData.bulletinsPage?.totalElements ?? 0,
                    totalBrut: cockpitData.kpis?.totalBrut ?? cockpitData.kpis?.masseSalarialeBrute ?? 0,
                    totalNet: cockpitData.kpis?.totalNetAPayer ?? cockpitData.kpis?.masseNetAPayer ?? 0,
                    totalAlertes: cockpitData.readiness?.nbAnomalies ?? cockpitData.kpis?.nbAlertes ?? 0,
                    populationCount: popCount,
                    readinessChecks: cockpitData.readiness?.checks || [],
                });
            } else {
                setPeriodKpis(null);
            }
        } catch {
            if (requestId === kpiRequest.current) setPeriodKpis(null);
        } finally {
            if (requestId === kpiRequest.current) setKpiLoading(false);
        }
    }, []);

    const pointageCheck = periodKpis?.readinessChecks?.find(check => check.code === 'POINTAGE_FROZEN');

    useEffect(() => {
        if (!needsCampaignContext) return;
        if (activePeriod?.id) {
            loadPeriodKpis(activePeriod.id);
        } else {
            setPeriodKpis(null);
        }
    }, [activePeriod?.id, loadPeriodKpis, needsCampaignContext]);

    useEffect(() => {
        let active = true;
        if (!needsCampaignContext || !activePeriod?.id) {
            setVariableSummary({ count: 0, gains: 0, retenues: 0 });
            return undefined;
        }
        paieService.listerAjustements(activePeriod.id)
            .then((response) => {
                if (!active) return;
                const rows = Array.isArray(response) ? response : (response?.content || response?.items || []);
                setVariableSummary(rows.reduce((summary, row) => {
                    const amount = Number(row.montant) || 0;
                    summary.count += 1;
                    if (row.sens === 'GAIN') summary.gains += amount;
                    if (row.sens === 'RETENUE') summary.retenues += amount;
                    return summary;
                }, { count: 0, gains: 0, retenues: 0 }));
            })
            .catch(() => active && setVariableSummary({ count: 0, gains: 0, retenues: 0 }));
        return () => { active = false; };
    }, [activePeriod?.id, needsCampaignContext]);

    // formatDate centralisé dans dateUtils.js — fmtDate() retourne DD/MM/YYYY
    // formatAr alias de formatMoneyAr — centralisé dans moneyUtils.js
    const formatAr = (val) => (val !== undefined && val !== null ? formatMoneyAr(val) : '–');

    // Navigation strictement fournie par le backend. Cette couche choisit seulement
    // le libellé et l'icône de présentation; elle ne reconstruit aucune transition.
    const nextLogicalAction = useMemo(() => {
        const nextStep = context?.nextStep;
        if (!nextStep?.destination) return null;
        const activePeriod = context?.selectedPeriod || context?.currentPeriod;
        const isClosed = activePeriod?.workflowStatuses?.paie?.code === 'CLOTUREE' || activePeriod?.state === 'CLOTUREE';
        const label = isClosed
            ? 'Consulter la période'
            : (nextStep.code ? `Continuer : ${nextStep.code.replaceAll('_', ' ')}` : 'Continuer la campagne');
        return {
            title: isClosed ? 'Campagne clôturée' : (currentStepObj?.label || 'Prochaine étape'),
            label,
            onClick: () => navigate(nextStep.destination),
        };
    }, [context?.nextStep, context?.selectedPeriod, context?.currentPeriod, currentStepObj?.label, navigate]);

    const hasCalculatedBulletins = (periodKpis?.totalBulletins ?? 0) > 0;

    // Rendu
    if (rawTab === 'periodes') {
        return (
            <Suspense fallback={<SkeletonTable rows={6} cols={6} />}>
                <PeriodePaiePage />
            </Suspense>
        );
    }


    if (rawTab === 'variables') {
        const addVariableCapability = context?.availableActions?.ADD_VARIABLE;
        return (
            <div className="space-y-6 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full pb-12 px-2 sm:px-4">
                <SEO
                    title="Variables de Paie | GALAXAM GRH"
                    description="Gestion des primes, acomptes et retenues par salarié."
                />
                <PageHeader
                    title="Variables de paie"
                    subtitle="Consultez et préparez les primes et retenues par salarié avant le calcul de la campagne."
                    icon={SlidersHorizontal}
                    breadcrumbs={[{ label: 'Paie', href: '/paie' }, { label: 'Variables de paie' }]}
                />
                <Suspense fallback={<SkeletonTable rows={6} cols={6} />}>
                    <div className="p-4 bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">
                        <VariablesPaieTab
                            idPeriodeActive={activePeriod?.id}
                            canMutate={addVariableCapability?.allowed === true}
                            mutationDisabledReason={(addVariableCapability?.reasonCodes || []).join(', ') || 'La période est en lecture seule.'}
                            periodStatus={activePeriod?.statutCode || activePeriod?.statut || activePeriod?.state}
                            onVariableChanged={() => activePeriod?.id && loadPeriodKpis(activePeriod.id)}
                        />
                    </div>
                </Suspense>
            </div>
        );
    }

    return (
        <div className="space-y-6 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full pb-12 px-2 sm:px-4">
            <SEO
                title="Centre de Paie | GALAXAM GRH"
                description="Tableau de bord opérationnel de gestion de la paie, campagnes actives et déclarations."
            />
            {location.state?.blockedOutputs && <Alert type="warning" title="Sorties indisponibles" message="Validez d’abord la paie de cette campagne. Reprenez l’étape indiquée ci-dessous avant d’accéder aux documents et à la clôture." />}

            <PageHeader
                title="Centre de paie"
                subtitle="Supervision opérationnelle, campagnes actives, acomptes et sorties réglementaires."
                icon={FileText}
                breadcrumbs={[{ label: 'Tableau de bord', href: '/dashboard' }, { label: 'Paie & Rémunération' }]}
                actions={periodes.length > 0 && (
                    <div className="flex w-full flex-col gap-1.5 md:w-[min(100%,440px)]">
                        <label htmlFor="payroll-period-select" className="text-xs font-medium text-[var(--color-fg-muted)]">Période de paie</label>
                        <SearchSelect
                            id="payroll-period-select"
                            placeholder="Rechercher une période…"
                            value={activePeriod?.id ? String(activePeriod.id) : String(periodes[0]?.idPeriodePaie || periodes[0]?.id || '')}
                            onChange={(val) => val && selectPeriode(Number(val))}
                            options={periodes.map(p => {
                                const pId = p.idPeriodePaie || p.id_periode_paie || p.id;
                                const statut = p.statutCode || p.statut || p.state || 'EN_COURS';
                                return {
                                    value: String(pId),
                                    label: [p.code, p.libelle].filter(Boolean).join(' — ') || String(pId),
                                    sublabel: statut,
                                };
                            })}
                            className="w-full"
                        />
                    </div>
                )}
            />

            {error && (
                <div className="mb-2">
                    <Alert
                        type="error"
                        title="Erreur lors du chargement de la campagne de paie"
                        message={error}
                    />
                </div>
            )}

            {/* Contexte de la période et progression de la campagne */}
            <section className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 sm:p-5 shadow-xs relative">
                {loading && !context ? (
                    <div className="space-y-4">
                        <div className="flex items-center gap-3">
                            <Skeleton className="h-4 w-28" />
                            <Skeleton className="h-8 w-44" />
                            <Skeleton className="h-6 w-20" />
                        </div>
                        <Skeleton className="h-4 w-72" />
                    </div>
                ) : (
                    <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1.5fr)_minmax(260px,0.8fr)] gap-5">
                        <div className="space-y-2 min-w-0">
                            <p className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-600)]">Campagne sélectionnée</p>
                            <div className="flex items-center gap-3 flex-wrap">
                                <h2 className="text-2xl sm:text-3xl font-bold text-[var(--color-fg)] tracking-tight">
                                    {activePeriod?.code || activePeriod?.label || 'Aucune campagne active'}
                                </h2>
                                {periodStateLabel && (
                                    <span className="px-2 py-1 text-[10px] font-semibold uppercase tracking-wide border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]">
                                        {String(periodStateLabel).replaceAll('_', ' ')}
                                    </span>
                                )}
                                <PayrollWorkflowStatusBadges statuses={activePeriod?.workflowStatuses} size="md" />
                                {activeBlockers.length > 0 && (
                                    <PayrollIssueBadge severity="BLOCKER" count={activeBlockers.length}
                                        label={activeBlockers.length === 1 ? 'point bloquant' : 'points bloquants'} />
                                )}
                            </div>

                            {activePeriod && (
                                <p className="text-xs text-[var(--color-fg-muted)] flex items-center gap-3 flex-wrap">
                                    <span>
                                        Du <strong className="text-[var(--color-fg)]">{fmtDate(activePeriod.startDate)}</strong> au <strong className="text-[var(--color-fg)]">{fmtDate(activePeriod.endDate)}</strong>
                                    </span>
                                    <span>· Population : <strong className="text-[var(--color-fg)] font-data">{periodKpis?.populationCount ?? context?.populationCount ?? activePeriod.employeeCount ?? '–'}</strong> salariés</span>
                                    {context?.snapshotPointageVersion && (
                                        <span>· Snapshot Pointage : <code className="font-data text-[var(--color-brand-600)]">v{context.snapshotPointageVersion}</code></span>
                                    )}
                                </p>
                            )}
                        </div>
                        {nextLogicalAction && (
                            <div className="bg-[var(--color-bg-subtle)] border-l-4 border-l-[var(--color-brand-500)] p-4 flex flex-col items-start justify-between gap-3">
                                <div>
                                    <p className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-600)]">Prochaine action</p>
                                    <h3 className="mt-1 text-base font-bold text-[var(--color-fg)]">
                                        {currentStepCode === 'POINTAGE' || currentStepCode === 'POINTAGE_SAISIE' ? 'Terminer la saisie du pointage' : nextLogicalAction.title}
                                    </h3>
                                    <p className="mt-1 text-xs text-[var(--color-fg-muted)]">{pointageCheck?.message || `Étape en cours : ${currentStepObj?.label || 'campagne de paie'}.`}</p>
                                </div>
                                <Button variant="primary" size="sm" onClick={nextLogicalAction.onClick} iconRight={<ChevronRight size={14} />}>
                                    {currentStepCode === 'POINTAGE' || currentStepCode === 'POINTAGE_SAISIE' ? 'Continuer la saisie' : nextLogicalAction.label}
                                </Button>
                            </div>
                        )}
                    </div>
                )}
            </section>

            {!loading && !activePeriod && (
                <section className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] border-l-4 border-l-[var(--color-brand-500)] p-4 sm:p-5 shadow-xs">
                    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                        <div>
                            <h3 className="text-sm font-bold text-[var(--color-fg)]">Aucune campagne de paie</h3>
                            <p className="mt-1 text-xs text-[var(--color-fg-muted)]">
                                Créez la période correspondant au cycle courant du 26 au 25.
                            </p>
                        </div>
                        <HasPermission authority={['PAIE_WRITE', 'PAIE_VALIDATE', 'POINTAGE_VALIDATE']}>
                            <Button
                                variant="primary"
                                size="sm"
                                onClick={() => navigate('/paie?tab=periodes&action=create')}
                                iconLeft={<CalendarRange size={14} />}
                            >
                                Créer la période suggérée
                            </Button>
                        </HasPermission>
                    </div>
                </section>
            )}

            <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
                <section className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 sm:p-5">
                    <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-3">
                        <div>
                            <h3 className="text-base font-bold text-[var(--color-fg)]">Points à traiter</h3>
                            <p className="text-sm text-[var(--color-fg-muted)] mt-1">Vérifications pour {activePeriod?.code || 'la période sélectionnée'}.</p>
                        </div>
                        {activePeriod && <Button variant="outline" size="sm" onClick={() => navigate(`/paie/periodes/${activePeriod.id}/variables`)}>Vérifier les variables</Button>}
                    </div>
                    {(recalcRequired || activeBlockers.length > 0) && (
                        <div id="payroll-blockers" className="mt-4 space-y-2" aria-live="polite">
                            {recalcRequired && <Alert type="warning" title="Recalcul requis" message="Le calcul doit être relancé après les dernières modifications des données sources." />}
                            {(blockersExpanded ? activeBlockers : activeBlockers.slice(0, 2)).map((blocker, idx) => (
                                <Alert key={blocker.code || idx} type="error" title="Point bloquant"
                                    message={<><span>{blocker.message}</span>{blocker.code === 'POINTAGE_NOT_FROZEN' && <span className="block mt-1 text-[var(--color-fg-muted)]">Terminez la saisie puis validez le pointage.</span>}</>} />
                            ))}
                            {activeBlockers.length > 2 && (
                                <Button variant="ghost" size="xs" onClick={() => setBlockersExpanded(value => !value)}>
                                    {blockersExpanded ? 'Réduire la liste' : `Voir les ${activeBlockers.length} points bloquants`}
                                </Button>
                            )}
                        </div>
                    )}
                    <dl className="mt-4 divide-y divide-[var(--color-border-subtle)]">
                        <div className="flex items-center justify-between gap-4 py-3 text-sm"><dt>Éléments variables à relire</dt><dd className="font-sans tabular-nums font-semibold">{variableSummary.count}</dd></div>
                        <div className="flex items-center justify-between gap-4 py-3 text-sm"><dt>Gains déclarés (primes, indemnités)</dt><dd className="font-sans tabular-nums font-semibold">{formatAr(variableSummary.gains)}</dd></div>
                        <div className="flex items-center justify-between gap-4 py-3 text-sm"><dt>Retenues déclarées (acomptes, déductions)</dt><dd className="font-sans tabular-nums font-semibold">{formatAr(variableSummary.retenues)}</dd></div>
                        <div className="flex items-center justify-between gap-4 py-3 text-sm"><dt>Pointage validé et figé</dt><dd className={`font-semibold ${pointageCheck?.status === 'PASS' ? 'text-[var(--color-success-600)]' : 'text-[var(--color-danger-600)]'}`}>{pointageCheck?.status === 'PASS' ? 'Oui' : pointageCheck ? 'À faire' : '—'}</dd></div>
                    </dl>
                </section>

                <section className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 sm:p-5">
                    <div className="flex items-start justify-between gap-3">
                        <div>
                            <h3 className="text-base font-bold text-[var(--color-fg)]">Résultats de la paie</h3>
                            <p className="text-sm text-[var(--color-fg-muted)] mt-1">{hasCalculatedBulletins ? `Dernier calcul pour ${activePeriod?.code || 'la période sélectionnée'}.` : 'S’affichent après le premier calcul.'}</p>
                        </div>
                        {recalcRequired && <PayrollIssueBadge severity="WARNING" label="À recalculer" />}
                    </div>
                    {!hasCalculatedBulletins && (
                        <div className="mt-4 flex items-center gap-3 border border-dashed border-[var(--color-border-subtle)] p-3 text-sm text-[var(--color-fg-muted)]">
                            <Clock3 size={19} className="shrink-0" />
                            <span>Aucun bulletin calculé pour le moment.</span>
                        </div>
                    )}
                    <div className="mt-4 grid grid-cols-2 border border-[var(--color-border-subtle)]">
                        <div className="p-3 border-b border-r border-[var(--color-border-subtle)]">
                            <p className="text-lg font-bold tabular-nums text-[var(--color-fg-muted)]">{kpiLoading ? '…' : `${periodKpis?.totalBulletins ?? 0} / ${periodKpis?.populationCount ?? context?.populationCount ?? activePeriod?.employeeCount ?? '–'}`}</p>
                            <p className="text-xs text-[var(--color-fg-muted)]">Bulletins calculés</p>
                        </div>
                        <div className="p-3 border-b border-[var(--color-border-subtle)]">
                            <p className="text-lg font-bold tabular-nums text-[var(--color-fg-muted)]">{hasCalculatedBulletins ? formatAr(periodKpis?.totalBrut) : '—'}</p>
                            <p className="text-xs text-[var(--color-fg-muted)]">Masse brute</p>
                        </div>
                        <div className="p-3 border-r border-[var(--color-border-subtle)]">
                            <p className="text-lg font-bold tabular-nums text-[var(--color-fg-muted)]">{hasCalculatedBulletins ? formatAr(periodKpis?.totalNet) : '—'}</p>
                            <p className="text-xs text-[var(--color-fg-muted)]">Net à payer</p>
                        </div>
                        <div className="p-3">
                            <p className="text-lg font-bold tabular-nums text-[var(--color-fg-muted)]">{periodKpis?.totalAlertes ?? 0}</p>
                            <p className="text-xs text-[var(--color-fg-muted)]">Anomalies détectées</p>
                        </div>
                    </div>
                    {hasCalculatedBulletins && <p className="mt-3 text-xs text-[var(--color-fg-muted)]">{recalcRequired ? 'Montants provisoires : un nouveau calcul est requis.' : 'Montants à confirmer lors de la validation de la campagne.'}</p>}
                </section>
            </div>

            {campaignSteps.length > 0 && (
                <section className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 sm:p-5" aria-label="Parcours de la campagne">
                    <div className="flex flex-wrap items-baseline justify-between gap-2 mb-4">
                        <div>
                            <h3 className="text-base font-bold text-[var(--color-fg)]">Parcours de la campagne</h3>
                            <p className="mt-1 text-xs text-[var(--color-fg-muted)]">Étape en cours : {currentStepObj?.label || '—'}</p>
                        </div>
                        {nextLogicalAction && <Button variant="outline" size="xs" onClick={nextLogicalAction.onClick}>Ouvrir l’étape →</Button>}
                    </div>
                    <PayrollPeriodStepper steps={campaignSteps} currentStep={currentStepCode} activeCode={null} loading={loading} />
                </section>
            )}

            {/* Navigation secondaire */}
            <nav aria-label="Autres espaces de paie" className="space-y-3 pt-2">
                <h3 className="text-sm font-bold text-[var(--color-fg)]">Autres espaces de paie</h3>
                <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3">
                    {[
                        { label: 'Campagnes et historique', path: '/paie?tab=periodes', icon: CalendarRange },
                        { label: 'Acomptes et avances', path: '/paie/acomptes-avances', icon: SlidersHorizontal },
                        { label: 'Documents et exports', path: '/paie/declarations', icon: FileText },
                        { label: 'Prestataires', path: '/paie/prestataires', icon: Users },
                    ].map(({ label, path, icon: Icon }) => (
                        <Button key={path} variant="outline" size="sm" onClick={() => navigate(path)}
                            iconLeft={<Icon size={16} />} iconRight={<ChevronRight size={15} />}
                            className="w-full justify-between min-h-[44px]">
                            {label}
                        </Button>
                    ))}
                </div>
            </nav>
        </div>
    );
}
