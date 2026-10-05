import { useEffect, lazy, Suspense, useState, useMemo, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Alert from '@/components/ui/feedback/Alert';
import SEO from '@/components/system/SEO';
import WorkflowStepper from '@/components/ui/navigation/WorkflowStepper';
import { useAuth } from '@/context/AuthContext';
import { periodePaieService } from '@/features/attendance/api/attendanceApi';
import { getTodayDateString } from '@/utils/dateUtils';
import organisationService from '@/features/organization/api/organisationService';

// Lazy-loading des etapes du cockpit
const SaisieQuotidiennePage    = lazy(() => import('@/features/attendance/pages/SaisieQuotidiennePage'));
const SuiviAnomaliesPage       = lazy(() => import('@/features/attendance/pages/SuiviAnomaliesPage'));
const ArbitrageDafPage         = lazy(() => import('@/features/attendance/pages/ArbitrageDafPage'));

const normalizePointageStep = (tab) => {
    if (tab === 'saisie') return 'saisie';
    if (tab === 'suivi' || tab === 'anomalies') return 'suivi';
    if (tab === 'arbitrage' || tab === 'validation' || tab === 'synthese') return 'arbitrage';
    return 'saisie';
};

/**
 * GestionPointagesPage — Orchestrateur du cockpit Pointage lineaire.
 *
 * Workflow :
 *   Agent : ① Saisie → ② Suivi & Anomalies + Transmission
 *   DAF   : ① Saisie (readonly) → ② Suivi (readonly) → ③ Arbitrage DAF
 */
export default function GestionPointagesPage() {
    // Contexte et navigation
    const [searchParams, setSearchParams] = useSearchParams();
    const { hasPermission } = useAuth();

    // État local
    const [allPeriodes, setAllPeriodes] = useState([]);
    const [allSites, setAllSites] = useState([]);
    const [activePeriode, setActivePeriode] = useState(null);
    const [anomaliesCount, setAnomaliesCount] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [refreshVersion, setRefreshVersion] = useState(0);
    const loadedRefresh = useRef(-1);

    useEffect(() => {
        const refresh = () => setRefreshVersion(value => value + 1);

        // Polling régulier en arrière-plan toutes les 15 secondes si l'onglet est actif
        const timer = setInterval(() => {
            if (typeof document === 'undefined' || document.visibilityState === 'visible') {
                refresh();
            }
        }, 15000);

        const handleVisibilityChange = () => {
            if (document.visibilityState === 'visible') {
                refresh();
            }
        };

        window.addEventListener('pointage-period-updated', refresh);
        window.addEventListener('sirh:period-updated', refresh);
        window.addEventListener('refresh-notifications', refresh);
        window.addEventListener('focus', refresh);
        document.addEventListener('visibilitychange', handleVisibilityChange);

        return () => {
            clearInterval(timer);
            window.removeEventListener('pointage-period-updated', refresh);
            window.removeEventListener('sirh:period-updated', refresh);
            window.removeEventListener('refresh-notifications', refresh);
            window.removeEventListener('focus', refresh);
            document.removeEventListener('visibilitychange', handleVisibilityChange);
        };
    }, []);

    // Données dérivées
    const isDaf = hasPermission('POINTAGE_VALIDATE');
    const activeStep = normalizePointageStep((searchParams.get('tab') || 'saisie').toLowerCase());
    const paramPeriodeId = searchParams.get('periode');
    const periodStatus = activePeriode?.statutCode;
    const activePeriodeId = activePeriode?.idPeriodePaie;
    const activePeriodeIdRef = useRef(activePeriodeId);
    useEffect(() => { activePeriodeIdRef.current = activePeriodeId; }, [activePeriodeId]);
    const submitted = ['A_VALIDER', 'TRANSMIS_DAF', 'VALIDEE', 'PAIE_VALIDEE', 'CLOTUREE'].includes(periodStatus);
    const frozen = ['VALIDEE', 'PAIE_VALIDEE', 'CLOTUREE'].includes(periodStatus);

    const steps = useMemo(() => [
        {
            id: 'saisie',
            label: 'Saisie',
            sublabel: null,
            status: submitted ? 'done' : activeStep === 'saisie' ? 'active' : 'pending'
        },
        {
            id: 'suivi',
            label: 'Suivi & Anomalies',
            sublabel: anomaliesCount > 0 ? `${anomaliesCount} anom.` : null,
            status: submitted ? 'done' : activeStep === 'suivi' ? 'active' : 'pending'
        },
        {
            id: 'arbitrage',
            label: isDaf ? 'Contrôle & Validation' : 'Synthèse & Contrôle',
            status: frozen ? 'done' : activeStep === 'arbitrage' ? 'active' : 'pending'
        }
    ], [activeStep, anomaliesCount, isDaf, submitted, frozen]);

    // Effets
    useEffect(() => {
        // L'URL est normalisée après le premier chargement. Ne recharge pas le contexte
        // quand elle reçoit simplement l'identifiant déjà sélectionné.
        if (paramPeriodeId && activePeriodeIdRef.current && String(activePeriodeIdRef.current) === String(paramPeriodeId) && loadedRefresh.current === refreshVersion) return undefined;
        let isMounted = true;
        const fetchData = async () => {
            if (!activePeriodeIdRef.current) setLoading(true);
            setError(null);
            try {
                const [list, sites] = await Promise.all([
                    periodePaieService.getPeriodOptions(),
                    organisationService.getSites().catch(() => [])
                ]);
                if (!isMounted) return;
                setAllSites(sites || []);
                if (!list || list.length === 0) {
                    setAllPeriodes([]);
                    setActivePeriode(null);
                    setError(activeStep === 'saisie' ? null : 'Aucune période de paie disponible.');
                    loadedRefresh.current = refreshVersion;
                    setLoading(false);
                    return;
                }
                loadedRefresh.current = refreshVersion;
                setAllPeriodes(list);
                setError(null);

                const today = getTodayDateString();
                const target = (paramPeriodeId ? list.find(p => String(p.idPeriodePaie) === String(paramPeriodeId)) : null)
                    || list.find(p => p.dateDebut <= today && p.dateFin >= today)
                    || list.find(p => ['EN_COURS', 'A_VALIDER', 'TRANSMIS_DAF'].includes(p.statutCode))
                    || list[0]
                    || null;
                setActivePeriode(current => current?.idPeriodePaie === target?.idPeriodePaie
                    && current?.version === target?.version && current?.statutCode === target?.statutCode
                    ? { ...target, availableActions: current.availableActions || {} } : target);

                // Initialisation ou correction du paramètre d'URL s'il est absent ou invalide
                if (target?.idPeriodePaie && (!paramPeriodeId || String(target.idPeriodePaie) !== String(paramPeriodeId))) {
                    setSearchParams(prev => {
                        const next = new URLSearchParams(prev);
                        next.set('periode', String(target.idPeriodePaie));
                        return next;
                    }, { replace: true });
                }

            } catch (err) {
                if (isMounted) {
                    console.error('Erreur chargement periode active:', err);
                    setError('Impossible de charger les données de la période de pointage.');
                }
            } finally {
                if (isMounted) setLoading(false);
            }
        };
        fetchData();
        return () => {
            isMounted = false;
        };
    }, [paramPeriodeId, setSearchParams, refreshVersion]);

    // Actions
    // Les droits détaillés arrivent séparément : leur précontrôle ne bloque plus la grille.
    useEffect(() => {
        if (!activePeriodeId) return undefined;
        let cancelled = false;
        setActivePeriode(current => ({ ...current, availableActions: {} }));
        periodePaieService.getContext(activePeriodeId).then(context => {
            if (cancelled) return;
            setActivePeriode(current => current?.idPeriodePaie === activePeriodeId
                ? { ...current, availableActions: context?.availableActions || {} } : current);
        }).catch(() => {
            // Les actions restent désactivées si leurs droits n'ont pas pu être vérifiés.
        });
        return () => { cancelled = true; };
    }, [activePeriodeId, activePeriode?.version, activePeriode?.statutCode, refreshVersion]);

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

    // Rendu
    return (
        <div className="space-y-3.5 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans">
            <SEO
                title="Pointage & Présences | GALAXAM GRH"
                description="Cockpit linéaire de gestion des pointages : saisie quotidienne, suivi des anomalies, contrôle et validation des heures."
            />

            {/* WorkflowStepper — navigation lineaire */}
            <WorkflowStepper
                steps={steps}
                currentStep={activeStep}
                onStepClick={(stepId) => handleStepChange(stepId)}
                activePeriode={activePeriode}
            />

            {error && (
                <div className="my-3">
                    <Alert type="error" title="Période indisponible" message={error} />
                </div>
            )}

            {/* Contenu de l'etape active */}
            <div className="w-full">
                {loading ? (
                    <SkeletonTable rows={8} columns={8} />
                ) : (
                    <Suspense fallback={<SkeletonTable rows={6} columns={6} />}>
                        {activeStep === 'saisie' && (
                            <SaisieQuotidiennePage
                                activePeriode={activePeriode}
                                sitesDisponibles={allSites}
                                onSelectPeriode={handlePeriodeChange}
                            />
                        )}

                        {activeStep === 'suivi' && activePeriode && (
                            <SuiviAnomaliesPage
                                activePeriode={activePeriode}
                                periodesDisponibles={allPeriodes}
                                sitesDisponibles={allSites}
                                onSelectPeriode={handlePeriodeChange}
                                onTabChange={handleStepChange}
                                onAnomaliesCountChange={setAnomaliesCount}
                            />
                        )}

                        {activeStep === 'arbitrage' && activePeriode && (
                            <ArbitrageDafPage
                                activePeriode={activePeriode}
                                periodesDisponibles={allPeriodes}
                                sitesDisponibles={allSites}
                                onSelectPeriode={handlePeriodeChange}
                            />
                        )}
                    </Suspense>
                )}
            </div>
        </div>
    );
}
