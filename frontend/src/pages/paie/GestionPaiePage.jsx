import { lazy, Suspense, useEffect, useMemo, useState, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { 
    Banknote, CalendarRange, ArrowRight, RefreshCw, FileText, LayoutDashboard,
    DollarSign, AlertTriangle, ShieldCheck, CheckCircle2, SlidersHorizontal, Calculator
} from 'lucide-react';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Tabs from '@/components/ui/navigation/Tabs';
import Button from '@/components/ui/forms/Button';
import Select from '@/components/ui/forms/Select';
import Badge from '@/components/ui/data-display/Badge';
import StatCard from '@/components/ui/data-display/StatCard';
import { Card, CardBody } from '@/components/ui/data-display/Card';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SEO from '@/components/system/SEO';
import PayrollPeriodStepper from '@/components/paie/PayrollPeriodStepper';
import { usePayrollCampaign } from '@/hooks/paie/usePayrollCampaign';
import paieService from '@/api/paie/paieService';

// Lazy loading des sous-composants
const CockpitPaieTab = lazy(() => import('@/components/paie/CockpitPaieTab'));
const PeriodePaiePage = lazy(() => import('@/pages/paie/PeriodePaiePage'));
const ExportsPaieTab = lazy(() => import('@/components/paie/ExportsPaieTab'));
const VariablesPaieTab = lazy(() => import('@/components/paie/VariablesPaieTab'));

export default function GestionPaiePage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const navigate = useNavigate();
    const rawTab = searchParams.get('tab') || 'campagne';
    const requestedPeriodId = Number(searchParams.get('periodId')) || null;

    const { periodes, context, loading, refresh, selectPeriode } = usePayrollCampaign(requestedPeriodId);

    // Statistiques réelles de la période sélectionnée
    const [periodKpis, setPeriodKpis] = useState(null);
    const [kpiLoading, setKpiLoading] = useState(false);

    // Compatibilité des anciens liens tab
    useEffect(() => {
        if (rawTab === 'validation') {
            navigate('/pointages?tab=validation', { replace: true });
        } else if (rawTab === 'bulletins') {
            const suffix = requestedPeriodId ? `/paie/periods/${requestedPeriodId}/results` : '/paie?tab=cockpit';
            navigate(suffix, { replace: true });
        }
    }, [rawTab, navigate, requestedPeriodId]);

    const activePeriod = context?.selectedPeriod || context?.currentPeriod;
    const campaignSteps = context?.campaignSteps || [];
    const currentStepCode = context?.currentStep;
    const currentStepObj = campaignSteps.find(s => s.code === currentStepCode);

    // Charger les KPI réels du cockpit lorsque la période active change
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

    // Destination canonique backend pour "Continuer la paie"
    const continueDestination = useMemo(() => {
        if (currentStepObj?.destination) return currentStepObj.destination;
        if (activePeriod?.id) return `/paie/periodes/${activePeriod.id}/preparation`;
        return null;
    }, [currentStepObj, activePeriod]);

    const activeTab = ['campagne', 'variables', 'periodes', 'cockpit', 'exports'].includes(rawTab) ? rawTab : 'campagne';

    const handleTabChange = (tabId) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('tab', tabId);
            return next;
        });
    };

    const handleSelectPeriod = (newPeriodId) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            if (newPeriodId) next.set('periodId', String(newPeriodId));
            else next.delete('periodId');
            return next;
        });
        selectPeriode(newPeriodId);
    };

    const formatDate = (d) => {
        if (!d) return '–';
        return new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
    };

    const formatAr = (val) => {
        if (val == null) return '0 Ar';
        return Number(val).toLocaleString('fr-FR', { minimumFractionDigits: 0, maximumFractionDigits: 0 }) + ' Ar';
    };

    const statutVariant = {
        EN_COURS: 'warning',
        A_VALIDER: 'info',
        TRANSMIS_DAF: 'info',
        VALIDEE: 'success',
        CLOTUREE: 'secondary',
        BROUILLON: 'default',
    };

    const tabs = [
        { id: 'campagne', label: 'Campagne active', icon: LayoutDashboard },
        { id: 'variables', label: 'Variables & Primes', icon: SlidersHorizontal },
        { id: 'periodes', label: 'Historique des Périodes', icon: CalendarRange },
        { id: 'cockpit', label: 'Revue Globale', icon: Banknote },
        { id: 'exports', label: 'Déclarations & Sorties', icon: FileText },
    ];

    const periodOptions = useMemo(() => {
        return (periodes || []).map(p => {
            const pId = p.idPeriodePaie || p.id_periode_paie || p.id;
            const pCode = p.code || '';
            const pLib = p.libelle || p.statutLibelle || '';
            const pStat = p.statutCode || p.codeStatut || p.statut || '';
            return {
                value: String(pId),
                label: `${pCode}${pLib ? ` — ${pLib}` : ''} (${pStat || 'EN_COURS'})`
            };
        });
    }, [periodes]);

    return (
        <div className="space-y-6 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans pb-12 px-4 sm:px-6">
            <SEO
                title="Gestion de la Paie | GALAXAM SIRH"
                description="Cockpit DAF de gestion des campagnes de paie, validation, calculs réglementaires Madagascar et exports légaux."
            />

            <PageHeader
                title="Paie & Rémunération"
                subtitle="Pilotage des campagnes mensuelles, contrôle de cohérence, calculs légaux (IRSA, CNaPS, FMFP) et déclarations."
                icon={Banknote}
                actions={
                    <Button
                        variant="ghost"
                        size="xs"
                        iconLeft={<RefreshCw size={12} className={loading ? 'animate-spin' : ''} />}
                        onClick={() => { refresh(); if (activePeriod?.id) loadPeriodKpis(activePeriod.id); }}
                        disabled={loading}
                    >
                        Actualiser
                    </Button>
                }
            />

            {/* HERO : Campagne & Sélecteur de Période */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 shadow-sm space-y-4">
                <div className="flex items-center justify-between gap-4 flex-wrap">
                    <div className="space-y-1.5 min-w-[260px]">
                        <div className="flex items-center gap-2.5 flex-wrap">
                            <span className="text-xs font-mono font-bold text-[var(--color-brand-600)] uppercase tracking-wider">
                                Campagne de paie
                            </span>
                            {activePeriod?.state && (
                                <Badge variant={statutVariant[activePeriod.state] || 'default'} size="xs" dot>
                                    {activePeriod.state}
                                </Badge>
                            )}
                        </div>

                        {/* Sélecteur de période avec toutes les options disponibles */}
                        <div className="pt-1">
                            {periodOptions.length > 0 ? (
                                <div className="flex items-center gap-2 max-w-sm sm:max-w-md">
                                    <span className="text-xs font-semibold text-[var(--color-fg-muted)] shrink-0">
                                        Période :
                                    </span>
                                    <Select
                                        value={activePeriod?.id ? String(activePeriod.id) : ''}
                                        options={periodOptions}
                                        onChange={(val) => handleSelectPeriod(Number(val))}
                                        className="!py-1 font-mono font-bold"
                                        containerClassName="w-full"
                                    />
                                </div>
                            ) : (
                                <h2 className="text-lg font-bold text-[var(--color-fg)]">
                                    {activePeriod ? (activePeriod.label || activePeriod.code) : 'Aucune période trouvée'}
                                </h2>
                            )}
                        </div>

                        {activePeriod && (
                            <p className="text-xs text-[var(--color-fg-muted)]">
                                Période du <span className="font-semibold text-[var(--color-fg)]">{formatDate(activePeriod.startDate)}</span> au <span className="font-semibold text-[var(--color-fg)]">{formatDate(activePeriod.endDate)}</span>
                                {activePeriod.code && <span className="ml-2 font-mono text-[var(--color-fg-subtle)]">({activePeriod.code})</span>}
                            </p>
                        )}
                    </div>

                    {/* Action principale DAF */}
                    <div>
                        {continueDestination && (
                            <Button
                                variant="primary"
                                size="md"
                                iconRight={<ArrowRight size={16} />}
                                onClick={() => navigate(continueDestination)}
                                id="btn-continuer-paie"
                                className="font-semibold shadow-sm"
                            >
                                {currentStepObj?.label ? `Continuer la paie (${currentStepObj.label})` : 'Continuer la paie'}
                            </Button>
                        )}
                    </div>
                </div>

                {/* Stepper horizontal intégré */}
                {campaignSteps.length > 0 && (
                    <div className="pt-2 border-t border-[var(--color-border-subtle)]">
                        <PayrollPeriodStepper
                            steps={campaignSteps}
                            currentStep={currentStepCode}
                            activeCode={null}
                            loading={loading}
                        />
                    </div>
                )}
            </div>

            {/* Onglets de navigation secondaire */}
            <Tabs
                tabs={tabs}
                activeTab={activeTab}
                onChange={handleTabChange}
            />

            {/* Contenu fluide de l'onglet actif */}
            <div className="pt-2">
                <Suspense fallback={<SkeletonTable rows={6} cols={6} />}>
                    {activeTab === 'campagne' && (
                        <div className="space-y-5">
                            {/* Grille de synthèse KPI utilisant StatCard */}
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
                                    subtext={(periodKpis?.totalAlertes ?? 0) > 0 ? 'Écarts M-1 ou anomalies détectés' : 'Aucune anomalie'}
                                    icon={<AlertTriangle size={18} />}
                                    color={(periodKpis?.totalAlertes ?? 0) > 0 ? 'warning' : 'neutral'}
                                />
                            </div>

                            {/* Accès rapide aux étapes clés */}
                            {activePeriod && (
                                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-1">
                                    <Card
                                        hover
                                        padding
                                        onClick={() => navigate(`/paie/periodes/${activePeriod.id}/variables`)}
                                        className="space-y-2 border-l-4 border-l-[var(--color-brand-500)]"
                                    >
                                        <div className="flex items-center gap-2 text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]">
                                            <SlidersHorizontal size={16} />
                                            <h3 className="text-sm font-bold">1. Variables & Primes</h3>
                                        </div>
                                        <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                                            Vérifiez et saisissez les heures supp., primes exceptionnelles et avances sur salaire.
                                        </p>
                                    </Card>

                                    <Card
                                        hover
                                        padding
                                        onClick={() => navigate(`/paie/periodes/${activePeriod.id}/calcul`)}
                                        className="space-y-2 border-l-4 border-l-[var(--color-info-500)]"
                                    >
                                        <div className="flex items-center gap-2 text-[var(--color-info-600)] dark:text-[var(--color-info-400)]">
                                            <Calculator size={16} />
                                            <h3 className="text-sm font-bold">2. Moteur de Calcul</h3>
                                        </div>
                                        <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                                            Simulez ou exécutez le calcul réel des bulletins et cotisations légales (CNaPS, FMFP, IRSA).
                                        </p>
                                    </Card>

                                    <Card
                                        hover
                                        padding
                                        onClick={() => navigate(`/paie/periodes/${activePeriod.id}/controle`)}
                                        className="space-y-2 border-l-4 border-l-[var(--color-success-500)]"
                                    >
                                        <div className="flex items-center gap-2 text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">
                                            <ShieldCheck size={16} />
                                            <h3 className="text-sm font-bold">3. Contrôle & Validation</h3>
                                        </div>
                                        <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                                            Analysez les écarts M-1, inspectez les bulletins individuels et validez la campagne.
                                        </p>
                                    </Card>
                                </div>
                            )}
                        </div>
                    )}

                    {activeTab === 'variables' && (
                        <VariablesPaieTab
                            idPeriodeActive={activePeriod?.id}
                            onVariableChanged={refresh}
                        />
                    )}

                    {activeTab === 'periodes' && (
                        <PeriodePaiePage />
                    )}

                    {activeTab === 'cockpit' && (
                        <CockpitPaieTab
                            idPeriodeActive={activePeriod?.id}
                            setIdPeriodeActive={(id) => setSearchParams(prev => {
                                const next = new URLSearchParams(prev);
                                if (id) next.set('periodId', String(id));
                                else next.delete('periodId');
                                return next;
                            })}
                            periodes={periodes}
                            onReloadPeriodes={refresh}
                            periodContext={context}
                        />
                    )}

                    {activeTab === 'exports' && (
                        <ExportsPaieTab
                            idPeriodeActive={activePeriod?.id}
                            codePeriode={activePeriod?.code || ''}
                            outputCapability={context?.availableActions?.GENERATE_OUTPUT}
                            outputs={context?.outputs || []}
                        />
                    )}
                </Suspense>
            </div>
        </div>
    );
}
