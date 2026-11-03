import { useNavigate, useParams } from 'react-router-dom';
import { lazy, Suspense, useEffect, useState } from 'react';
import PayrollCampaignShell from '@/features/payroll/components/workflow/PayrollCampaignShell';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import NextAction from '@/components/ui/feedback/NextAction';

const VariablesPaieTab = lazy(() => import('@/features/payroll/components/tabs/VariablesPaieTab'));

/**
 * VariablesStepPage — Étape 3 : Variables exceptionnelles (V1 Full V3).
 */
export default function VariablesStepPage() {
    const navigate = useNavigate();
    // Contexte et hook métier
    const { periodId } = useParams();
    const numericPeriodId = Number(periodId) || null;
    const { periodes, context, loading, refreshSilent, selectedPeriodeId } = usePayrollCampaign(numericPeriodId);

    // Données dérivées
    const activePeriodId = numericPeriodId || selectedPeriodeId;
    const addVariableCapability = context?.availableActions?.ADD_VARIABLE;
    const canMutate = addVariableCapability?.allowed === true;
    const mutationDisabledReason = (addVariableCapability?.reasonCodes || []).join(', ') || 'La période est en lecture seule.';
    const [pendingCount, setPendingCount] = useState(null);
    const [variablesError, setVariablesError] = useState(null);

    useEffect(() => { setPendingCount(null); setVariablesError(null); }, [activePeriodId]);

    // Actions
    const handleVariableChange = () => refreshSilent();

    // Rendu
    return (
        <PayrollCampaignShell
            title="Étape 3 — Variables par salarié"
            subtitle="Consultez le bulletin provisoire du salarié, puis préparez ses primes et retenues avant le calcul de la campagne."
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
                disabled: pendingCount === null || pendingCount > 0,
                disabledReason: variablesError
                    ? 'Impossible de vérifier les variables en attente suite à une erreur réseau.'
                    : pendingCount === null
                        ? 'Chargement des variables…'
                        : pendingCount > 0
                            ? `${pendingCount} variable(s) à approuver avant le calcul.`
                            : undefined,
            }}
        >
            {(pendingCount > 0 || variablesError) && <div className="mb-4">
                <NextAction
                    tone={pendingCount > 0 || variablesError ? 'warning' : 'success'}
                    title={variablesError ? 'Vérifier les EVP en attente' : pendingCount > 0 ? `Approuver ou corriger ${pendingCount} EVP en attente` : 'Passer au calcul de la paie'}
                    description={variablesError || (pendingCount > 0
                        ? 'Les variables non approuvées empêchent le calcul réel. Ouvrez le dossier du salarié concerné ci-dessous.'
                        : 'Les EVP de cette période ne bloquent plus le calcul.')}
                    actionLabel={pendingCount === 0 && !variablesError ? 'Ouvrir le calcul' : undefined}
                    onAction={pendingCount === 0 && !variablesError ? () => navigate(`/paie/periodes/${activePeriodId}/calcul`) : undefined}
                />
            </div>}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 sm:p-5">
                <Suspense fallback={<SkeletonTable rows={6} cols={6} />}>
                    <VariablesPaieTab
                        idPeriodeActive={activePeriodId}
                        periodEndDate={context?.selectedPeriod?.endDate || context?.currentPeriod?.endDate}
                        onVariableChanged={handleVariableChange}
                        onPendingCountChange={(count, err) => { setPendingCount(count); setVariablesError(err); }}
                        canMutate={canMutate}
                        mutationDisabledReason={mutationDisabledReason}
                        periodStatus={context?.selectedPeriod?.statutCode || context?.selectedPeriod?.statut || context?.selectedPeriod?.state || context?.currentPeriod?.statutCode || context?.currentPeriod?.statut || context?.currentPeriod?.state}
                    />
                </Suspense>
            </div>
        </PayrollCampaignShell>
    );
}
