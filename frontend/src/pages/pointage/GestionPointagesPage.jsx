import { useEffect, lazy, Suspense, useState, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SEO from '@/components/system/SEO';
import WorkflowStepper from '@/components/ui/navigation/WorkflowStepper';
import { useAuth } from '@/context/AuthContext';
import periodePaieService from '@/services/periodePaieService';
import pointageAnomalieService from '@/api/pointage/pointageAnomalieService';

// Lazy-loading des etapes du cockpit
const SaisieQuotidiennePage    = lazy(() => import('@/pages/pointage/SaisieQuotidiennePage'));
const SuiviAnomaliesPage       = lazy(() => import('@/pages/pointage/SuiviAnomaliesPage'));
const ArbitrageDafPage         = lazy(() => import('@/pages/pointage/ArbitrageDafPage'));

/**
 * GestionPointagesPage — Orchestrateur du cockpit Pointage lineaire.
 *
 * Workflow :
 *   Agent : ① Saisie → ② Suivi & Anomalies + Transmission
 *   DAF   : ① Saisie (readonly) → ② Suivi (readonly) → ③ Arbitrage DAF
 */
export default function GestionPointagesPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const { hasPermission } = useAuth();
    const isDaf = hasPermission('POINTAGE_VALIDATE');

    // Normalisation du tab : supporte les anciens alias (validation, arbitrage, anomalies, synthese)
    const rawTab = (searchParams.get('tab') || 'saisie').toLowerCase();
    const normalizeStep = (tab) => {
        if (tab === 'saisie') return 'saisie';
        if (tab === 'suivi' || tab === 'anomalies') return 'suivi';
        if (tab === 'arbitrage' || tab === 'validation' || tab === 'synthese') return 'arbitrage';
        return 'saisie';
    };
    const activeStep = normalizeStep(rawTab);

    const [allPeriodes, setAllPeriodes] = useState([]);
    const [activePeriode, setActivePeriode] = useState(null);
    const [anomaliesCount, setAnomaliesCount] = useState(0);

    const paramPeriodeId = searchParams.get('periode');

    // Chargement et synchronisation de la période active avec l'URL
    useEffect(() => {
        let isMounted = true;
        const fetchData = async () => {
            try {
                const [list, context] = await Promise.all([
                    periodePaieService.getAllPeriodes(),
                    periodePaieService.getContext(paramPeriodeId ? Number(paramPeriodeId) : null)
                ]);
                if (!isMounted || !list || list.length === 0) return;
                setAllPeriodes(list);

                const selectedId = context?.selectedPeriod?.id;
                const target = list.find(p => String(p.idPeriodePaie) === String(selectedId)) || null;
                setActivePeriode(target);

                // Initialisation ou correction du paramètre d'URL s'il est absent ou invalide
                if (target?.idPeriodePaie && (!paramPeriodeId || String(target.idPeriodePaie) !== String(paramPeriodeId))) {
                    setSearchParams(prev => {
                        const next = new URLSearchParams(prev);
                        next.set('periode', String(target.idPeriodePaie));
                        return next;
                    }, { replace: true });
                }

                if (target?.idPeriodePaie) {
                    try {
                        const res = await pointageAnomalieService.getAnomalies({
                            idPeriodePaie: target.idPeriodePaie,
                            pageSize: 1
                        });
                        if (isMounted) {
                            setAnomaliesCount(res?.totalElements ?? res?.anomalies?.length ?? 0);
                        }
                    } catch {
                        if (isMounted) setAnomaliesCount(0);
                    }
                }
            } catch (err) {
                console.error('Erreur chargement periode active:', err);
            }
        };
        fetchData();
        return () => { isMounted = false; };
    }, [paramPeriodeId, setSearchParams]);

    const handlePeriodeChange = (newPeriodeId) => {
        const found = allPeriodes.find(p => String(p.idPeriodePaie) === String(newPeriodeId));
        if (found) {
            setActivePeriode(found);
            setSearchParams(prev => {
                const next = new URLSearchParams(prev);
                next.set('periode', String(newPeriodeId));
                return next;
            });
        }
    };

    const handleStepChange = (stepId, extraParams = {}) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('tab', stepId);
            Object.entries(extraParams).forEach(([k, v]) => {
                if (v !== undefined && v !== null) next.set(k, v);
                else next.delete(k);
            });
            return next;
        });
    };

    // Le stepper décrit uniquement la navigation ; le backend porte les décisions de workflow.
    const steps = useMemo(() => {
        const allSteps = [
            {
                id: 'saisie',
                label: 'Saisie',
                sublabel: null,
                status: activeStep === 'saisie' ? 'active' : 'pending'
            },
            {
                id: 'suivi',
                label: 'Suivi & Anomalies',
                sublabel: anomaliesCount > 0 ? `${anomaliesCount} anom.` : null,
                status: activeStep === 'suivi' ? 'active' : 'pending'
            },
            {
                id: 'arbitrage',
                label: isDaf ? 'Contrôle & Validation' : 'Synthèse & Contrôle',
                status: activeStep === 'arbitrage' ? 'active' : 'pending'
            }
        ];

        return allSteps;
    }, [activeStep, isDaf, anomaliesCount]);

    return (
        <div className="space-y-3.5 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans">
            <SEO
                title="Pointage & Présences | GALAXAM SIRH"
                description="Cockpit linéaire de gestion des pointages : saisie quotidienne, suivi des anomalies, contrôle et validation des heures."
            />

            {/* WorkflowStepper — navigation lineaire */}
            <WorkflowStepper
                steps={steps}
                currentStep={activeStep}
                onStepClick={(stepId) => handleStepChange(stepId)}
                activePeriode={activePeriode}
            />

            {/* Contenu de l'etape active */}
            <div className="w-full">
                <Suspense fallback={<SkeletonTable rows={6} columns={6} />}>
                    {activeStep === 'saisie' && (
                        <SaisieQuotidiennePage
                            activePeriode={activePeriode}
                            onSelectPeriode={handlePeriodeChange}
                        />
                    )}

                    {activeStep === 'suivi' && (
                        <SuiviAnomaliesPage
                            activePeriode={activePeriode}
                            onSelectPeriode={handlePeriodeChange}
                            onTabChange={handleStepChange}
                        />
                    )}

                    {activeStep === 'arbitrage' && (
                        <ArbitrageDafPage
                            activePeriode={activePeriode}
                            onSelectPeriode={handlePeriodeChange}
                        />
                    )}
                </Suspense>
            </div>
        </div>
    );
}
