import { useParams } from 'react-router-dom';
import { lazy, Suspense } from 'react';
import PayrollCampaignShell from '@/features/payroll/components/workflow/PayrollCampaignShell';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';

const VariablesPaieTab = lazy(() => import('@/features/payroll/components/tabs/VariablesPaieTab'));

/**
 * VariablesStepPage — Étape 3 : Variables exceptionnelles (V1 Full V3).
 */
export default function VariablesStepPage() {
    // Contexte et hook métier
    const { periodId } = useParams();
    const numericPeriodId = Number(periodId) || null;
    const { periodes, context, loading, refresh, selectedPeriodeId } = usePayrollCampaign(numericPeriodId);

    // Données dérivées
    const activePeriodId = numericPeriodId || selectedPeriodeId;
    const addVariableCapability = context?.availableActions?.ADD_VARIABLE;
    const canMutate = addVariableCapability?.allowed === true;
    const mutationDisabledReason = (addVariableCapability?.reasonCodes || []).join(', ') || 'La période est en lecture seule.';

    // Actions
    const handleVariableChange = () => refresh();

    // Rendu
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
