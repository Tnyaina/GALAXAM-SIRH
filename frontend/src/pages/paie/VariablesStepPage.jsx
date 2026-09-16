import { useParams, useNavigate } from 'react-router-dom';
import { lazy, Suspense } from 'react';
import SEO from '@/components/system/SEO';
import PayrollCampaignHeader from '@/components/paie/PayrollCampaignHeader';
import PayrollNavigationFooter from '@/components/paie/PayrollNavigationFooter';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { usePayrollCampaign } from '@/hooks/paie/usePayrollCampaign';

const VariablesPaieTab = lazy(() => import('@/components/paie/VariablesPaieTab'));

/**
 * VariablesStepPage — Étape 3 : Variables & Primes.
 *
 * Réutilise le composant VariablesPaieTab existant dans le layout de campagne Sprint 2B.
 * Affiche l'en-tête de campagne avec le stepper et la bannière de recalcul.
 */
export default function VariablesStepPage() {
    const { periodId } = useParams();
    const navigate = useNavigate();
    const numericPeriodId = Number(periodId) || null;
    const { periodes, context, loading, refresh, selectedPeriodeId } = usePayrollCampaign(numericPeriodId);

    // Après une action sur les variables, rafraîchir le contexte (pour recalculationRequired)
    const handleVariableChange = () => refresh();

    const activePeriodId = numericPeriodId || selectedPeriodeId;

    return (
        <div className="flex-1 flex flex-col min-h-full">
            <SEO
                title="Variables & Primes | Paie GALAXAM SIRH"
                description="Saisie des éléments variables de paie : primes, acomptes, retenues, avances."
            />
            <PayrollCampaignHeader
                context={context}
                periodes={periodes}
                onSelectPeriod={(newId) => navigate(`/paie/periodes/${newId}/variables`)}
                activeStep="VARIABLES"
                loading={loading}
                onRefresh={refresh}
            />

            <div className="flex-1 max-w-6xl mx-auto px-4 py-6 w-full">
                <Suspense fallback={<SkeletonTable rows={6} cols={6} />}>
                    <VariablesPaieTab
                        idPeriodeActive={activePeriodId}
                        onVariableChanged={handleVariableChange}
                    />
                </Suspense>
            </div>

            <PayrollNavigationFooter
                prevStep={{
                    label: 'Population éligible',
                    destination: `/paie/periodes/${activePeriodId}/population`,
                }}
                nextStep={{
                    label: 'Calcul de la paie',
                    destination: `/paie/periodes/${activePeriodId}/calcul`,
                }}
            />
        </div>
    );
}
