import { useParams, useNavigate } from 'react-router-dom';
import { lazy, Suspense } from 'react';
import SEO from '@/components/system/SEO';
import PayrollCampaignHeader from '@/components/paie/PayrollCampaignHeader';
import PayrollNavigationFooter from '@/components/paie/PayrollNavigationFooter';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { usePayrollCampaign } from '@/hooks/paie/usePayrollCampaign';
import { FileText, Lock, ArrowLeft } from 'lucide-react';

const ExportsPaieTab = lazy(() => import('@/components/paie/ExportsPaieTab'));

/**
 * SortiesStepPage — Étape 7 : Bulletins & Exports réglementaires.
 *
 * Affiche les exports disponibles (SAFI, DNTS, Virement, EDI-ISI)
 * uniquement si la période est clôturée ou si les bulletins sont validés.
 * Réutilise le composant ExportsPaieTab existant dans le layout Sprint 2B.
 */
export default function SortiesStepPage() {
    const { periodId } = useParams();
    const navigate = useNavigate();
    const numericPeriodId = Number(periodId) || null;
    const { periodes, context, loading, refresh } = usePayrollCampaign(numericPeriodId);

    const actions = context?.availableActions || {};
    const outputCapability = actions?.GENERATE_OUTPUT;
    const outputs = context?.outputs || [];
    const periode = context?.selectedPeriod || context?.currentPeriod;
    const canOutput = outputCapability?.allowed === true;

    return (
        <div className="flex-1 flex flex-col min-h-full">
            <SEO
                title="Bulletins & Exports | Paie GALAXAM SIRH"
                description="Génération des bulletins de paie et des exports réglementaires SAFI, DNTS, Virement, EDI-ISI."
            />
            <PayrollCampaignHeader
                context={context}
                periodes={periodes}
                onSelectPeriod={(newId) => navigate(`/paie/periodes/${newId}/sorties`)}
                activeStep="OUTPUTS"
                loading={loading}
                onRefresh={refresh}
            />

            <div className="flex-1 max-w-6xl mx-auto px-4 py-6 space-y-6 w-full">

                {/* En-tête */}
                <div className="flex items-start gap-3">
                    <div className="w-10 h-10 bg-gradient-to-br from-[var(--color-brand-400)] to-[var(--color-brand-600)] text-white flex items-center justify-center shrink-0">
                        <FileText size={20} strokeWidth={2} />
                    </div>
                    <div>
                        <h1 className="text-base font-bold text-[var(--color-fg)] tracking-tight">
                            Étape 7 — Bulletins & Exports réglementaires
                        </h1>
                        <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                            Téléchargez les bulletins individuels et les fichiers déclaratifs (SAFI, DNTS CNaPS, Virement, EDI-ISI).
                        </p>
                    </div>
                </div>

                {/* Blocage si pas d'accès outputs */}
                {!canOutput && !loading && (
                    <Alert
                        type="warning"
                        title="Exports réglementaires non disponibles"
                        message={
                            (outputCapability?.reasonCodes || []).includes('PAYROLL_NOT_VALIDATED')
                                ? 'L’ensemble des bulletins de paie doivent être validés par la DAF avant de pouvoir générer les déclarations légales et fichiers de virement.'
                                : 'La campagne de paie n’est pas encore au statut permettant l’accès aux sorties réglementaires.'
                        }
                        action={
                            <Button
                                variant="outline"
                                size="xs"
                                onClick={() => navigate(`/paie/periodes/${numericPeriodId}/validation`)}
                                iconLeft={<ArrowLeft size={13} />}
                            >
                                Retour à la Validation
                            </Button>
                        }
                    />
                )}

                {/* Composant Exports existant */}
                {canOutput && (
                    <Suspense fallback={<SkeletonTable rows={6} cols={4} />}>
                        <ExportsPaieTab
                            idPeriodeActive={numericPeriodId}
                            codePeriode={periode?.code || ''}
                            outputCapability={outputCapability}
                            outputs={outputs}
                        />
                    </Suspense>
                )}
            </div>

            <PayrollNavigationFooter
                prevStep={{
                    label: 'Validation & Clôture',
                    destination: `/paie/periodes/${numericPeriodId}/validation`,
                }}
                nextStep={{
                    label: 'Campagnes de paie',
                    destination: '/paie',
                }}
            />
        </div>
    );
}
