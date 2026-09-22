import { useParams, useNavigate } from 'react-router-dom';
import { lazy, Suspense } from 'react';
import PayrollCampaignShell from '@/components/paie/PayrollCampaignShell';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { usePayrollCampaign } from '@/hooks/paie/usePayrollCampaign';

const VariablesPaieTab = lazy(() => import('@/components/paie/VariablesPaieTab'));

/**
 * VariablesStepPage — Étape 3 : Variables exceptionnelles (V1 Full V3).
 */
export default function VariablesStepPage() {
    const { periodId } = useParams();
    const navigate = useNavigate();
    const numericPeriodId = Number(periodId) || null;
    const { periodes, context, loading, refresh, selectedPeriodeId } = usePayrollCampaign(numericPeriodId);

    const handleVariableChange = () => refresh();

    const activePeriodId = numericPeriodId || selectedPeriodeId;
    const addVariableCapability = context?.availableActions?.ADD_VARIABLE;
    const canMutate = addVariableCapability?.allowed === true;
    const mutationDisabledReason = (addVariableCapability?.reasonCodes || []).join(', ') || 'La période est en lecture seule.';

    return (
        <PayrollCampaignShell
            title="Étape 3 — Variables exceptionnelles"
            subtitle="Primes exceptionnelles, acomptes et retenues manuelles autorisées par le catalogue backend."
            activeStep="VARIABLES"
            context={context}
            periodes={periodes}
            loading={loading}
            prevStep={{
                label: 'Population éligible',
                destination: `/paie/periodes/${activePeriodId}/population`,
            }}
            nextStep={{
                label: 'Calcul de la paie',
                destination: `/paie/periodes/${activePeriodId}/calcul`,
            }}
        >
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 sm:p-5">
                <Suspense fallback={<SkeletonTable rows={6} cols={6} />}>
                    <VariablesPaieTab
                        idPeriodeActive={activePeriodId}
                        onVariableChanged={handleVariableChange}
                        canMutate={canMutate}
                        mutationDisabledReason={mutationDisabledReason}
                    />
                </Suspense>
            </div>
        </PayrollCampaignShell>
    );
}
