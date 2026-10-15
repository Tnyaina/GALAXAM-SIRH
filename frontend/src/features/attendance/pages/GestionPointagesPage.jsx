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
const SaisieQuotidiennePage = lazy(() => import('@/features/attendance/pages/SaisieQuotidiennePage'));
const SaisiePeriodeEmployePage = lazy(() => import('@/features/attendance/pages/SaisiePeriodeEmployePage'));
const SuiviAnomaliesPage = lazy(() => import('@/features/attendance/pages/SuiviAnomaliesPage'));
const ArbitrageDafPage = lazy(() => import('@/features/attendance/pages/ArbitrageDafPage'));

const normalizePointageStep = (tab) => {
    if (tab === 'saisie' || tab === 'saisie-employe') return 'saisie';
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

        // Réactualisation ciblée uniquement sur les événements métier (validation, clôture de période)
        window.addEventListener('pointage-period-updated', refresh);
        window.addEventListener('sirh:period-updated', refresh);

        return () => {
            window.removeEventListener('pointage-period-updated', refresh);
            window.removeEventListener('sirh:period-updated', refresh);
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
            label: 'À traiter',
            sublabel: anomaliesCount > 0 ? `${anomaliesCount} point(s)` : null,
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

                // Préserver la référence d'objet si les sites n'ont pas changé
                setAllSites(prev => {
                    const next = sites || [];
                    if (prev.length === next.length && prev.every((s, i) => s.idSite === next[i]?.idSite)) return prev;
                    return next;
                });

                if (!list || list.length === 0) {
                    setAllPeriodes([]);
                    setActivePeriode(null);
                    setError(activeStep === 'saisie' ? null : 'Aucune période de paie disponible.');
                    loadedRefresh.current = refreshVersion;
                    setLoading(false);
                    return;
                }
                loadedRefresh.current = refreshVersion;

                // Préserver la référence d'objet si les périodes n'ont pas changé
                setAllPeriodes(prev => {
                    if (prev.length === list.length && prev.every((p, i) => p.idPeriodePaie === list[i]?.idPeriodePaie && p.statutCode === list[i]?.statutCode && p.version === list[i]?.version)) {
                        return prev;
                    }
                    return list;
                });
                setError(null);

                const today = getTodayDateString();
                const target = (paramPeriodeId ? list.find(p => String(p.idPeriodePaie) === String(paramPeriodeId)) : null)
                    || list.find(p => p.dateDebut <= today && p.dateFin >= today)
                    || list.find(p => ['EN_COURS', 'A_VALIDER', 'TRANSMIS_DAF'].includes(p.statutCode))
                    || list[0]
                    || null;

                setActivePeriode(current => {
                    if (!current) return target;
                    if (current.idPeriodePaie === target?.idPeriodePaie
                        && current.version === target?.version
                        && current.statutCode === target?.statutCode) {
                        return current;
                    }
                    return { ...target, availableActions: target.availableActions || current.availableActions || {} };
                });

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
    }, [activeStep, paramPeriodeId, setSearchParams, refreshVersion]);

    // Actions
    // Les droits détaillés arrivent séparément : leur précontrôle ne bloque plus la grille.
    useEffect(() => {
        if (!activePeriodeId) return undefined;
        let cancelled = false;
        // On initialise availableActions à vide uniquement lors du premier chargement absolu
        setActivePeriode(current => {
            if (!current) return current;
            if (!current.availableActions) return { ...current, availableActions: {} };
            return current;
        });

        periodePaieService.getContext(activePeriodeId).then(context => {
            if (cancelled) return;
            const nextActions = context?.availableActions || {};
            setActivePeriode(current => {
                if (!current || current.idPeriodePaie !== activePeriodeId) return current;
                const currentActions = current.availableActions || {};
                const isIdentical = Object.keys(nextActions).length === Object.keys(currentActions).length
                    && Object.keys(nextActions).every(k => nextActions[k]?.allowed === currentActions[k]?.allowed);
                if (isIdentical) return current;
                return { ...current, availableActions: nextActions };
            });
        }).catch(() => {
            // Les actions restent inchangées ou désactivées
        });
        return () => { cancelled = true; };
    }, [activePeriodeId, activePeriode?.version, activePeriode?.statutCode]);

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

            {activePeriode?.statutCode === 'EN_COURS' && activePeriode?.motifRejet && (
                <Alert
                    type="warning"
                    title="Période renvoyée par la DAF pour correction"
                    message={activePeriode.motifRejet}
                />
            )}

            {/* Contenu de l'etape active */}
            <div className="w-full">
                {loading ? (
                    <SkeletonTable rows={8} columns={8} />
                ) : (
                    <Suspense fallback={<SkeletonTable rows={6} columns={6} />}>
                        {activeStep === 'saisie' && (
                            (searchParams.get('mode') === 'employe' || searchParams.get('tab') === 'saisie-employe') ? (
                                <SaisiePeriodeEmployePage
                                    activePeriode={activePeriode}
                                    periodesDisponibles={allPeriodes}
                                    sitesDisponibles={allSites}
                                    onSelectPeriode={handlePeriodeChange}
                                />
                            ) : (
                                <SaisieQuotidiennePage
                                    activePeriode={activePeriode}
                                    sitesDisponibles={allSites}
                                    onSelectPeriode={handlePeriodeChange}
                                />
                            )
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
