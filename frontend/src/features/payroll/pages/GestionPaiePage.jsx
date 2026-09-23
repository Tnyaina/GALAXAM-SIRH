import { useEffect, useMemo, useState, useCallback, lazy, Suspense } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { 
    Banknote, CalendarRange, ArrowRight, FileText,
    DollarSign, AlertTriangle, SlidersHorizontal,
    ChevronRight, TrendingUp, TrendingDown
} from 'lucide-react';
import SEO from '@/components/system/SEO';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import StatCard from '@/components/ui/data-display/StatCard';
import PayrollPeriodStepper from '@/features/payroll/components/workflow/PayrollPeriodStepper';
import PayrollIssueBadge from '@/features/payroll/components/feedback/PayrollIssueBadge';
import PageHeader from '@/components/ui/navigation/PageHeader';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import paieService from '@/features/payroll/api/paieService';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { fmtDate } from '@/utils/dateUtils';
import { getPaieStatusVariant } from '@/features/payroll/utils/payrollPresentation';

const PeriodePaiePage = lazy(() => import('@/features/payroll/pages/PeriodePaiePage'));
const ExportsPaieTab = lazy(() => import('@/features/payroll/components/tabs/ExportsPaieTab'));
const VariablesPaieTab = lazy(() => import('@/features/payroll/components/tabs/VariablesPaieTab'));

// PRESENTATION ONLY : statuts centralisés dans paieUtils.js → PAIE_STATUS_PRESENTATION

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
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const rawTab = searchParams.get('tab') || 'centre';
    const requestedPeriodId = Number(searchParams.get('periodId')) || null;

    const { periodes, context, loading, selectPeriode } = usePayrollCampaign(requestedPeriodId);

    const [periodKpis, setPeriodKpis] = useState(null);
    const [kpiLoading, setKpiLoading] = useState(false);
    const [variableSummary, setVariableSummary] = useState({ count: 0, gains: 0, retenues: 0 });

    // Compatibilité et redirection des anciens onglets
    useEffect(() => {
        if (rawTab === 'validation') {
            navigate('/pointages?tab=validation', { replace: true });
        } else if (rawTab === 'bulletins') {
            const suffix = requestedPeriodId ? `/paie/periodes/${requestedPeriodId}/controle` : '/paie';
            navigate(suffix, { replace: true });
        }
    }, [rawTab, navigate, requestedPeriodId]);

    const activePeriod = context?.selectedPeriod || context?.currentPeriod || periodes[0] || null;
    const isCurrentBusinessPeriod = Boolean(context?.currentPeriod?.id && context.currentPeriod.id === activePeriod?.id);
    const campaignSteps = context?.campaignSteps || [];
    const currentStepCode = context?.currentStep;
    const currentStepObj = campaignSteps.find(s => s.code === currentStepCode);
    const recalcRequired = context?.recalculationRequired === true;
    const blockers = context?.issues || [];

    // Charger les KPI réels
    const loadPeriodKpis = useCallback(async (periodId) => {
        if (!periodId) {
            setPeriodKpis(null);
            return;
        }
        setKpiLoading(true);
        try {
            const cockpitData = await paieService.getCockpitPeriode(periodId, { size: 1 });
            if (cockpitData?.kpis) {
                setPeriodKpis({
                    totalBulletins: cockpitData.kpis.totalBulletins ?? 0,
                    totalBrut: cockpitData.kpis.totalBrut ?? cockpitData.kpis.masseSalarialeBrute ?? 0,
                    totalNet: cockpitData.kpis.totalNetAPayer ?? cockpitData.kpis.masseNetAPayer ?? 0,
                    totalAlertes: cockpitData.readiness?.nbAnomalies ?? cockpitData.kpis.nbAlertes ?? 0,
                });
            } else {
                setPeriodKpis(null);
            }
        } catch {
            setPeriodKpis(null);
        } finally {
            setKpiLoading(false);
        }
    }, []);

    useEffect(() => {
        if (activePeriod?.id) {
            loadPeriodKpis(activePeriod.id);
        } else {
            setPeriodKpis(null);
        }
    }, [activePeriod?.id, loadPeriodKpis]);

    useEffect(() => {
        let active = true;
        if (!activePeriod?.id) {
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
    }, [activePeriod?.id]);

    const continueDestination = useMemo(() => {
        if (currentStepObj?.destination) return currentStepObj.destination;
        if (activePeriod?.id) return `/paie/periodes/${activePeriod.id}/preparation`;
        return null;
    }, [currentStepObj, activePeriod]);

    // formatDate centralisé dans dateUtils.js — fmtDate() retourne DD/MM/YYYY
    // formatAr alias de formatMoneyAr — centralisé dans moneyUtils.js
    const formatAr = (val) => (val !== undefined && val !== null ? formatMoneyAr(val) : '–');

    // Navigation strictement fournie par le backend. Cette couche choisit seulement
    // le libellé et l'icône de présentation; elle ne reconstruit aucune transition.
    const nextLogicalAction = useMemo(() => {
        const nextStep = context?.nextStep;
        if (!nextStep?.destination) return null;
        const label = nextStep.code ? `Continuer : ${nextStep.code.replaceAll('_', ' ')}` : 'Continuer la campagne';
        return {
            title: 'Prochaine étape de la campagne',
            description: 'Destination déterminée par le workflow de paie.',
            label,
            icon: ArrowRight,
            onClick: () => navigate(nextStep.destination),
            tone: 'brand',
        };
    }, [context?.nextStep, navigate]);

    // Rendu des sous-vues si compatibilité demandée
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
            <Suspense fallback={<SkeletonTable rows={6} cols={6} />}>
                <div className="p-4 bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">
                    <VariablesPaieTab
                        idPeriodeActive={activePeriod?.id}
                        canMutate={addVariableCapability?.allowed === true}
                        mutationDisabledReason={(addVariableCapability?.reasonCodes || []).join(', ') || 'La période est en lecture seule.'}
                        onVariableChanged={() => activePeriod?.id && loadPeriodKpis(activePeriod.id)}
                    />
                </div>
            </Suspense>
        );
    }

    return (
        <div className="space-y-6 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full pb-12 px-2 sm:px-4">
            <SEO
                title="Centre de Paie | GALAXAM SIRH"
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

            {/* CARTE CAMPAGNE ACTIVE (V1 Full V3) */}
            <section className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 sm:p-6 shadow-xs relative">
                <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6">
                    <div className="space-y-2">
                        <div className="flex items-center gap-3 flex-wrap">
                            <span className="px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider bg-[var(--color-brand-500)]/15 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)]">
                                {isCurrentBusinessPeriod ? 'Campagne en cours' : activePeriod ? 'Période sélectionnée' : 'Campagne en cours'}
                            </span>
                            <h2 className="text-2xl sm:text-3xl font-bold text-[var(--color-fg)] tracking-tight">
                                {activePeriod?.code || activePeriod?.label || 'Aucune campagne active'}
                            </h2>
                            {activePeriod?.state && (
                                <Badge variant={getPaieStatusVariant(activePeriod.state)} size="md">
                                    {activePeriod.state}
                                </Badge>
                            )}
                            {recalcRequired && (
                                <PayrollIssueBadge severity="WARNING" label="Calcul obsolète" />
                            )}
                            {blockers.length > 0 && (
                                <PayrollIssueBadge severity="BLOCKER" count={blockers.length} label="bloquant(s)" />
                            )}
                        </div>

                        {activePeriod && (
                            <p className="text-xs text-[var(--color-fg-muted)] flex items-center gap-3 flex-wrap">
                                <span>
                                    Du <strong className="text-[var(--color-fg)]">{fmtDate(activePeriod.startDate)}</strong> au <strong className="text-[var(--color-fg)]">{fmtDate(activePeriod.endDate)}</strong>
                                </span>
                                <span>· Population : <strong className="text-[var(--color-fg)] font-mono">{activePeriod.employeeCount ?? context?.populationCount ?? '–'}</strong> salariés</span>
                                {context?.snapshotPointageVersion && (
                                    <span>· Snapshot Pointage : <code className="font-mono text-[var(--color-brand-600)]">v{context.snapshotPointageVersion}</code></span>
                                )}
                            </p>
                        )}
                    </div>

                    {/* Bouton Reprendre la campagne */}
                    {continueDestination && (
                        <div className="shrink-0">
                            <Button
                                variant="primary"
                                size="lg"
                                iconRight={<ArrowRight size={18} />}
                                onClick={() => navigate(continueDestination)}
                                id="btn-reprendre-campagne"
                                className="font-semibold shadow-xs"
                            >
                                Reprendre la campagne
                            </Button>
                        </div>
                    )}
                </div>

                {/* Stepper de campagne compact */}
                {campaignSteps.length > 0 && (
                    <div className="mt-6 pt-4 border-t border-[var(--color-border-subtle)]">
                        <div className="flex items-center justify-between mb-2">
                            <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                                Progression du workflow
                            </span>
                            <span className="text-xs text-[var(--color-brand-600)] font-medium">
                                Étape en cours : <strong>{currentStepObj?.label || 'Préparation'}</strong>
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

            {/* SECTION "À FAIRE MAINTENANT" (V1 Full V3) */}
            {nextLogicalAction && (
                <section className="bg-[var(--color-bg-subtle)] border-l-4 border-[var(--color-brand-500)] border border-y-[var(--color-border-subtle)] border-r-[var(--color-border-subtle)] p-4 sm:p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                    <div className="flex items-start gap-3.5">
                        <div className="p-2 rounded-full bg-[var(--color-surface)] border border-[var(--color-border-subtle)] text-[var(--color-brand-600)] shrink-0 mt-0.5">
                            <nextLogicalAction.icon size={20} />
                        </div>
                        <div>
                            <div className="flex items-center gap-2">
                                <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]">
                                    À faire maintenant
                                </span>
                            </div>
                            <h3 className="text-sm font-bold text-[var(--color-fg)] mt-0.5">
                                {nextLogicalAction.title}
                            </h3>
                            <p className="text-xs text-[var(--color-fg-muted)] mt-1 max-w-2xl">
                                {nextLogicalAction.description}
                            </p>
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
                <StatCard
                    title="Bulletins calculés"
                    value={kpiLoading ? '…' : (periodKpis?.totalBulletins ?? 0)}
                    subtext={activePeriod ? `Période ${activePeriod.code || ''}` : ''}
                    icon={<FileText size={18} />}
                    color="brand"
                />
                <StatCard
                    title="Masse Brute"
                    value={kpiLoading ? '…' : formatAr(periodKpis?.totalBrut)}
                    subtext="Salaires bruts imposables"
                    icon={<DollarSign size={18} />}
                    color="info"
                />
                <StatCard
                    title="Net à payer"
                    value={kpiLoading ? '…' : formatAr(periodKpis?.totalNet)}
                    subtext="Montant total des virements"
                    icon={<Banknote size={18} />}
                    color="success"
                />
                <StatCard
                    title="Alertes & Contrôles"
                    value={kpiLoading ? '…' : (periodKpis?.totalAlertes ?? 0)}
                    subtext={(periodKpis?.totalAlertes ?? 0) > 0 ? 'Anomalies nécessitant examen' : 'Aucune anomalie'}
                    icon={<AlertTriangle size={18} />}
                    color={(periodKpis?.totalAlertes ?? 0) > 0 ? 'warning' : 'neutral'}
                />
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
                    {(blockers.length > 0 || recalcRequired) && (
                        <div className="px-4 py-3 bg-[var(--color-warning-500)]/10 text-xs text-[var(--color-fg)]">
                            {recalcRequired ? 'Le calcul doit être relancé après les dernières modifications. ' : ''}
                            {blockers.length > 0 ? `${blockers.length} point(s) bloquant(s) doivent être résolus avant validation.` : ''}
                        </div>
                    )}
                </section>
            )}

            {/* ESPACES FONCTIONNELS DE GESTION (V1 Full V3) */}
            <div className="space-y-3 pt-2">
                <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                    Espaces de gestion
                </h3>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    {/* Acomptes & Avances */}
                    <div
                        onClick={() => navigate('/paie/acomptes-avances')}
                        className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 cursor-pointer hover:border-[var(--color-brand-500)] hover:shadow-xs transition-all group"
                    >
                        <div className="flex items-center justify-between">
                            <div className="flex items-center gap-2.5 text-[var(--color-fg)]">
                                <SlidersHorizontal size={18} className="text-[var(--color-brand-600)]" />
                                <h4 className="text-sm font-bold group-hover:text-[var(--color-brand-600)] transition-colors">
                                    Acomptes & Avances
                                </h4>
                            </div>
                            <ChevronRight size={16} className="text-[var(--color-fg-subtle)] group-hover:translate-x-0.5 transition-transform" />
                        </div>
                        <p className="text-xs text-[var(--color-fg-muted)] mt-2 leading-relaxed">
                            Gestion des demandes d’acomptes, avances sur salaire et suivi des imputations mensuelles.
                        </p>
                    </div>

                    {/* Historique des Périodes */}
                    <div
                        onClick={() => navigate('/paie?tab=periodes')}
                        className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 cursor-pointer hover:border-[var(--color-brand-500)] hover:shadow-xs transition-all group"
                    >
                        <div className="flex items-center justify-between">
                            <div className="flex items-center gap-2.5 text-[var(--color-fg)]">
                                <CalendarRange size={18} className="text-[var(--color-brand-600)]" />
                                <h4 className="text-sm font-bold group-hover:text-[var(--color-brand-600)] transition-colors">
                                    Historique des Périodes
                                </h4>
                            </div>
                            <ChevronRight size={16} className="text-[var(--color-fg-subtle)] group-hover:translate-x-0.5 transition-transform" />
                        </div>
                        <p className="text-xs text-[var(--color-fg-muted)] mt-2 leading-relaxed">
                            Consultation des campagnes passées, archives en lecture seule, ouverture et clôture de périodes.
                        </p>
                    </div>

                    {/* Déclarations & Sorties */}
                    <div
                        onClick={() => navigate('/paie/declarations')}
                        className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 cursor-pointer hover:border-[var(--color-brand-500)] hover:shadow-xs transition-all group"
                    >
                        <div className="flex items-center justify-between">
                            <div className="flex items-center gap-2.5 text-[var(--color-fg)]">
                                <FileText size={18} className="text-[var(--color-brand-600)]" />
                                <h4 className="text-sm font-bold group-hover:text-[var(--color-brand-600)] transition-colors">
                                    Déclarations & Sorties
                                </h4>
                            </div>
                            <ChevronRight size={16} className="text-[var(--color-fg-subtle)] group-hover:translate-x-0.5 transition-transform" />
                        </div>
                        <p className="text-xs text-[var(--color-fg-muted)] mt-2 leading-relaxed">
                            Édition des bulletins PDF, Grand-Livre, déclarations mensuelles (IRSA) et trimestrielles (CNaPS, FMFP, Santé).
                        </p>
                    </div>
                </div>
            </div>
        </div>
    );
}
