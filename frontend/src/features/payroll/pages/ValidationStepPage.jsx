import { Textarea as SharedTextarea } from '@/components/ui/forms/Input';
import { useState, useCallback, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowRight, CheckCircle2, ShieldCheck, RefreshCw } from 'lucide-react';
import PayrollCampaignShell from '@/features/payroll/components/workflow/PayrollCampaignShell';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import Modal from '@/components/ui/overlays/Modal';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Badge from '@/components/ui/data-display/Badge';
import StatCard from '@/components/ui/data-display/StatCard';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import { useToast } from '@/components/ui/feedback/Toast';
import { Skeleton, SkeletonStatCard } from '@/components/ui/feedback/Skeleton';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import paieService from '@/features/payroll/api/paieService';
import { periodePaieService } from '@/features/attendance/api/attendanceApi';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { getErrorMessage } from '@/api/apiClient';

const CHECK_PRESENTATION = {
    PASS: { label: 'Conforme', variant: 'success' },
    WARNING: { label: 'À examiner', variant: 'warning' },
    BLOCK: { label: 'Bloquant', variant: 'danger' },
};

const formatAmount = (amount) => amount == null ? 'Non renseigné' : formatMoneyAr(amount);

/** Validation de campagne alimentée par le contexte et le cockpit backend. */
export default function ValidationStepPage() {
    const { periodId } = useParams();
    const navigate = useNavigate();
    const { toast } = useToast();
    const numericPeriodId = Number(periodId) || null;
    const {
        periodes,
        context,
        loading: ctxLoading,
        refreshSilent,
    } = usePayrollCampaign(numericPeriodId);

    const [cockpit, setCockpit] = useState(null);
    const [cockpitLoading, setCockpitLoading] = useState(true);
    const [financialError, setFinancialError] = useState('');
    const [actionLoading, setActionLoading] = useState(false);
    const [confirmModalOpen, setConfirmModalOpen] = useState(false);
    const [isReopenModalOpen, setIsReopenModalOpen] = useState(false);
    const [motifReouverture, setMotifReouverture] = useState('');

    const actions = context?.availableActions || {};
    const canValidate = actions.VALIDATE?.allowed === true;
    const canReopen = actions.REOPEN?.allowed === true;
    const reopenBlockedByPayment = actions.REOPEN?.reasonCodes?.includes('PAYMENT_OR_STC_ALREADY_RECORDED') === true;
    const periode = context?.selectedPeriod || context?.currentPeriod;
    const payrollStatus = periode?.workflowStatuses?.paie;
    const isValidee = ['VALIDEE', 'CLOTUREE'].includes(payrollStatus?.code);
    const isCloturee = payrollStatus?.code === 'CLOTUREE';
    const readiness = cockpit?.readiness || {};
    const checks = Array.isArray(readiness.checks) ? readiness.checks : [];
    const kpis = cockpit?.kpis || {};
    const summary = cockpit?.validationSummary || {};

    const loadCockpit = useCallback(() => {
        if (!numericPeriodId) return Promise.resolve();
        setCockpitLoading(true);
        setCockpit(null);
        setFinancialError('');
        return paieService.getCockpitPeriode(numericPeriodId)
            .then((data) => setCockpit(data || null))
            .catch((err) => setFinancialError(getErrorMessage(err, 'Résumé de validation indisponible.')))
            .finally(() => setCockpitLoading(false));
    }, [numericPeriodId]);

    useEffect(() => {
        let isMounted = true;
        if (!numericPeriodId) return undefined;
        setCockpitLoading(true);
        setFinancialError('');
        paieService.getCockpitPeriode(numericPeriodId)
            .then((data) => { if (isMounted) setCockpit(data || null); })
            .catch((err) => {
                if (isMounted) setFinancialError(getErrorMessage(err, 'Résumé de validation indisponible.'));
            })
            .finally(() => { if (isMounted) setCockpitLoading(false); });
        return () => { isMounted = false; };
    }, [numericPeriodId]);

    const refreshView = useCallback(async () => {
        await Promise.all([refreshSilent(), loadCockpit()]);
    }, [refreshSilent, loadCockpit]);

    const handleValider = useCallback(async () => {
        if (!numericPeriodId || !canValidate) return;
        setActionLoading(true);
        try {
            await paieService.validerPaiePeriode(numericPeriodId);
            toast.success('Campagne validée. Les bulletins sont scellés.', 'Validation réussie');
            setConfirmModalOpen(false);
            window.dispatchEvent(new CustomEvent('sirh:period-updated', { detail: { periodId: numericPeriodId } }));
            await refreshView();
        } catch (err) {
            toast.error(getErrorMessage(err, 'Validation impossible.'), 'Erreur de validation');
        } finally {
            setActionLoading(false);
        }
    }, [numericPeriodId, canValidate, refreshView, toast]);

    const handleReouvrirSubmit = useCallback(async (event) => {
        event.preventDefault();
        if (!canReopen || motifReouverture.trim().length < 5) return;
        setActionLoading(true);
        try {
            await periodePaieService.executeCommand(numericPeriodId, 'REOPEN', {
                version: periode?.version,
                motif: motifReouverture.trim(),
            });
            setIsReopenModalOpen(false);
            setMotifReouverture('');
            toast.success('Période réouverte pour correction.', 'Réouverture effectuée');
            window.dispatchEvent(new CustomEvent('sirh:period-updated', { detail: { periodId: numericPeriodId } }));
            await refreshView();
        } catch (err) {
            toast.error(getErrorMessage(err, 'Réouverture impossible.'), 'Erreur de réouverture');
        } finally {
            setActionLoading(false);
        }
    }, [numericPeriodId, canReopen, periode, motifReouverture, refreshView, toast]);

    const outputsDestination = context?.nextStep?.destination
        || context?.campaignSteps?.find((step) => step.code === 'OUTPUTS')?.destination
        || (numericPeriodId ? `/paie/periodes/${numericPeriodId}/sorties` : null);

    return (
        <PayrollCampaignShell
            context={context}
            periodes={periodes}
            activeStep="VALIDATION"
            loading={ctxLoading}
            title="Validation de la paie"
            subtitle="Vérifiez les contrôles et les totaux avant de valider la campagne."
            prevStep={{
                label: 'Contrôle',
                destination: `/paie/periodes/${numericPeriodId}/controle`,
            }}
            primaryAction={isValidee ? {
                label: 'Continuer vers les sorties',
                onClick: () => outputsDestination && navigate(outputsDestination),
                disabled: !outputsDestination,
                icon: <ArrowRight size={14} />,
            } : {
                label: 'Valider la paie',
                onClick: () => setConfirmModalOpen(true),
                loading: actionLoading,
                disabled: !canValidate || cockpitLoading || !cockpit,
                icon: <ShieldCheck size={14} />,
            }}
        >
            <div className="space-y-4">
                {financialError && <Alert type="error" title="Résumé indisponible" message={financialError} />}

                {isValidee && (
                    <Alert
                        type="success"
                        title={isCloturee ? 'Campagne clôturée' : 'Campagne validée'}
                        message={isCloturee
                            ? 'Les bulletins sont clôturés. Consultez les sorties réglementaires.'
                            : 'Les bulletins sont validés. Les sorties réglementaires sont disponibles.'}
                    />
                )}

                {!isValidee && context?.recalculationRequired && (
                    <Alert
                        type="warning"
                        title="Recalcul requis"
                        message={context.recalculationReason || 'Les données de paie ont changé depuis le dernier calcul.'}
                    />
                )}

                <Card>
                    <CardHeader>
                        <CardTitle size="sm">Contrôles de validation</CardTitle>
                    </CardHeader>
                    <CardBody>
                        {cockpitLoading ? (
                            <div className="space-y-3 py-2">
                                <Skeleton className="h-6 w-full" />
                                <Skeleton className="h-6 w-5/6" />
                                <Skeleton className="h-6 w-4/6" />
                            </div>
                        ) : checks.length > 0 ? (
                            <ul className="divide-y divide-[var(--color-border-subtle)]">
                                {checks.map((check) => {
                                    const presentation = CHECK_PRESENTATION[check.status] || CHECK_PRESENTATION.WARNING;
                                    return (
                                        <li key={check.code} className="flex items-start justify-between gap-4 py-3 first:pt-0 last:pb-0">
                                            <div className="flex min-w-0 items-start gap-2.5">
                                                <CheckCircle2
                                                    size={16}
                                                    aria-hidden="true"
                                                    className={check.status === 'PASS'
                                                        ? 'mt-0.5 shrink-0 text-[var(--color-success-600)]'
                                                        : check.status === 'BLOCK'
                                                            ? 'mt-0.5 shrink-0 text-[var(--color-danger-600)]'
                                                            : 'mt-0.5 shrink-0 text-[var(--color-warning-600)]'}
                                                />
                                                <p className="text-sm text-[var(--color-fg)]">{check.message}</p>
                                            </div>
                                            <Badge variant={presentation.variant} size="sm">{presentation.label}</Badge>
                                        </li>
                                    );
                                })}
                            </ul>
                        ) : (
                            <p className="text-sm text-[var(--color-fg-muted)]">Aucun contrôle disponible.</p>
                        )}

                        {!isValidee && !canValidate && !cockpitLoading && (
                            <Alert
                                type="warning"
                                className="mt-4"
                                title="Validation indisponible"
                                message="Les règles du serveur ne permettent pas encore de valider cette campagne."
                            />
                        )}
                    </CardBody>
                </Card>

                <Card>
                    <CardHeader>
                        <CardTitle size="sm">Synthèse financière</CardTitle>
                    </CardHeader>
                    <CardBody>
                        {cockpitLoading ? (
                            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">
                                {Array.from({ length: 6 }).map((_, i) => (
                                    <SkeletonStatCard key={i} />
                                ))}
                            </div>
                        ) : (
                            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">
                                <StatCard title="Salariés calculés" value={kpis.effectifCalcule ?? 0} color="neutral" subtext={`Population attendue : ${readiness.effectifAttendu ?? 'Non renseignée'}`} />
                                <StatCard title="Masse brute" value={formatAmount(kpis.totalBrut)} color="brand" subtext="Salaires bruts imposables" />
                                <StatCard title="Cotisations salariales" value={formatAmount(kpis.totalCotisationsSalariales)} color="info" subtext="Charges sociales déduites" />
                                <StatCard title="IRSA" value={formatAmount(kpis.totalIrsa)} color="warning" subtext="Impôt salarial retenu à la source" />
                                <StatCard title="Net à payer" value={formatAmount(summary.netTotal ?? kpis.totalNetAPayer)} color="success" subtext="Montant net des virements" />
                                <StatCard title="Charges patronales" value={formatAmount(summary.employerContributions ?? kpis.totalCotisationsPatronales)} color="neutral" subtext="Coût employeur CNaPS / Santé" />
                            </div>
                        )}
                    </CardBody>
                </Card>

                {canReopen && isCloturee && (
                    <div className="flex justify-end">
                        <Button
                            variant="secondary"
                            size="sm"
                            onClick={() => setIsReopenModalOpen(true)}
                            iconLeft={<RefreshCw size={14} />}
                        >
                            Rouvrir la période
                        </Button>
                    </div>
                )}
                {isCloturee && reopenBlockedByPayment && (
                    <Alert type="info" title="Réouverture indisponible"
                        message="Un bulletin possède déjà un règlement, un virement préparé ou un STC actif. Conservez cette période et saisissez la correction dans une campagne ultérieure." />
                )}
            </div>

            <ConfirmDialog
                isOpen={confirmModalOpen}
                onClose={() => setConfirmModalOpen(false)}
                onConfirm={handleValider}
                title="Confirmer la validation"
                message={`Valider la paie pour ${kpis.effectifCalcule ?? 0} salarié(s), pour un net total de ${formatAmount(summary.netTotal ?? kpis.totalNetAPayer)} ? Les bulletins seront scellés.`}
                confirmLabel="Valider la paie"
                variant="primary"
                loading={actionLoading}
            />

            <Modal
                isOpen={isReopenModalOpen}
                onClose={() => setIsReopenModalOpen(false)}
                title="Réouverture de la période"
                size="md"
            >
                <form onSubmit={handleReouvrirSubmit} className="space-y-4">
                    <p className="text-sm text-[var(--color-fg-muted)]">
                        Indiquez le motif requis pour l’audit de la réouverture.
                    </p>
                    <div>
                        <label htmlFor="reopen-reason" className="mb-1 block text-sm font-semibold text-[var(--color-fg)]">
                            Motif
                        </label>
                        <SharedTextarea plain
                            id="reopen-reason"
                            className="w-full border border-[var(--color-border)] bg-[var(--color-surface)] p-3 text-sm text-[var(--color-fg)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-focus-ring)]"
                            rows={3}
                            placeholder="Décrivez la correction à effectuer."
                            value={motifReouverture}
                            onChange={(event) => setMotifReouverture(event.target.value)}
                            minLength={5}
                            required
                        />
                    </div>
                    <div className="flex justify-end gap-2">
                        <Button type="button" variant="secondary" size="sm" onClick={() => setIsReopenModalOpen(false)}>
                            Annuler
                        </Button>
                        <Button variant="warning" size="sm" type="submit" loading={actionLoading} disabled={!canReopen || motifReouverture.trim().length < 5}>
                            Confirmer la réouverture
                        </Button>
                    </div>
                </form>
            </Modal>
        </PayrollCampaignShell>
    );
}
