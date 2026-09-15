import { lazy, Suspense, useEffect, useState, useCallback } from 'react';
import { NavLink, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { CalendarRange, Banknote, SlidersHorizontal, FileText } from 'lucide-react';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Tabs from '@/components/ui/navigation/Tabs';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SEO from '@/components/system/SEO';
import periodePaieService from '@/services/periodePaieService';

// Lazy loading des composants onglets
const CockpitPaieTab = lazy(() => import('@/components/paie/CockpitPaieTab'));
const VariablesPaieTab = lazy(() => import('@/components/paie/VariablesPaieTab'));
const PeriodePaiePage = lazy(() => import('@/pages/paie/PeriodePaiePage'));
const ExportsPaieTab = lazy(() => import('@/components/paie/ExportsPaieTab'));

export default function GestionPaiePage() {
    const { periodId: routePeriodId, step: routeStep, employeeId } = useParams();
    const [searchParams, setSearchParams] = useSearchParams();
    const navigate = useNavigate();
    const rawTab = searchParams.get('tab') || 'cockpit';
    const requestedPeriodId = Number(routePeriodId || searchParams.get('periodId')) || null;
    const workflowStep = routeStep || null;

    // Compatibilité des anciens liens : aucune valeur d'onglet ne doit produire une page vide.
    useEffect(() => {
        if (rawTab === 'validation') {
            navigate('/pointages?tab=validation', { replace: true });
        } else if (rawTab === 'bulletins') {
            const suffix = requestedPeriodId ? `/paie/periods/${requestedPeriodId}/results` : '/paie?tab=cockpit';
            navigate(suffix, { replace: true });
        }
    }, [rawTab, navigate, requestedPeriodId]);

    const knownTabs = new Set(['cockpit', 'variables', 'periodes', 'exports']);
    const activeTab = workflowStep
        ? (workflowStep === 'variables' ? 'variables' : workflowStep === 'outputs' ? 'exports' : 'cockpit')
        : (knownTabs.has(rawTab) ? rawTab : 'cockpit');

    // État partagé des périodes de paie
    const [periodes, setPeriodes] = useState([]);
    const [idPeriodeActive, setIdPeriodeActive] = useState(null);
    const [periodContext, setPeriodContext] = useState(null);
    const [_loadingPeriodes, setLoadingPeriodes] = useState(false);

    const chargerPeriodes = useCallback(async () => {
        setLoadingPeriodes(true);
        try {
            const [data, context] = await Promise.all([
                periodePaieService.getAllPeriodes(),
                periodePaieService.getContext(requestedPeriodId)
            ]);
            const liste = Array.isArray(data) ? data : data?.items || [];
            setPeriodes(liste);
            setPeriodContext(context);
            setIdPeriodeActive(context?.selectedPeriod?.id || null);
        } catch (err) {
            console.error('Erreur lors du chargement des périodes de paie :', err);
        } finally {
            setLoadingPeriodes(false);
        }
    }, [requestedPeriodId]);

    useEffect(() => {
        chargerPeriodes();
    }, [chargerPeriodes]);

    const handleTabChange = (tabId) => {
        if (routePeriodId) {
            const stepByTab = { cockpit: 'overview', variables: 'variables', exports: 'outputs' };
            if (stepByTab[tabId]) {
                navigate(`/paie/periods/${routePeriodId}/${stepByTab[tabId]}`);
                return;
            }
        }
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('tab', tabId);
            return next;
        });
    };

    const handlePeriodChange = (periodId) => {
        if (workflowStep && periodId) {
            navigate(`/paie/periods/${periodId}/${workflowStep}`);
            return;
        }
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            if (periodId) next.set('periodId', String(periodId));
            else next.delete('periodId');
            return next;
        });
    };

    const periodeActiveObj = periodes.find(p => (p.idPeriodePaie || p.id_periode_paie) === idPeriodeActive);

    const tabs = [
        {
            id: 'cockpit',
            label: 'Cockpit Paie',
            icon: Banknote
        },
        {
            id: 'variables',
            label: 'Variables & Primes',
            icon: SlidersHorizontal
        },
        {
            id: 'periodes',
            label: 'Périodes de Paie',
            icon: CalendarRange
        },
        {
            id: 'exports',
            label: 'Déclarations & Exports',
            icon: FileText
        }
    ];

    const workflowSteps = [
        ['overview', 'Préparation'],
        ['readiness', 'Prérequis'],
        ['population', 'Population'],
        ['variables', 'Variables'],
        ['results', 'Calcul & contrôle'],
        ['validation', 'Validation'],
        ['outputs', 'Sorties']
    ];

    return (
        <div className="space-y-4 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans pb-8">
            <SEO 
                title="Cockpit Paie & Rémunération | GALAXAM SIRH"
                description="Cockpit DAF de calcul, validation de la paie, suivi des variables, déclarations SAFI 25 col et DNTS CNaPS."
            />

            <PageHeader
                title="Paie & Rémunération"
                subtitle="Cockpit de gestion de la paie, contrôle M-1, calculs légaux Madagascar (IRSA, CNaPS, FMFP, Santé) et exports réglementaires."
                icon={Banknote}
            />

            {/* Navigation par Onglets */}
            <Tabs
                tabs={tabs}
                activeTab={activeTab}
                onChange={handleTabChange}
            />

            {idPeriodeActive && activeTab !== 'periodes' && (
                <nav aria-label="Étapes du workflow de paie" className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] overflow-x-auto">
                    <ol className="flex min-w-max">
                        {workflowSteps.map(([step, label], index) => (
                            <li key={step}>
                                <NavLink
                                    to={`/paie/periods/${idPeriodeActive}/${step}`}
                                    className={({ isActive }) => `block px-3 py-2.5 text-xs border-r border-[var(--color-border-subtle)] ${
                                        isActive
                                            ? 'bg-[var(--color-brand-500)] text-white font-bold'
                                            : 'text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'
                                    }`}
                                >
                                    <span className="font-mono mr-1.5">{index + 1}</span>{label}
                                </NavLink>
                            </li>
                        ))}
                    </ol>
                </nav>
            )}

            {/* Contenu fluide de l'onglet actif */}
            <div className="pt-2">
                <Suspense fallback={<SkeletonTable rows={6} cols={6} />}>
                    {activeTab === 'cockpit' && (
                        <CockpitPaieTab
                            idPeriodeActive={idPeriodeActive}
                            setIdPeriodeActive={handlePeriodChange}
                            periodes={periodes}
                            onReloadPeriodes={chargerPeriodes}
                            periodContext={periodContext}
                            view={workflowStep || 'overview'}
                            requestedEmployeeId={Number(employeeId) || null}
                        />
                    )}
                    {activeTab === 'variables' && (
                        <VariablesPaieTab
                            idPeriodeActive={idPeriodeActive}
                        />
                    )}
                    {activeTab === 'periodes' && (
                        <PeriodePaiePage />
                    )}
                    {activeTab === 'exports' && (
                        <ExportsPaieTab
                            idPeriodeActive={idPeriodeActive}
                            codePeriode={periodeActiveObj?.code || ''}
                        />
                    )}
                </Suspense>
            </div>
        </div>
    );
}
