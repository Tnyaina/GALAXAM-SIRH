import { useEffect, useMemo, useState, useCallback, useRef, lazy, Suspense } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { 
    Banknote, CalendarRange, FileText,
    DollarSign, AlertTriangle, SlidersHorizontal,
    ChevronRight, TrendingUp, TrendingDown, Users
} from 'lucide-react';
import SEO from '@/components/system/SEO';
import Button from '@/components/ui/forms/Button';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Alert from '@/components/ui/feedback/Alert';
import { Card } from '@/components/ui/data-display/Card';
import StatCard from '@/components/ui/data-display/StatCard';
import PayrollPeriodStepper from '@/features/payroll/components/workflow/PayrollPeriodStepper';
import PayrollIssueBadge from '@/features/payroll/components/feedback/PayrollIssueBadge';
import PageHeader from '@/components/ui/navigation/PageHeader';
import { Skeleton, SkeletonStatCard, SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import paieService from '@/features/payroll/api/paieService';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { fmtDate } from '@/utils/dateUtils';
import PayrollWorkflowStatusBadges from '@/features/payroll/components/workflow/PayrollWorkflowStatusBadges';
import HasPermission from '@/features/auth/components/HasPermission';

const PeriodePaiePage = lazy(() => import('@/features/payroll/pages/PeriodePaiePage'));
const ExportsPaieTab = lazy(() => import('@/features/payroll/components/tabs/ExportsPaieTab'));
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

    // Données dérivées
    const activePeriod = context?.selectedPeriod || context?.currentPeriod || periodes[0] || null;
    const isCurrentBusinessPeriod = Boolean(context?.currentPeriod?.id && context.currentPeriod.id === activePeriod?.id);
    const campaignSteps = context?.campaignSteps || [];
    const currentStepCode = context?.currentStep;
    const currentStepObj = campaignSteps.find(s => s.code === currentStepCode);
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

    if (rawTab === 'exports') {
        return (
            <Suspense fallback={<SkeletonTable rows={6} cols={6} />}>
                <ExportsPaieTab />
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

            <PageHeader
                title="Centre de paie"
                subtitle="Supervision opérationnelle, campagnes actives, acomptes et sorties réglementaires."
                icon={Banknote}
                breadcrumbs={[{ label: 'Tableau de bord', href: '/dashboard' }, { label: 'Paie & Rémunération' }]}
                actions={periodes.length > 0 && (
                    <div className="flex items-center gap-2 min-w-[260px]">
                        <CalendarRange size={16} className="text-[var(--color-brand-600)] shrink-0" aria-hidden="true" />
                        <SearchSelect
                            placeholder="Rechercher une période…"
                            value={activePeriod?.id ? String(activePeriod.id) : String(periodes[0]?.idPeriodePaie || periodes[0]?.id || '')}
                            onChange={(val) => val && selectPeriode(Number(val))}
                            options={periodes.map(p => {
                                const pId = p.idPeriodePaie || p.id_periode_paie || p.id;
                                const statut = p.statutCode || p.statut || p.state || 'EN_COURS';
                                return {
                                    value: String(pId),
                                    // SearchSelect recherche dans label et sublabel : exposer
                                    // systématiquement code + libellé, pas l'un au détriment de l'autre.
                                    label: [p.code, p.libelle].filter(Boolean).join(' — ') || String(pId),
                                    sublabel: statut,
                                };
                            })}
                            className="w-full"
                        />
                    </div>
                )}
            />

            <Card padding shadow="none" className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-start gap-2.5">
                    <Users size={18} className="mt-0.5 shrink-0 text-[var(--color-brand-600)]" />
                    <div><h2 className="text-sm font-bold">Un consultant indépendant ou un autre prestataire de services ?</h2>
                        <p className="text-xs text-[var(--color-fg-muted)]">Créez son dossier, son contrat et ses factures dans le parcours prestataires. Son règlement est distinct des bulletins et du STC.</p></div>
                </div>
                <Button type="button" variant="outline" onClick={() => navigate('/paie/prestataires')}>Ouvrir les prestataires indépendants</Button>
            </Card>

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
            <section className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 sm:p-6 shadow-xs relative">
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
                    <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6">
                        <div className="space-y-2">
                            <div className="flex items-center gap-3 flex-wrap">
                                <span className="px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider bg-[var(--color-brand-500)]/15 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)]">
                                    {isCurrentBusinessPeriod ? 'Période courante' : 'Période sélectionnée'}
                                </span>
                                <h2 className="text-2xl sm:text-3xl font-bold text-[var(--color-fg)] tracking-tight">
                                    {activePeriod?.code || activePeriod?.label || 'Aucune campagne active'}
                                </h2>
                                <PayrollWorkflowStatusBadges statuses={activePeriod?.workflowStatuses} size="md" />
                                {recalcRequired && (
                                    <PayrollIssueBadge severity="WARNING" label="Calcul obsolète" />
                                )}
                                {activeBlockers.length > 0 && (
                                    <PayrollIssueBadge
                                        severity="BLOCKER"
                                        count={activeBlockers.length}
                                        label="bloquant(s)"
                                        className="cursor-help"
                                        title={activeBlockers.map(b => b.message).join('\n')}
                                    />
                                )}
                            </div>

                            {activePeriod && (
                                <p className="text-xs text-[var(--color-fg-muted)] flex items-center gap-3 flex-wrap">
                                    <span>
                                        Du <strong className="text-[var(--color-fg)]">{fmtDate(activePeriod.startDate)}</strong> au <strong className="text-[var(--color-fg)]">{fmtDate(activePeriod.endDate)}</strong>
                                    </span>
                                    <span>· Population : <strong className="text-[var(--color-fg)] font-mono">{periodKpis?.populationCount ?? context?.populationCount ?? activePeriod.employeeCount ?? '–'}</strong> salariés</span>
                                    {context?.snapshotPointageVersion && (
                                        <span>· Snapshot Pointage : <code className="font-mono text-[var(--color-brand-600)]">v{context.snapshotPointageVersion}</code></span>
                                    )}
                                </p>
                            )}
                        </div>
                    </div>
                )}

                {/* Stepper de campagne compact */}
                {campaignSteps.length > 0 && (
                    <div className="mt-6 pt-4 border-t border-[var(--color-border-subtle)]">
                        <div className="flex items-center justify-between mb-2">
                            <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                                Progression du workflow
                            </span>
                            <span className="text-xs text-[var(--color-brand-600)] font-medium">
                                Étape en cours : <strong>{currentStepObj?.label || '—'}</strong>
                            </span>
                        </div>
                        <PayrollPeriodStepper
                            steps={campaignSteps}
                            currentStep={currentStepCode}
                            activeCode={null}
                            loading={loading}
                        />
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

            {/* Action fournie par le workflow */}
            {nextLogicalAction && (
                <section className="bg-[var(--color-bg-subtle)] border-l-4 border-[var(--color-brand-500)] border border-y-[var(--color-border-subtle)] border-r-[var(--color-border-subtle)] p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                    <div className="flex items-start gap-3.5">
                        <div>
                            <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]">Prochaine action</span>
                            <h3 className="text-sm font-bold text-[var(--color-fg)] mt-0.5">
                                {nextLogicalAction.title}
                            </h3>
                        </div>
                    </div>

                    <div className="shrink-0 sm:self-center">
                        <Button
                            variant="primary"
                            size="sm"
                            onClick={nextLogicalAction.onClick}
                            iconRight={<ChevronRight size={14} />}
                        >
                            {nextLogicalAction.label}
                        </Button>
                    </div>
                </section>
            )}

            {/* GRILLE DE SYNTHÈSE KPI */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                {(loading && !context) || (kpiLoading && !periodKpis) ? (
                    <>
                        <SkeletonStatCard />
                        <SkeletonStatCard />
                        <SkeletonStatCard />
                        <SkeletonStatCard />
                    </>
                ) : (
                    <>
                        <StatCard
                            title="Bulletins calculés"
                            value={kpiLoading ? '…' : (periodKpis?.totalBulletins ?? 0)}
                            subtext={hasCalculatedBulletins ? `Période ${activePeriod?.code || ''}` : 'En attente du calcul'}
                            icon={<FileText size={18} />}
                            color="brand"
                        />
                        <StatCard
                            title="Masse Brute"
                            value={kpiLoading ? '…' : hasCalculatedBulletins ? formatAr(periodKpis?.totalBrut) : '—'}
                            subtext={hasCalculatedBulletins ? 'Salaires bruts imposables' : 'En attente du calcul'}
                            icon={<DollarSign size={18} />}
                            color="info"
                        />
                        <StatCard
                            title="Net à payer"
                            value={kpiLoading ? '…' : hasCalculatedBulletins ? formatAr(periodKpis?.totalNet) : '—'}
                            subtext={hasCalculatedBulletins ? 'Montant total des virements' : 'En attente du calcul'}
                            icon={<Banknote size={18} />}
                            color="success"
                        />
                        <StatCard
                            title="Alertes & Contrôles"
                            value={kpiLoading ? '…' : (periodKpis?.totalAlertes ?? 0)}
                            subtext={(periodKpis?.totalAlertes ?? 0) > 0 ? 'Anomalies nécessitant examen' : 'Aucune anomalie signalée'}
                            icon={<AlertTriangle size={18} />}
                            color={(periodKpis?.totalAlertes ?? 0) > 0 ? 'warning' : 'neutral'}
                        />
                    </>
                )}
            </div>

            {activePeriod && (
                <section className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">
                    <div className="p-4 border-b border-[var(--color-border-subtle)] flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                        <div>
                            <h3 className="text-sm font-bold text-[var(--color-fg)]">Contrôles avant calcul</h3>
                            <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">Les éléments qui influencent directement la paie de {activePeriod.code || activePeriod.libelle}.</p>
                        </div>
                        <Button variant="secondary" size="sm" onClick={() => navigate(`/paie/periodes/${activePeriod.id}/variables`)} iconLeft={<SlidersHorizontal size={14} />}>
                            Vérifier les variables
                        </Button>
                    </div>
                    <div className="grid grid-cols-1 md:grid-cols-3 divide-y md:divide-y-0 md:divide-x divide-[var(--color-border-subtle)]">
                        <div className="p-4">
                            <p className="text-[10px] uppercase tracking-wider font-bold text-[var(--color-fg-muted)]">Éléments variables</p>
                            <p className="mt-1 text-xl font-bold text-[var(--color-fg)]">{variableSummary.count}</p>
                            <p className="text-xs text-[var(--color-fg-muted)]">à relire avant le calcul</p>
                        </div>
                        <div className="p-4">
                            <p className="text-[10px] uppercase tracking-wider font-bold text-[var(--color-fg-muted)] flex items-center gap-1"><TrendingUp size={13} /> Gains déclarés</p>
                            <p className="mt-1 text-xl font-bold text-[var(--color-success-600)]">+{formatAr(variableSummary.gains)}</p>
                            <p className="text-xs text-[var(--color-fg-muted)]">primes et indemnités</p>
                        </div>
                        <div className="p-4">
                            <p className="text-[10px] uppercase tracking-wider font-bold text-[var(--color-fg-muted)] flex items-center gap-1"><TrendingDown size={13} /> Retenues déclarées</p>
                            <p className="mt-1 text-xl font-bold text-[var(--color-danger-600)]">-{formatAr(variableSummary.retenues)}</p>
                            <p className="text-xs text-[var(--color-fg-muted)]">acomptes et déductions</p>
                        </div>
                    </div>
                    {(activeBlockers.length > 0 || recalcRequired) && (
                        <div className="p-4 border-t border-[var(--color-border-subtle)] space-y-2">
                            {recalcRequired && (
                                <Alert
                                    type="warning"
                                    title="Recalcul requis"
                                    message="Le calcul doit être relancé après les dernières modifications des données sources."
                                />
                            )}
                            {activeBlockers.map((blocker, idx) => (
                                <Alert
                                    key={blocker.code || idx}
                                    type="error"
                                    title="Point bloquant"
                                    message={blocker.message}
                                />
                            ))}
                        </div>
                    )}
                </section>
            )}

            {/* Navigation secondaire */}
            <div className="space-y-3 pt-2">
                <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                    Accès rapides
                </h3>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    {/* Acomptes & Avances */}
                    <Card
                        hover
                        padding
                        className="text-left cursor-pointer transition-all group"
                        onClick={() => navigate('/paie/acomptes-avances')}
                        role="button"
                        tabIndex={0}
                        onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && navigate('/paie/acomptes-avances')}
                    >
                        <div className="flex items-center justify-between">
                            <div className="flex items-center gap-2.5 text-[var(--color-fg)]">
                                <SlidersHorizontal size={18} className="text-[var(--color-brand-600)] shrink-0" />
                                <h4 className="text-sm font-bold group-hover:text-[var(--color-brand-600)] transition-colors">
                                    Acomptes & Avances
                                </h4>
                            </div>
                            <ChevronRight size={16} className="text-[var(--color-fg-subtle)] group-hover:translate-x-0.5 transition-transform shrink-0" />
                        </div>
                        <p className="text-xs text-[var(--color-fg-muted)] mt-2 leading-relaxed">
                            Gestion des demandes d’acomptes, avances sur salaire et suivi des imputations mensuelles.
                        </p>
                    </Card>

                    {/* Historique des Périodes */}
                    <Card
                        hover
                        padding
                        className="text-left cursor-pointer transition-all group"
                        onClick={() => navigate('/paie?tab=periodes')}
                        role="button"
                        tabIndex={0}
                        onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && navigate('/paie?tab=periodes')}
                    >
                        <div className="flex items-center justify-between">
                            <div className="flex items-center gap-2.5 text-[var(--color-fg)]">
                                <CalendarRange size={18} className="text-[var(--color-brand-600)] shrink-0" />
                                <h4 className="text-sm font-bold group-hover:text-[var(--color-brand-600)] transition-colors">
                                    Historique des Périodes
                                </h4>
                            </div>
                            <ChevronRight size={16} className="text-[var(--color-fg-subtle)] group-hover:translate-x-0.5 transition-transform shrink-0" />
                        </div>
                        <p className="text-xs text-[var(--color-fg-muted)] mt-2 leading-relaxed">
                            Consultation des campagnes passées, archives en lecture seule, ouverture et clôture de périodes.
                        </p>
                    </Card>

                    {/* Documents et exports de paie */}
                    <Card
                        hover
                        padding
                        className="text-left cursor-pointer transition-all group"
                        onClick={() => navigate('/paie/declarations')}
                        role="button"
                        tabIndex={0}
                        onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && navigate('/paie/declarations')}
                    >
                        <div className="flex items-center justify-between">
                            <div className="flex items-center gap-2.5 text-[var(--color-fg)]">
                                <FileText size={18} className="text-[var(--color-brand-600)] shrink-0" />
                                <h4 className="text-sm font-bold group-hover:text-[var(--color-brand-600)] transition-colors">
                                    Documents et exports
                                </h4>
                            </div>
                            <ChevronRight size={16} className="text-[var(--color-fg-subtle)] group-hover:translate-x-0.5 transition-transform shrink-0" />
                        </div>
                        <p className="text-xs text-[var(--color-fg-muted)] mt-2 leading-relaxed">
                            Bulletins et états de paie mensuels, trimestriels et annuels à consulter ou télécharger.
                        </p>
                    </Card>
                </div>
            </div>
        </div>
    );
}
